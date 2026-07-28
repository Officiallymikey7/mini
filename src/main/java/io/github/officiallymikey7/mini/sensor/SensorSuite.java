package io.github.officiallymikey7.mini.sensor;

import io.github.officiallymikey7.mini.brain.MemoryModule;
import io.github.officiallymikey7.mini.core.WorldState;

/**
 * Aggregates the three player-NPC sensors into a single {@link SensorData} snapshot.
 *
 * <p>Call {@link #update(WorldState, MemoryModule)} once per brain tick.  The results
 * are stateless per call – the sensor suite itself holds no mutable state.
 *
 * <p>Sensor pipeline:
 * <ol>
 *   <li>{@link VoxelSpatialSensor} – terrain / hazard analysis</li>
 *   <li>{@link InventorySensor} – inventory / crafting analysis</li>
 *   <li>{@link CombatThreatSensor} – combat threat scoring</li>
 * </ol>
 */
public final class SensorSuite {

    /** Updates all sensors and returns a fresh {@link SensorData} snapshot. */
    public SensorData update(WorldState state, MemoryModule memory) {

        // 1. Spatial
        VoxelSpatialSensor.SpatialResult spatial = VoxelSpatialSensor.analyse(state);

        // 2. Inventory
        InventorySensor.InventoryResult inv = InventorySensor.analyse(state);

        // 3. Combat threat
        CombatThreatSensor.ThreatResult threat = CombatThreatSensor.analyse(state);

        return new SensorData(
                spatial.walkableZones(),
                spatial.hasDropHazard(),
                spatial.hasLavaHazard(),
                spatial.breakableObstacles(),
                inv.gearTier(),
                inv.foodCount(),
                inv.buildingMaterialCount(),
                inv.canCraftIronPickaxe(),
                inv.canCraftShield(),
                inv.inventoryFull(),
                threat.threatScore(),
                threat.hasLineOfSight(),
                threat.bestWeapon(),
                threat.weaponAdvantage());
    }
}
