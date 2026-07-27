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

        public RawWorldState(
                String agentName,
                int gameTick,
                float health,
                float hunger,
                List<HostileEntity> nearbyHostiles,
                List<InventoryItem> inventory,
                int shelterDistance,
                boolean hasShelter,
                List<String> nearbyChat) {
            this.agentName       = agentName;
            this.gameTick        = gameTick;
            this.health          = health;
            this.hunger          = hunger;
            this.nearbyHostiles  = List.copyOf(nearbyHostiles);
            this.inventory       = List.copyOf(inventory);
            this.shelterDistance = shelterDistance;
            this.hasShelter      = hasShelter;
            this.nearbyChat      = List.copyOf(nearbyChat);
        }
    }
}
