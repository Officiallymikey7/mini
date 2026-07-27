package io.github.officiallymikey7.mini.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maintains a rolling window of nearby chat messages and builds the
 * [Social Horizon Block] summary injected into every planner prompt.
 */
public final class SocialMemory {

    private static final int MAX_MESSAGES = 20;
    /** Only include messages from the last N milliseconds in the summary. */
    private static final long HORIZON_MS = 120_000; // 2 minutes

    private final List<ChatMessage> messages = new ArrayList<>();

    /** Ingest new chat strings observed this tick. */
    public void ingest(List<String> newMessages, String defaultSender) {
        long now = System.currentTimeMillis();
        for (String text : newMessages) {
            // Parse "SenderName: message" format if present
            int colonIdx = text.indexOf(':');
            String sender = defaultSender;
            String body   = text;
            if (colonIdx > 0 && colonIdx < 20) {
                sender = text.substring(0, colonIdx).trim();
                body   = text.substring(colonIdx + 1).trim();
            }
            messages.add(new ChatMessage(sender, body, now));
        }
        if (messages.size() > MAX_MESSAGES) {
            messages.subList(0, messages.size() - MAX_MESSAGES).clear();
        }
    }

    /** Build the [Social Horizon Block] summary string. */
    public SocialBlock build() {
        long now = System.currentTimeMillis();
        List<ChatMessage> recent = messages.stream()
                .filter(m -> now - m.timestamp <= HORIZON_MS)
                .collect(Collectors.toList());

        if (recent.isEmpty()) {
            return new SocialBlock("No recent nearby activity observed.", List.of());
        }

        List<String> lines = new ArrayList<>();
        lines.add("Nearby agent/player messages:");
        for (ChatMessage m : recent) {
            lines.add("  " + m.sender + ": \"" + m.text + "\"");
        }
        return new SocialBlock(String.join("\n", lines), recent);
    }

    public List<ChatMessage> getAll() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
