package io.github.officiallymikey7.mini.client.renderer;

import io.github.officiallymikey7.mini.entity.AiPlayerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/**
 * Client-side renderer for {@link AiPlayerEntity}.
 *
 * <p>Binds the entity to Minecraft's built-in {@link BipedEntityModel} (the
 * standard Steve skeleton) so it renders with correct limb-swing, head
 * yaw/pitch, and arm-swing animations out of the box.
 *
 * <p>The renderer is registered in {@link io.github.officiallymikey7.mini.client.MiniModClient}
 * via {@code EntityRendererRegistry} so it is never classloaded on a dedicated server.
 *
 * <p><b>Texture:</b> {@code assets/mini/textures/entity/ai_player.png} —
 * replace this 64 × 64 PNG with a custom skin to change the entity's appearance.
 */
@Environment(EnvType.CLIENT)
public final class AiPlayerRenderer extends MobEntityRenderer<AiPlayerEntity, BipedEntityModel<AiPlayerEntity>> {

    /**
     * Texture path: {@code assets/mini/textures/entity/ai_player.png}.
     *
     * <p>The layout follows the standard 64 × 64 player skin format so any
     * vanilla skin editor can be used to create a custom texture.
     */
    private static final Identifier TEXTURE =
            Identifier.of("mini", "textures/entity/ai_player.png");

    /**
     * @param ctx Fabric render context — provides the model-part tree baked from
     *            {@link EntityModelLayers#PLAYER} and the entity texture atlas.
     */
    public AiPlayerRenderer(EntityRendererFactory.Context ctx) {
        super(
                ctx,
                // Reuse the vanilla player model layer (Steve geometry, 64×64 UV)
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)),
                // Shadow radius in blocks (matches default player shadow)
                0.5f);
    }

    /**
     * Returns the texture applied to every {@link AiPlayerEntity}.
     *
     * <p>Override per-entity (e.g. by agent name) if multiple agent skins are
     * needed in future.
     */
    @Override
    public Identifier getTexture(AiPlayerEntity entity) {
        return TEXTURE;
    }
}
