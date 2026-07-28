package io.github.officiallymikey7.mini.entity;

import io.github.officiallymikey7.mini.MiniMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Central registry for all entity types introduced by this mod.
 *
 * <p>Call {@link #register()} from {@link io.github.officiallymikey7.mini.MiniMod#onInitialize()}
 * to add all types to the game's entity registry and bind their default attributes.
 *
 * <p>Entity types are declared as {@code public static final} fields here so any
 * code that needs to reference the type (entity spawning, renderer registration,
 * {@code /summon} auto-complete, etc.) imports a single authoritative constant.
 */
public final class EntityTypes {

    private EntityTypes() {}

    /**
     * The physical body entity for the mini AI agent.
     *
     * <p>Dimensions match a standard Minecraft player (0.6 × 1.8 blocks).
     * Spawn group {@link SpawnGroup#MISC} means it is never spawned naturally
     * and does not count toward mob caps.
     */
    public static final EntityType<AiPlayerEntity> AI_PLAYER =
            EntityType.Builder.create(AiPlayerEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)    // same footprint as a player
                    .build();

    /**
     * Registers {@link #AI_PLAYER} with the game and binds its default
     * attribute map.  Must be called on both the logical server and client
     * (i.e. from a common {@link net.fabricmc.api.ModInitializer}).
     */
    public static void register() {
        Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(MiniMod.MOD_ID, "ai_player"),
                AI_PLAYER);

        // Bind the entity's base attribute values (health, speed, follow-range)
        FabricDefaultAttributeRegistry.register(AI_PLAYER, AiPlayerEntity.createAttributes());

        MiniMod.LOGGER.info("[Mini] Registered entity type: {}", Identifier.of(MiniMod.MOD_ID, "ai_player"));
    }
}
