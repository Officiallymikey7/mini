package io.github.officiallymikey7.mini.body;

import io.github.officiallymikey7.mini.core.InventoryItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.tag.BlockTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Manages a real Villager entity as the in-world autonomous body for one agent.
 *
 * <p>This controller owns long-running action execution so the planner can choose
 * one action while movement/work continues every server tick.
 */
public final class VillagerBody {

    private static final Logger LOG = LoggerFactory.getLogger(VillagerBody.class);

    private static final String BODY_NAME = "MiniBot";
    private static final double WALK_SPEED = 0.8;
    private static final double RUN_SPEED = 1.1;
    private static final int VILLAGER_SEARCH_RADIUS = 16;
    private static final int STUCK_THRESHOLD = 60;
    private static final int RESOURCE_RADIUS = 14;
    private static final int HOSTILE_RADIUS = 12;

    private final String ownerUuid;

    private UUID villagerUuid;
    private String villagerWorldKey;
    private Vec3d lastPos;
    private int stuckCounter;

    private String activeAction;
    private BlockPos actionTarget;
    private int actionTicks;

    private int woodStock;
    private int foodStock;
    private boolean hasCraftedTools;

    public VillagerBody(String ownerUuid) {
       this.ownerUuid = ownerUuid;
    }

    public void ensureSpawned(ServerPlayerEntity player) {
       ServerWorld world = player.getServerWorld();
       String worldKey = world.getRegistryKey().getValue().toString();

       if (villagerUuid != null && worldKey.equals(villagerWorldKey)) {
           var existing = world.getEntity(villagerUuid);
           if (existing instanceof VillagerEntity e && e.isAlive()) {
               return;
           }
           LOG.info("[Mini] VillagerBody {} missing/dead; respawning for agent {}", villagerUuid, ownerUuid);
       }

       VillagerEntity nearest = findNearestAvailableVillager(player, world);
       if (nearest != null) {
           bindToVillager(nearest, worldKey);
           LOG.info("[Mini] Bound existing villager {} to agent {}", villagerUuid, ownerUuid);
           return;
       }

       spawnNewVillager(player, world, worldKey);
    }

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
       activeAction     = null;
       actionTarget     = null;
       actionTicks      = 0;
    }

    public void tick(ServerPlayerEntity player) {
       if (player.isSpectator()) return;
       ensureSpawned(player);

       VillagerEntity body = resolveBody(player.getServerWorld());
       if (body == null) return;

       if (hasImmediateDanger(player.getServerWorld(), body)
               && activeAction != null
               && !activeAction.equals("flee_to_shelter")
               && !activeAction.equals("attack_nearest_hostile")) {
           activeAction = "flee_to_shelter";
           actionTarget = null;
           actionTicks = 0;
       }

       runActionTick(player.getServerWorld(), body);

       Vec3d currentPos = body.getPos();
       if (lastPos != null
               && activeAction != null
               && !body.getNavigation().isIdle()
               && currentPos.distanceTo(lastPos) < 0.03) {
           stuckCounter++;
           if (stuckCounter >= STUCK_THRESHOLD) {
               BlockPos fallback = body.getBlockPos().add(2, 0, 2);
               body.refreshPositionAndAngles(fallback.getX() + 0.5, fallback.getY(), fallback.getZ() + 0.5, 0f, 0f);
               body.getNavigation().stop();
               stuckCounter = 0;
               actionTarget = null;
               LOG.debug("[Mini] VillagerBody {} was stuck – nudged to recover path", villagerUuid);
           }
       } else {
           stuckCounter = 0;
       }
       lastPos = currentPos;
    }

    public String performAction(String action, ServerPlayerEntity owner) {
       ensureSpawned(owner);
       if (activeAction != null && activeAction.equals(action)) {
           return "Continuing action: " + action;
       }
       activeAction = action;
       actionTarget = null;
       actionTicks = 0;
       return "Started action: " + action;
    }

    public VillagerEntity getControlledVillager(ServerPlayerEntity owner) {
       ensureSpawned(owner);
       return resolveBody(owner.getServerWorld());
    }

    public UUID getVillagerUuid() {
       return villagerUuid;
    }

    public List<InventoryItem> getInventorySnapshot() {
       List<InventoryItem> out = new ArrayList<>();
       if (woodStock > 0) out.add(new InventoryItem("minecraft:oak_log", woodStock));
       if (foodStock > 0) out.add(new InventoryItem("minecraft:bread", foodStock));
       if (hasCraftedTools) out.add(new InventoryItem("minecraft:stone_axe", 1));
       return out;
    }

    private void spawnNewVillager(ServerPlayerEntity player, ServerWorld world, String worldKey) {
       VillagerEntity body = new VillagerEntity(EntityType.VILLAGER, world);

       Vec3d spawnPos = player.getPos().add(2.0, 0.0, 0.0);
       body.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0f, 0f);
       bindToVillager(body, worldKey);
       world.spawnEntity(body);

       LOG.info("[Mini] Spawned VillagerBody {} for agent {}", villagerUuid, ownerUuid);
    }

    private void bindToVillager(VillagerEntity body, String worldKey) {
       body.setCustomName(Text.literal(BODY_NAME));
       body.setCustomNameVisible(true);
       body.setPersistent();
       villagerUuid = body.getUuid();
       villagerWorldKey = worldKey;
       lastPos = null;
       stuckCounter = 0;
    }

    private VillagerEntity resolveBody(ServerWorld world) {
       if (villagerUuid == null) return null;
       var e = world.getEntity(villagerUuid);
       return (e instanceof VillagerEntity body && body.isAlive()) ? body : null;
    }

    private VillagerEntity findNearestAvailableVillager(ServerPlayerEntity player, ServerWorld world) {
       Box searchBox = player.getBoundingBox().expand(VILLAGER_SEARCH_RADIUS);
       return world.getEntitiesByClass(VillagerEntity.class, searchBox, v -> v.isAlive() && !v.isBaby()).stream()
               .filter(v -> {
                   Text name = v.getCustomName();
                   return name == null || !name.getString().startsWith(BODY_NAME);
               })
               .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(player)))
               .orElse(null);
    }

    private void runActionTick(ServerWorld world, VillagerEntity body) {
       if (activeAction == null) return;

       actionTicks++;

       switch (activeAction) {
           case "explore" -> runExplore(body);
           case "flee_to_shelter" -> runFleeToShelter(world, body);
           case "find_or_build_shelter", "build_shelter" -> runShelterAction(world, body);
           case "gather_wood" -> runGatherWood(world, body);
           case "gather_food", "forage_food" -> runGatherFood(world, body);
           case "eat_food" -> runEatFood(body);
           case "craft_tools" -> runCraftTools();
           case "attack_nearest_hostile", "craft_sword_or_flee" -> runDefensive(world, body);
           default -> runExplore(body);
       }
    }

    private void runExplore(VillagerEntity body) {
       if (actionTarget == null || actionTicks % 80 == 0 || isNear(body, actionTarget, 1.8)) {
           actionTarget = body.getBlockPos().add(
                   body.getRandom().nextBetween(-8, 8),
                   0,
                   body.getRandom().nextBetween(-8, 8));
       }
       moveTo(body, actionTarget, WALK_SPEED);
    }

    private void runFleeToShelter(ServerWorld world, VillagerEntity body) {
       BlockPos shelter = findNearestShelter(world, body.getBlockPos(), 24);
       if (shelter != null) {
           actionTarget = shelter;
           moveTo(body, shelter, RUN_SPEED);
           if (isSheltered(world, body.getBlockPos())) {
               activeAction = null;
           }
           return;
       }

       LivingEntity nearestHostile = findNearestHostile(world, body);
       if (nearestHostile != null) {
           Vec3d away = body.getPos().subtract(nearestHostile.getPos()).normalize().multiply(8.0);
           BlockPos flee = BlockPos.ofFloored(body.getPos().add(away));
           actionTarget = flee;
           moveTo(body, flee, RUN_SPEED);
       } else {
           activeAction = null;
       }
    }

    private void runShelterAction(ServerWorld world, VillagerEntity body) {
       BlockPos shelter = findNearestShelter(world, body.getBlockPos(), 24);
       if (shelter != null) {
           actionTarget = shelter;
           moveTo(body, shelter, WALK_SPEED);
           if (isSheltered(world, body.getBlockPos())) {
               activeAction = null;
           }
           return;
       }

       if (activeAction.equals("build_shelter") || activeAction.equals("find_or_build_shelter")) {
           buildSimpleShelter(world, body.getBlockPos());
           activeAction = null;
       }
    }

    private void runGatherWood(ServerWorld world, VillagerEntity body) {
       if (actionTarget == null || !isWood(world.getBlockState(actionTarget))) {
           actionTarget = findNearestBlock(world, body.getBlockPos(), RESOURCE_RADIUS, -2, 4, VillagerBody::isWood);
           if (actionTarget == null) {
               runExplore(body);
               return;
           }
       }

       moveTo(body, actionTarget, WALK_SPEED);
       if (isNear(body, actionTarget, 1.8) && isWood(world.getBlockState(actionTarget))) {
           world.breakBlock(actionTarget, false);
           woodStock++;
           actionTarget = null;
       }
    }

    private void runGatherFood(ServerWorld world, VillagerEntity body) {
       if (actionTarget == null || !isFoodBlock(world.getBlockState(actionTarget))) {
           actionTarget = findNearestBlock(world, body.getBlockPos(), RESOURCE_RADIUS, -2, 3, VillagerBody::isFoodBlock);
           if (actionTarget == null) {
               runExplore(body);
               return;
           }
       }

       moveTo(body, actionTarget, WALK_SPEED);
       if (isNear(body, actionTarget, 1.8) && isFoodBlock(world.getBlockState(actionTarget))) {
           world.breakBlock(actionTarget, false);
           foodStock++;
           actionTarget = null;
       }
    }

    private void runEatFood(VillagerEntity body) {
       if (foodStock > 0 && body.getHealth() < body.getMaxHealth()) {
           body.heal(4.0f);
           foodStock--;
       }
       activeAction = null;
    }

    private void runCraftTools() {
       if (hasCraftedTools) {
           activeAction = null;
           return;
       }
       if (woodStock >= 3) {
           woodStock -= 3;
           hasCraftedTools = true;
           activeAction = null;
           return;
       }
       activeAction = "gather_wood";
       actionTarget = null;
       actionTicks = 0;
    }

    private void runDefensive(ServerWorld world, VillagerEntity body) {
       if (!hasCraftedTools && woodStock >= 3) {
           runCraftTools();
       }
       runFleeToShelter(world, body);
    }

    private boolean hasImmediateDanger(ServerWorld world, VillagerEntity body) {
       return findNearestHostile(world, body) != null;
    }

    private LivingEntity findNearestHostile(ServerWorld world, VillagerEntity body) {
       Box dangerBox = body.getBoundingBox().expand(HOSTILE_RADIUS);
       return world.getEntitiesByClass(LivingEntity.class, dangerBox,
                       e -> e instanceof Monster monster && monster.isAlive())
               .stream()
               .min(Comparator.comparingDouble(body::distanceTo))
               .orElse(null);
    }

    private static boolean isWood(BlockState state) {
       return state.isIn(BlockTags.LOGS);
    }

    private static boolean isFoodBlock(BlockState state) {
       return state.isOf(Blocks.WHEAT)
               || state.isOf(Blocks.CARROTS)
               || state.isOf(Blocks.POTATOES)
               || state.isOf(Blocks.BEETROOTS)
               || state.isOf(Blocks.MELON)
               || state.isOf(Blocks.PUMPKIN)
               || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean isSheltered(ServerWorld world, BlockPos pos) {
       return !world.isSkyVisible(pos.up());
    }

    private static BlockPos findNearestShelter(ServerWorld world, BlockPos origin, int radius) {
       if (isSheltered(world, origin)) return origin;

       BlockPos bed = findNearestBlock(world, origin, radius, -3, 4, state -> state.isIn(BlockTags.BEDS));
       if (bed != null) return bed;

       return findNearestBlock(world, origin, radius, -2, 4, state -> !state.isAir() && !world.isSkyVisible(origin.up()));
    }

    private static BlockPos findNearestBlock(
           ServerWorld world,
           BlockPos origin,
           int horizontalRadius,
           int minY,
           int maxY,
           Predicate<BlockState> predicate) {
       BlockPos best = null;
       double bestDist = Double.MAX_VALUE;
       BlockPos.Mutable mutable = new BlockPos.Mutable();

       for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
           for (int dy = minY; dy <= maxY; dy++) {
               for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                   mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                   BlockState state = world.getBlockState(mutable);
                   if (!predicate.test(state)) continue;

                   double dist = origin.getSquaredDistance(mutable);
                   if (dist < bestDist) {
                       bestDist = dist;
                       best = mutable.toImmutable();
                   }
               }
           }
       }
       return best;
    }

    private static void buildSimpleShelter(ServerWorld world, BlockPos base) {
       BlockPos roof = base.up(2);
       if (world.getBlockState(roof).isAir()) {
           world.setBlockState(roof, Blocks.OAK_PLANKS.getDefaultState());
       }
    }

    private static boolean isNear(VillagerEntity villager, BlockPos pos, double threshold) {
       double dx = villager.getX() - (pos.getX() + 0.5);
       double dy = villager.getY() - pos.getY();
       double dz = villager.getZ() - (pos.getZ() + 0.5);
       return (dx * dx + dy * dy + dz * dz) <= threshold * threshold;
    }

    private static void moveTo(VillagerEntity villager, BlockPos target, double speed) {
       villager.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
       villager.getLookControl().lookAt(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 30f, 30f);
    }
}
