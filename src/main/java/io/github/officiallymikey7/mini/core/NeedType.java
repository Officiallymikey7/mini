package io.github.officiallymikey7.mini.core;

/** The categories of survival need that the agent tracks and prioritises. */
public enum NeedType {
    SURVIVAL_DEFENSE,
    FOOD,
    SHELTER,
    TOOLS,
    RESOURCES,
    /** Upgrade to better tool/armour tier (iron → diamond → netherite). Utility 0–75. */
    GEAR_UP,
    /** Construct or improve a home base; triggered by full inventory or approaching night. Utility 0–60. */
    BUILD_BASE,
    /** Default idle exploration when all survival needs are satisfied. Utility 0–20. */
    EXPLORE
}
