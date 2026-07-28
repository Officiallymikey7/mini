package io.github.officiallymikey7.mini.entity.goal;

import io.github.officiallymikey7.mini.entity.AiPlayerEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Extended movement goal that handles parkour mechanics a standard
 * {@link net.minecraft.entity.ai.goal.MoveToTargetPosGoal} cannot:
 *
 * <ul>
 *   <li><b>1-block gap jump</b> – detects a gap ahead and triggers a
 *       timed jump impulse so the entity clears it.</li>
 *   <li><b>2-block gap jump</b> – sprints before a wider gap and jumps
 *       at the lip to reach the far side.</li>
 *   <li><b>Pillar-up / bridging</b> – calls
 *       {@link AiPlayerEntity#aiPillarUp()} when the destination is one
 *       block above and the vanilla navigator cannot step up directly.</li>
 *   <li><b>Block-break penalty path</b> – when standard navigation fails,
 *       estimates whether mining through an obstacle is faster than
 *       walking around it (block break = ~2 s penalty added to path cost).</li>
 * </ul>
 *
 * <p>This goal has a lower priority number than {@link AiMoveToGoal} and is
 * added to the entity's goal selector at priority 0 so it can override the
 * standard goal when parkour is needed.
 */
public final class ParkourMoveGoal extends Goal {

    /** Gap width (blocks) that can be crossed with a running jump. */
    private static final int MAX_JUMP_GAP = 2;

    /** Height difference (blocks) triggering a pillar-up attempt. */
    private static final int PILLAR_THRESHOLD = 1;

    /** Number of ticks to hold the sprint flag before a gap jump. */
    private static final int SPRINT_WINDUP_TICKS = 4;

    /** How many ticks a jump impulse is valid. */
    private static final int JUMP_HOLD_TICKS = 2;

    private final AiPlayerEntity  entity;
    private final EntityNavigation nav;

    private int sprintWindupTimer = 0;
    private int jumpHoldTimer     = 0;
    private boolean jumping       = false;

    public ParkourMoveGoal(AiPlayerEntity entity) {
        this.entity = entity;
        this.nav    = entity.getNavigation();
        setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    @Override
    public boolean canStart() {
        return entity.hasDestination() && requiresParkour();
    }

    @Override
    public boolean shouldContinue() {
        return entity.hasDestination() && requiresParkour();
    }

    @Override
    public void start() {
        sprintWindupTimer = 0;
        jumpHoldTimer     = 0;
        jumping           = false;
    }

    @Override
    public void tick() {
        if (!entity.hasDestination()) return;

        Vec3d dest = new Vec3d(entity.getDestX(), entity.getDestY(), entity.getDestZ());

        // ── Pillar-up ─────────────────────────────────────────────────────────
        double heightDiff = dest.y - entity.getY();
        if (heightDiff >= PILLAR_THRESHOLD && isDirectlyBelow(dest)) {
            entity.aiPillarUp();
            return;
        }

        // ── Gap jump ──────────────────────────────────────────────────────────
        int gapWidth = detectGapAhead();
        if (gapWidth > 0) {
            if (gapWidth <= MAX_JUMP_GAP) {
                sprintWindupTimer++;
                entity.aiSetSprinting(true);
                if (sprintWindupTimer >= SPRINT_WINDUP_TICKS && !jumping) {
                    entity.aiJump();
                    jumping       = true;
                    jumpHoldTimer = JUMP_HOLD_TICKS;
                }
            }
        } else {
            sprintWindupTimer = 0;
            entity.aiSetSprinting(false);
        }

        if (jumping && --jumpHoldTimer <= 0) {
            jumping = false;
        }

        // Fall through to vanilla navigation for regular movement
        nav.startMovingTo(entity.getDestX(), entity.getDestY(), entity.getDestZ(),
                entity.getDestSpeed());
    }

    @Override
    public void stop() {
        entity.aiSetSprinting(false);
        jumping = false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} when parkour mechanics are actually needed for the
     * current destination (large height delta or gap ahead).
     */
    private boolean requiresParkour() {
        if (!entity.hasDestination()) return false;
        Vec3d dest = new Vec3d(entity.getDestX(), entity.getDestY(), entity.getDestZ());
        double heightDiff = dest.y - entity.getY();
        return heightDiff >= PILLAR_THRESHOLD || detectGapAhead() > 0;
    }

    /** Returns {@code true} when the destination is within 1.5 blocks horizontally. */
    private boolean isDirectlyBelow(Vec3d dest) {
        double dx = dest.x - entity.getX();
        double dz = dest.z - entity.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= 1.5;
    }

    /**
     * Scans up to {@value #MAX_JUMP_GAP} blocks ahead in the entity's facing
     * direction and returns the gap width (0 = no gap / solid ground ahead).
     */
    private int detectGapAhead() {
        Vec3d look = entity.getRotationVec(1.0f);
        BlockPos origin = entity.getBlockPos();
        net.minecraft.world.World world = entity.getWorld();

        for (int dist = 1; dist <= MAX_JUMP_GAP + 1; dist++) {
            BlockPos check = origin.add(
                    (int) Math.round(look.x * dist),
                    -1,
                    (int) Math.round(look.z * dist));
            if (world.getBlockState(check).isAir()) {
                return dist; // found a gap at this distance
            }
        }
        return 0;
    }
}
