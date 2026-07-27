package io.github.officiallymikey7.mini.governance;

/** An active rule in the agent community's constitution. */
public final class Rule {
    public final String id;
    public final String text;
    public final long addedAt;

    public Rule(String id, String text, long addedAt) {
        this.id = id;
        this.text = text;
        this.addedAt = addedAt;
    }
}
