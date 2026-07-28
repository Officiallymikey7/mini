package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.core.*;
import io.github.officiallymikey7.mini.memory.ReflectionBlock;
import io.github.officiallymikey7.mini.memory.ReflectionMemory;
import io.github.officiallymikey7.mini.memory.SocialBlock;
import io.github.officiallymikey7.mini.memory.SocialMemory;
import io.github.officiallymikey7.mini.roles.RoleDefinition;
import io.github.officiallymikey7.mini.roles.RoleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Planner#plan(PlannerInput)}.
 *
 * <p>Mirrors the TypeScript {@code planner.test.ts} suite.
 */
class PlannerTest {

    private ReflectionBlock emptyReflection;
    private SocialBlock     emptySocial;
    private RoleDefinition  farmer;

    @BeforeEach
    void setUp() {
        emptyReflection = new ReflectionMemory().build();
        emptySocial     = new SocialMemory().build();
        farmer          = RoleRegistry.getRole("farmer").orElseThrow();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private WorldState state(float health, float hunger, boolean isNight, boolean hasShelter,
                             boolean hasTools, List<HostileEntity> hostiles, List<InventoryItem> inv) {
        return new WorldState("Arlo", 6000, isNight, health, hunger,
                hostiles, inv, 0, hasShelter, hasTools, List.of(),
                System.currentTimeMillis(),
                0.0, 64.0, 0.0, 15, "plains", "air", "air", List.of());
    }

    private PlannerInput input(WorldState s) {
        List<NeedScore> needs = Needs.computeNeeds(s);
        return new PlannerInput(s, needs, farmer, emptyReflection, emptySocial,
                farmer.getCommunityGoal());
    }

    // ── Emergency paths ───────────────────────────────────────────────────────

    @Test
    void criticalHealthTriggersEmergencyHeal() {
        WorldState s = state(3, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.EMERGENCY, out.subgoal.tag);
        assertEquals("eat_food", out.subgoal.action);
    }

    @Test
    void hostileClose_triggersAttack() {
        WorldState s = state(20, 18, false, true, true,
                List.of(new HostileEntity("zombie", 3.0)), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.EMERGENCY, out.subgoal.tag);
        assertEquals("attack_nearest_hostile", out.subgoal.action);
    }

    @Test
    void hostileFar_triggersFlee() {
        WorldState s = state(20, 18, false, true, true,
                List.of(new HostileEntity("zombie", 7.0)), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.EMERGENCY, out.subgoal.tag);
        assertEquals("flee_to_shelter", out.subgoal.action);
    }

    @Test
    void nightNoShelter_triggersEmergencyShelter() {
        WorldState s = state(20, 18, true, false, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.EMERGENCY, out.subgoal.tag);
        assertEquals("find_or_build_shelter", out.subgoal.action);
    }

    @Test
    void starvation_triggersForageFood() {
        WorldState s = state(20, 1, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.EMERGENCY, out.subgoal.tag);
        assertEquals("forage_food", out.subgoal.action);
    }

    // ── Social reactions ──────────────────────────────────────────────────────

    @Test
    void neighborCallsHelp_triggersAssist() {
        SocialMemory sm = new SocialMemory();
        sm.ingest(List.of("Bob: help I'm dying!"), "nearby");
        SocialBlock social = sm.build();

        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        List<NeedScore> needs = Needs.computeNeeds(s);
        PlannerInput pin = new PlannerInput(s, needs, farmer, emptyReflection, social, farmer.getCommunityGoal());

        PlannerOutput out = Planner.plan(pin);
        assertEquals(SubgoalTag.SOCIAL, out.subgoal.tag);
        assertEquals("assist_neighbor", out.subgoal.action);
    }

    @Test
    void neighborStarving_triggersShareFood() {
        SocialMemory sm = new SocialMemory();
        sm.ingest(List.of("Alice: I'm so hungry, starving here"), "nearby");
        SocialBlock social = sm.build();

        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        List<NeedScore> needs = Needs.computeNeeds(s);
        PlannerInput pin = new PlannerInput(s, needs, farmer, emptyReflection, social, farmer.getCommunityGoal());

        PlannerOutput out = Planner.plan(pin);
        assertEquals(SubgoalTag.SOCIAL, out.subgoal.tag);
        assertEquals("share_food", out.subgoal.action);
    }

    @Test
    void zombieBreachReport_triggersDefend() {
        SocialMemory sm = new SocialMemory();
        sm.ingest(List.of("Guard: zombie broke through the door!"), "nearby");
        SocialBlock social = sm.build();

        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        List<NeedScore> needs = Needs.computeNeeds(s);
        PlannerInput pin = new PlannerInput(s, needs, farmer, emptyReflection, social, farmer.getCommunityGoal());

        PlannerOutput out = Planner.plan(pin);
        assertEquals(SubgoalTag.SOCIAL, out.subgoal.tag);
        assertEquals("craft_and_defend", out.subgoal.action);
    }

    // ── Role-biased planning ──────────────────────────────────────────────────

    @Test
    void safeState_farmerPrioritisesFood() {
        // Farmer has food weight 1.4 – with low hunger they should gather food
        WorldState s = state(20, 8, false, true, true, List.of(),
                List.of(new InventoryItem("oak_log", 20))); // enough wood
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.ROLE, out.subgoal.tag);
        assertEquals("gather_food", out.subgoal.action,
                "Farmer should prioritise food gathering");
    }

    @Test
    void roleTagPresentOnRolePlanning() {
        WorldState s = state(20, 18, false, true, true, List.of(),
                List.of(new InventoryItem("oak_log", 20)));
        PlannerOutput out = Planner.plan(input(s));
        assertEquals(SubgoalTag.ROLE, out.subgoal.tag);
    }

    // ── Prompt context ────────────────────────────────────────────────────────

    @Test
    void promptContextContainsAgentName() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains("Arlo"),
                "Prompt context should include the agent name");
    }

    @Test
    void promptContextContainsCommunityGoal() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains(farmer.getCommunityGoal()),
                "Prompt context should include the community goal");
    }

    @Test
    void promptContextContainsReflectionBlock() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains("[Self-Reflection Block]"),
                "Prompt context should include the reflection block header");
    }

    @Test
    void promptContextContainsJsonSchema() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains("\"reasoning\""),
                "Prompt context should include the JSON schema 'reasoning' field");
        assertTrue(out.promptContext.contains("\"subgoal_type\""),
                "Prompt context should include the JSON schema 'subgoal_type' field");
        assertTrue(out.promptContext.contains("\"action\""),
                "Prompt context should include the JSON schema 'action' field");
        assertTrue(out.promptContext.contains("\"priority\""),
                "Prompt context should include the JSON schema 'priority' field");
    }

    @Test
    void promptContextContainsSpatialState() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains("Position:"),
                "Prompt context should include position");
        assertTrue(out.promptContext.contains("Light Level:"),
                "Prompt context should include light level");
        assertTrue(out.promptContext.contains("Biome:"),
                "Prompt context should include biome");
        assertTrue(out.promptContext.contains("Main Hand:"),
                "Prompt context should include main hand item");
        assertTrue(out.promptContext.contains("Nearby Blocks of Interest:"),
                "Prompt context should include nearby blocks section");
    }

    @Test
    void promptContextContainsMasteryMemory() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of());
        PlannerOutput out = Planner.plan(input(s));
        assertTrue(out.promptContext.contains("Mastered Skills:"),
                "Prompt context should include mastered skills");
        assertTrue(out.promptContext.contains("Recent failures:"),
                "Prompt context should include recent failures");
    }
}
