package io.github.officiallymikey7.mini.core;

import io.github.officiallymikey7.mini.integration.BotAdapter;

/**
 * Executes a subgoal action via the bot adapter and returns an outcome.
 * A configurable timeout causes a TIMEOUT result if the action takes too long.
 */
public final class Executor {

    private static final long DEFAULT_TIMEOUT_MS = 15_000;

    private Executor() {}

    public static ExecutionOutcome execute(BotAdapter adapter, Subgoal subgoal) {
        return execute(adapter, subgoal, DEFAULT_TIMEOUT_MS);
    }

    public static ExecutionOutcome execute(BotAdapter adapter, Subgoal subgoal, long timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            String result = adapter.performAction(subgoal.action);
            long duration = System.currentTimeMillis() - start;
            if (duration > timeoutMs) {
                return new ExecutionOutcome(subgoal.id, subgoal.action,
                        ExecutionStatus.TIMEOUT,
                        "Action timed out after " + timeoutMs + "ms", duration);
            }
            boolean success = !result.toLowerCase().startsWith("error:");
            return new ExecutionOutcome(subgoal.id, subgoal.action,
                    success ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE,
                    result, duration);
        } catch (Exception e) {
            return new ExecutionOutcome(subgoal.id, subgoal.action,
                    ExecutionStatus.FAILURE,
                    "Error: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
