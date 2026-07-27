package io.github.officiallymikey7.mini.core;

/** Result returned after executing one subgoal action. */
public final class ExecutionOutcome {
    public final String subgoalId;
    public final String action;
    public final ExecutionStatus status;
    public final String message;
    public final long durationMs;

    public ExecutionOutcome(String subgoalId, String action,
                            ExecutionStatus status, String message, long durationMs) {
        this.subgoalId = subgoalId;
        this.action = action;
        this.status = status;
        this.message = message;
        this.durationMs = durationMs;
    }
}
