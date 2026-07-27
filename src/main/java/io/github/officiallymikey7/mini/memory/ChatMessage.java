package io.github.officiallymikey7.mini.memory;

/** A chat message observed from a nearby player or agent. */
public final class ChatMessage {
    public final String sender;
    public final String text;
    public final long timestamp;

    public ChatMessage(String sender, String text, long timestamp) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
    }
}
