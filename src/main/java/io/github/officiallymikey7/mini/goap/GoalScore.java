package io.github.officiallymikey7.mini.goap;

import io.github.officiallymikey7.mini.core.NeedType;

/**
 * A utility score for a specific goal type, produced by {@link GOAPPlanner#evaluateHighestUtility}.
 *
 * @param goalType   the goal this score represents
 * @param score      utility score in the range 0–100
 * @param reasoning  human-readable explanation of why this score was assigned
 */
public record GoalScore(NeedType goalType, double score, String reasoning) {

    /** Returns {@code true} when this goal has a non-trivial score (≥ 1). */
    public boolean isActive() {
        return score >= 1.0;
    }
}
