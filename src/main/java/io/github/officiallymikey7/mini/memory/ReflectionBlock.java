package io.github.officiallymikey7.mini.memory;

import java.util.List;

/** The [Self-Reflection Block] appended to every planner prompt. */
public final class ReflectionBlock {
    /** Human-readable multi-line summary. */
    public final String summary;
    public final List<ReflectionEntry> entries;
    /**
     * Actions the agent has performed successfully at least
     * {@link ReflectionMemory#MASTERY_THRESHOLD} times.
     */
    public final List<String> masteredSkills;

    public ReflectionBlock(String summary, List<ReflectionEntry> entries, List<String> masteredSkills) {
        this.summary = summary;
        this.entries = List.copyOf(entries);
        this.masteredSkills = List.copyOf(masteredSkills);
    }
}
