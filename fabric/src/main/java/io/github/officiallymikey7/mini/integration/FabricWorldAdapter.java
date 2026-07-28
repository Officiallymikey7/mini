package io.github.officiallymikey7.mini.integration;

import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
 * Fabric / Minecraft adapter that reads live world state from a
 * {@link ServerPlayerEntity} and dispatches simple actions via the server API.
 *
 * <p>This adapter is intentionally kept simple: it reads common player stats and
 * performs actions by sending chat commands or adjusting player state directly.
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

    private final ServerPlayerEntity player;

    public FabricWorldAdapter(ServerPlayerEntity player) {
        this.player = player;
    }

    // ── BotAdapter ───────────────────────────────────────────────────────────

    @Override
    public RawWorldState getWorldState() {
        ServerWorld world = player.getServerWorld();

        // --- time ---
        int gameTick = (int) (world.getTimeOfDay() % 24000);

        // --- health / hunger ---
        float health = player.getHealth();
        float hunger = player.getHungerManager().getFoodLevel();

        // --- nearby hostiles ---
        Box box = player.getBoundingBox().expand(DETECTION_RADIUS);
        List<HostileEntity> hostiles = new ArrayList<>();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e instanceof Monster && e.isAlive())) {
            double dist = player.distanceTo(entity);
            hostiles.add(new HostileEntity(entity.getType().toString(), dist));
        }

        // --- inventory ---
        List<InventoryItem> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                inventory.add(new InventoryItem(
                        stack.getItem().toString(), stack.getCount()));
            }
        }

        // --- shelter heuristic: agent is sheltered when indoors (no sky exposure) ---
        boolean hasShelter = !world.isSkyVisible(player.getBlockPos().up());
        int shelterDistance = hasShelter ? 0 : -1; // -1 = unknown distance

        // --- nearby chat (populated externally via injectChat) ---
        List<String> chat = List.copyOf(pendingChat);
        pendingChat.clear();

        // --- spatial context ---
        double posX = player.getX();
        double posY = player.getY();
        double posZ = player.getZ();
        int lightLevel = world.getLightLevel(player.getBlockPos());
        String biome = world.getBiome(player.getBlockPos())
                .getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");
        String mainHandItem = Registries.ITEM
                .getId(player.getMainHandStack().getItem()).getPath();
        String offHandItem = Registries.ITEM
                .getId(player.getOffHandStack().getItem()).getPath();
        List<String> nearbyBlocks = scanNearbyBlocks(player, world);

        return new RawWorldState(
                player.getName().getString(),
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
        return switch (action) {
            case "explore"               -> { player.sendMessage(Text.of("[Mini] Exploring…"), false); yield "Exploring area."; }
            case "flee_to_shelter"       -> { player.sendMessage(Text.of("[Mini] Fleeing to shelter!"), false); yield "Fleeing to shelter."; }
            case "find_or_build_shelter" -> { player.sendMessage(Text.of("[Mini] Seeking shelter…"), false); yield "Seeking shelter."; }
            case "gather_wood"           -> { player.sendMessage(Text.of("[Mini] Gathering wood…"), false); yield "Looking for wood."; }
            case "gather_food",
                 "forage_food"           -> { player.sendMessage(Text.of("[Mini] Foraging food…"), false); yield "Foraging for food."; }
            case "eat_food"              -> { player.sendMessage(Text.of("[Mini] Eating food…"), false); yield "Eating available food."; }
            case "craft_tools"           -> { player.sendMessage(Text.of("[Mini] Crafting tools…"), false); yield "Attempting to craft tools."; }
            case "build_shelter"         -> { player.sendMessage(Text.of("[Mini] Building shelter…"), false); yield "Building shelter."; }
            case "attack_nearest_hostile"-> { player.sendMessage(Text.of("[Mini] Attacking hostile!"), false); yield "Attacking nearest hostile."; }
            case "craft_sword_or_flee"   -> { player.sendMessage(Text.of("[Mini] Preparing defense…"), false); yield "Preparing defense."; }
            default                      -> { player.sendMessage(Text.of("[Mini] " + action), false); yield "Performed: " + action; }
        };
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
     * Scans a small cube around the player and returns up to
     * {@value #MAX_NEARBY_BLOCKS} distinct non-trivial block-type names.
     * The scan intentionally excludes common terrain blocks (dirt, stone, grass)
     * so the list highlights blocks that are strategically relevant to the agent
     * (logs, ores, water, chests, crafting tables, etc.).
     */
    private static List<String> scanNearbyBlocks(ServerPlayerEntity player, ServerWorld world) {
        BlockPos origin = player.getBlockPos();
        Set<String> seen = new LinkedHashSet<>();
        int r = NEARBY_BLOCK_RADIUS;
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (seen.size() >= MAX_NEARBY_BLOCKS) break outer;
                    BlockState state = world.getBlockState(origin.add(dx, dy, dz));
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
}
