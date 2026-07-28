package io.github.officiallymikey7.mini.ai.decision;

import io.github.officiallymikey7.mini.ai.VillagerRole;
import io.github.officiallymikey7.mini.ai.perception.PerceptionSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic utility scorer for Phase 2 action selection.
 */
public final class UtilityDecisionEngine {

    private static final List<String> ACTION_TIE_BREAKER = List.of(
            "attack_nearest_hostile",
            "flee_to_shelter",
            "forage_food",
            "gather_food",
            "find_or_build_shelter",
            "build_shelter",
            "gather_wood",
            "explore");

    public record ScoredCandidate(
            DecisionPlan plan,
            double score,
            double safety,
            double hunger,
            double shelter,
            double roleAffinity,
            double distance) {
        public String summary() {
            return plan.actionName + "=" + String.format("%.2f", score)
                    + "(s=" + String.format("%.1f", safety)
                    + ",h=" + String.format("%.1f", hunger)
                    + ",sh=" + String.format("%.1f", shelter)
                    + ",r=" + String.format("%.1f", roleAffinity)
                    + ",d=" + String.format("%.1f", distance) + ")";
        }
    }

    public record DecisionResult(DecisionPlan selectedPlan, List<ScoredCandidate> rankedCandidates) {}

    public DecisionResult choosePlan(PerceptionSnapshot snapshot, VillagerRole role) {
        List<ScoredCandidate> scored = new ArrayList<>();
        score(scored, role, snapshot, DecisionPlan.of("attack_nearest_hostile", null, 180, 2));
        score(scored, role, snapshot, DecisionPlan.of("flee_to_shelter", null, 220, 3));
        score(scored, role, snapshot, DecisionPlan.of("forage_food", null, 260, 4));
        score(scored, role, snapshot, DecisionPlan.of("gather_food", null, 260, 4));
        score(scored, role, snapshot, DecisionPlan.of("find_or_build_shelter", null, 300, 3));
        score(scored, role, snapshot, DecisionPlan.of("build_shelter", null, 300, 3));
        score(scored, role, snapshot, DecisionPlan.of("gather_wood", null, 260, 3));
        score(scored, role, snapshot, DecisionPlan.of("explore", null, 200, 2));

        scored.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparingInt(c -> tieBreakerIndex(c.plan.actionName)));

        return new DecisionResult(scored.get(0).plan, List.copyOf(scored));
    }

    private void score(List<ScoredCandidate> scored, VillagerRole role, PerceptionSnapshot snapshot, DecisionPlan plan) {
        String action = plan.actionName;

        double safety = 0.0;
        double hunger = 0.0;
        double shelter = 0.0;
        double roleAffinity = roleAffinity(role, action);
        double distance = 0.0;

        if (snapshot.hasHostileThreat()) {
            safety += switch (action) {
                case "attack_nearest_hostile", "flee_to_shelter" -> 8.0;
                case "find_or_build_shelter" -> 4.0;
                default -> -4.0;
            };
        } else if ("explore".equals(action)) {
            safety += 2.0;
        }

        if ("forage_food".equals(action) || "gather_food".equals(action)) {
            hunger += snapshot.foodStock <= 0 ? 7.0 : snapshot.foodStock <= 2 ? 3.0 : -2.0;
        } else if ("gather_wood".equals(action) && snapshot.foodStock <= 0) {
            hunger -= 2.0;
        }

        if ("find_or_build_shelter".equals(action) || "build_shelter".equals(action)) {
            shelter += !snapshot.isSheltered && snapshot.isNight ? 7.0 : !snapshot.isSheltered ? 3.0 : -2.0;
            if (role == VillagerRole.BUILDER && snapshot.woodStock < 2) {
                shelter -= 3.0;
            }
        } else if ("gather_wood".equals(action) && role == VillagerRole.BUILDER && snapshot.woodStock < 2) {
            shelter += 4.0;
        }

        if ("attack_nearest_hostile".equals(action)) {
            distance += proximityScore(snapshot.nearestHostileDistance, 12.0, 2.0);
        } else if ("forage_food".equals(action) || "gather_food".equals(action)) {
            distance += proximityScore(snapshot.nearestFoodDistance, 14.0, 1.5);
        } else if ("gather_wood".equals(action)) {
            distance += proximityScore(snapshot.nearestWoodDistance, 14.0, 1.5);
        }

        double total = safety + hunger + shelter + roleAffinity + distance;
        scored.add(new ScoredCandidate(plan, total, safety, hunger, shelter, roleAffinity, distance));
    }

    private static double roleAffinity(VillagerRole role, String action) {
        return switch (role) {
            case FARMER -> switch (action) {
                case "forage_food", "gather_food" -> 4.5;
                case "find_or_build_shelter" -> 0.5;
                case "explore" -> 0.2;
                case "attack_nearest_hostile" -> -1.0;
                default -> 0.0;
            };
            case GUARD -> switch (action) {
                case "attack_nearest_hostile" -> 5.0;
                case "flee_to_shelter" -> 2.0;
                case "explore" -> 2.5;
                case "forage_food" -> -0.5;
                default -> 0.0;
            };
            case BUILDER -> switch (action) {
                case "find_or_build_shelter", "build_shelter" -> 5.0;
                case "gather_wood" -> 3.0;
                case "attack_nearest_hostile" -> -1.0;
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    private static double proximityScore(double distance, double maxRange, double weight) {
        if (distance < 0) return -weight;
        double normalized = Math.max(0.0, 1.0 - (distance / maxRange));
        return normalized * weight;
    }

    private static int tieBreakerIndex(String action) {
        int index = ACTION_TIE_BREAKER.indexOf(action);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
