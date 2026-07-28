package io.github.officiallymikey7.mini.ai;

import io.github.officiallymikey7.mini.ai.action.ActionResult;
import io.github.officiallymikey7.mini.ai.decision.DecisionPlan;
import io.github.officiallymikey7.mini.ai.decision.UtilityDecisionEngine;
import io.github.officiallymikey7.mini.ai.memory.AgentMemory;
import io.github.officiallymikey7.mini.ai.perception.PerceptionSnapshot;
import io.github.officiallymikey7.mini.body.VillagerBody;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Tick orchestrator that drives the Perception → Decision → Action (PDA) loop
 * for one managed villager.
 *
 * <p><b>Phase 2</b> adds deterministic role assignment and a lightweight utility
 * scoring decision engine while preserving Phase 1 execution semantics in
 * {@link VillagerBody}.
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
    /** Radius (blocks) for simple resource scanning used by utility scoring. */
    private static final int RESOURCE_SCAN_RADIUS = 14;
    /** Maximum block targets sampled per resource type to keep scanning lightweight. */
    private static final int MAX_RESOURCE_TARGETS = 8;

    private final VillagerBody body;
    private final AgentMemory memory = new AgentMemory();
    private final UtilityDecisionEngine decisionEngine = new UtilityDecisionEngine();
    private final Map<UUID, VillagerRole> roleByVillager = new HashMap<>();

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
    /** Current villager role resolved from deterministic assignment map. */
    private VillagerRole role = VillagerRole.FARMER;
    private UUID currentVillagerUuid;

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
                updateRole(villager);
                lastSnapshot = buildSnapshot(player, villager);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Mini][AI] Perception: role={} {}", role, lastSnapshot);
                }
            }
        }

        // 3. Decision phase (every DECISION_INTERVAL ticks, or on interrupt)
        boolean interruptRequired = lastSnapshot != null && lastSnapshot.hasHostileThreat()
                && (currentPlan == null || !currentPlan.actionName.equals("flee_to_shelter"));

        if (interruptRequired || totalTicks % DECISION_INTERVAL == 0 || currentPlan == null) {
            DecisionPlan newPlan = decide(lastSnapshot, role);
            if (newPlan != null && (currentPlan == null || !newPlan.actionName.equals(currentPlan.actionName))) {
                LOG.debug("[Mini][AI] Decision: role={} switching to plan={}", role, newPlan);
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
        LOG.debug("[Mini][AI] Plan assigned externally: role={} {}", role, plan);
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
     * <p>Phase 2: still reads stock data from {@link VillagerBody} but now also
     * scans nearby food/wood blocks for utility distance scoring.
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
        ScanResult foodScan = scanNearbyBlocks(world, villager.getBlockPos(), VillagerAgentRuntime::isFoodBlock);
        ScanResult woodScan = scanNearbyBlocks(world, villager.getBlockPos(), VillagerAgentRuntime::isWoodBlock);

        // Scan for the nearest hostile within a 12-block radius
        Box dangerBox = villager.getBoundingBox().expand(HOSTILE_SCAN_RADIUS);
        LivingEntity nearestHostile = world.getEntitiesByClass(LivingEntity.class, dangerBox,
                        e -> e instanceof Monster && !e.isDead())
                .stream()
                .min(Comparator.comparingDouble(villager::distanceTo))
                .orElse(null);

        double nearestHostileDistance = nearestHostile != null ? villager.distanceTo(nearestHostile) : -1.0;
        double nearestFoodDistance = foodScan.nearestDistance;
        double nearestWoodDistance = woodScan.nearestDistance;

        return new PerceptionSnapshot(
                nearestHostile,
                foodScan.blocks,
                woodScan.blocks,
                sheltered,
                villager.getHealth(),
                foodStock,
                woodStock,
                night,
                nearestHostileDistance,
                nearestFoodDistance,
                nearestWoodDistance);
    }

    private DecisionPlan decide(PerceptionSnapshot snapshot, VillagerRole role) {
        if (snapshot == null) return currentPlan;

        UtilityDecisionEngine.DecisionResult result = decisionEngine.choosePlan(snapshot, role);
        DecisionPlan selected = result.selectedPlan();
        if (LOG.isDebugEnabled()) {
            LOG.debug("[Mini][AI] Decision score role={} top={} selected={}",
                    role, summarizeTopCandidates(result.rankedCandidates(), 3), selected.actionName);
        }

        // TODO(Phase 3): merge village blackboard requests/deliveries into utility factors.
        return selected;
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
                if (LOG.isTraceEnabled()) {
                    LOG.trace("[Mini][AI] Action '{}' in progress – tick {}/{}",
                            currentPlan.actionName, planTicks,
                            currentPlan.timeoutTicks > 0 ? currentPlan.timeoutTicks : "∞");
                }
            }
        }
    }

    private void updateRole(VillagerEntity villager) {
        UUID villagerUuid = villager.getUuid();
        roleByVillager.computeIfAbsent(villagerUuid, this::assignInitialRole);
        if (!villagerUuid.equals(currentVillagerUuid)) {
            currentVillagerUuid = villagerUuid;
            role = roleByVillager.get(villagerUuid);
            LOG.debug("[Mini][AI] Role assigned villager={} role={}", villagerUuid, role);
        }
    }

    private VillagerRole assignInitialRole(UUID villagerUuid) {
        int slot = Math.floorMod(villagerUuid.hashCode(), 3);
        return switch (slot) {
            case 0 -> VillagerRole.FARMER;
            case 1 -> VillagerRole.GUARD;
            default -> VillagerRole.BUILDER;
        };
    }

    private static ScanResult scanNearbyBlocks(
            ServerWorld world,
            BlockPos origin,
            Predicate<BlockState> predicate) {
        List<BlockPos> matches = new ArrayList<>();
        double nearestSq = -1.0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -RESOURCE_SCAN_RADIUS; dx <= RESOURCE_SCAN_RADIUS; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -RESOURCE_SCAN_RADIUS; dz <= RESOURCE_SCAN_RADIUS; dz++) {
                    BlockPos target = mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (predicate.test(world.getBlockState(target))) {
                        double sq = origin.getSquaredDistance(target);
                        if (nearestSq < 0 || sq < nearestSq) {
                            nearestSq = sq;
                        }
                        if (matches.size() < MAX_RESOURCE_TARGETS) {
                            matches.add(target.toImmutable());
                        }
                    }
                }
            }
        }
        double nearestDistance = nearestSq < 0 ? -1.0 : Math.sqrt(nearestSq);
        return new ScanResult(matches, nearestDistance);
    }

    private record ScanResult(List<BlockPos> blocks, double nearestDistance) {}

    private static boolean isFoodBlock(BlockState state) {
        return state.isOf(Blocks.WHEAT)
                || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES)
                || state.isOf(Blocks.BEETROOTS)
                || state.isOf(Blocks.MELON)
                || state.isOf(Blocks.PUMPKIN)
                || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean isWoodBlock(BlockState state) {
        return state.isIn(BlockTags.LOGS);
    }

    private static String summarizeTopCandidates(List<UtilityDecisionEngine.ScoredCandidate> ranked, int max) {
        int count = Math.min(max, ranked.size());
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(ranked.get(i).summary());
        }
        return out.toString();
    }
}
