package io.github.officiallymikey7.mini.entity.goal;

import io.github.officiallymikey7.mini.entity.AiPlayerEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI {@link Goal} that moves an {@link AiPlayerEntity} toward a destination
 * set by the external AI engine.
 *
 * <p>Design notes:
 * <ul>
 *   <li>The goal holds exclusive control of the {@link Goal.Control#MOVE} slot so it
 *       does not compete with the idle wander goal while active.</li>
 *   <li>Paths are recalculated when the destination changes <em>or</em> after
 *       {@value #REPATH_INTERVAL} ticks — this keeps the path fresh if terrain
 *       changes (e.g. falling blocks) without saturating the path-finder.</li>
 *   <li>Arrival is declared when the entity is within {@value #ARRIVAL_THRESHOLD}
 *       blocks of the target; at that point navigation is stopped and the
 *       destination flag is cleared.</li>
 * </ul>
 */
public final class AiMoveToGoal extends Goal {

    /** Distance (blocks) at which the entity is considered to have arrived. */
    private static final double ARRIVAL_THRESHOLD = 1.0;

    /**
     * Maximum server ticks between forced repath calls.
     * At 20 TPS this is 1 second — frequent enough to track a moving player
     * without hammering the path-finder every tick.
     */
    private static final int REPATH_INTERVAL = 20;

    private final AiPlayerEntity  entity;
    private final EntityNavigation nav;

    private double lastDestX;
    private double lastDestY;
    private double lastDestZ;
    private int    repathTimer;

    public AiMoveToGoal(AiPlayerEntity entity) {
        this.entity = entity;
        this.nav    = entity.getNavigation();
        // Claim the MOVE control slot exclusively — displaces idle WanderAroundGoal
        setControls(EnumSet.of(Goal.Control.MOVE));
    }

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    /** Start when the agent has set a destination. */
    @Override
    public boolean canStart() {
        return entity.hasDestination();
    }

    /**
     * Keep running while the agent still has a destination and the entity has
     * not arrived yet.
     */
    @Override
    public boolean shouldContinue() {
        if (!entity.hasDestination()) return false;
        Vec3d dest = new Vec3d(entity.getDestX(), entity.getDestY(), entity.getDestZ());
        return entity.getPos().distanceTo(dest) > ARRIVAL_THRESHOLD;
    }

    /** Issue the first path request on goal start. */
    @Override
    public void start() {
        repathTimer = 0;
        pathToDestination();
        lastDestX = entity.getDestX();
        lastDestY = entity.getDestY();
        lastDestZ = entity.getDestZ();
    }

    /**
     * Each tick: check if the destination moved; repath on the periodic interval
     * or immediately when the target has changed.
     */
    @Override
    public void tick() {
        double dx = entity.getDestX();
        double dy = entity.getDestY();
        double dz = entity.getDestZ();

        repathTimer++;
        boolean destChanged = dx != lastDestX || dy != lastDestY || dz != lastDestZ;

        if (destChanged || repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            pathToDestination();
            lastDestX = dx;
            lastDestY = dy;
            lastDestZ = dz;
        }
    }

    /** Stop navigation and clear the destination so idle goals resume. */
    @Override
    public void stop() {
        nav.stop();
        entity.clearDestination();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Issues a path request to the entity's {@link EntityNavigation}.
     *
     * <p>{@code EntityNavigation.startMovingTo} handles all terrain logic
     * internally: it uses a block-level A* search that accounts for jump heights,
     * water depth, and fall damage avoidance.
     */
    private void pathToDestination() {
        nav.startMovingTo(entity.getDestX(), entity.getDestY(), entity.getDestZ(), entity.getDestSpeed());
    }
}
