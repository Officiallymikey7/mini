package io.github.officiallymikey7.mini.memory;

import io.github.officiallymikey7.mini.core.ExecutionOutcome;
import io.github.officiallymikey7.mini.core.InventoryItem;

import java.util.List;

/** A record of one agent execution cycle stored in reflection memory. */
public final class ReflectionEntry {
    public final int tick;
    public final String subgoalId;
    public final String action;
    public final String status;
    public final String message;
    public final List<InventoryItem> inventorySnapshot;

    public ReflectionEntry(int tick, ExecutionOutcome outcome, List<InventoryItem> inventory) {
        this.tick = tick;
        this.subgoalId = outcome.subgoalId;
        this.action = outcome.action;
        this.status = outcome.status.name().toLowerCase();
        this.message = outcome.message;
        this.inventorySnapshot = List.copyOf(inventory);
    }
}
