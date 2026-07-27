package io.github.officiallymikey7.mini.agent;

import io.github.officiallymikey7.mini.core.*;
import io.github.officiallymikey7.mini.governance.Amendment;
import io.github.officiallymikey7.mini.governance.Constitution;
import io.github.officiallymikey7.mini.governance.Rule;
import io.github.officiallymikey7.mini.integration.BotAdapter;
import io.github.officiallymikey7.mini.memory.ReflectionMemory;
import io.github.officiallymikey7.mini.memory.SocialMemory;
import io.github.officiallymikey7.mini.roles.RoleDefinition;
import io.github.officiallymikey7.mini.roles.RoleRegistry;
import io.github.officiallymikey7.mini.safety.StallGuard;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main agent class.
 *
 * <p>Each call to {@link #tick()} runs the full perception → plan → execute
 * pipeline. In a Fabric dev world this is typically driven by
 * {@code ServerTickEvents} every N game ticks; in tests it can be driven
 * directly.
 *
 * <p>Pipeline (mirrors the TypeScript implementation):
 * <ol>
 *   <li>Perceive world state</li>
 *   <li>Ingest social messages</li>
 *   <li>Compute needs</li>
 *   <li>Resolve role</li>
 *   <li>Build memory blocks (reflection + social)</li>
 *   <li>Plan subgoal</li>
 *   <li>Stall-guard check / override</li>
 *   <li>Execute subgoal action</li>
 *   <li>Update stall guard on success</li>
 *   <li>Record memory</li>
 *   <li>Periodic governance check</li>
 * </ol>
 */
public final class Agent {

    private static final Logger LOG = Logger.getLogger(Agent.class.getName());

    private final BotAdapter adapter;
    private final AgentConfig config;

    private final StallGuard       stallGuard  = new StallGuard();
    private final ReflectionMemory reflection  = new ReflectionMemory();
    private final SocialMemory     social      = new SocialMemory();
    private final Constitution     constitution = new Constitution();

    private int        tickCount   = 0;
    private WorldState latestState = null;

    public Agent(BotAdapter adapter, AgentConfig config) {
        this.adapter = adapter;
        this.config  = config;

        // Seed with a starter governance rule
        Amendment a = constitution.proposeAmendment(
                "system",
                "Agents must not steal resources from other agents without consent.");
        constitution.tally(a.id, 1);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Execute one full agent tick synchronously. */
    public void tick() {
        tickCount++;
        int tick = tickCount;

        // ── Step 1: Perceive ─────────────────────────────────────────────────
        WorldState state = Perception.perceive(adapter);
        latestState = state;

        // ── Step 2: Ingest social messages ────────────────────────────────────
        social.ingest(state.nearbyChat, "nearby");

        // ── Step 3: Compute needs ─────────────────────────────────────────────
        List<NeedScore> topNeeds = Needs.computeNeeds(state);

        // ── Step 4: Resolve role ──────────────────────────────────────────────
        RoleDefinition role = config.customRole != null
                ? config.customRole
                : RoleRegistry.getRole(config.roleId)
                        .orElseGet(() -> RoleRegistry.getRole("farmer").orElseThrow());

        // ── Step 5: Build memory blocks ───────────────────────────────────────
        var reflectionBlock = reflection.build();
        var socialBlock     = social.build();

        // ── Step 6: Plan ──────────────────────────────────────────────────────
        PlannerOutput planResult = Planner.plan(new PlannerInput(
                state, topNeeds, role, reflectionBlock, socialBlock, role.getCommunityGoal()));
        Subgoal subgoal = planResult.subgoal;

        // ── Step 7: Stall guard ───────────────────────────────────────────────
        if (stallGuard.isStalled(subgoal.id)) {
            String fallback = stallGuard.getFallbackAction(subgoal.id);
            log(Level.WARNING, "Stall detected on \"" + subgoal.id + "\" → falling back to: " + fallback);
            stallGuard.reset();
            subgoal = new Subgoal(
                    "fallback_" + fallback,
                    "Fallback: " + fallback,
                    fallback,
                    SubgoalTag.SURVIVAL,
                    subgoal.priority);
        }

        stallGuard.recordAttempt(subgoal.id);
        log(Level.INFO, "[Tick " + tick + "] Subgoal: " + subgoal.description
                + " → action: " + subgoal.action);

        // ── Step 8: Execute ───────────────────────────────────────────────────
        ExecutionOutcome outcome = Executor.execute(adapter, subgoal);
        log(Level.INFO, outcome.status.name() + ": " + outcome.message);

        // ── Step 9: Update stall guard ────────────────────────────────────────
        if (outcome.status == ExecutionStatus.SUCCESS) {
            stallGuard.recordSuccess(subgoal.id);
        }

        // ── Step 10: Record memory ────────────────────────────────────────────
        WorldState endState = Perception.perceive(adapter);
        latestState = endState;
        reflection.record(tick, outcome, endState.inventory);

        // ── Step 11: Governance check (periodic) ──────────────────────────────
        if (tick % config.governanceTickInterval == 0) {
            runGovernanceCheck(state);
        }
    }

    /** Returns the most recently perceived world state, or {@code null} before the first tick. */
    public WorldState getLatestState() {
        return latestState;
    }

    /** Returns the total number of ticks executed so far. */
    public int getTickCount() {
        return tickCount;
    }

    // ── Governance ────────────────────────────────────────────────────────────

    private void runGovernanceCheck(WorldState state) {
        for (String msg : state.nearbyChat) {
            List<Rule> violations = constitution.checkViolations(msg);
            if (!violations.isEmpty()) {
                violations.forEach(r ->
                        log(Level.WARNING, "Possible rule violation in \"" + msg + "\" → rule: \"" + r.text + "\""));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void log(Level level, String message) {
        LOG.log(level, "[" + config.name + "] " + message);
    }
}
