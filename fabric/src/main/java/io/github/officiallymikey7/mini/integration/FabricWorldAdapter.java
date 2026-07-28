package io.github.officiallymikey7.mini.integration;

import io.github.officiallymikey7.mini.body.VillagerBody;
import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fabric / Minecraft adapter that reads live world state from a controlled
 * villager body and dispatches actions into its autonomous runtime.
 *
 * <p>This adapter is intentionally kept simple: it reads common villager stats and
 * delegates action execution to {@link VillagerBody}.
 * Extend or replace for richer behaviour (pathfinding, crafting, etc.).
 *
 * <p><b>Thread safety:</b> Call {@link #getWorldState()} and
 * {@link #performAction(String)} only from the Minecraft server tick thread
 * (e.g. inside a {@code ServerTickEvents} callback).
 */
public final class FabricWorldAdapter implements BotAdapter {

    /** Detection radius for nearby mobs and shelter checks. */
    private static final double DETECTION_RADIUS = 24.0;

    /**
     * Block types considered structurally trivial; excluded from the
     * nearby-blocks list to keep the agent context focused on items of interest.
     */
    private static final Set<String> TRIVIAL_BLOCKS = Set.of(
            "air", "cave_air", "void_air", "grass_block", "dirt", "stone",
            "deepslate", "sand", "gravel", "grass", "short_grass");

    /** Maximum distinct block types reported in the nearby-blocks list. */
    private static final int MAX_NEARBY_BLOCKS = 10;

    /** Scan radius (in blocks) for the nearby-blocks survey. */
    private static final int NEARBY_BLOCK_RADIUS = 6;

    /** Refresh interval for the nearby-blocks cache (ticks). 20 = once per second at 20 TPS. */
    private static final int NEARBY_BLOCKS_CACHE_INTERVAL = 20;

    private final ServerPlayerEntity player;
    private final VillagerBody body;

    /** Cached result of the last nearby-blocks scan. */
    private List<String> cachedNearbyBlocks = List.of();
    /** Game tick at which the cache was last populated. */
    private long cachedNearbyBlocksTick = -NEARBY_BLOCKS_CACHE_INTERVAL;

    public FabricWorldAdapter(ServerPlayerEntity player, VillagerBody body) {
        this.player = player;
        this.body = body;
    }

    // ── BotAdapter ───────────────────────────────────────────────────────────

    @Override
    public RawWorldState getWorldState() {
        ServerWorld world = player.getServerWorld();
        VillagerEntity villager = body.getControlledVillager(player);
        if (villager == null) {
            return emptyState(world);
        }

        // --- time ---
        int gameTick = (int) (world.getTimeOfDay() % 24000);
        long worldTime = world.getTimeOfDay(); // monotonically increasing; used for cache tracking

        // --- inventory (built first so hunger can be derived from food count) ---
        List<InventoryItem> inventory = body.getInventorySnapshot();

        // --- health / hunger ---
        // Hunger is derived from the villager inventory food count so that the planner
        // correctly treats low food stock as a FOOD urgency trigger rather than relying
        // on damage-based health proxy.
        float health = villager.getHealth();
        int foodCount = 0;
        for (InventoryItem item : inventory) {
            if ("minecraft:bread".equals(item.name)) {
                foodCount = item.count;
                break;
            }
        }
        float hunger = Math.min(20f, foodCount * 4f);

        // --- nearby hostiles ---
        Box box = villager.getBoundingBox().expand(DETECTION_RADIUS);
        List<HostileEntity> hostiles = new ArrayList<>();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e instanceof Monster && e.isAlive())) {
            double dist = villager.distanceTo(entity);
            hostiles.add(new HostileEntity(entity.getType().toString(), dist));
        }

        // --- shelter heuristic: agent is sheltered when indoors (no sky exposure) ---
        boolean hasShelter = !world.isSkyVisible(villager.getBlockPos().up());
        int shelterDistance = hasShelter ? 0 : estimateShelterDistance(world, villager.getBlockPos());

        // --- nearby chat (populated externally via injectChat) ---
        List<String> chat = List.copyOf(pendingChat);
        pendingChat.clear();

        // --- spatial context ---
        double posX = villager.getX();
        double posY = villager.getY();
        double posZ = villager.getZ();
        int lightLevel = world.getLightLevel(villager.getBlockPos());
        String biome = world.getBiome(villager.getBlockPos())
                .getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");
        String mainHandItem = Registries.ITEM
                .getId(villager.getMainHandStack().getItem()).getPath();
        String offHandItem = Registries.ITEM
                .getId(villager.getOffHandStack().getItem()).getPath();
        List<String> nearbyBlocks = getNearbyBlocksCached(worldTime, world, villager);

        return new RawWorldState(
                villager.getName().getString(),
                gameTick,
                health,
                hunger,
                hostiles,
                inventory,
                shelterDistance,
                hasShelter,
                chat,
                posX,
                posY,
                posZ,
                lightLevel,
                biome,
                mainHandItem,
                offHandItem,
                nearbyBlocks);
    }

    @Override
    public String performAction(String action) {
        String result = body.performAction(action, player);
        player.sendMessage(Text.of("[Mini] " + result), false);
        return result;
    }

    @Override
    public void sendChat(String message) {
        player.getServer().getCommandManager().executeWithPrefix(
                player.getCommandSource(), "say " + message);
    }

    // ── Chat injection (call from chat event listener) ───────────────────────

    private final List<String> pendingChat = new ArrayList<>();

    /** Inject a chat message for inclusion in the next world-state snapshot. */
    public void injectChat(String senderName, String message) {
        pendingChat.add(senderName + ": " + message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the nearby-blocks list, refreshing the cache at most once every
     * {@value #NEARBY_BLOCKS_CACHE_INTERVAL} ticks to avoid scanning ~1 183 block states
     * on every agent tick.
     */
    private List<String> getNearbyBlocksCached(long currentTick, ServerWorld world, VillagerEntity villager) {
        if (currentTick - cachedNearbyBlocksTick >= NEARBY_BLOCKS_CACHE_INTERVAL) {
            cachedNearbyBlocks = scanNearbyBlocks(villager, world);
            cachedNearbyBlocksTick = currentTick;
        }
        return cachedNearbyBlocks;
    }

    /**
     * Scans a small cube around the player and returns up to
     * {@value #MAX_NEARBY_BLOCKS} distinct non-trivial block-type names.
     * The scan intentionally excludes common terrain blocks (dirt, stone, grass)
     * so the list highlights blocks that are strategically relevant to the agent
     * (logs, ores, water, chests, crafting tables, etc.).
     *
     * <p>Uses a single mutable {@link BlockPos.Mutable} to avoid per-iteration allocations.
     */
    private static List<String> scanNearbyBlocks(VillagerEntity villager, ServerWorld world) {
        BlockPos origin = villager.getBlockPos();
        Set<String> seen = new LinkedHashSet<>();
        int r = NEARBY_BLOCK_RADIUS;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (seen.size() >= MAX_NEARBY_BLOCKS) break outer;
                    BlockState state = world.getBlockState(mutable.set(origin, dx, dy, dz));
                    if (!state.isAir()) {
                        String name = Registries.BLOCK.getId(state.getBlock()).getPath();
                        if (!TRIVIAL_BLOCKS.contains(name)) {
                            seen.add(name);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private RawWorldState emptyState(ServerWorld world) {
        int gameTick = (int) (world.getTimeOfDay() % 24000);
        return new RawWorldState(
                player.getName().getString(),
                gameTick,
                20f,
                20f,
                List.of(),
                List.of(),
                -1,
                false,
                List.of(),
                player.getX(),
                player.getY(),
                player.getZ(),
                world.getLightLevel(player.getBlockPos()),
                "unknown",
                "air",
                "air",
                List.of());
    }

    private static int estimateShelterDistance(ServerWorld world, BlockPos origin) {
        if (!world.isSkyVisible(origin.up())) return 0;

        int radius = 18;
        int bestSquared = Integer.MAX_VALUE;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) continue;
                    if (!world.isSkyVisible(mutable.up())) {
                        int sq = dx * dx + dy * dy + dz * dz;
                        if (sq < bestSquared) bestSquared = sq;
                    }
                }
            }
        }
        if (bestSquared == Integer.MAX_VALUE) return -1;
        return (int) Math.sqrt(bestSquared);
    }
}
