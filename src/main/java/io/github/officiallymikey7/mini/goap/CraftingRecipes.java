package io.github.officiallymikey7.mini.goap;

import io.github.officiallymikey7.mini.core.NeedType;

import java.util.List;
import java.util.Map;

/**
 * Static crafting-recipe dependency graph used by {@link GOAPPlanner#buildPlan}.
 *
 * <p>Each entry maps a target goal/item to the ordered chain of prerequisite
 * actions the agent must execute.  The planner checks inventory state to skip
 * steps that are already satisfied.
 *
 * <p>Dependency chains follow the pattern described in the architecture spec:
 * <pre>
 *   Shield ← Planks ← Oak Log
 *          ← Iron Ingot ← Iron Ore ← Mine
 * </pre>
 */
final class CraftingRecipes {

    private CraftingRecipes() {}

    /**
     * Returns the ordered action chain for the given goal type.
     *
     * <p>Actions are listed from first prerequisite to final crafting step.
     * The planner is responsible for skipping steps the agent can already skip
     * based on current inventory.
     */
    static List<String> chainFor(NeedType goal) {
        return CHAINS.getOrDefault(goal, List.of("explore"));
    }

    private static final Map<NeedType, List<String>> CHAINS = Map.of(

        NeedType.SURVIVAL_DEFENSE, List.of(
            "eat_food",
            "flee_to_shelter",
            "attack_nearest_hostile"
        ),

        NeedType.GEAR_UP, List.of(
            "gather_wood",       // step 1 – get wood logs
            "craft_planks",      // step 2 – planks from logs
            "craft_sticks",      // step 3 – sticks from planks
            "mine_stone",        // step 4 – cobblestone for stone tools
            "craft_stone_tools", // step 5 – stone pick + sword (baseline)
            "mine_iron",         // step 6 – iron ore
            "smelt_iron",        // step 7 – iron ingots
            "craft_iron_tools",  // step 8 – iron pick + sword
            "mine_diamonds",     // step 9 – diamond ore
            "craft_diamond_tools"// step 10 – diamond pick + sword
        ),

        NeedType.BUILD_BASE, List.of(
            "gather_wood",           // logs for planks
            "craft_planks",          // workbench material
            "place_crafting_table",  // set up crafting station
            "gather_cobblestone",    // walls / floor
            "build_shelter_walls",   // enclose space
            "add_door",              // entry point
            "place_chest",           // storage
            "build_base"             // final consolidation action
        ),

        NeedType.EXPLORE, List.of(
            "explore"
        ),

        NeedType.FOOD, List.of(
            "forage_food",
            "gather_food",
            "eat_food"
        ),

        NeedType.SHELTER, List.of(
            "find_or_build_shelter",
            "build_shelter"
        ),

        NeedType.TOOLS, List.of(
            "gather_wood",
            "craft_tools"
        ),

        NeedType.RESOURCES, List.of(
            "gather_wood"
        )
    );
}
