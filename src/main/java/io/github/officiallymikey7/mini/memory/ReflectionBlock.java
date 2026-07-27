package io.github.officiallymikey7.mini.memory;

import java.util.List;

/** The [Self-Reflection Block] appended to every planner prompt. */
public final class ReflectionBlock {
    /** Human-readable multi-line summary. */
    public final String summary;
    public final List<ReflectionEntry> entries;

    public ReflectionBlock(String summary, List<ReflectionEntry> entries) {
        this.summary = summary;
        this.entries = List.copyOf(entries);
    }
}
