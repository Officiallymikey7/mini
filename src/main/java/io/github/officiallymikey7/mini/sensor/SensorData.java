package io.github.officiallymikey7.mini.sensor;

import java.util.List;

/**
 * Aggregated output produced by {@link SensorSuite} during a single brain tick.
 *
 * <p>Contains the combined results of the three player-NPC sensors:
 * <ul>
 *   <li>{@link VoxelSpatialSensor} – terrain and hazard analysis</li>
 *   <li>{@link InventorySensor} – gear tier, food, and crafting potential</li>
 *   <li>{@link CombatThreatSensor} – composite threat score and weapon assessment</li>
 * </ul>
 */
public final class SensorData {

    // ── VoxelSpatialSensor ───────────────────────────────────────────────────

    /** Named navigable zones detected near the agent (e.g. {@code "open_plain"}, {@code "cave_entrance"}). */
    public final List<String> walkableZones;
    /** {@code true} when a 3+ block drop is detected within the scan radius. */
    public final boolean hasDropHazard;
    /** {@code true} when lava is detected within the scan radius. */
    public final boolean hasLavaHazard;
    /** Block types that can be mined to open a new navigation path. */
    public final List<String> breakableObstacles;

    // ── InventorySensor ──────────────────────────────────────────────────────

    /**
     * Best gear tier present in the inventory:
     * 0=none, 1=wood/gold, 2=stone, 3=iron, 4=diamond, 5=netherite.
     */
    public final int gearTier;
    /** Total count of food items in the inventory. */
    public final int foodCount;
    /** Total count of building materials (planks, cobblestone, dirt). */
    public final int buildingMaterialCount;
    /** {@code true} when the inventory holds enough materials to craft an iron pickaxe. */
    public final boolean canCraftIronPickaxe;
    /** {@code true} when the inventory holds enough materials to craft a shield. */
    public final boolean canCraftShield;
    /** {@code true} when more than 27 inventory slots are occupied. */
    public final boolean inventoryFull;

    // ── CombatThreatSensor ───────────────────────────────────────────────────

    /** Composite threat score 0–100 (combines distance, weapon match-up, and count). */
    public final double threatScore;
    /**
     * Approximated line-of-sight to the nearest hostile:
     * {@code true} when the closest threat is within an unobstructed range heuristic.
     */
    public final boolean hasLineOfSight;
    /** Best weapon item name found in the inventory, or {@code "fist"} if empty-handed. */
    public final String bestWeapon;
    /**
     * Weapon advantage estimate in range −1.0 (severe disadvantage) to +1.0 (strong advantage).
     * Based on the agent's weapon tier versus the threat type.
     */
    public final double weaponAdvantage;

    public SensorData(
            List<String> walkableZones,
            boolean hasDropHazard,
            boolean hasLavaHazard,
            List<String> breakableObstacles,
            int gearTier,
            int foodCount,
            int buildingMaterialCount,
            boolean canCraftIronPickaxe,
            boolean canCraftShield,
            boolean inventoryFull,
            double threatScore,
            boolean hasLineOfSight,
            String bestWeapon,
            double weaponAdvantage) {
        this.walkableZones         = List.copyOf(walkableZones);
        this.hasDropHazard         = hasDropHazard;
        this.hasLavaHazard         = hasLavaHazard;
        this.breakableObstacles    = List.copyOf(breakableObstacles);
        this.gearTier              = gearTier;
        this.foodCount             = foodCount;
        this.buildingMaterialCount = buildingMaterialCount;
        this.canCraftIronPickaxe   = canCraftIronPickaxe;
        this.canCraftShield        = canCraftShield;
        this.inventoryFull         = inventoryFull;
        this.threatScore           = threatScore;
        this.hasLineOfSight        = hasLineOfSight;
        this.bestWeapon            = bestWeapon;
        this.weaponAdvantage       = weaponAdvantage;
    }
}
