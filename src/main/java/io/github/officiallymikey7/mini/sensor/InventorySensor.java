package io.github.officiallymikey7.mini.sensor;

import io.github.officiallymikey7.mini.core.InventoryItem;
import io.github.officiallymikey7.mini.core.WorldState;

import java.util.Set;

/**
 * Continuously catalogs the agent's inventory to determine:
 * <ul>
 *   <li>Current gear tier (tool / armour quality)</li>
 *   <li>Available food count</li>
 *   <li>Available building materials</li>
 *   <li>Auto-crafting potential for key items (iron pickaxe, shield)</li>
 *   <li>Whether the inventory is full (≥ 28 stacks occupied)</li>
 * </ul>
 *
 * <p>All analysis is purely derived from {@link WorldState#inventory} so this
 * sensor runs without any Minecraft dependencies.
 */
public final class InventorySensor {

    /** Block / item name substrings that qualify as food. */
    private static final Set<String> FOOD_NAMES = Set.of(
            "bread", "apple", "cooked_porkchop", "cooked_beef", "cooked_chicken",
            "cooked_mutton", "cooked_cod", "cooked_salmon", "carrot", "potato",
            "baked_potato", "cookie", "pumpkin_pie", "beetroot", "melon_slice",
            "rabbit_stew", "mushroom_stew", "suspicious_stew", "golden_carrot",
            "golden_apple", "chorus_fruit", "dried_kelp");

    /** Building-material name substrings. */
    private static final Set<String> BUILD_MATERIAL_NAMES = Set.of(
            "planks", "cobblestone", "dirt", "stone", "sand", "gravel",
            "oak_log", "birch_log", "spruce_log", "jungle_log",
            "dark_oak_log", "acacia_log", "mangrove_log");

    /** Crafting threshold: iron pickaxe needs 3 iron ingots + 2 sticks. */
    private static final int IRON_PICKAXE_IRON   = 3;
    private static final int IRON_PICKAXE_STICKS = 2;

    /** Crafting threshold: shield needs 6 planks + 1 iron ingot. */
    private static final int SHIELD_PLANKS = 6;
    private static final int SHIELD_IRON   = 1;

    private InventorySensor() {}

    /**
     * Scans the inventory and produces an {@link InventoryResult}.
     *
     * @param state current world snapshot
     * @return populated inventory analysis
     */
    public static InventoryResult analyse(WorldState state) {
        int bestTier        = 0;
        int foodCount       = 0;
        int buildCount      = 0;
        int ironIngots      = 0;
        int sticks          = 0;
        int planks          = 0;
        int occupiedSlots   = state.inventory.size();

        for (InventoryItem item : state.inventory) {
            String n = item.name.toLowerCase();

            // Gear tier
            if (containsAny(n, "netherite_pickaxe", "netherite_sword", "netherite_axe")) {
                bestTier = Math.max(bestTier, 5);
            } else if (containsAny(n, "diamond_pickaxe", "diamond_sword", "diamond_axe")) {
                bestTier = Math.max(bestTier, 4);
            } else if (containsAny(n, "iron_pickaxe", "iron_sword", "iron_axe")) {
                bestTier = Math.max(bestTier, 3);
            } else if (containsAny(n, "stone_pickaxe", "stone_sword", "stone_axe")) {
                bestTier = Math.max(bestTier, 2);
            } else if (n.contains("pickaxe") || n.contains("sword") || n.contains("_axe")) {
                bestTier = Math.max(bestTier, 1);
            }

            // Food
            if (FOOD_NAMES.stream().anyMatch(n::contains)) foodCount += item.count;

            // Build material
            if (BUILD_MATERIAL_NAMES.stream().anyMatch(n::contains)) buildCount += item.count;

            // Crafting ingredients
            if (n.contains("iron_ingot"))   ironIngots += item.count;
            if (n.contains("stick"))        sticks     += item.count;
            if (n.contains("planks"))       planks     += item.count;
        }

        if (bestTier == 0 && state.hasTools) bestTier = 1;

        boolean canCraftIronPickaxe =
                ironIngots >= IRON_PICKAXE_IRON && sticks >= IRON_PICKAXE_STICKS;
        boolean canCraftShield = planks >= SHIELD_PLANKS && ironIngots >= SHIELD_IRON;

        return new InventoryResult(
                bestTier, foodCount, buildCount,
                canCraftIronPickaxe, canCraftShield,
                occupiedSlots > 27);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    /** Result container for inventory analysis. */
    public record InventoryResult(
            int gearTier,
            int foodCount,
            int buildingMaterialCount,
            boolean canCraftIronPickaxe,
            boolean canCraftShield,
            boolean inventoryFull) {}
}
