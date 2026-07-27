package io.github.officiallymikey7.mini.core;

import java.util.List;

/**
 * Full world-state snapshot perceived by the agent each tick.
 *
 * <p>All list fields are unmodifiable copies.
 */
public final class WorldState {
    /** Agent display name. */
    public final String agentName;
    /** Current Minecraft game tick (0–23999). */
    public final int gameTick;
    /** {@code true} when gameTick is in the night range (13000–23000). */
    public final boolean isNight;
    /** Agent health 0–20. */
    public final float health;
    /** Agent food level 0–20. */
    public final float hunger;
    /** Hostile mobs within detection radius. */
    public final List<HostileEntity> nearbyHostiles;
    /** Current inventory snapshot. */
    public final List<InventoryItem> inventory;
    /** Distance to the nearest known shelter, or -1 if unknown. */
    public final int shelterDistance;
    /** {@code true} when the agent is currently inside a shelter. */
    public final boolean hasShelter;
    /** {@code true} when the agent has at least one usable tool (axe, pickaxe, sword). */
    public final boolean hasTools;
    /** Recent chat messages from nearby players/agents. */
    public final List<String> nearbyChat;
    /** Epoch-millisecond timestamp of this perception snapshot. */
    public final long timestamp;

    public WorldState(
            String agentName,
            int gameTick,
            boolean isNight,
            float health,
            float hunger,
            List<HostileEntity> nearbyHostiles,
            List<InventoryItem> inventory,
            int shelterDistance,
            boolean hasShelter,
            boolean hasTools,
            List<String> nearbyChat,
            long timestamp) {
        this.agentName = agentName;
        this.gameTick = gameTick;
        this.isNight = isNight;
        this.health = health;
        this.hunger = hunger;
        this.nearbyHostiles = List.copyOf(nearbyHostiles);
        this.inventory = List.copyOf(inventory);
        this.shelterDistance = shelterDistance;
        this.hasShelter = hasShelter;
        this.hasTools = hasTools;
        this.nearbyChat = List.copyOf(nearbyChat);
        this.timestamp = timestamp;
    }
}
