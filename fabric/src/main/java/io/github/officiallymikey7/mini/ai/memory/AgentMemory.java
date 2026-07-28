package io.github.officiallymikey7.mini.ai.memory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight short-term memory store for one villager agent.
 *
 * <p>Phase 1 placeholder – records the last N action events (action name +
 * failure reason) for debug introspection. More sophisticated episodic and
 * semantic memory layers will be added in later phases.
 */
public final class AgentMemory {

    /** Maximum number of events kept in the short-term ring buffer. */
    private static final int MAX_EVENTS = 10;

    /** Immutable record of a past action outcome. */
    public record ActionEvent(String action, String outcome, long tickStamp) {}

    private final Deque<ActionEvent> recentEvents = new ArrayDeque<>(MAX_EVENTS);

    /**
     * Records an action outcome.
     *
     * @param action    action name (e.g. "gather_food")
     * @param outcome   result string (e.g. "SUCCESS", "FAILED(NO_TARGET)")
     * @param tick      current server tick count
     */
    public void recordAction(String action, String outcome, long tick) {
        if (recentEvents.size() >= MAX_EVENTS) {
            recentEvents.pollFirst();
        }
        recentEvents.addLast(new ActionEvent(action, outcome, tick));
    }

    /**
     * Returns an unmodifiable view of recent events, oldest first.
     */
    public List<ActionEvent> getRecentEvents() {
        return Collections.unmodifiableList(recentEvents.stream().toList());
    }

    /** Clears all recorded events. */
    public void clear() {
        recentEvents.clear();
    }
}
