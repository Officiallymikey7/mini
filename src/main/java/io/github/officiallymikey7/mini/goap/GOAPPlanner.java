package io.github.officiallymikey7.mini.goap;

import io.github.officiallymikey7.mini.brain.MemoryModule;
import io.github.officiallymikey7.mini.core.NeedType;
import io.github.officiallymikey7.mini.core.WorldState;
import io.github.officiallymikey7.mini.sensor.SensorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Goal-Oriented Action Planning (GOAP) system with Utility AI goal selection.
 *
 * <h3>Two-step pipeline</h3>
 * <ol>
 *   <li>{@link #evaluateHighestUtility} – scores every goal type using sensor data and
 *       returns the goal with the highest utility score.</li>
 *   <li>{@link #buildPlan} – resolves the chosen goal into an ordered
 *       {@link ActionPlan} by walking the {@link CraftingRecipes} dependency chain
 *       and skipping steps that the current inventory already satisfies.</li>
 * </ol>
 *
 * <h3>Goal utility ranges (per spec)</h3>
 * <ul>
 *   <li>SURVIVE        – 0–100 (health, hunger, threat)</li>
 *   <li>GEAR_UP        – 0–80  (gear tier shortfall)</li>
 *   <li>BUILD_BASE     – 0–60  (inventory fullness, nightfall, no home)</li>
 *   <li>EXPLORE        – 0–20  (idle baseline)</li>
 * </ul>
 */
public final class GOAPPlanner {

    public GOAPPlanner() {}

    // ── Utility evaluation ───────────────────────────────────────────────────

    /**
     * Evaluates all goal types and returns the one with the highest utility score.
     *
     * @param state     current world snapshot
     * @param sensors   latest sensor data from {@link io.github.officiallymikey7.mini.sensor.SensorSuite}
     * @param memory    agent memory (home location, known resources)
     * @return the goal with the highest computed utility score
     */
    public GoalScore evaluateHighestUtility(WorldState state, SensorData sensors,
                                            MemoryModule memory) {
        List<GoalScore> scores = new ArrayList<>();

        scores.add(scoreSurvive(state, sensors));
        scores.add(scoreGearUp(state, sensors));
        scores.add(scoreBuildBase(state, sensors, memory));
        scores.add(scoreExplore(state, sensors));

        return scores.stream()
                .max(Comparator.comparingDouble(GoalScore::score))
                .orElseGet(() -> new GoalScore(NeedType.EXPLORE, 5.0, "fallback idle"));
    }

    // ── Plan building ────────────────────────────────────────────────────────

    /**
     * Resolves {@code goal} into a concrete {@link ActionPlan} using the
     * crafting-recipe dependency graph.
     *
     * <p>Steps already satisfied by the current inventory are skipped so the agent
     * does not redundantly re-gather materials it already holds.
     *
     * @param goal    the selected goal (from {@link #evaluateHighestUtility})
     * @param state   current world snapshot (used to skip satisfied steps)
     * @param sensors latest sensor data (used to skip satisfied steps)
     * @return an ordered action plan ready for execution
     */
    public ActionPlan buildPlan(GoalScore goal, WorldState state, SensorData sensors) {
        List<String> chain = CraftingRecipes.chainFor(goal.goalType());
        List<String> plan  = new ArrayList<>();

        for (String step : chain) {
            if (!isStepSatisfied(step, state, sensors)) {
                plan.add(step);
            }
        }

        if (plan.isEmpty()) plan.add("explore"); // nothing to do → idle
        return new ActionPlan(goal, plan);
    }

    // ── Private: utility scorers ─────────────────────────────────────────────

    private static GoalScore scoreSurvive(WorldState state, SensorData sensors) {
        double score = 0;
        StringBuilder reason = new StringBuilder();

        // Health dimension
        if (state.health < 4) {
            score = Math.max(score, 100);
            reason.append("critical health; ");
        } else if (state.health < 8) {
            score = Math.max(score, 85);
            reason.append("low health; ");
        } else if (state.health < 12) {
            score = Math.max(score, 50);
            reason.append("moderate health; ");
        }

        // Hunger dimension
        if (state.hunger <= 2) {
            score = Math.max(score, 95);
            reason.append("starving; ");
        } else if (state.hunger <= 6) {
            score = Math.max(score, 70);
            reason.append("hungry; ");
        }

        // Threat dimension (from CombatThreatSensor)
        if (sensors.threatScore >= 80) {
            score = Math.max(score, 95);
            reason.append("imminent threat; ");
        } else if (sensors.threatScore >= 50) {
            score = Math.max(score, 65);
            reason.append("elevated threat; ");
        } else if (sensors.threatScore >= 20) {
            score = Math.max(score, 30);
            reason.append("minor threat; ");
        }

        // Hazard dimension
        if (sensors.hasLavaHazard) {
            score = Math.max(score, 60);
            reason.append("lava nearby; ");
        }
        if (sensors.hasDropHazard) {
            score = Math.max(score, 45);
            reason.append("drop hazard; ");
        }

        return new GoalScore(NeedType.SURVIVAL_DEFENSE, score,
                score == 0 ? "safe" : reason.toString().stripTrailing());
    }

    private static GoalScore scoreGearUp(WorldState state, SensorData sensors) {
        double score = switch (sensors.gearTier) {
            case 0 -> 75.0; // no tools at all
            case 1 -> 55.0; // wood / gold only
            case 2 -> 38.0; // stone tools
            case 3 -> 20.0; // iron tools – diamond upgrade worthwhile
            case 4 ->  8.0; // diamond – minor netherite nudge
            default ->  0.0; // netherite maxed
        };

        // Weapon disadvantage boosts urgency
        if (sensors.weaponAdvantage < -0.3) score = Math.min(80, score + 15);

        // Active close-range threat suppresses gear-up: agent must survive first
        // (GEAR_UP is a strategic goal; it cannot be safely pursued under direct attack)
        if (sensors.threatScore >= 60) score = Math.min(score, 35.0);

        return new GoalScore(NeedType.GEAR_UP, score,
                "gear tier " + sensors.gearTier + ", weapon adv " +
                        String.format("%.2f", sensors.weaponAdvantage));
    }

    private static GoalScore scoreBuildBase(WorldState state, SensorData sensors,
                                             MemoryModule memory) {
        double score;
        if (state.isNight && !state.hasShelter) {
            score = 58.0;
        } else if (!state.hasShelter && state.gameTick >= 10_000 && state.gameTick < 13_000) {
            score = 45.0;  // dusk approaching
        } else if (!state.hasShelter) {
            score = 25.0;  // daytime prep
        } else if (!memory.hasHomeLocation()) {
            score = 18.0;  // has shelter but no home registered yet
        } else {
            score = 5.0;   // settled – maintenance only
        }

        if (sensors.inventoryFull) score = Math.min(60.0, score + 15.0);

        return new GoalScore(NeedType.BUILD_BASE, score,
                "shelter=" + state.hasShelter + " homeKnown=" + memory.hasHomeLocation());
    }

    private static GoalScore scoreExplore(WorldState state, SensorData sensors) {
        // Explore is the default idle task; it yields when anything else is urgent
        boolean allSafe = state.health >= 15
                && state.hunger >= 12
                && sensors.threatScore < 10
                && !sensors.hasLavaHazard
                && !sensors.hasDropHazard;

        double score = allSafe ? 20.0 : 5.0;
        return new GoalScore(NeedType.EXPLORE, score,
                allSafe ? "all needs satisfied" : "survival needs active");
    }

    // ── Private: step-satisfaction checks ────────────────────────────────────

    /**
     * Returns {@code true} when the given action step is already satisfied and
     * can be skipped in the plan.
     */
    private static boolean isStepSatisfied(String step, WorldState state, SensorData sensors) {
        return switch (step) {
            case "gather_wood" ->
                    state.inventory.stream()
                            .filter(i -> i.name.contains("log") || i.name.contains("planks"))
                            .mapToInt(i -> i.count).sum() >= 8;
            case "craft_planks" ->
                    state.inventory.stream()
                            .filter(i -> i.name.contains("planks"))
                            .mapToInt(i -> i.count).sum() >= 4;
            case "craft_sticks" ->
                    state.inventory.stream()
                            .filter(i -> i.name.equals("stick") || i.name.equals("minecraft:stick"))
                            .mapToInt(i -> i.count).sum() >= 4;
            case "mine_stone" ->
                    state.inventory.stream()
                            .filter(i -> i.name.contains("cobblestone"))
                            .mapToInt(i -> i.count).sum() >= 3;
            case "craft_stone_tools" -> sensors.gearTier >= 2;
            case "mine_iron" ->
                    state.inventory.stream()
                            .filter(i -> i.name.contains("iron_ingot") || i.name.contains("raw_iron"))
                            .mapToInt(i -> i.count).sum() >= 3;
            case "smelt_iron" ->
                    state.inventory.stream()
                            .filter(i -> i.name.contains("iron_ingot"))
                            .mapToInt(i -> i.count).sum() >= 3;
            case "craft_iron_tools"       -> sensors.gearTier >= 3;
            case "craft_iron_pickaxe"     -> sensors.canCraftIronPickaxe;
            case "craft_shield"           -> sensors.canCraftShield;
            case "mine_diamonds"          -> sensors.gearTier >= 4;
            case "craft_diamond_tools"    -> sensors.gearTier >= 4;
            case "eat_food"               -> state.hunger >= 18;
            case "find_or_build_shelter",
                 "build_shelter"          -> state.hasShelter;
            case "gather_food",
                 "forage_food"            -> sensors.foodCount >= 4;
            case "craft_tools"            -> state.hasTools;
            default                       -> false;
        };
    }
}
