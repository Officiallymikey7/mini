package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.command.AgentCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric mod entry-point for <b>mini</b> – the role-driven autonomous
 * Minecraft agent framework.
 *
 * <p>On initialisation this class:
 * <ol>
 *   <li>Registers the {@code /mini} command tree.</li>
 *   <li>Hooks into the server tick event to advance active agent cycles.</li>
 * </ol>
 *
 * <p>To test in a dev world:
 * <pre>
 *   ./gradlew runClient          # or runServer
 *   # In-game:
 *   /mini start farmer           # start an agent with the "farmer" role
 *   /mini status                 # check the current tick count
 *   /mini stop                   # stop the agent
 *   /mini roles                  # list all available roles
 * </pre>
 */
public final class MiniMod implements ModInitializer {

    public static final String MOD_ID = "mini";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Mini] Initialising…");

        // Register /mini commands
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        AgentCommand.register(dispatcher));

        // Drive active agent ticks on every server tick
        ServerTickEvents.END_SERVER_TICK.register(server ->
                AgentCommand.tickActiveAgents());

        LOGGER.info("[Mini] Ready. Use /mini start [role] in-game.");
    }
}
