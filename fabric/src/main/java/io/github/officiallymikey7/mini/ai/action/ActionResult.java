package io.github.officiallymikey7.mini.ai.action;

/**
 * Result returned by an action after one execution tick.
 *
 * <p>The status indicates how the action finished (or is progressing), while
 * {@link #failureReason} carries a human-readable code for diagnostics when
 * the status is {@link Status#FAILED}.
 */
public final class ActionResult {

    /** Possible outcomes of a single action tick or an overall action. */
    public enum Status {
        /** Action is still ongoing – call again next tick. */
        IN_PROGRESS,
        /** Action completed successfully. */
        SUCCESS,
        /** Action cannot proceed; see {@link ActionResult#failureReason}. */
        FAILED
    }

    /** Well-known failure reason codes for debug logging and retry logic. */
    public static final String REASON_NO_TARGET  = "NO_TARGET";
    public static final String REASON_NO_PATH    = "NO_PATH";
    public static final String REASON_BLOCKED    = "BLOCKED";
    public static final String REASON_TIMEOUT    = "TIMEOUT";
    public static final String REASON_STUCK      = "STUCK";
    public static final String REASON_MAX_RETRY  = "MAX_RETRY";

    public final Status status;

    /**
     * Short failure code (one of the {@code REASON_*} constants, or a custom
     * string) when {@link #status} is {@link Status#FAILED}, otherwise {@code null}.
     */
    public final String failureReason;

    private ActionResult(Status status, String failureReason) {
        this.status        = status;
        this.failureReason = failureReason;
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    /** Returns a result indicating the action is still in progress. */
    public static ActionResult inProgress() {
        return new ActionResult(Status.IN_PROGRESS, null);
    }

    /** Returns a result indicating the action succeeded. */
    public static ActionResult success() {
        return new ActionResult(Status.SUCCESS, null);
    }

    /**
     * Returns a result indicating the action failed with the given reason code.
     *
     * @param reason one of the {@code REASON_*} constants or a custom string
     */
    public static ActionResult failed(String reason) {
        return new ActionResult(Status.FAILED, reason);
    }

    // ── Convenience predicates ────────────────────────────────────────────────

    public boolean isSuccess()    { return status == Status.SUCCESS;     }
    public boolean isFailed()     { return status == Status.FAILED;      }
    public boolean isInProgress() { return status == Status.IN_PROGRESS; }

    @Override
    public String toString() {
        return failureReason == null ? status.name() : status + "(" + failureReason + ")";
    }
}
