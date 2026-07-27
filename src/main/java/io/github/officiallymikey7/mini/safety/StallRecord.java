package io.github.officiallymikey7.mini.safety;

/** Tracks how many times the agent has attempted a specific subgoal. */
public final class StallRecord {
    public final String subgoalId;
    public int attempts;
    public final long firstAttemptAt;
    public long lastAttemptAt;

    public StallRecord(String subgoalId, long now) {
        this.subgoalId      = subgoalId;
        this.attempts       = 1;
        this.firstAttemptAt = now;
        this.lastAttemptAt  = now;
    }
}
