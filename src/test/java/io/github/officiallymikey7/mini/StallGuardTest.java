package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.safety.StallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StallGuard}.
 *
 * <p>Mirrors the TypeScript {@code stallGuard.test.ts} suite.
 */
class StallGuardTest {

    private StallGuard guard;

    @BeforeEach
    void setUp() {
        // maxAttempts=3, timeoutMs=60_000
        guard = new StallGuard(3, 60_000);
    }

    // ── Not stalled initially ─────────────────────────────────────────────────

    @Test
    void notStalledWithNoAttempts() {
        assertFalse(guard.isStalled("some_goal"));
    }

    @Test
    void notStalledAfterOneAttempt() {
        guard.recordAttempt("goal_a");
        assertFalse(guard.isStalled("goal_a"));
    }

    @Test
    void notStalledAfterTwoAttempts() {
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        assertFalse(guard.isStalled("goal_a"));
    }

    // ── Stalled after maxAttempts ─────────────────────────────────────────────

    @Test
    void stalledAfterThreeAttempts() {
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        assertTrue(guard.isStalled("goal_a"));
    }

    // ── Success clears stall ──────────────────────────────────────────────────

    @Test
    void successClearsStall() {
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        assertTrue(guard.isStalled("goal_a"));
        guard.recordSuccess("goal_a");
        assertFalse(guard.isStalled("goal_a"));
    }

    // ── Reset clears all records ──────────────────────────────────────────────

    @Test
    void resetClearsAllRecords() {
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_b");
        guard.reset();
        assertTrue(guard.getRecords().isEmpty());
        assertFalse(guard.isStalled("goal_a"));
    }

    // ── Fallback actions ──────────────────────────────────────────────────────

    @Test
    void fallbackForCraftTools() {
        assertEquals("gather_wood", guard.getFallbackAction("craft_tools"));
    }

    @Test
    void fallbackForBuildShelter() {
        assertEquals("gather_wood", guard.getFallbackAction("build_shelter"));
    }

    @Test
    void fallbackForEatFood() {
        assertEquals("forage_food", guard.getFallbackAction("eat_food"));
    }

    @Test
    void fallbackForUnknownGoal() {
        assertEquals("explore", guard.getFallbackAction("some_unknown_goal"));
    }

    @Test
    void fallbackForCompoundId_roleFarmerFood() {
        // "role_farmer_food" – base segment is "food", no mapping → explore
        assertEquals("explore", guard.getFallbackAction("role_farmer_food"));
    }

    // ── Multiple goals independently tracked ─────────────────────────────────

    @Test
    void independentGoals() {
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a");
        guard.recordAttempt("goal_a"); // stalled

        guard.recordAttempt("goal_b");

        assertTrue(guard.isStalled("goal_a"));
        assertFalse(guard.isStalled("goal_b"));
    }

    // ── Timeout-based stall ───────────────────────────────────────────────────

    @Test
    void timeoutStall() throws InterruptedException {
        StallGuard fastGuard = new StallGuard(100, 50); // 50ms timeout, 100 max attempts
        fastGuard.recordAttempt("goal_slow");
        assertFalse(fastGuard.isStalled("goal_slow"));
        Thread.sleep(150); // exceed 50ms timeout with ample margin for scheduler jitter
        assertTrue(fastGuard.isStalled("goal_slow"));
    }
}
