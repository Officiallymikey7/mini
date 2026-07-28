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
    /** Agent world X coordinate. */
    public final double x;
    /** Agent world Y coordinate. */
    public final double y;
    /** Agent world Z coordinate. */
    public final double z;
    /** Combined block + sky light level at the agent's position (0–15). */
    public final int lightLevel;
    /** Biome name at the agent's position (e.g. {@code "plains"}). */
    public final String biome;
    /** Item held in the main hand (e.g. {@code "iron_sword"}, {@code "air"} if empty). */
    public final String mainHandItem;
    /** Item held in the off hand (e.g. {@code "shield"}, {@code "air"} if empty). */
    public final String offHandItem;
    /** Distinct non-trivial block types nearby within a short radius. */

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
            long timestamp,
            double x,
            double y,
            double z,
            int lightLevel,
            String biome,
            String mainHandItem,
            String offHandItem,
            List<String> nearbyBlocks) {
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
        this.x = x;
        this.y = y;
        this.z = z;
        this.lightLevel = lightLevel;
        this.biome = biome;
        this.mainHandItem = mainHandItem;
        this.offHandItem = offHandItem;
        this.nearbyBlocks = List.copyOf(nearbyBlocks);
    }
}
