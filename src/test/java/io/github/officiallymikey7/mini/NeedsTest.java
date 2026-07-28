package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Needs#computeNeeds(WorldState)}.
 *
 * <p>Mirrors the TypeScript {@code needs.test.ts} suite.
 */
class NeedsTest {

    // ── Helper ───────────────────────────────────────────────────────────────

    private static WorldState state(float health, float hunger, boolean isNight, boolean hasShelter,
                                    boolean hasTools, List<HostileEntity> hostiles,
                                    List<InventoryItem> inv, int shelterDist) {
        return new WorldState("Tester", 6000, isNight, health, hunger,
                hostiles, inv, shelterDist, hasShelter, hasTools, List.of(),
                System.currentTimeMillis(),
                0.0, 64.0, 0.0, 15, "plains", "air", "air", List.of());
    }

    // ── Basic scoring ─────────────────────────────────────────────────────────

    @Test
    void safeMiddayGoodHealth_returnsResources() {
        WorldState s = state(20, 20, false, true, true, List.of(),
                List.of(new InventoryItem("oak_log", 2)), 0);
        List<NeedScore> scores = Needs.computeNeeds(s);
        // With high health/hunger/shelter the top need should be RESOURCES (low wood)
        assertFalse(scores.isEmpty());
        assertEquals(NeedType.RESOURCES, scores.get(0).need);
    }

    @Test
    void criticalHunger_foodScoreIs95() {
        WorldState s = state(20, 2, false, true, true, List.of(),
                List.of(), 0);
        NeedScore food = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.FOOD).findFirst().orElseThrow();
        assertEquals(95, food.score, 0.01);
    }

    @Test
    void starvation_foodScoreIs70() {
        WorldState s = state(20, 5, false, true, true, List.of(),
                List.of(), 0);
        NeedScore food = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.FOOD).findFirst().orElseThrow();
        assertEquals(70, food.score, 0.01);
    }

    @Test
    void hostileInRange5_defenseScore100() {
        WorldState s = state(20, 18, false, true, true,
                List.of(new HostileEntity("zombie", 3.0)), List.of(), 0);
        NeedScore def = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.SURVIVAL_DEFENSE).findFirst().orElseThrow();
        assertEquals(100, def.score, 0.01);
    }

    @Test
    void nightNoShelter_defenseScore60() {
        WorldState s = state(20, 18, true, false, true, List.of(), List.of(), 5);
        NeedScore def = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.SURVIVAL_DEFENSE).findFirst().orElseThrow();
        assertEquals(60, def.score, 0.01);
    }

    @Test
    void lowHealth_defenseAtLeast90() {
        WorldState s = state(5, 18, false, true, true, List.of(), List.of(), 0);
        NeedScore def = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.SURVIVAL_DEFENSE).findFirst().orElseThrow();
        assertTrue(def.score >= 90);
    }

    @Test
    void sortedDescending() {
        WorldState s = state(5, 2, true, false, false,
                List.of(new HostileEntity("zombie", 3.0)), List.of(), 10);
        List<NeedScore> scores = Needs.computeNeeds(s);
        for (int i = 0; i < scores.size() - 1; i++) {
            assertTrue(scores.get(i).score >= scores.get(i + 1).score,
                    "Scores not sorted at index " + i);
        }
    }

    @Test
    void noTools_toolScore45() {
        WorldState s = state(20, 18, false, true, false, List.of(), List.of(), 0);
        NeedScore tools = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.TOOLS).findFirst().orElseThrow();
        assertEquals(45, tools.score, 0.01);
    }

    @Test
    void hasTools_toolScore5() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of(), 0);
        NeedScore tools = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.TOOLS).findFirst().orElseThrow();
        assertEquals(5, tools.score, 0.01);
    }

    @Test
    void nightNeedsShelter_shelterHighScore() {
        WorldState s = state(20, 18, true, false, true, List.of(), List.of(), 0);
        NeedScore shelter = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.SHELTER).findFirst().orElseThrow();
        assertTrue(shelter.score >= 85, "Expected shelter score >= 85, got " + shelter.score);
    }

    @Test
    void allNeedsPresentInResult() {
        WorldState s = state(20, 18, false, true, true, List.of(), List.of(), 0);
        List<NeedScore> scores = Needs.computeNeeds(s);
        for (NeedType nt : NeedType.values()) {
            assertTrue(scores.stream().anyMatch(n -> n.need == nt),
                    "Missing need: " + nt);
        }
    }

    // ── New goal types ────────────────────────────────────────────────────────

    @Test
    void noTools_gearUpHigherThanResourcesScore() {
        WorldState s = state(20, 18, false, true, false, List.of(), List.of(), 0);
        List<NeedScore> scores = Needs.computeNeeds(s);
        NeedScore gearUp = scores.stream().filter(n -> n.need == NeedType.GEAR_UP).findFirst().orElseThrow();
        NeedScore resources = scores.stream().filter(n -> n.need == NeedType.RESOURCES).findFirst().orElseThrow();
        assertTrue(gearUp.score > resources.score,
                "GEAR_UP should outrank RESOURCES when agent has no tools");
    }

    @Test
    void safeStateWithTools_gearUpBelowResources() {
        // hasTools=true, inv=[oak_log:2] (wood=2 < 16 → RESOURCES=30)
        WorldState s = state(20, 20, false, true, true, List.of(),
                List.of(new InventoryItem("oak_log", 2)), 0);
        List<NeedScore> scores = Needs.computeNeeds(s);
        NeedScore gearUp = scores.stream().filter(n -> n.need == NeedType.GEAR_UP).findFirst().orElseThrow();
        NeedScore resources = scores.stream().filter(n -> n.need == NeedType.RESOURCES).findFirst().orElseThrow();
        assertTrue(gearUp.score < resources.score,
                "GEAR_UP with hasTools=true should be below RESOURCES=30");
    }

    @Test
    void nightNoShelter_buildBaseScore58() {
        WorldState s = state(20, 18, true, false, true, List.of(), List.of(), 0);
        NeedScore buildBase = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.BUILD_BASE).findFirst().orElseThrow();
        assertEquals(58.0, buildBase.score, 0.01);
    }

    @Test
    void safeState_exploreScore20() {
        // Fully safe: health=20, hunger=20, hasShelter=true, isNight=false, no hostiles
        WorldState s = state(20, 20, false, true, true, List.of(), List.of(), 0);
        NeedScore explore = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.EXPLORE).findFirst().orElseThrow();
        assertEquals(20.0, explore.score, 0.01);
    }

    @Test
    void dangerState_exploreScore5() {
        // Nighttime danger state: EXPLORE should drop to 5
        WorldState s = state(20, 18, true, false, true,
                List.of(new HostileEntity("zombie", 7.0)), List.of(), 0);
        NeedScore explore = Needs.computeNeeds(s).stream()
                .filter(n -> n.need == NeedType.EXPLORE).findFirst().orElseThrow();
        assertEquals(5.0, explore.score, 0.01);
    }
}
