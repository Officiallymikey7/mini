package io.github.officiallymikey7.mini.safety;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects "goal obsession" / stalling: when the agent repeatedly attempts the
 * same subgoal or exceeds a time budget without progress.
 *
 * <p>On stall detection the guard forces replanning to a prerequisite or
 * alternative subgoal rather than retrying the blocked action indefinitely.
 */
public final class StallGuard {

    /** Maximum consecutive attempts before a stall is declared. */
    private final int maxAttempts;
    /** Maximum elapsed time (ms) on the same subgoal before a stall is declared. */
    private final long timeoutMs;

    private final Map<String, StallRecord> records = new HashMap<>();

    /** Maps subgoalId → prerequisite action to fall back to on stall. */
    private static final Map<String, String> PREREQUISITE_MAP = new HashMap<>();
    static {
        PREREQUISITE_MAP.put("craft_tools",          "gather_wood");
        PREREQUISITE_MAP.put("build_shelter",        "gather_wood");
        PREREQUISITE_MAP.put("craft_sword_or_flee",  "gather_wood");
        PREREQUISITE_MAP.put("gather_food",          "find_water_source");
        PREREQUISITE_MAP.put("eat_food",             "forage_food");
        PREREQUISITE_MAP.put("craft_and_defend",     "flee_to_shelter");
        PREREQUISITE_MAP.put("find_or_build_shelter","gather_wood");
    }

    public StallGuard() {
        this(3, 60_000);
    }

    public StallGuard(int maxAttempts, long timeoutMs) {
        this.maxAttempts = maxAttempts;
        this.timeoutMs   = timeoutMs;
    }

    /**
     * Record one attempt on the given subgoal.
     * Call this every time the agent tries to execute a subgoal.
     */
    public void recordAttempt(String subgoalId) {
        long now = System.currentTimeMillis();
        StallRecord rec = records.get(subgoalId);
        if (rec != null) {
            rec.attempts++;
            rec.lastAttemptAt = now;
        } else {
            records.put(subgoalId, new StallRecord(subgoalId, now));
        }
    }

    /** Record that the subgoal succeeded and clear its stall record. */
    public void recordSuccess(String subgoalId) {
        records.remove(subgoalId);
    }

    /** Returns {@code true} if the subgoal should be considered stalled. */
    public boolean isStalled(String subgoalId) {
        StallRecord rec = records.get(subgoalId);
        if (rec == null) return false;
        long elapsed = System.currentTimeMillis() - rec.firstAttemptAt;
        return rec.attempts >= maxAttempts || elapsed >= timeoutMs;
    }

    /**
     * Returns the fallback action for a stalled subgoal.
     * Falls back to {@code "explore"} if no specific prerequisite is mapped.
     */
    public String getFallbackAction(String subgoalId) {
        // Extract the base action from compound ids (e.g. "role_farmer_food" → "food")
        String base = subgoalId.contains("_")
                ? subgoalId.substring(subgoalId.lastIndexOf('_') + 1)
                : subgoalId;
        String fallback = PREREQUISITE_MAP.get(base);
        if (fallback == null) fallback = PREREQUISITE_MAP.get(subgoalId);
        return fallback != null ? fallback : "explore";
    }

    /** Returns a snapshot of all current stall records. */
    public List<StallRecord> getRecords() {
        return new ArrayList<>(records.values());
    }

    /** Clears all stall records (e.g. after a full replan). */
    public void reset() {
        records.clear();
    }
}
