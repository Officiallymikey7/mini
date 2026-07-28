package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.brain.MemoryModule;
import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;
import io.github.officiallymikey7.mini.core.NeedType;
import io.github.officiallymikey7.mini.core.WorldState;
import io.github.officiallymikey7.mini.goap.ActionPlan;
import io.github.officiallymikey7.mini.goap.GOAPPlanner;
import io.github.officiallymikey7.mini.goap.GoalScore;
import io.github.officiallymikey7.mini.sensor.SensorData;
import io.github.officiallymikey7.mini.sensor.SensorSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GOAPPlanner} – utility evaluation and plan building.
 */
class GOAPPlannerTest {

    private GOAPPlanner planner;
    private MemoryModule memory;

    @BeforeEach
    void setUp() {
        planner = new GOAPPlanner();
        memory  = new MemoryModule();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static WorldState state(float health, float hunger, boolean isNight, boolean hasShelter,
                                    boolean hasTools, List<HostileEntity> hostiles,
                                    List<InventoryItem> inv) {
        return new WorldState("Brain", 6000, isNight, health, hunger,
                hostiles, inv, 0, hasShelter, hasTools, List.of(),
                System.currentTimeMillis(),
                0.0, 64.0, 0.0, 15, "plains", "air", "air", List.of());
    }

    private SensorData sensors(WorldState s) {
        return new SensorSuite().update(s, memory);
    }

    // ── Utility evaluation ────────────────────────────────────────────────────

    @Test
    void criticalHealth_survivePriority() {
        WorldState s = state(3, 18, false, true, true, List.of(), List.of());
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        assertEquals(NeedType.SURVIVAL_DEFENSE, top.goalType());
        assertEquals(100.0, top.score(), 0.01);
    }

    @Test
    void starving_surviveHighScore() {
        WorldState s = state(20, 1, false, true, true, List.of(), List.of());
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        assertEquals(NeedType.SURVIVAL_DEFENSE, top.goalType());
        assertTrue(top.score() >= 90);
    }

    @Test
    void noTools_gearUpSelectedOverExplore() {
        WorldState s = state(20, 18, false, true, false, List.of(), List.of());
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        assertNotEquals(NeedType.EXPLORE, top.goalType(),
                "Agent with no tools should not idle-explore");
    }

    @Test
    void safeFullyEquipped_exploreSelected() {
        WorldState s = state(20, 20, false, true, true, List.of(),
                List.of(new InventoryItem("diamond_sword", 1),
                        new InventoryItem("diamond_pickaxe", 1)));
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        assertEquals(NeedType.EXPLORE, top.goalType(),
                "Safe, well-equipped agent should idle-explore");
    }

    @Test
    void closeThreat_surviveBeatsGearUp() {
        WorldState s = state(20, 18, false, true, false,
                List.of(new HostileEntity("zombie", 3.0)), List.of());
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        assertEquals(NeedType.SURVIVAL_DEFENSE, top.goalType());
    }

    @Test
    void nightNoShelter_buildBaseActive() {
        WorldState s = state(20, 18, true, false, true, List.of(), List.of());
        GoalScore top = planner.evaluateHighestUtility(s, sensors(s), memory);
        // At night with no shelter, either SURVIVE (night danger) or BUILD_BASE should win
        assertTrue(
                top.goalType() == NeedType.SURVIVAL_DEFENSE ||
                top.goalType() == NeedType.BUILD_BASE,
                "Night + no shelter should trigger SURVIVE or BUILD_BASE, got: " + top.goalType());
    }

    // ── Plan building ─────────────────────────────────────────────────────────

    @Test
    void gearUpPlan_containsMineIron() {
        WorldState s = state(20, 18, false, true, false, List.of(), List.of());
        GoalScore goal = new GoalScore(NeedType.GEAR_UP, 60.0, "test");
        ActionPlan plan = planner.buildPlan(goal, s, sensors(s));
        assertTrue(plan.allActions().contains("mine_iron"),
                "GEAR_UP plan should contain mine_iron step");
    }

    @Test
    void gearUpPlanWithIronAlready_skipsMineIron() {
        // Agent already has iron ingots – should skip mine_iron and smelt_iron
        WorldState s = state(20, 18, false, true, false, List.of(),
                List.of(new InventoryItem("iron_ingot", 5)));
        GoalScore goal = new GoalScore(NeedType.GEAR_UP, 60.0, "test");
        ActionPlan plan = planner.buildPlan(goal, s, sensors(s));
        assertFalse(plan.allActions().contains("mine_iron"),
                "mine_iron should be skipped when agent already has iron ingots");
    }

    @Test
    void explorePlan_singleExploreAction() {
        WorldState s = state(20, 20, false, true, true, List.of(), List.of());
        GoalScore goal = new GoalScore(NeedType.EXPLORE, 20.0, "idle");
        ActionPlan plan = planner.buildPlan(goal, s, sensors(s));
        assertTrue(plan.hasNextAction());
        assertEquals("explore", plan.getNextAction());
    }

    @Test
    void actionPlan_iteratesCorrectly() {
        WorldState s = state(20, 18, false, true, false, List.of(), List.of());
        GoalScore goal = new GoalScore(NeedType.GEAR_UP, 60.0, "test");
        ActionPlan plan = planner.buildPlan(goal, s, sensors(s));
        assertTrue(plan.size() > 0);
        while (plan.hasNextAction()) {
            assertNotNull(plan.getNextAction());
        }
        assertFalse(plan.hasNextAction());
    }

    @Test
    void goalScoreIsActive_whenAboveThreshold() {
        GoalScore active = new GoalScore(NeedType.GEAR_UP, 50.0, "test");
        GoalScore idle   = new GoalScore(NeedType.EXPLORE, 0.5, "test");
        assertTrue(active.isActive());
        assertFalse(idle.isActive());
    }
}
