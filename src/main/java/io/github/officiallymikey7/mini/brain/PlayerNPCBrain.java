package io.github.officiallymikey7.mini.brain;

import io.github.officiallymikey7.mini.core.Perception;
import io.github.officiallymikey7.mini.core.WorldState;
import io.github.officiallymikey7.mini.goap.ActionPlan;
import io.github.officiallymikey7.mini.goap.GOAPPlanner;
import io.github.officiallymikey7.mini.goap.GoalScore;
import io.github.officiallymikey7.mini.integration.BotAdapter;
import io.github.officiallymikey7.mini.sensor.SensorData;
import io.github.officiallymikey7.mini.sensor.SensorSuite;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player-like NPC brain – the top-level controller that ties together the
 * GOAP planner, sensor suite, memory, and humanizer into a single tick loop.
 *
 * <h3>Tick pipeline (mirrors the spec)</h3>
 * <ol>
 *   <li>Gather environmental and internal state via {@link Perception}.</li>
 *   <li>Update the {@link SensorSuite} to produce a fresh {@link SensorData} snapshot.</li>
 *   <li>Select the goal with the highest utility score via {@link GOAPPlanner#evaluateHighestUtility}.</li>
 *   <li>Resolve the goal into a concrete {@link ActionPlan} via {@link GOAPPlanner#buildPlan}.</li>
 *   <li>Pass the next action to the {@link HumanInputController}, which applies reaction
 *       delays and strafe humanisation before forwarding to the {@link BotAdapter}.</li>
 * </ol>
 *
 * <p>Unlike the original {@link io.github.officiallymikey7.mini.agent.Agent}, this
 * brain operates at a fixed-rate tick controlled by the caller (e.g. every 4 server
 * ticks / 200 ms) and does not drive governance or social memory directly.  Those
 * responsibilities remain in the original {@code Agent} which can be run in parallel.
 */
public final class PlayerNPCBrain {

    private static final Logger LOG = Logger.getLogger(PlayerNPCBrain.class.getName());

    private final MemoryModule        memory;
    private final SensorSuite         sensors;
    private final GOAPPlanner         planner;
    private final HumanInputController input;
    private final BotAdapter          adapter;

    /** Current active plan; replaced when the goal changes. */
    private ActionPlan currentPlan = ActionPlan.empty();
    /** The goal that produced {@link #currentPlan}. */
    private GoalScore  lastGoal    = null;

    private int tickCount = 0;

    /**
     * Creates a brain with a fresh memory and a new set of sensors/planner/humanizer.
     *
     * @param adapter the world adapter for perception and action dispatch
     */
    public PlayerNPCBrain(BotAdapter adapter) {
        this.adapter  = adapter;
        this.memory   = new MemoryModule();
        this.sensors  = new SensorSuite();
        this.planner  = new GOAPPlanner();
        this.input    = new HumanInputController(adapter);
    }

    /**
     * Executes one brain tick.
     *
     * <p>Safe to call from the Minecraft server tick thread.
     */
    public void tick() {
        tickCount++;

        // ── Step 1: Perceive ─────────────────────────────────────────────────
        WorldState state = Perception.perceive(adapter);

        // ── Step 2: Update sensors ────────────────────────────────────────────
        SensorData sensorData = sensors.update(state, memory);

        // ── Step 3: Evaluate highest-utility goal ─────────────────────────────
        GoalScore goal = planner.evaluateHighestUtility(state, sensorData, memory);

        // ── Step 4: Rebuild plan when goal changes ────────────────────────────
        if (lastGoal == null || !lastGoal.goalType().equals(goal.goalType())
                || !currentPlan.hasNextAction()) {
            currentPlan = planner.buildPlan(goal, state, sensorData);
            lastGoal = goal;
            LOG.log(Level.INFO,
                    "[Tick {0}] New goal: {1} (score={2}) → plan: {3}",
                    new Object[]{tickCount, goal.goalType(), goal.score(),
                            currentPlan.allActions()});

            // If the base-building goal was selected and agent is in shelter,
            // record this position as the home location
            if (goal.goalType() == io.github.officiallymikey7.mini.core.NeedType.BUILD_BASE
                    && state.hasShelter && !memory.hasHomeLocation()) {
                memory.setHomeLocation(state.x, state.y, state.z);
                LOG.log(Level.INFO, "[Tick {0}] Home location registered at ({1}, {2}, {3})",
                        new Object[]{tickCount, state.x, state.y, state.z});
            }
        }

        // ── Step 5: Execute next action through the humanizer ─────────────────
        if (currentPlan.hasNextAction()) {
            String nextAction = currentPlan.getNextAction();
            boolean dispatched = input.execute(nextAction);
            if (dispatched) {
                LOG.log(Level.FINE, "[Tick {0}] Dispatched: {1}", new Object[]{tickCount, nextAction});
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the total number of ticks processed by this brain. */
    public int getTickCount() { return tickCount; }

    /** Returns the agent's persistent memory module. */
    public MemoryModule getMemory() { return memory; }

    /** Returns the current active plan (may be exhausted). */
    public ActionPlan getCurrentPlan() { return currentPlan; }

    /** Returns the last goal that was selected, or {@code null} before the first tick. */
    public GoalScore getLastGoal() { return lastGoal; }
}
