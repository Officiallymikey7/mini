package io.github.officiallymikey7.mini.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes urgency scores for each survival need and exposes deterministic
 * priority selection. Higher score = more urgent (0–100).
 */
public final class Needs {

    private Needs() {}

    /**
     * Returns all need scores sorted descending (most urgent first).
     */
    public static List<NeedScore> computeNeeds(WorldState state) {
        List<NeedScore> scores = new ArrayList<>();

        // ── Survival / Defense ──────────────────────────────────────────────
        int hostileCount = state.nearbyHostiles.size();
        double closestHostile = hostileCount > 0
                ? state.nearbyHostiles.stream().mapToDouble(h -> h.distance).min().orElse(Double.POSITIVE_INFINITY)
                : Double.POSITIVE_INFINITY;

        double defenseScore = 0;
        if (closestHostile < 5)                           defenseScore = 100;
        else if (closestHostile < 10)                     defenseScore = 80;
        else if (closestHostile < 20)                     defenseScore = 50;
        else if (state.isNight && !state.hasShelter)      defenseScore = 60;
        else if (state.isNight)                           defenseScore = 20;
        if (state.health < 6)                             defenseScore = Math.max(defenseScore, 90);

        String defenseReason = hostileCount > 0
                ? hostileCount + " hostile(s), closest at " + String.format("%.1f", closestHostile) + "m"
                : state.isNight ? "Night-time danger" : "Safe";
        scores.add(new NeedScore(NeedType.SURVIVAL_DEFENSE, defenseScore, defenseReason));

        // ── Food ────────────────────────────────────────────────────────────
        double foodScore = 0;
        if      (state.hunger <= 2)  foodScore = 95;
        else if (state.hunger <= 6)  foodScore = 70;
        else if (state.hunger <= 10) foodScore = 40;
        else if (state.hunger <= 14) foodScore = 15;
        scores.add(new NeedScore(NeedType.FOOD, foodScore, "Hunger: " + (int) state.hunger + "/20"));

        // ── Shelter ─────────────────────────────────────────────────────────
        double shelterScore = 0;
        if (state.isNight && !state.hasShelter) {
            shelterScore = state.shelterDistance < 0 ? 85
                    : Math.max(10, 85 - state.shelterDistance * 2.0);
        } else if (!state.isNight && !state.hasShelter) {
            shelterScore = 20; // prepare before nightfall
        }
        String shelterReason = state.hasShelter
                ? "Has shelter"
                : "No shelter (distance: " + state.shelterDistance + ")";
        scores.add(new NeedScore(NeedType.SHELTER, shelterScore, shelterReason));

        // ── Tools ────────────────────────────────────────────────────────────
        double toolScore = state.hasTools ? 5 : 45;
        scores.add(new NeedScore(NeedType.TOOLS, toolScore,
                state.hasTools ? "Tools available" : "No usable tools"));

        // ── Resources ────────────────────────────────────────────────────────
        int woodCount = state.inventory.stream()
                .filter(i -> i.name.contains("log") || i.name.contains("wood"))
                .mapToInt(i -> i.count)
                .sum();
        double resourceScore = woodCount < 16 ? 30 : 10;
        scores.add(new NeedScore(NeedType.RESOURCES, resourceScore, "Wood: " + woodCount));

        // ── Gear-Up (0–75): upgrade tool / armour tier ───────────────────────
        int bestTier = detectBestGearTier(state);
        double gearUpScore = switch (bestTier) {
            case 0 -> 75.0; // no tools at all
            case 1 -> 25.0; // only wood / gold tools
            case 2 -> 18.0; // stone tools
            case 3 -> 10.0; // iron tools – diamond upgrade worthwhile
            case 4 ->  5.0; // diamond – minor netherite nudge
            default ->  0.0; // netherite – maxed out
        };
        scores.add(new NeedScore(NeedType.GEAR_UP, gearUpScore,
                "Best gear tier: " + bestTier));

        // ── Build-Base (0–60): proactive base construction ───────────────────
        int invCount = state.inventory.size();
        double buildScore;
        if (state.isNight && !state.hasShelter) {
            buildScore = 58.0; // night + no shelter: urgent base needed
        } else if (!state.hasShelter && state.gameTick >= 10_000 && state.gameTick < 13_000) {
            buildScore = 45.0; // dusk approaching + no shelter
        } else if (!state.hasShelter) {
            buildScore = 25.0; // daytime prep before nightfall
        } else {
            buildScore = 5.0;  // has shelter: low maintenance drive
        }
        // Bonus when inventory is nearly full (> 25 stacks) – agent needs a chest
        if (invCount > 25) buildScore = Math.min(60.0, buildScore + 15.0);
        scores.add(new NeedScore(NeedType.BUILD_BASE, buildScore,
                "Shelter: " + state.hasShelter + ", inv: " + invCount + " stacks"));

        // ── Explore (0–20): default idle baseline ────────────────────────────
        boolean allSafe = defenseScore <= 10 && foodScore <= 10
                && shelterScore == 0 && !state.isNight;
        double exploreScore = allSafe ? 20.0 : 5.0;
        scores.add(new NeedScore(NeedType.EXPLORE, exploreScore,
                allSafe ? "All needs satisfied" : "Survival needs active"));

        scores.sort(Comparator.comparingDouble((NeedScore n) -> n.score).reversed());
        return scores;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the best gear tier found in the inventory:
     * 0=none, 1=wood/gold, 2=stone, 3=iron, 4=diamond, 5=netherite.
     * Falls back to tier 1 when {@code state.hasTools} is {@code true} but no
     * named tool items are present (e.g. in unit tests with abstract inventory).
     */
    private static int detectBestGearTier(WorldState state) {
        int best = 0;
        for (InventoryItem item : state.inventory) {
            String n = item.name.toLowerCase();
            if (n.contains("netherite_pickaxe") || n.contains("netherite_sword")
                    || n.contains("netherite_axe")) {
                best = Math.max(best, 5);
            } else if (n.contains("diamond_pickaxe") || n.contains("diamond_sword")
                    || n.contains("diamond_axe")) {
                best = Math.max(best, 4);
            } else if (n.contains("iron_pickaxe") || n.contains("iron_sword")
                    || n.contains("iron_axe")) {
                best = Math.max(best, 3);
            } else if (n.contains("stone_pickaxe") || n.contains("stone_sword")
                    || n.contains("stone_axe")) {
                best = Math.max(best, 2);
            } else if (n.contains("pickaxe") || n.contains("sword") || n.contains("_axe")) {
                best = Math.max(best, 1); // wood / gold
            }
        }
        // If hasTools is true but no tool item was found, treat as wood-tier
        if (best == 0 && state.hasTools) best = 1;
        return best;
    }

    /** Returns the single highest-priority need (deterministic). */
    public static NeedScore getTopNeed(WorldState state) {
        return computeNeeds(state).get(0);
    }
}
