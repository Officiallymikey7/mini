package io.github.officiallymikey7.mini.core;

/** A single stack of items in the agent's inventory. */
public final class InventoryItem {
    public final String name;
    public final int count;

    public InventoryItem(String name, int count) {
        this.name = name;
        this.count = count;
    }

    @Override
    public String toString() {
        return count + "x " + name;
    }
}
