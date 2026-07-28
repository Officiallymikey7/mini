package io.github.officiallymikey7.mini.core;

/** Urgency score for one survival need. Higher score = more urgent (0–100). */
public final class NeedScore {
    public final NeedType need;
    /** Urgency value 0–100. */
    public final double score;
    /** Human-readable explanation of the score. */
    public final String reason;

    public NeedScore(NeedType need, double score, String reason) {
        this.need = need;
        this.score = score;
        this.reason = reason;
    }
}
