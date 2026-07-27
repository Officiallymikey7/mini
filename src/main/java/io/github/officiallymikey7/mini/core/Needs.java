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

        scores.sort(Comparator.comparingDouble((NeedScore n) -> n.score).reversed());
        return scores;
    }

    /** Returns the single highest-priority need (deterministic). */
    public static NeedScore getTopNeed(WorldState state) {
        return computeNeeds(state).get(0);
    }
}
