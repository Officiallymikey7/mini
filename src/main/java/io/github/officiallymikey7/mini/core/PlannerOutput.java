package io.github.officiallymikey7.mini.core;

/** The selected subgoal and the prompt context used to derive it. */
public final class PlannerOutput {
    public final Subgoal subgoal;
    /** Full prompt string (for debugging / logging). */
    public final String promptContext;

    public PlannerOutput(Subgoal subgoal, String promptContext) {
        this.subgoal = subgoal;
        this.promptContext = promptContext;
    }
}
