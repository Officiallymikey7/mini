package io.github.officiallymikey7.mini.client;

import io.github.officiallymikey7.mini.MiniMod;
import io.github.officiallymikey7.mini.client.renderer.AiPlayerRenderer;
import io.github.officiallymikey7.mini.entity.EntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client-only entry-point for the <b>mini</b> mod.
 *
 * <p>Registered in {@code fabric.mod.json} under the {@code client} entrypoints
 * key.  Everything here executes only on a Minecraft client (singleplayer or
 * integrated server); it is never loaded on a dedicated server.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Binds {@link io.github.officiallymikey7.mini.entity.AiPlayerEntity} to its
 *       {@link AiPlayerRenderer} so the entity renders in-world.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class MiniModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MiniMod.LOGGER.info("[Mini] Initialising client…");

        // Register the renderer that draws AiPlayerEntity using BipedEntityModel
        EntityRendererRegistry.register(EntityTypes.AI_PLAYER, AiPlayerRenderer::new);

        MiniMod.LOGGER.info("[Mini] Client ready – AiPlayerRenderer registered.");
    }
}
