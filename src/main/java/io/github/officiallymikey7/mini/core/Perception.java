package io.github.officiallymikey7.mini.core;

import io.github.officiallymikey7.mini.integration.BotAdapter;

import java.util.Set;

/**
 * Collects a snapshot of the agent's current world state from the bot adapter
 * and computes derived fields ({@code isNight}, {@code hasTools}).
 */
public final class Perception {

    /** Item names that count as a usable tool. */
    private static final Set<String> TOOL_NAMES = Set.of(
            "wooden_axe",    "stone_axe",    "iron_axe",    "diamond_axe",
            "wooden_pickaxe","stone_pickaxe","iron_pickaxe","diamond_pickaxe",
            "wooden_sword",  "stone_sword",  "iron_sword",  "diamond_sword",
            "netherite_sword","netherite_pickaxe");

    private Perception() {}

    /** Returns a fully populated {@link WorldState} from the adapter's raw data. */
    public static WorldState perceive(BotAdapter adapter) {
        BotAdapter.RawWorldState raw = adapter.getWorldState();

        boolean isNight  = raw.gameTick >= 13000 && raw.gameTick <= 23000;
        boolean hasTools = raw.inventory.stream().anyMatch(i -> TOOL_NAMES.contains(i.name));

        return new WorldState(
                raw.agentName,
                raw.gameTick,
                isNight,
                raw.health,
                raw.hunger,
                raw.nearbyHostiles,
                raw.inventory,
                raw.shelterDistance,
                raw.hasShelter,
                hasTools,
                raw.nearbyChat,
                System.currentTimeMillis());
    }
}
