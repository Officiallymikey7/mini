package io.github.officiallymikey7.mini.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.officiallymikey7.mini.agent.Agent;
import io.github.officiallymikey7.mini.agent.AgentConfig;
import io.github.officiallymikey7.mini.body.VillagerBody;
import io.github.officiallymikey7.mini.integration.FabricWorldAdapter;
import io.github.officiallymikey7.mini.roles.RoleRegistry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the {@code /mini} command tree for use in a Fabric dev world.
 *
 * <ul>
 *   <li>{@code /mini start [role]} – start the agent for the executing player</li>
 *   <li>{@code /mini stop}         – stop the agent</li>
 *   <li>{@code /mini status}       – show the current tick count and latest subgoal</li>
 *   <li>{@code /mini roles}        – list available roles</li>
 * </ul>
 *
 * <p>The agent tick is driven externally by {@link io.github.officiallymikey7.mini.MiniMod}
 * via a {@code ServerTickEvents} listener, so starting the agent only stores the
 * instance here; the mod calls {@link #tickActiveAgents()} each game tick.
 */
public final class AgentCommand {

    /** Ticks between agent cycles (20 game ticks = 1 second at 20 TPS). */
    private static final int AGENT_TICK_INTERVAL = 20;

    /** Active agents keyed by controlled villager UUID string. */
    private static final Map<String, ActiveEntry> ACTIVE_AGENTS = new HashMap<>();
    /** Owner player UUID -> controlled villager UUID mapping for command routing. */
    private static final Map<String, String> OWNER_TO_VILLAGER = new HashMap<>();

    private AgentCommand() {}

    /** Register all /mini sub-commands with Brigadier. */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("mini")
                        // /mini start [role]
                        .then(CommandManager.literal("start")
                                .then(CommandManager.argument("role", StringArgumentType.string())
                                        .executes(ctx -> startAgent(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "role"))))
                                .executes(ctx -> startAgent(ctx.getSource(), "farmer")))
                        // /mini stop
                        .then(CommandManager.literal("stop")
                                .executes(ctx -> stopAgent(ctx.getSource())))
                        // /mini status
                        .then(CommandManager.literal("status")
                                .executes(ctx -> showStatus(ctx.getSource())))
                        // /mini roles
                        .then(CommandManager.literal("roles")
                                .executes(ctx -> listRoles(ctx.getSource())))
        );
    }

    // ── Tick loop integration ────────────────────────────────────────────────

    /**
     * Called every server tick by {@link io.github.officiallymikey7.mini.MiniMod}.
     * <ul>
     *   <li>Drives the Villager body movement on <em>every</em> tick for smooth following.</li>
     *   <li>Executes one agent logic tick every {@value #AGENT_TICK_INTERVAL} game ticks.</li>
     * </ul>
     */
    public static void tickActiveAgents() {
        for (Map.Entry<String, ActiveEntry> entry : ACTIVE_AGENTS.entrySet()) {
            ActiveEntry e = entry.getValue();

            // Body movement runs every server tick for smooth in-world following
            try {
                e.body.tick(e.player);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            // Agent logic runs at the slower AGENT_TICK_INTERVAL cadence
            e.gameTickCounter++;
            if (e.gameTickCounter >= AGENT_TICK_INTERVAL) {
                e.gameTickCounter = 0;
                try {
                    e.agent.tick();
                } catch (Exception ex) {
                    // Log but do not crash the server
                    ex.printStackTrace();
                }
            }
        }
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private static int startAgent(ServerCommandSource source, String roleId) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.of("§cThis command must be run by a player."), false);
            return 0;
        }

        String ownerUuid = player.getUuid().toString();
        if (OWNER_TO_VILLAGER.containsKey(ownerUuid)) {
            source.sendFeedback(() -> Text.of("§eAgent already running. Use /mini stop first."), false);
            return 0;
        }

        if (RoleRegistry.getRole(roleId).isEmpty()) {
            source.sendFeedback(() -> Text.of("§cUnknown role: " + roleId + ". Try /mini roles."), false);
            return 0;
        }

        VillagerBody body = new VillagerBody(ownerUuid);
        body.ensureSpawned(player);
        if (body.getVillagerUuid() == null) {
            source.sendFeedback(() -> Text.of("§cFailed to acquire or spawn a villager body."), false);
            return 0;
        }

        String villagerUuid = body.getVillagerUuid().toString();
        if (ACTIVE_AGENTS.containsKey(villagerUuid)) {
            source.sendFeedback(() -> Text.of("§cThe nearest villager is already controlled by another agent."), false);
            return 0;
        }

        FabricWorldAdapter adapter = new FabricWorldAdapter(player, body);
        AgentConfig config = AgentConfig.builder(player.getName().getString(), roleId).build();
        Agent agent = new Agent(adapter, config);

        ACTIVE_AGENTS.put(villagerUuid, new ActiveEntry(agent, adapter, player, body));
        OWNER_TO_VILLAGER.put(ownerUuid, villagerUuid);
        source.sendFeedback(() ->
                Text.of("§a[Mini] Agent started as role: " + roleId + ". Use /mini stop to stop."), false);
        return 1;
    }

    private static int stopAgent(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        String ownerUuid = player.getUuid().toString();
        String villagerUuid = OWNER_TO_VILLAGER.remove(ownerUuid);
        ActiveEntry removed = villagerUuid == null ? null : ACTIVE_AGENTS.remove(villagerUuid);
        if (removed != null) {
            removed.body.despawn(removed.player);
            source.sendFeedback(() -> Text.of("§a[Mini] Agent stopped."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.of("§eNo agent is running for you."), false);
        return 0;
    }

    private static int showStatus(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        String ownerUuid = player.getUuid().toString();
        String villagerUuid = OWNER_TO_VILLAGER.get(ownerUuid);
        ActiveEntry entry = villagerUuid == null ? null : ACTIVE_AGENTS.get(villagerUuid);
        if (entry == null) {
            source.sendFeedback(() -> Text.of("§eNo agent running. Use /mini start [role]."), false);
            return 0;
        }

        Agent agent = entry.agent;
        String stateDesc = agent.getLatestState() == null
                ? "no state yet"
                : "tick=" + agent.getTickCount()
                  + " health=" + agent.getLatestState().health
                  + " hunger=" + (int) agent.getLatestState().hunger;
        source.sendFeedback(() -> Text.of("§b[Mini] Agent running – " + stateDesc), false);
        return 1;
    }

    private static int listRoles(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder("§b[Mini] Available roles: ");
        RoleRegistry.listRoles().forEach(r -> sb.append(r.getId()).append(" (").append(r.getLabel()).append("), "));
        if (sb.charAt(sb.length() - 2) == ',') sb.setLength(sb.length() - 2);
        source.sendFeedback(() -> Text.of(sb.toString()), false);
        return 1;
    }

    // ── Internal helper ──────────────────────────────────────────────────────

    private static final class ActiveEntry {
        final Agent              agent;
        final FabricWorldAdapter adapter;
        final ServerPlayerEntity player;
        final VillagerBody       body;
        int gameTickCounter = 0;

        ActiveEntry(Agent agent, FabricWorldAdapter adapter, ServerPlayerEntity player, VillagerBody body) {
            this.agent   = agent;
            this.adapter = adapter;
            this.player  = player;
            this.body    = body;
        }
    }
}
