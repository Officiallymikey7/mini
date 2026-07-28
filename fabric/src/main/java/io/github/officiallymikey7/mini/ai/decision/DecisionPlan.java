package io.github.officiallymikey7.mini.ai.decision;

import net.minecraft.util.math.BlockPos;

/**
 * Immutable plan produced by the decision phase of the PDA loop.
 *
 * <p>Describes the chosen action name, an optional world-space target, and
 * retry/timeout metadata that the action executor can use to avoid getting
 * stuck indefinitely.
 */
public final class DecisionPlan {

    /** Name of the chosen action (matches the switch labels in VillagerBody). */
    public final String actionName;

    /** Optional target block position for the action, or {@code null}. */
    public final BlockPos targetPos;

    /**
     * Maximum number of server ticks this plan may run before being considered
     * timed-out. A value of {@code 0} means no limit.
     */
    public final int timeoutTicks;

    /**
     * Maximum number of consecutive times the action may fail before the plan
     * is abandoned and replanning is triggered.
     */
    public final int maxRetries;

    public DecisionPlan(String actionName, BlockPos targetPos, int timeoutTicks, int maxRetries) {
        this.actionName   = actionName;
        this.targetPos    = targetPos;
        this.timeoutTicks = timeoutTicks;
        this.maxRetries   = maxRetries;
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    /** Creates a plan with default timeout (400 ticks / ~20 s) and 3 retries. */
    public static DecisionPlan of(String actionName) {
        return new DecisionPlan(actionName, null, 400, 3);
    }

    /** Creates a targeted plan with default timeout (400 ticks) and 3 retries. */
    public static DecisionPlan of(String actionName, BlockPos target) {
        return new DecisionPlan(actionName, target, 400, 3);
    }

    /** Creates a plan with explicit timeout and retry limits. */
    public static DecisionPlan of(String actionName, BlockPos target, int timeoutTicks, int maxRetries) {
        return new DecisionPlan(actionName, target, timeoutTicks, maxRetries);
    }

    @Override
    public String toString() {
        return "DecisionPlan{action='" + actionName + '\''
                + ", target=" + targetPos
                + ", timeout=" + timeoutTicks
                + ", maxRetries=" + maxRetries + '}';
    }
}
