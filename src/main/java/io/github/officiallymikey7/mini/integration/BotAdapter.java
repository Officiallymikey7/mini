package io.github.officiallymikey7.mini.integration;

import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;

import java.util.List;

/**
 * Abstract adapter interface for Minecraft world interaction.
 *
 * <p>Implement this interface to connect the agent framework to any Minecraft
 * environment: the Fabric in-game adapter, a test mock, a remote server, etc.
 *
 * @see MockBotAdapter
 * @see FabricWorldAdapter
 */
public interface BotAdapter {

    /** Returns the current raw world state from the server/simulation. */
    RawWorldState getWorldState();

    /**
     * Performs the named action and returns a result message.
     * If the action fails, the message must start with {@code "Error:"}.
     */
    String performAction(String action);

    /** Sends a chat message as the agent. */
    void sendChat(String message);

    // ── Raw world state ──────────────────────────────────────────────────────

    /**
     * Raw data returned by the adapter before perception post-processing.
     * {@code isNight} and {@code hasTools} are derived by {@link io.github.officiallymikey7.mini.core.Perception}.
     */
    final class RawWorldState {
        public final String agentName;
        public final int gameTick;
        public final float health;
        public final float hunger;
        public final List<HostileEntity> nearbyHostiles;
        public final List<InventoryItem> inventory;
        public final int shelterDistance;
        public final boolean hasShelter;
        public final List<String> nearbyChat;
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
        /** Distinct non-trivial block types scanned within a short radius (cube survey, no line-of-sight). */
        public final List<String> nearbyBlocks;

        public RawWorldState(
                String agentName,
                int gameTick,
                float health,
                float hunger,
                List<HostileEntity> nearbyHostiles,
                List<InventoryItem> inventory,
                int shelterDistance,
                boolean hasShelter,
                List<String> nearbyChat,
                double x,
                double y,
                double z,
                int lightLevel,
                String biome,
                String mainHandItem,
                String offHandItem,
                List<String> nearbyBlocks) {
            this.agentName       = agentName;
            this.gameTick        = gameTick;
            this.health          = health;
            this.hunger          = hunger;
            this.nearbyHostiles  = List.copyOf(nearbyHostiles);
            this.inventory       = List.copyOf(inventory);
            this.shelterDistance = shelterDistance;
            this.hasShelter      = hasShelter;
            this.nearbyChat      = List.copyOf(nearbyChat);
            this.x               = x;
            this.y               = y;
            this.z               = z;
            this.lightLevel      = lightLevel;
            this.biome           = biome;
            this.mainHandItem    = mainHandItem;
            this.offHandItem     = offHandItem;
            this.nearbyBlocks    = List.copyOf(nearbyBlocks);
        }
    }
}
