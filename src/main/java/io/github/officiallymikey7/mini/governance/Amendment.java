package io.github.officiallymikey7.mini.governance;

import java.util.HashMap;
import java.util.Map;

/**
 * A proposed addition or replacement of a constitution rule.
 *
 * <p>{@code ruleId == null} means a brand-new rule is proposed;
 * a non-null ruleId means the existing rule with that id will be replaced.
 */
public final class Amendment {
    public final String id;
    public final String proposedBy;
    public final long proposedAt;
    /** {@code null} for a new rule; the existing rule ID to replace otherwise. */
    public final String ruleId;
    public final String proposedText;
    /** Mutable: updated by {@link Constitution#vote} and {@link Constitution#tally}. */
    public AmendmentStatus status;
    /** agentName → "yes" or "no". */
    public final Map<String, String> votes = new HashMap<>();

    public Amendment(String id, String proposedBy, long proposedAt,
                     String ruleId, String proposedText) {
        this.id           = id;
        this.proposedBy   = proposedBy;
        this.proposedAt   = proposedAt;
        this.ruleId       = ruleId;
        this.proposedText = proposedText;
        this.status       = AmendmentStatus.OPEN;
    }
}
