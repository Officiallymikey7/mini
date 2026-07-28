package io.github.officiallymikey7.mini.brain;

import io.github.officiallymikey7.mini.integration.BotAdapter;

import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Humanizer module that intercepts brain decisions and translates them into
 * believable, human-like motor controls.
 *
 * <h3>Humanization features</h3>
 * <ul>
 *   <li><b>Reaction delay</b> – a random 100–250 ms pause is enforced between
 *       sensing a threat/event and taking the first combat or emergency action.</li>
 *   <li><b>Combat strafing</b> – during combat actions the controller randomly
 *       alternates left/right strafe commands to mimic player movement patterns.</li>
 *   <li><b>Inventory micro-pauses</b> – crafting and inventory actions have an
 *       additional random 50–150 ms delay to simulate human decision time.</li>
 *   <li><b>Look smoothing</b> – camera rotation is handled at the Minecraft-entity
 *       level via {@code LookControl} (see {@code AiPlayerEntity}) rather than here,
 *       but this controller signals the target position so the entity can interpolate
 *       it with a Bezier-like curve over several ticks.</li>
 * </ul>
 */
public final class HumanInputController {

    private static final Logger LOG = Logger.getLogger(HumanInputController.class.getName());

    /** Minimum reaction delay in milliseconds. */
    private static final long MIN_REACTION_MS = 100;
    /** Maximum reaction delay in milliseconds. */
    private static final long MAX_REACTION_MS = 250;
    /** Extra inventory micro-pause range (ms). */
    private static final long MAX_INVENTORY_PAUSE_MS = 150;

    private static final Set<String> COMBAT_ACTIONS = Set.of(
            "attack_nearest_hostile", "craft_sword_or_flee", "flee_to_shelter",
            "craft_and_defend", "assist_neighbor");

    private static final Set<String> INVENTORY_ACTIONS = Set.of(
            "craft_tools", "craft_iron_tools", "craft_stone_tools", "craft_diamond_tools",
            "craft_planks", "craft_sticks", "craft_iron_pickaxe", "craft_shield",
            "smelt_iron", "place_chest", "place_crafting_table");

    private final BotAdapter adapter;
    private final Random      rng = new Random();

    private long lastActionTimeMs = 0;
    private long reactionDelayMs  = nextReactionDelay();

    /** Current combat strafe direction: -1 = left, 0 = none, +1 = right. */
    private int strafeDir = 0;
    /** Ticks until the next strafe direction change. */
    private int strafeCooldown = 0;

    public HumanInputController(BotAdapter adapter) {
        this.adapter = adapter;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempts to execute the given action through the adapter.
     *
     * <p>Execution is blocked when the reaction delay has not yet elapsed.
     * Humanizer state (strafe, misclick pauses) is updated on every call
     * regardless of whether execution proceeds.
     *
     * @param action the action ID to execute
     * @return {@code true} when the action was dispatched to the adapter;
     *         {@code false} when still in the reaction-delay window
     */
    public boolean execute(String action) {
        long now = System.currentTimeMillis();

        // Reaction delay gate
        if (now - lastActionTimeMs < reactionDelayMs) {
            LOG.finest("HumanInput: action '" + action + "' deferred (reaction delay)");
            return false;
        }

        // Update strafe state for combat realism
        updateStrafe(action);

        // Inventory micro-pause (applied as extended delay on the next tick)
        long extraPause = INVENTORY_ACTIONS.contains(action)
                ? (long) (rng.nextDouble() * MAX_INVENTORY_PAUSE_MS) : 0L;

        String humanizedAction = applyCombatStrafing(action);
        adapter.performAction(humanizedAction);
        LOG.fine("HumanInput: executed '" + humanizedAction + "' (strafe=" + strafeDir + ")");

        lastActionTimeMs = now;
        reactionDelayMs  = nextReactionDelay() + extraPause;
        return true;
    }

    /**
     * Returns the current strafe direction:
     * -1 = strafe-left, 0 = no strafe, +1 = strafe-right.
     */
    public int getStrafeDirection() {
        return strafeDir;
    }

    /** Resets the reaction-delay timer (call after a stall recovery or teleport). */
    public void resetDelay() {
        lastActionTimeMs = 0;
        reactionDelayMs  = nextReactionDelay();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long nextReactionDelay() {
        return MIN_REACTION_MS + (long) (rng.nextDouble() * (MAX_REACTION_MS - MIN_REACTION_MS));
    }

    private void updateStrafe(String action) {
        if (!COMBAT_ACTIONS.contains(action)) {
            strafeDir = 0;
            return;
        }
        if (--strafeCooldown <= 0) {
            // Randomly flip strafe direction to mimic player movement
            int[] dirs = {-1, 0, 1};
            strafeDir = dirs[rng.nextInt(dirs.length)];
            strafeCooldown = 3 + rng.nextInt(5); // hold for 3-7 ticks
        }
    }

    /**
     * For combat actions where the agent is strafing, appends the strafe
     * modifier to the action string so the Fabric layer can apply movement.
     */
    private String applyCombatStrafing(String action) {
        if (COMBAT_ACTIONS.contains(action) && strafeDir != 0) {
            return action + (strafeDir < 0 ? ":strafe_left" : ":strafe_right");
        }
        return action;
    }
}
