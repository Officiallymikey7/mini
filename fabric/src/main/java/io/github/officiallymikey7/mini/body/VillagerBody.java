package io.github.officiallymikey7.mini.body;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Manages the physical in-world Villager body for a single agent.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Call {@link #ensureSpawned(ServerPlayerEntity)} once to create (or reattach to) the body.</li>
 *   <li>Call {@link #tick(ServerPlayerEntity)} every server tick to drive movement.</li>
 *   <li>Call {@link #despawn(ServerPlayerEntity)} when the agent stops to remove the body.</li>
 * </ol>
 *
 * <p>The body automatically respawns if the Villager dies or cannot be found, and
 * performs a stuck-recovery teleport when it has not moved for {@value #STUCK_THRESHOLD} ticks
 * while still needing to follow the player.
 */
public final class VillagerBody {

    private static final Logger LOG = LoggerFactory.getLogger(VillagerBody.class);

    /** Name shown above the Villager. */
    private static final String BODY_NAME = "MiniBot";

    /** Blocks from player before the body starts moving closer. */
    private static final double FOLLOW_DISTANCE = 3.0;

    /** Navigation speed (1.0 = default walk speed). */
    private static final double MOVE_SPEED = 0.6;

    /**
     * Consecutive ticks where the body has not moved while needing to follow,
     * after which a recovery teleport is triggered (~3 s at 20 TPS).
     */
    private static final int STUCK_THRESHOLD = 60;

    // ── State ────────────────────────────────────────────────────────────────

    private final String ownerUuid;

    private UUID   villagerUuid;
    /** Registry-key string of the dimension where the Villager was spawned. */
    private String villagerWorldKey;
    private Vec3d  lastPos;
    private int    stuckCounter;

    public VillagerBody(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Ensures that a live Villager body exists near {@code player}.
     * Spawns a fresh one if the current binding is absent, dead, or in the wrong world.
     * Safe to call every tick (no-ops when the body is already live).
     */
    public void ensureSpawned(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        String      worldKey = world.getRegistryKey().getValue().toString();

        if (villagerUuid != null && worldKey.equals(villagerWorldKey)) {
            var existing = world.getEntity(villagerUuid);
            if (existing instanceof VillagerEntity v && v.isAlive()) {
                return; // already live in the right world
            }
            LOG.info("[Mini] VillagerBody {} missing/dead; respawning for agent {}", villagerUuid, ownerUuid);
        }

        spawnNew(player, world, worldKey);
    }

    /**
     * Removes the Villager from the world and clears the binding.
     * Safe to call even if the body was already removed.
     */
    public void despawn(ServerPlayerEntity player) {
        if (villagerUuid == null) return;

        ServerWorld world = player.getServerWorld();
        var entity = world.getEntity(villagerUuid);
        if (entity != null) {
            entity.discard();
            LOG.info("[Mini] Despawned VillagerBody {} for agent {}", villagerUuid, ownerUuid);
        }
        villagerUuid     = null;
        villagerWorldKey = null;
        lastPos          = null;
        stuckCounter     = 0;
    }

    // ── Per-tick movement ─────────────────────────────────────────────────────

    /**
     * Drives the body's movement and look direction each server tick.
     * Automatically recovers from a missing or dead Villager.
     *
     * @param player the player this body should follow/face
     */
    public void tick(ServerPlayerEntity player) {
        ensureSpawned(player);

        VillagerEntity villager = resolveVillager(player.getServerWorld());
        if (villager == null) return;

        double dist = villager.distanceTo(player);
        EntityNavigation nav = villager.getNavigation();

        if (dist > FOLLOW_DISTANCE) {
            nav.startMovingTo(player, MOVE_SPEED);
        } else {
            nav.stop();
        }

        // Always keep looking toward the player
        villager.getLookControl().lookAt(player, 30f, 30f);

        // Stuck detection: if far but position has not changed, teleport to recover
        Vec3d currentPos = villager.getPos();
        if (lastPos != null && dist > FOLLOW_DISTANCE + 1.0 && currentPos.distanceTo(lastPos) < 0.05) {
            stuckCounter++;
            if (stuckCounter >= STUCK_THRESHOLD) {
                villager.refreshPositionAndAngles(
                        player.getX() + 1.5, player.getY(), player.getZ(), 0f, 0f);
                nav.stop();
                stuckCounter = 0;
                LOG.debug("[Mini] VillagerBody {} was stuck – teleported near player", villagerUuid);
            }
        } else {
            stuckCounter = 0;
        }
        lastPos = currentPos;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void spawnNew(ServerPlayerEntity player, ServerWorld world, String worldKey) {
        VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, world);

        Vec3d spawnPos = player.getPos().add(2.0, 0.0, 0.0);
        villager.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0f, 0f);

        villager.setCustomName(Text.literal(BODY_NAME));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        // Prevent natural despawn while bound to an agent
        villager.setPersistent();

        world.spawnEntity(villager);

        villagerUuid     = villager.getUuid();
        villagerWorldKey = worldKey;
        lastPos          = null;
        stuckCounter     = 0;

        LOG.info("[Mini] Spawned VillagerBody {} for agent {}", villagerUuid, ownerUuid);
    }

    private VillagerEntity resolveVillager(ServerWorld world) {
        if (villagerUuid == null) return null;
        var e = world.getEntity(villagerUuid);
        return (e instanceof VillagerEntity v && v.isAlive()) ? v : null;
    }
}
