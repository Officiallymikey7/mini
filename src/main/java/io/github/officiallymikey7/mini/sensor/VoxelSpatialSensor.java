package io.github.officiallymikey7.mini.sensor;

import io.github.officiallymikey7.mini.core.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Analyses the agent's nearby-block list to map the immediate terrain.
 *
 * <p>Without direct block-coordinate access (platform-agnostic layer), this
 * sensor works from the {@link WorldState#nearbyBlocks} list produced by the
 * {@code FabricWorldAdapter}'s cube scan.  It classifies blocks into:
 * <ul>
 *   <li><b>Hazard zones</b> – lava / large drops (block name heuristic)</li>
 *   <li><b>Walkable zones</b> – open terrain types inferred from biome + blocks</li>
 *   <li><b>Breakable obstacles</b> – mineable blocks that can open a path</li>
 * </ul>
 */
public final class VoxelSpatialSensor {

    private static final Set<String> LAVA_BLOCKS = Set.of("lava", "magma_block");

    private static final Set<String> DROP_INDICATORS = Set.of(
            "air", "void_air", "cave_air"); // detected when deep cave scan finds air below agent

    private static final Set<String> MINEABLE_OBSTACLES = Set.of(
            "stone", "cobblestone", "granite", "diorite", "andesite",
            "deepslate", "sandstone", "netherrack", "oak_log", "birch_log",
            "spruce_log", "jungle_log", "dark_oak_log", "acacia_log", "mangrove_log");

    private static final Set<String> OPEN_ZONE_BLOCKS = Set.of(
            "grass_block", "sand", "gravel", "podzol", "mycelium",
            "coarse_dirt", "farmland", "snow_block", "ice", "packed_ice");

    private VoxelSpatialSensor() {}

    /**
     * Analyses the world-state near-blocks list and returns a partial
     * {@link SensorData.Builder} with the spatial fields populated.
     *
     * @param state current world snapshot
     * @return populated spatial analysis
     */
    public static SpatialResult analyse(WorldState state) {
        List<String> blocks    = state.nearbyBlocks;
        boolean lava           = blocks.stream().anyMatch(LAVA_BLOCKS::contains);
        boolean dropHazard     = state.y < 20 && blocks.stream().anyMatch(DROP_INDICATORS::contains);

        List<String> breakable = new ArrayList<>();
        for (String b : blocks) {
            if (MINEABLE_OBSTACLES.contains(b)) breakable.add(b);
        }

        List<String> zones = new ArrayList<>();
        boolean hasOpen = blocks.stream().anyMatch(OPEN_ZONE_BLOCKS::contains);
        if (hasOpen) zones.add("open_terrain");
        if (lava)    zones.add("lava_zone");
        if (!blocks.isEmpty() && !hasOpen) zones.add("cave_or_structure");

        return new SpatialResult(zones, dropHazard, lava, breakable);
    }

    /** Result container for spatial analysis. */
    public record SpatialResult(
            List<String> walkableZones,
            boolean hasDropHazard,
            boolean hasLavaHazard,
            List<String> breakableObstacles) {}
}
