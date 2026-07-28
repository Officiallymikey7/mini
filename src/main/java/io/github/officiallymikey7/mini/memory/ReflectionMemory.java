package io.github.officiallymikey7.mini.memory;

import io.github.officiallymikey7.mini.core.ExecutionOutcome;
import io.github.officiallymikey7.mini.core.InventoryItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintains a rolling window of recent action outcomes and builds the
 * [Self-Reflection Block] summary injected into every planner prompt.
 */
public final class ReflectionMemory {

    private static final int MAX_ENTRIES = 10;

    private final List<ReflectionEntry> entries = new ArrayList<>();

    /** Record the outcome of one execution cycle. */
    public void record(int tick, ExecutionOutcome outcome, List<InventoryItem> currentInventory) {
        entries.add(new ReflectionEntry(tick, outcome, currentInventory));
        if (entries.size() > MAX_ENTRIES) {
            entries.subList(0, entries.size() - MAX_ENTRIES).clear();
        }
    }

    /** Build the [Self-Reflection Block] summary string. */
    public ReflectionBlock build() {
        if (entries.isEmpty()) {
            return new ReflectionBlock("No previous actions recorded yet.", List.of());
        }

        List<String> lines = new ArrayList<>();
        lines.add("Recent actions (last " + entries.size() + "):");
        for (ReflectionEntry e : entries) {
            String icon = switch (e.status) {
                case "success" -> "✓";
                case "failure" -> "✗";
                default        -> "⚡";
            };
            lines.add("  [Tick " + e.tick + "] " + icon + " " + e.action
                    + " → " + e.status + ": " + e.message);
        }

        // Inventory delta vs. previous snapshot
        List<InventoryItem> current = entries.get(entries.size() - 1).inventorySnapshot;
        List<InventoryItem> prev = entries.size() > 1
                ? entries.get(entries.size() - 2).inventorySnapshot
                : List.of();
        List<String> deltas = computeInventoryDelta(prev, current);
        if (!deltas.isEmpty()) {
            lines.add("Inventory changes since last tick: " + String.join(", ", deltas));
        }

        // Highlight recent mistakes
        List<ReflectionEntry> mistakes = entries.stream()
                .filter(e -> e.status.equals("failure") || e.status.equals("timeout"))
                .toList();
        if (!mistakes.isEmpty()) {
            String actions = mistakes.stream().map(m -> m.action).distinct()
                    .collect(Collectors.joining(", "));
            lines.add("Recent mistakes / failed actions: " + actions);
        }

        return new ReflectionBlock(String.join("\n", lines), List.copyOf(entries));
    }

    private static List<String> computeInventoryDelta(
            List<InventoryItem> before, List<InventoryItem> after) {
        Map<String, Integer> beforeMap = new LinkedHashMap<>();
        for (InventoryItem i : before) beforeMap.merge(i.name, i.count, Integer::sum);
        Map<String, Integer> afterMap  = new LinkedHashMap<>();
        for (InventoryItem i : after)  afterMap.merge(i.name, i.count, Integer::sum);

        List<String> deltas = new ArrayList<>();
        for (Map.Entry<String, Integer> e : afterMap.entrySet()) {
            int was  = beforeMap.getOrDefault(e.getKey(), 0);
            int diff = e.getValue() - was;
            if      (diff > 0) deltas.add("+" + diff + " " + e.getKey());
            else if (diff < 0) deltas.add(diff + " " + e.getKey());
        }
        for (Map.Entry<String, Integer> e : beforeMap.entrySet()) {
            if (!afterMap.containsKey(e.getKey())) {
                deltas.add("-" + e.getValue() + " " + e.getKey());
            }
        }
        return deltas;
    }
}
