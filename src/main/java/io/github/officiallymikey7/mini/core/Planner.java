package io.github.officiallymikey7.mini.core;

import io.github.officiallymikey7.mini.memory.ChatMessage;
import io.github.officiallymikey7.mini.memory.SocialBlock;
import io.github.officiallymikey7.mini.roles.RoleDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hierarchical planner:
 * <ol>
 *   <li>Emergency override (life-threatening conditions)</li>
 *   <li>Social-horizon reactive goal (neighbour in danger)</li>
 *   <li>Role-biased top need</li>
 * </ol>
 */
public final class Planner {

    private Planner() {}

    // ── Need → action mapping ────────────────────────────────────────────────

    private record ActionMapping(String action, String description) {}

    private static final Map<NeedType, ActionMapping> NEED_ACTION_MAP =
            new EnumMap<>(NeedType.class);

    static {
        NEED_ACTION_MAP.put(NeedType.SURVIVAL_DEFENSE,
                new ActionMapping("craft_sword_or_flee", "Prepare for defense or flee danger"));
        NEED_ACTION_MAP.put(NeedType.FOOD,
                new ActionMapping("gather_food", "Gather or grow food to restore hunger"));
        NEED_ACTION_MAP.put(NeedType.SHELTER,
                new ActionMapping("build_shelter", "Construct or improve shelter"));
        NEED_ACTION_MAP.put(NeedType.TOOLS,
                new ActionMapping("craft_tools", "Gather wood and craft basic tools"));
        NEED_ACTION_MAP.put(NeedType.RESOURCES,
                new ActionMapping("gather_wood", "Chop trees and gather wood resources"));
    }

    // ── Social reaction patterns ─────────────────────────────────────────────

    private record SocialPattern(List<String> words, String action, String description) {}

    private static final List<SocialPattern> SOCIAL_PATTERNS = List.of(
            new SocialPattern(
                    List.of("zombie", "broke", "door", "attacked"),
                    "craft_and_defend",
                    "Neighbor reports hostile breach – craft weapon and assist"),
            new SocialPattern(
                    List.of("help", "dying"),
                    "assist_neighbor",
                    "Neighbor requesting assistance"),
            new SocialPattern(
                    List.of("food", "starving", "hungry"),
                    "share_food",
                    "Neighbor is hungry – share food if available")
    );

    // ── Emergency detection ──────────────────────────────────────────────────

    private static Subgoal checkEmergency(WorldState state, List<NeedScore> topNeeds) {
        NeedScore defense = topNeeds.stream()
                .filter(n -> n.need == NeedType.SURVIVAL_DEFENSE)
                .findFirst().orElse(null);
        NeedScore food = topNeeds.stream()
                .filter(n -> n.need == NeedType.FOOD)
                .findFirst().orElse(null);

        // Critical health – check before defense-score boost
        if (state.health < 4) {
            return new Subgoal("emergency_heal",
                    "Critically low health – eat or heal immediately",
                    "eat_food", SubgoalTag.EMERGENCY, 95);
        }

        // Imminent hostile threat
        if (defense != null && defense.score >= 80 && !state.nearbyHostiles.isEmpty()) {
            HostileEntity hostile = state.nearbyHostiles.stream()
                    .min(Comparator.comparingDouble(h -> h.distance))
                    .orElseThrow();
            String action = hostile.distance < 5 ? "attack_nearest_hostile" : "flee_to_shelter";
            return new Subgoal("emergency_defend",
                    "Defend against nearby " + hostile.type,
                    action, SubgoalTag.EMERGENCY, 100);
        }

        // Night with no shelter
        if (state.isNight && !state.hasShelter) {
            return new Subgoal("emergency_shelter",
                    "Seek or build emergency shelter – it is night",
                    "find_or_build_shelter", SubgoalTag.EMERGENCY, 90);
        }

        // Starvation
        if (food != null && food.score >= 90) {
            return new Subgoal("emergency_eat",
                    "Starving – find and eat food immediately",
                    "forage_food", SubgoalTag.EMERGENCY, 88);
        }

        return null;
    }

    // ── Social-horizon reactive planning ─────────────────────────────────────

    private static Subgoal checkSocialReaction(SocialBlock social) {
        for (ChatMessage msg : social.messages) {
            String lower = msg.text.toLowerCase();
            for (SocialPattern pattern : SOCIAL_PATTERNS) {
                if (pattern.words.stream().anyMatch(lower::contains)) {
                    return new Subgoal(
                            "social_react_" + pattern.action,
                            pattern.description,
                            pattern.action,
                            SubgoalTag.SOCIAL,
                            70);
                }
            }
        }
        return null;
    }

    // ── Role bias ────────────────────────────────────────────────────────────

    /**
     * Applies role-specific need-weight multipliers and returns the top adjusted need.
     * Emergency needs (score ≥ 75) are NEVER reduced below their threshold.
     */
    private static NeedScore applyRoleBias(List<NeedScore> needs, RoleDefinition role) {
        Map<NeedType, Double> weights = role.getNeedWeights();
        List<NeedScore> adjusted = new ArrayList<>();
        for (NeedScore n : needs) {
            double multiplier = weights.getOrDefault(n.need, 1.0);
            double rawAdj = n.score * multiplier;
            double adj = n.score >= 75 ? Math.max(n.score, rawAdj) : rawAdj;
            adjusted.add(new NeedScore(n.need, adj, n.reason));
        }
        adjusted.sort(Comparator.comparingDouble((NeedScore n) -> n.score).reversed());
        return adjusted.get(0);
    }

    // ── Subgoal prompt builder ───────────────────────────────────────────────

    /**
     * Builds the structured decision prompt injected into every planner cycle.
     *
     * <p>The prompt addresses three operational bottlenecks:
     * <ol>
     *   <li><b>Structured output</b> – the agent is told to reply with a strict JSON
     *       schema so the Java executor can parse the response without prose
     *       interpretation.</li>
     *   <li><b>Spatial context</b> – position, light level, biome, equipped items, and
     *       nearby blocks are included so the agent can make spatially-aware
     *       decisions (e.g. detect a hostile 4 blocks away before choosing to chop
     *       a tree).</li>
     *   <li><b>Mastery memory</b> – a list of already-mastered actions is embedded so
     *       the agent does not keep re-selecting goals it has already achieved.</li>
     * </ol>
     */
    public static String buildSubgoalPrompt(PlannerInput input) {
        WorldState    state = input.state;
        RoleDefinition role = input.role;

        String traits = role.getTraits().isEmpty()
                ? "resourceful"
                : String.join(", ", role.getTraits());

        // ── Mastered skills ──────────────────────────────────────────────────
        String masteredSkillsList = input.reflection.masteredSkills.isEmpty()
                ? "none"
                : String.join(", ", input.reflection.masteredSkills);

        // ── Recent failures ──────────────────────────────────────────────────
        String recentFailures = input.reflection.entries.stream()
                .filter(e -> e.status.equals("failure") || e.status.equals("timeout"))
                .map(e -> e.action)
                .distinct()
                .collect(Collectors.joining(", "));
        if (recentFailures.isEmpty()) recentFailures = "none";

        // ── JSON serialisation of complex fields ─────────────────────────────
        String inventoryJson = state.inventory.isEmpty()
                ? "[]"
                : state.inventory.stream()
                        .map(i -> "{\"item\":\"" + i.name + "\",\"count\":" + i.count + "}")
                        .collect(Collectors.joining(",", "[", "]"));

        String nearbyMobsJson = state.nearbyHostiles.isEmpty()
                ? "[]"
                : state.nearbyHostiles.stream()
                        .map(h -> "{\"type\":\"" + h.type
                                + "\",\"distance\":" + String.format("%.1f", h.distance) + "}")
                        .collect(Collectors.joining(",", "[", "]"));

        String nearbyBlocksJson = state.nearbyBlocks.isEmpty()
                ? "[]"
                : state.nearbyBlocks.stream()
                        .map(b -> "\"" + b + "\"")
                        .collect(Collectors.joining(",", "[", "]"));

        return "You are an autonomous Minecraft Fabric AI agent operating in a real-time world.\n"
                + "Your core objective is to explore, survive, gather resources, and build a skill\n"
                + "library without human assistance.\n\n"
                + "RULES:\n"
                + "1. Always prioritise immediate survival (low health, nearby hostiles, nightfall)"
                + " over long-term goals.\n"
                + "2. Output MUST be valid JSON matching the schema shown in the TASK section.\n"
                + "3. Do not attempt goals listed in 'mastered_skills' unless required for basic"
                + " survival.\n"
                + "4. If an action continuously fails, select an alternative strategy.\n\n"
                + "=== REAL-TIME STATE ===\n"
                + "Health: " + state.health + "/20 | Hunger: " + (int) state.hunger + "/20\n"
                + "Position: X=" + String.format("%.1f", state.x)
                + ", Y=" + String.format("%.1f", state.y)
                + ", Z=" + String.format("%.1f", state.z) + "\n"
                + "Biome: " + state.biome
                + " | Time of Day: " + state.gameTick
                + " | Night: " + state.isNight + "\n"
                + "Light Level: " + state.lightLevel + "\n"
                + "Main Hand: " + state.mainHandItem
                + " | Off Hand: " + state.offHandItem + "\n"
                + "Inventory: " + inventoryJson + "\n"
                + "Nearby Mobs: " + nearbyMobsJson + "\n"
                + "Nearby Blocks of Interest: " + nearbyBlocksJson + "\n\n"
                + "=== AGENT MEMORY ===\n"
                + "Agent: " + state.agentName + "\n"
                + "Role: " + role.getLabel() + " | Traits: " + traits + "\n"
                + "Community Goal: " + input.communityGoal + "\n"
                + "Mastered Skills: " + masteredSkillsList + "\n"
                + "Last Action Status: " + recentFailures + " (recent failures)\n\n"
                + "[Self-Reflection Block]\n"
                + input.reflection.summary + "\n\n"
                + "[Social Horizon Block]\n"
                + input.social.summary + "\n\n"
                + "=== TASK ===\n"
                + "Analyse your state and determine the single most logical immediate subgoal.\n"
                + "Respond ONLY with a JSON object in this exact format:\n"
                + "{\n"
                + "  \"reasoning\": \"<one sentence explaining your choice>\",\n"
                + "  \"subgoal_type\": \"<EMERGENCY|SOCIAL|ROLE>\",\n"
                + "  \"action\": \"<action_id>\",\n"
                + "  \"priority\": <0-100>\n"
                + "}";
    }

    // ── Main entry-point ─────────────────────────────────────────────────────

    /**
     * Selects the next subgoal using the three-level hierarchy:
     * emergency → social → role-biased need.
     */
    public static PlannerOutput plan(PlannerInput input) {
        String promptContext = buildSubgoalPrompt(input);

        Subgoal emergency = checkEmergency(input.state, input.topNeeds);
        if (emergency != null) return new PlannerOutput(emergency, promptContext);

        Subgoal socialReact = checkSocialReaction(input.social);
        if (socialReact != null) return new PlannerOutput(socialReact, promptContext);

        NeedScore topNeed = applyRoleBias(input.topNeeds, input.role);
        ActionMapping mapped = NEED_ACTION_MAP.getOrDefault(topNeed.need,
                new ActionMapping("explore", "Explore surroundings for opportunities"));

        Subgoal subgoal = new Subgoal(
                "role_" + input.role.getId() + "_" + topNeed.need.name().toLowerCase(),
                "[" + input.role.getLabel() + "] " + mapped.description,
                mapped.action,
                SubgoalTag.ROLE,
                topNeed.score);

        return new PlannerOutput(subgoal, promptContext);
    }
}
