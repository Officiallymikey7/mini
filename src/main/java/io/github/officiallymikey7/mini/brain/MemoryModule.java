package io.github.officiallymikey7.mini.brain;

import java.util.Optional;

/**
 * Persistent memory for the {@link PlayerNPCBrain}.
 *
 * <p>Tracks spatial knowledge that outlives individual ticks:
 * <ul>
 *   <li><b>Home location</b> – the coordinates of the agent's current base.
 *       Set when the {@code BUILD_BASE} goal places or identifies a home structure.</li>
 *   <li><b>Known resource locations</b> – named points of interest (ore veins, trees,
 *       water sources) learned during exploration.</li>
 *   <li><b>Mastered recipes</b> – items the agent has already crafted at least once,
 *       used by the planner to prefer higher-tier goals.</li>
 * </ul>
 */
public final class MemoryModule {

    private Double homeX;
    private Double homeY;
    private Double homeZ;

    private final java.util.LinkedHashMap<String, double[]> knownResources =
            new java.util.LinkedHashMap<>();

    private final java.util.LinkedHashSet<String> masteredRecipes =
            new java.util.LinkedHashSet<>();

    // ── Home location ─────────────────────────────────────────────────────────

    /** Returns {@code true} when a home location has been registered. */
    public boolean hasHomeLocation() {
        return homeX != null;
    }

    /**
     * Registers the agent's home base coordinates.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     */
    public void setHomeLocation(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
    }

    /**
     * Returns the home location as {@code [x, y, z]}, or an empty optional if
     * no home has been registered.
     */
    public Optional<double[]> getHomeLocation() {
        if (homeX == null) return Optional.empty();
        return Optional.of(new double[]{homeX, homeY, homeZ});
    }

    // ── Known resource locations ──────────────────────────────────────────────

    /**
     * Records a named resource at the given coordinates.
     *
     * @param resourceName e.g. {@code "iron_ore"}, {@code "oak_forest"}
     * @param x            world X
     * @param y            world Y
     * @param z            world Z
     */
    public void rememberResource(String resourceName, double x, double y, double z) {
        knownResources.put(resourceName, new double[]{x, y, z});
    }

    /** Returns the last-known coordinates for the named resource, if any. */
    public Optional<double[]> recallResource(String resourceName) {
        double[] pos = knownResources.get(resourceName);
        return pos == null ? Optional.empty() : Optional.of(pos.clone());
    }

    // ── Mastered recipes ──────────────────────────────────────────────────────

    /** Marks {@code recipe} as mastered (e.g. {@code "iron_pickaxe"}). */
    public void masterRecipe(String recipe) {
        masteredRecipes.add(recipe);
    }

    /** Returns {@code true} when the agent has mastered the named recipe. */
    public boolean hasMastered(String recipe) {
        return masteredRecipes.contains(recipe);
    }

    /** Returns an unmodifiable view of all mastered recipes. */
    public java.util.Set<String> getMasteredRecipes() {
        return java.util.Collections.unmodifiableSet(masteredRecipes);
    }
}
