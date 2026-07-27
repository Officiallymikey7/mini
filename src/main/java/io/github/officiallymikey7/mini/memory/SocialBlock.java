package io.github.officiallymikey7.mini.memory;

import java.util.List;

/** The [Social Horizon Block] appended to every planner prompt. */
public final class SocialBlock {
    /** Human-readable multi-line summary. */
    public final String summary;
    public final List<ChatMessage> messages;

    public SocialBlock(String summary, List<ChatMessage> messages) {
        this.summary = summary;
        this.messages = List.copyOf(messages);
    }
}
