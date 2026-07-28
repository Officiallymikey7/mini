package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.governance.Amendment;
import io.github.officiallymikey7.mini.governance.AmendmentStatus;
import io.github.officiallymikey7.mini.governance.Constitution;
import io.github.officiallymikey7.mini.governance.InMemoryConstitutionStorage;
import io.github.officiallymikey7.mini.governance.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Constitution}.
 *
 * <p>Mirrors the TypeScript {@code governance.test.ts} suite.
 */
class GovernanceTest {

    private Constitution constitution;

    @BeforeEach
    void setUp() {
        constitution = new Constitution(new InMemoryConstitutionStorage(), 0.5);
    }

    // ── Propose & retrieve ────────────────────────────────────────────────────

    @Test
    void proposeAmendmentIsStoredAsOpen() {
        Amendment a = constitution.proposeAmendment("agentA", "No griefing allowed.");
        assertEquals(AmendmentStatus.OPEN, a.status);
        assertNotNull(a.id);
        assertEquals("agentA", a.proposedBy);
    }

    @Test
    void initiallyNoRules() {
        assertTrue(constitution.getRules().isEmpty());
    }

    // ── Voting ────────────────────────────────────────────────────────────────

    @Test
    void votingRecordsDecision() {
        Amendment a = constitution.proposeAmendment("agentA", "Be kind.");
        constitution.vote(a.id, "agentA", "yes");
        constitution.vote(a.id, "agentB", "yes");
        assertEquals("yes", a.votes.get("agentA"));
        assertEquals("yes", a.votes.get("agentB"));
    }

    @Test
    void votingOnClosedAmendmentThrows() {
        Amendment a = constitution.proposeAmendment("agentA", "Test rule.");
        constitution.tally(a.id, 1); // will be rejected (0/1 yes)
        assertThrows(IllegalStateException.class,
                () -> constitution.vote(a.id, "agentB", "yes"));
    }

    // ── Tally: acceptance ────────────────────────────────────────────────────

    @Test
    void majorityAcceptsAmendment() {
        Amendment a = constitution.proposeAmendment("agentA", "Agents must share food.");
        constitution.vote(a.id, "agentA", "yes");
        constitution.vote(a.id, "agentB", "yes");
        constitution.vote(a.id, "agentC", "no");
        Amendment result = constitution.tally(a.id, 3);
        assertEquals(AmendmentStatus.ACCEPTED, result.status);
    }

    @Test
    void acceptedAmendmentAddsRule() {
        Amendment a = constitution.proposeAmendment("agentA", "Protect farmland.");
        constitution.vote(a.id, "agentA", "yes");
        constitution.vote(a.id, "agentB", "yes");
        constitution.tally(a.id, 2);
        List<Rule> rules = constitution.getRules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> r.text.equals("Protect farmland.")));
    }

    // ── Tally: rejection ──────────────────────────────────────────────────────

    @Test
    void minorityRejectsAmendment() {
        Amendment a = constitution.proposeAmendment("agentA", "Build a wall.");
        constitution.vote(a.id, "agentA", "yes");
        constitution.vote(a.id, "agentB", "no");
        constitution.vote(a.id, "agentC", "no");
        Amendment result = constitution.tally(a.id, 3);
        assertEquals(AmendmentStatus.REJECTED, result.status);
    }

    @Test
    void rejectedAmendmentAddsNoRule() {
        Amendment a = constitution.proposeAmendment("agentA", "Mine all emeralds.");
        constitution.vote(a.id, "agentA", "no");
        constitution.tally(a.id, 1);
        assertTrue(constitution.getRules().isEmpty());
    }

    // ── Violation checking ───────────────────────────────────────────────────

    @Test
    void noViolationsWhenNoRules() {
        List<Rule> v = constitution.checkViolations("someone stole resources");
        assertTrue(v.isEmpty());
    }

    @Test
    void detectsKeywordViolation() {
        // Add a rule manually via accepted amendment
        Amendment a = constitution.proposeAmendment("system",
                "Agents must not steal resources from other agents without consent.");
        constitution.vote(a.id, "system", "yes");
        constitution.tally(a.id, 1);

        List<Rule> v = constitution.checkViolations("I saw someone steal diamonds");
        assertFalse(v.isEmpty(), "Expected violation for 'steal'");
    }

    @Test
    void noViolationForUnrelatedText() {
        Amendment a = constitution.proposeAmendment("system",
                "Agents must not steal resources from other agents without consent.");
        constitution.vote(a.id, "system", "yes");
        constitution.tally(a.id, 1);

        List<Rule> v = constitution.checkViolations("I planted some wheat today");
        assertTrue(v.isEmpty());
    }

    // ── Replace existing rule ─────────────────────────────────────────────────

    @Test
    void amendmentReplacesExistingRule() {
        Amendment a1 = constitution.proposeAmendment("agentA", "Original rule.");
        constitution.vote(a1.id, "agentA", "yes");
        constitution.tally(a1.id, 1);
        List<Rule> rules = constitution.getRules();
        assertEquals(1, rules.size());

        String ruleId = rules.get(0).id;
        Amendment a2 = constitution.proposeAmendment("agentA", "Revised rule.", ruleId);
        constitution.vote(a2.id, "agentA", "yes");
        constitution.tally(a2.id, 1);

        List<Rule> updatedRules = constitution.getRules();
        assertEquals(1, updatedRules.size());
        assertEquals("Revised rule.", updatedRules.get(0).text);
    }

    // ── Tally idempotency ─────────────────────────────────────────────────────

    @Test
    void tallyOnAlreadyClosedIsNoop() {
        Amendment a = constitution.proposeAmendment("agentA", "Test.");
        constitution.vote(a.id, "agentA", "yes");
        constitution.tally(a.id, 1);
        // Calling tally again should not throw
        assertDoesNotThrow(() -> constitution.tally(a.id, 1));
    }
}
