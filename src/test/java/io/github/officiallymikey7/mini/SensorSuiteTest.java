package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.brain.MemoryModule;
import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;
import io.github.officiallymikey7.mini.core.WorldState;
import io.github.officiallymikey7.mini.sensor.CombatThreatSensor;
import io.github.officiallymikey7.mini.sensor.InventorySensor;
import io.github.officiallymikey7.mini.sensor.SensorData;
import io.github.officiallymikey7.mini.sensor.SensorSuite;
import io.github.officiallymikey7.mini.sensor.VoxelSpatialSensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the three player-NPC sensors and {@link SensorSuite}.
 */
class SensorSuiteTest {

    // ── Helper ───────────────────────────────────────────────────────────────

    private static WorldState state(float health, float hunger, boolean isNight, boolean hasShelter,
                                    boolean hasTools, List<HostileEntity> hostiles,
                                    List<InventoryItem> inv, List<String> nearbyBlocks,
                                    double y) {
        return new WorldState("Test", 6000, isNight, health, hunger,
                hostiles, inv, 0, hasShelter, hasTools, List.of(),
                System.currentTimeMillis(),
                0.0, y, 0.0, 15, "plains",
                "air", "air", nearbyBlocks);
    }

    // ── VoxelSpatialSensor ────────────────────────────────────────────────────

    @Test
    void lavaDetected_spatialFlagsLavaHazard() {
        WorldState s = state(20, 18, false, false, true, List.of(), List.of(),
                List.of("oak_log", "lava", "stone"), 64);
        VoxelSpatialSensor.SpatialResult r = VoxelSpatialSensor.analyse(s);
        assertTrue(r.hasLavaHazard());
    }

    @Test
    void noLava_lavaHazardFalse() {
        WorldState s = state(20, 18, false, false, true, List.of(), List.of(),
                List.of("oak_log", "grass_block"), 64);
        VoxelSpatialSensor.SpatialResult r = VoxelSpatialSensor.analyse(s);
        assertFalse(r.hasLavaHazard());
    }

    @Test
    void mineableObstacle_appearsInBreakableList() {
        WorldState s = state(20, 18, false, false, true, List.of(), List.of(),
                List.of("stone", "cobblestone", "oak_log"), 64);
        VoxelSpatialSensor.SpatialResult r = VoxelSpatialSensor.analyse(s);
        assertFalse(r.breakableObstacles().isEmpty());
        assertTrue(r.breakableObstacles().contains("stone"));
    }

    // ── InventorySensor ───────────────────────────────────────────────────────

    @Test
    void ironSwordInInventory_gearTier3() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("iron_sword", 1)), List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertEquals(3, r.gearTier());
    }

    @Test
    void diamondPickaxeInInventory_gearTier4() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("diamond_pickaxe", 1)), List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertEquals(4, r.gearTier());
    }

    @Test
    void foodItems_countedCorrectly() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("bread", 5), new InventoryItem("apple", 3)),
                List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertEquals(8, r.foodCount());
    }

    @Test
    void enoughForIronPickaxe_crafting_possible() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("iron_ingot", 3), new InventoryItem("stick", 2)),
                List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertTrue(r.canCraftIronPickaxe());
    }

    @Test
    void notEnoughIron_ironPickaxeNotCraftable() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("iron_ingot", 2), new InventoryItem("stick", 2)),
                List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertFalse(r.canCraftIronPickaxe());
    }

    @Test
    void shieldIngredients_shieldCraftable() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("oak_planks", 6), new InventoryItem("iron_ingot", 1)),
                List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertTrue(r.canCraftShield());
    }

    @Test
    void moreThan27Stacks_inventoryFull() {
        List<InventoryItem> inv = new java.util.ArrayList<>();
        for (int i = 0; i < 28; i++) inv.add(new InventoryItem("dirt", 1));
        WorldState s = state(20, 18, false, true, true, List.of(), inv, List.of(), 64);
        InventorySensor.InventoryResult r = InventorySensor.analyse(s);
        assertTrue(r.inventoryFull());
    }

    // ── CombatThreatSensor ────────────────────────────────────────────────────

    @Test
    void noHostiles_threatScoreZero() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of(), List.of(), 64);
        CombatThreatSensor.ThreatResult r = CombatThreatSensor.analyse(s);
        assertEquals(0.0, r.threatScore(), 0.01);
        assertFalse(r.hasLineOfSight());
    }

    @Test
    void closeZombie_highThreat() {
        WorldState s = state(20, 18, false, true, true,
                List.of(new HostileEntity("zombie", 3.0)), List.of(), List.of(), 64);
        CombatThreatSensor.ThreatResult r = CombatThreatSensor.analyse(s);
        assertTrue(r.threatScore() >= 60, "Close zombie should yield high threat");
        assertTrue(r.hasLineOfSight());
    }

    @Test
    void ironSwordVsZombie_positiveWeaponAdvantage() {
        WorldState s = state(20, 18, false, true, true,
                List.of(new HostileEntity("zombie", 3.0)),
                List.of(new InventoryItem("iron_sword", 1)), List.of(), 64);
        CombatThreatSensor.ThreatResult r = CombatThreatSensor.analyse(s);
        assertTrue(r.weaponAdvantage() > 0, "Iron sword vs zombie should be advantageous");
    }

    @Test
    void noWeaponVsWarden_negativeWeaponAdvantage() {
        WorldState s = state(20, 18, false, true, false,
                List.of(new HostileEntity("warden", 8.0)), List.of(), List.of(), 64);
        CombatThreatSensor.ThreatResult r = CombatThreatSensor.analyse(s);
        assertTrue(r.weaponAdvantage() < 0, "No weapon vs warden should be a disadvantage");
    }

    // ── SensorSuite (integration) ─────────────────────────────────────────────

    @Test
    void sensorSuite_producesAllFields() {
        WorldState s = state(15, 12, false, true, true,
                List.of(new HostileEntity("zombie", 10.0)),
                List.of(new InventoryItem("iron_sword", 1), new InventoryItem("bread", 3)),
                List.of("oak_log", "grass_block"), 64);
        SensorSuite suite = new SensorSuite();
        SensorData data = suite.update(s, new MemoryModule());

        assertEquals(3, data.gearTier);
        assertEquals(3, data.foodCount);
        assertTrue(data.threatScore > 0);
        assertNotNull(data.bestWeapon);
        assertFalse(data.hasLavaHazard);
    }
}
