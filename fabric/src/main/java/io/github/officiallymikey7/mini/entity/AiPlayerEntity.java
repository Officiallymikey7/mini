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
        // Priority 0 – parkour override: activates when jumping / bridging is required
        this.goalSelector.add(0, new ParkourMoveGoal(this));
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

    /**
     * Triggers a jump.  Safe to call on the server tick thread; the jump impulse
     * is applied on the next physics tick via {@link net.minecraft.entity.LivingEntity#jump()}.
     */
    public void aiJump() {
        this.jump();
    }

    /**
     * Attempts to place a solid block directly under the agent's feet to gain
     * elevation (pillar-up) or bridge over a gap.
     *
     * <p>The entity must hold a placeable block in its main hand.  This method
     * calls the entity's use-item logic against the block face below, which
     * triggers the standard block-placement pipeline including adventure-mode
     * and claim checks.
     *
     * <p>Returns {@code true} when a placement attempt was dispatched.
     * Success is not guaranteed – the block may fail to place if the face is
     * obstructed or the item is not a block.
     *
     * @return {@code true} when the placement attempt was made
     */
    public boolean aiPlaceBlockUnderFoot() {
        net.minecraft.util.math.BlockPos below = this.getBlockPos().down();
        net.minecraft.world.World w = this.getWorld();
        if (!w.isClient() && w.getBlockState(below).isAir()) {
            net.minecraft.item.ItemStack stack = this.getMainHandStack();
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                net.minecraft.util.math.BlockPos target = below;
                net.minecraft.item.ItemPlacementContext ctx =
                        new net.minecraft.item.ItemPlacementContext(
                                new net.minecraft.item.ItemUsageContext(
                                        (net.minecraft.server.world.ServerWorld) w,
                                        null,
                                        net.minecraft.util.Hand.MAIN_HAND,
                                        stack,
                                        new net.minecraft.util.hit.BlockHitResult(
                                                this.getPos(),
                                                net.minecraft.util.math.Direction.UP,
                                                target,
                                                false)));
                blockItem.place(ctx);
                return true;
            }
        }
        return false;
    }

    /**
     * Performs a jump-and-place sequence to ascend one block height (pillar up).
     * Calls {@link #aiJump()} followed by {@link #aiPlaceBlockUnderFoot()} so the
     * block lands just as the entity clears the ledge.
     *
     * @return {@code true} when both the jump impulse and placement were dispatched
     */
    public boolean aiPillarUp() {
        aiJump();
        return aiPlaceBlockUnderFoot();
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
