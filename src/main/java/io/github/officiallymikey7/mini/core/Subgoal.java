package io.github.officiallymikey7.mini.core;

/** A single planned subgoal the agent will attempt to execute. */
public final class Subgoal {
    public final String id;
    public final String description;
    /** Top-level action string forwarded to the executor. */
    public final String action;
    /** Tag indicating how this subgoal was selected. */
    public final SubgoalTag tag;
    /** Higher value = more urgent. */
    public final double priority;

    public Subgoal(String id, String description, String action,
                   SubgoalTag tag, double priority) {
        this.id = id;
        this.description = description;
        this.action = action;
        this.tag = tag;
        this.priority = priority;
    }
}
