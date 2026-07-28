package io.github.officiallymikey7.mini.entity;

import io.github.officiallymikey7.mini.entity.goal.AiMoveToGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Custom entity that serves as the physical in-world body for the mini AI agent.
 *
 * <p>It extends {@link PathAwareEntity} so it inherits full Minecraft pathfinding
 * (terrain-aware navigation, block-edge jumping, drowning/fall avoidance), limb
 * animations, and the standard mob goal/target system.
 *
 * <p><b>AI engine integration:</b> The external agent code drives this entity by
 * calling the {@code ai*()} hook methods below.  An internal {@link AiMoveToGoal}
 * reads the destination fields and issues the actual path requests; this decoupling
 * keeps the agent tick rate independent of Minecraft's 20 TPS goal tick.
 */
public class AiPlayerEntity extends PathAwareEntity {

    // ── Destination state (written by agent, read by AiMoveToGoal) ───────────

    private double destX;
    private double destY;
    private double destZ;
    private double destSpeed = 0.5;
    private boolean hasDestination = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AiPlayerEntity(EntityType<? extends AiPlayerEntity> type, World world) {
        super(type, world);
    }

    // ── Goal registration ─────────────────────────────────────────────────────

    @Override
    protected void initGoals() {
        // Priority 1 – AI-driven navigation; starts only when hasDestination is true
        this.goalSelector.add(1, new AiMoveToGoal(this));
        // Priority 2/3 – idle wander + look-around when the agent has no destination
        this.goalSelector.add(2, new WanderAroundGoal(this, 0.4));
        this.goalSelector.add(3, new LookAroundGoal(this));
    }

    // ── Attribute definition ──────────────────────────────────────────────────

    /**
     * Returns the default attribute map.  Must be registered via
     * {@link net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry}
     * during mod init (see {@link EntityTypes#register()}).
     */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    // ── AI engine hooks ───────────────────────────────────────────────────────

    /**
     * Instructs the entity to path to {@code (x, y, z)} at the given speed.
     * The {@link AiMoveToGoal} picks this up on its next tick and issues a
     * fresh path request to {@link net.minecraft.entity.ai.pathing.EntityNavigation}.
     *
     * @param x target block X (centre)
     * @param y target block Y
     * @param z target block Z (centre)
     * @param speed walk speed multiplier (1.0 = base mob speed)
     */
    public void aiMoveTo(double x, double y, double z, double speed) {
        this.destX = x;
        this.destY = y;
        this.destZ = z;
        this.destSpeed = speed;
        this.hasDestination = true;
    }

    /**
     * Rotates the entity's head toward {@code (x, y, z)} over the next few ticks.
     * Works through Minecraft's built-in {@code LookControl} so head yaw/pitch
     * interpolate smoothly.
     *
     * @param x look-target world X
     * @param y look-target world Y (typically entity eye height of the target)
     * @param z look-target world Z
     */
    public void aiLookAt(double x, double y, double z) {
        // maxYawChange=30°, maxPitchChange=30° per tick – matches vanilla mob behaviour
        this.getLookControl().lookAt(x, y, z, 30f, 30f);
    }

    /**
     * Triggers a main-hand swing animation.  The animation is purely visual and
     * does not deal damage.
     */
    public void aiSwingArm() {
        this.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Enables or disables the sprinting state.  When {@code true} the movement
     * speed is multiplied by the base sprinting modifier and the leg animation
     * frequency increases.
     *
     * @param sprinting {@code true} to sprint, {@code false} to walk
     */
    public void aiSetSprinting(boolean sprinting) {
        this.setSprinting(sprinting);
    }

    /**
     * Enables or disables the sneaking (crouching) pose.  When {@code true}
     * {@link net.minecraft.entity.LivingEntity#isInSneakingPose()} returns
     * {@code true}, which causes {@link net.minecraft.client.render.entity.model.BipedEntityModel}
     * to render the entity in its crouched stance automatically.
     *
     * @param sneaking {@code true} to crouch, {@code false} to stand upright
     */
    public void aiSetSneaking(boolean sneaking) {
        this.setSneaking(sneaking);
    }

    // ── Package-private accessors for AiMoveToGoal ───────────────────────────

    /** @return {@code true} when the agent has set a movement target. */
    public boolean hasDestination() { return hasDestination; }

    /** @return X component of the current movement target. */
    public double getDestX() { return destX; }

    /** @return Y component of the current movement target. */
    public double getDestY() { return destY; }

    /** @return Z component of the current movement target. */
    public double getDestZ() { return destZ; }

    /** @return Requested movement speed multiplier. */
    public double getDestSpeed() { return destSpeed; }

    /**
     * Clears the movement target so the entity returns to idle goals.
     * Called by {@link AiMoveToGoal#stop()} when navigation finishes.
     */
    public void clearDestination() { hasDestination = false; }
}
