package io.github.officiallymikey7.mini.ai.perception;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a villager's perceived environment at a single tick.
 *
 * <p>Built by the perception phase of the PDA loop and handed to the decision
 * engine. All fields are read-only after construction.
 */
public final class PerceptionSnapshot {

    /** Nearest hostile living entity within detection range, or {@code null} if none. */
    public final LivingEntity nearestHostile;

    /** Food block positions visible within the resource scan radius. */
    public final List<BlockPos> nearbyFoodBlocks;

    /** Wood/log block positions visible within the resource scan radius. */
    public final List<BlockPos> nearbyWoodBlocks;

    /** {@code true} if the villager is currently under overhead cover. */
    public final boolean isSheltered;

    /** Villager's current health, in half-hearts (0–20 for default max health). */
    public final float health;

    /** Villager's current food stock (internal bookkeeping). */
    public final int foodStock;

    /** Villager's current wood stock (internal bookkeeping). */
    public final int woodStock;

    /** {@code true} when it is night-time in the world. */
    public final boolean isNight;

    public PerceptionSnapshot(
            LivingEntity nearestHostile,
            List<BlockPos> nearbyFoodBlocks,
            List<BlockPos> nearbyWoodBlocks,
            boolean isSheltered,
            float health,
            int foodStock,
            int woodStock,
            boolean isNight) {
        this.nearestHostile  = nearestHostile;
        this.nearbyFoodBlocks = Collections.unmodifiableList(nearbyFoodBlocks);
        this.nearbyWoodBlocks = Collections.unmodifiableList(nearbyWoodBlocks);
        this.isSheltered     = isSheltered;
        this.health          = health;
        this.foodStock       = foodStock;
        this.woodStock       = woodStock;
        this.isNight         = isNight;
    }

    /** {@code true} if any hostile entity is perceived. */
    public boolean hasHostileThreat() {
        return nearestHostile != null;
    }

    /** {@code true} if at least one food source was found nearby. */
    public boolean hasFoodNearby() {
        return !nearbyFoodBlocks.isEmpty();
    }

    @Override
    public String toString() {
        return "PerceptionSnapshot{hostile=" + (nearestHostile != null)
                + ", food=" + nearbyFoodBlocks.size()
                + ", wood=" + nearbyWoodBlocks.size()
                + ", sheltered=" + isSheltered
                + ", health=" + health
                + ", foodStock=" + foodStock
                + ", woodStock=" + woodStock
                + ", night=" + isNight + '}';
    }
}
