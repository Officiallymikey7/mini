package io.github.officiallymikey7.mini.ai;

import io.github.officiallymikey7.mini.ai.action.ActionResult;
import io.github.officiallymikey7.mini.ai.decision.DecisionPlan;
import io.github.officiallymikey7.mini.ai.memory.AgentMemory;
import io.github.officiallymikey7.mini.ai.perception.PerceptionSnapshot;
import io.github.officiallymikey7.mini.body.VillagerBody;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

/**
 * Tick orchestrator that drives the Perception → Decision → Action (PDA) loop
 * for one managed villager.
 *
 * <p><b>Phase 1</b> wraps the existing {@link VillagerBody} so that the PDA
 * architecture is in place without breaking current behaviour. Subsequent
 * phases will move logic out of {@code VillagerBody} and into dedicated
 * perception/decision modules wired through this runtime.
 *
 * <h2>Tick cadences</h2>
 * <ul>
 *   <li>Perception: every {@value #PERCEPTION_INTERVAL} ticks (~1 s)</li>
 *   <li>Decision:   every {@value #DECISION_INTERVAL} ticks (~2 s), or
 *                   immediately on interrupt (threat detected)</li>
 *   <li>Action:     every tick</li>
 * </ul>
 */
public final class VillagerAgentRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(VillagerAgentRuntime.class);

    /** How often (in ticks) to refresh the perception snapshot. */
    private static final int PERCEPTION_INTERVAL = 20;

    /** How often (in ticks) to run the decision step if no interrupt occurred. */
    private static final int DECISION_INTERVAL = 40;

    /** Radius (blocks) used when scanning for hostile entities during perception. */
    private static final double HOSTILE_SCAN_RADIUS = 12.0;

    private final VillagerBody body;
    private final AgentMemory memory = new AgentMemory();

    /** Last cached perception snapshot. */
    private PerceptionSnapshot lastSnapshot;

    /** Currently active plan from the decision step. */
    private DecisionPlan currentPlan;

    /** Number of ticks the current plan has been running. */
    private int planTicks;

    /** Number of consecutive failures for the current plan. */
    private int planFailures;

    /** Monotonically increasing tick counter for this runtime. */
    private long totalTicks;

    public VillagerAgentRuntime(VillagerBody body) {
        this.body = body;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Advance the PDA loop by one server tick.
     *
     * <p>Delegates action execution to the wrapped {@link VillagerBody} via
     * {@link VillagerBody#performAction}. The decision and perception phases
     * run at coarser intervals.
     *
     * @param player the owning player (used by VillagerBody for world access)
     */
    public void tick(ServerPlayerEntity player) {
        totalTicks++;

        // 1. Tick the underlying body (handles movement, danger detection, etc.)
        body.tick(player);

        // 2. Perception phase (every PERCEPTION_INTERVAL ticks)
        if (totalTicks % PERCEPTION_INTERVAL == 0) {
            VillagerEntity villager = body.getControlledVillager(player);
            if (villager != null) {
                lastSnapshot = buildSnapshot(player, villager);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Mini][AI] Perception: {}", lastSnapshot);
                }
            }
        }

        // 3. Decision phase (every DECISION_INTERVAL ticks, or on interrupt)
        boolean interruptRequired = lastSnapshot != null && lastSnapshot.hasHostileThreat()
                && (currentPlan == null || !currentPlan.actionName.equals("flee_to_shelter"));

        if (interruptRequired || totalTicks % DECISION_INTERVAL == 0 || currentPlan == null) {
            DecisionPlan newPlan = decide(lastSnapshot);
            if (newPlan != null && (currentPlan == null || !newPlan.actionName.equals(currentPlan.actionName))) {
                LOG.debug("[Mini][AI] Decision: switching to plan={}", newPlan);
                currentPlan  = newPlan;
                planTicks    = 0;
                planFailures = 0;
            }
        }

        // 4. Action phase – execute the active plan each tick
        if (currentPlan != null) {
            planTicks++;
            ActionResult result = executeActionTick(player, currentPlan);
            handleActionResult(result);
        }
    }

    /**
     * Directly assign an action plan (e.g. from the {@code /mini} command).
     */
    public void setPlan(DecisionPlan plan) {
        currentPlan  = plan;
        planTicks    = 0;
        planFailures = 0;
        LOG.debug("[Mini][AI] Plan assigned externally: {}", plan);
    }

    /** Returns the agent memory store for inspection. */
    public AgentMemory getMemory() {
        return memory;
    }

    /** Returns the last perception snapshot (may be {@code null} before first sense). */
    public PerceptionSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    /** Returns the currently active plan (may be {@code null}). */
    public DecisionPlan getCurrentPlan() {
        return currentPlan;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds a {@link PerceptionSnapshot} from the current world state.
     *
     * <p>Phase 1: delegates to VillagerBody's inventory snapshot for stock data
     * and reads world state for the remaining fields. A dedicated PerceptionModule
     * will be introduced in Phase 2.
     */
    private PerceptionSnapshot buildSnapshot(ServerPlayerEntity player, VillagerEntity villager) {
        // Pull stock data from body's inventory snapshot
        var inv = body.getInventorySnapshot();
        int foodStock = inv.stream()
                .filter(i -> i.name.contains("bread") || i.name.contains("food"))
                .mapToInt(i -> i.count)
                .sum();
        int woodStock = inv.stream()
                .filter(i -> i.name.contains("log") || i.name.contains("wood"))
                .mapToInt(i -> i.count)
                .sum();

        ServerWorld world = villager.getServerWorld();
        boolean night      = !world.isDay();
        boolean sheltered  = !world.isSkyVisible(villager.getBlockPos().up());

        // Scan for the nearest hostile within a 12-block radius
        Box dangerBox = villager.getBoundingBox().expand(HOSTILE_SCAN_RADIUS);
        LivingEntity nearestHostile = world.getEntitiesByClass(LivingEntity.class, dangerBox,
                        e -> e instanceof Monster && !e.isDead())
                .stream()
                .min(Comparator.comparingDouble(villager::distanceTo))
                .orElse(null);

        // In Phase 1 the snapshot uses empty lists for block positions because
        // block scanning is still owned by VillagerBody. Phase 2 will move that
        // scanning here and expose it through the decision engine.
        return new PerceptionSnapshot(
                nearestHostile,
                java.util.List.of(),
                java.util.List.of(),
                sheltered,
                villager.getHealth(),
                foodStock,
                woodStock,
                night);
    }

    /**
     * Minimal rule-based decision step.
     *
     * <p>Phase 1 only implements the safety-first priority: if a threat is
     * present, override with {@code flee_to_shelter}. Otherwise the existing
     * plan continues. A full utility-scoring decision engine will be added in
     * Phase 2.
     */
    private DecisionPlan decide(PerceptionSnapshot snapshot) {
        if (snapshot == null) return currentPlan;

        if (snapshot.hasHostileThreat()) {
            return DecisionPlan.of("flee_to_shelter", null, 200, 3);
        }

        // Keep current plan; VillagerBody handles lower-level transitions
        return currentPlan;
    }

    /** Delegates action execution to the wrapped VillagerBody. */
    private ActionResult executeActionTick(ServerPlayerEntity player, DecisionPlan plan) {
        // VillagerBody.tick() already ran above; performAction just updates the
        // activeAction field if it changed.  The actual per-tick work is done
        // inside tick().  Here we check plan timeout and return a result.
        body.performAction(plan.actionName, player);

        // Timeout check
        if (plan.timeoutTicks > 0 && planTicks >= plan.timeoutTicks) {
            LOG.warn("[Mini][AI] Plan '{}' timed out after {} ticks", plan.actionName, planTicks);
            return ActionResult.failed(ActionResult.REASON_TIMEOUT);
        }

        // In Phase 1 the body handles its own completion internally.
        // We treat every non-timed-out tick as in-progress.
        return ActionResult.inProgress();
    }

    /** Handles the result returned from the action phase. */
    private void handleActionResult(ActionResult result) {
        switch (result.status) {
            case SUCCESS -> {
                LOG.debug("[Mini][AI] Action '{}' succeeded after {} ticks",
                        currentPlan.actionName, planTicks);
                memory.recordAction(currentPlan.actionName, "SUCCESS", totalTicks);
                currentPlan  = null;
                planTicks    = 0;
                planFailures = 0;
            }
            case FAILED -> {
                planFailures++;
                String reason = result.failureReason != null ? result.failureReason : "UNKNOWN";
                LOG.warn("[Mini][AI] Action '{}' failed: reason={} failures={}/{}",
                        currentPlan.actionName, reason, planFailures, currentPlan.maxRetries);
                memory.recordAction(currentPlan.actionName,
                        "FAILED(" + reason + ")", totalTicks);

                if (planFailures >= currentPlan.maxRetries) {
                    LOG.warn("[Mini][AI] Max retries reached for '{}' – abandoning plan",
                            currentPlan.actionName);
                    memory.recordAction(currentPlan.actionName,
                            "ABANDONED(" + ActionResult.REASON_MAX_RETRY + ")", totalTicks);
                    currentPlan  = null;
                    planTicks    = 0;
                    planFailures = 0;
                }
            }
            case IN_PROGRESS -> {
                // Normal – log at trace level only
                if (LOG.isTraceEnabled()) {
                    LOG.trace("[Mini][AI] Action '{}' in progress – tick {}/{}",
                            currentPlan.actionName, planTicks,
                            currentPlan.timeoutTicks > 0 ? currentPlan.timeoutTicks : "∞");
                }
            }
        }
    }
}
