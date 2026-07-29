package de.quest.quest;

import de.quest.quest.daily.DailyQuestService;
import de.quest.util.WoolItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class QuestDropTracker {
    private static final long DROP_SOURCE_EXPIRE_TICKS = 5L;
    private static final int MAX_WOOL_SHEAR_DROP = 3;
    private static final int MAX_MOB_DROP = 4;
    private static final double BLOCK_BREAK_RADIUS_SQR = 4.0D;
    private static final double ENTITY_DROP_RADIUS_SQR = 9.0D;

    private static final List<PendingDropSource> PENDING_SOURCES = new ArrayList<>();
    private static final List<PendingShearSource> PENDING_SHEARS = new ArrayList<>();

    private QuestDropTracker() {}

    public static void clear() {
        PENDING_SOURCES.clear();
        PENDING_SHEARS.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        matchNearbyDrops();
        cleanupExpiredSources();
        cleanupExpiredShears();
    }

    public static void onBlockBreakStart(ServerLevel world,
                                         ServerPlayer player,
                                         BlockPos pos,
                                         BlockState state,
                                         BlockEntity blockEntity) {
        cleanupExpiredSources();
        Map<Item, Integer> expectedDrops = getTrackedBlockDrops(world, player, pos, state, blockEntity);
        if (expectedDrops.isEmpty()) {
            return;
        }
        PENDING_SOURCES.add(PendingDropSource.blockBreak(world, player.getUUID(), pos, expectedDrops));
    }

    public static void onBlockBreakCanceled(ServerLevel world, ServerPlayer player, BlockPos pos) {
        removeBlockBreakSource(world, player.getUUID(), pos);
    }

    public static void onBlockBreakFinished(ServerLevel world, ServerPlayer player, BlockPos pos) {
        dispatchBlockBreakSource(world, player, pos);
    }

    public static void onBlockResourceDrop(ServerLevel world, BlockPos pos, ItemStack stack) {
        if (world == null || pos == null || stack == null || stack.isEmpty() || !isTrackedBlockDrop(stack.getItem())) {
            return;
        }

        cleanupExpiredSources();
        for (PendingDropSource source : PENDING_SOURCES) {
            if (!source.matchesBlockResource(world, pos, stack.getItem())) {
                continue;
            }

            ServerPlayer player = world.getServer().getPlayerList().getPlayer(source.playerId);
            if (player == null) {
                return;
            }

            int count = stack.getCount();
            DailyQuestService.onTrackedItemPickup(world, player, stack.copy(), count);
            source.captureBlockResource(stack.getItem());
            return;
        }
    }

    public static void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack stack) {
        cleanupExpiredSources();
        if (!(entity instanceof Sheep sheep) || stack == null || !stack.is(Items.SHEARS) || !sheep.readyForShearing()) {
            return;
        }
        removePendingShear(world, sheep.getUUID());
        PENDING_SHEARS.add(PendingShearSource.create(world, player.getUUID(), sheep.getUUID()));
    }

    public static void onKilledOtherEntity(ServerLevel world, ServerPlayer player, LivingEntity killedEntity) {
        cleanupExpiredSources();
        if (killedEntity instanceof EnderMan) {
            PENDING_SOURCES.add(PendingDropSource.entityDrop(
                    world,
                    player.getUUID(),
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    ENTITY_DROP_RADIUS_SQR,
                    Map.of(Items.ENDER_PEARL, MAX_MOB_DROP)
            ));
        } else if (killedEntity instanceof Blaze) {
            PENDING_SOURCES.add(PendingDropSource.entityDrop(
                    world,
                    player.getUUID(),
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    ENTITY_DROP_RADIUS_SQR,
                    Map.of(Items.BLAZE_ROD, MAX_MOB_DROP)
            ));
        }
    }

    public static void onEntityLoad(Entity entity, ServerLevel world) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        cleanupExpiredSources();
        PendingDropSource bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (PendingDropSource source : PENDING_SOURCES) {
            if (!source.matches(world, itemEntity)) {
                continue;
            }

            double distance = itemEntity.distanceToSqr(source.originX, source.originY, source.originZ);
            if (bestMatch == null || distance < bestDistance) {
                bestMatch = source;
                bestDistance = distance;
            }
        }

        if (bestMatch == null) {
            return;
        }

        if (bestMatch.tryTrack(itemEntity) && bestMatch.isExhausted()) {
            PENDING_SOURCES.remove(bestMatch);
        }
    }

    public static void onTrackedItemPickup(ServerPlayer player, ItemEntity itemEntity, int count) {
        if (player == null || itemEntity == null || count <= 0) {
            return;
        }

        TrackedQuestDrop trackedDrop = (TrackedQuestDrop) itemEntity;
        UUID trackedPlayerId = trackedDrop.villageQuest$getTrackedPlayerId();
        if (!Objects.equals(trackedPlayerId, player.getUUID())
                && !tryTrackAtPickup((ServerLevel) player.level(), player.getUUID(), itemEntity)) {
            return;
        }

        ItemStack pickedUpStack = itemEntity.getItem().copyWithCount(count);
        DailyQuestService.onTrackedItemPickup((ServerLevel) player.level(), player, pickedUpStack, count);
    }

    public static void onShearedDrop(ServerLevel world, Sheep sheep, ItemStack stack) {
        if (world == null || sheep == null || stack == null || stack.isEmpty() || !isWool(stack.getItem())) {
            return;
        }

        cleanupExpiredShears();
        PendingShearSource match = findPendingShear(world, sheep.getUUID());
        if (match == null) {
            return;
        }

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(match.playerId);
        if (player == null) {
            return;
        }
        DailyQuestService.onTrackedItemPickup(world, player, stack.copy(), stack.getCount());
    }

    public static void onShearedFinished(ServerLevel world, Sheep sheep) {
        if (world == null || sheep == null) {
            return;
        }
        cleanupExpiredShears();
        PendingShearSource match = findPendingShear(world, sheep.getUUID());
        if (match != null) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(match.playerId);
            if (player != null) {
                DailyQuestService.onSheepSheared(world, player, sheep);
            }
        }
        removePendingShear(world, sheep.getUUID());
    }

    private static void cleanupExpiredSources() {
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove();
            }
        }
    }

    private static void cleanupExpiredShears() {
        Iterator<PendingShearSource> iterator = PENDING_SHEARS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove();
            }
        }
    }

    private static void matchNearbyDrops() {
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PendingDropSource source = iterator.next();
            if (source.world == null || source.isExpired()) {
                continue;
            }

            AABB searchBox = new AABB(source.originX, source.originY, source.originZ, source.originX, source.originY, source.originZ)
                    .inflate(Math.sqrt(source.radiusSqr));
            List<ItemEntity> nearbyItems = source.world.getEntitiesOfClass(
                    ItemEntity.class,
                    searchBox,
                    itemEntity -> itemEntity != null
                            && !itemEntity.isRemoved()
                            && itemEntity.getAge() <= DROP_SOURCE_EXPIRE_TICKS
            );
            nearbyItems.sort(Comparator.comparingDouble(
                    itemEntity -> itemEntity.distanceToSqr(source.originX, source.originY, source.originZ)
            ));

            for (ItemEntity itemEntity : nearbyItems) {
                source.tryTrack(itemEntity);
                if (source.isExhausted()) {
                    break;
                }
            }

            if (source.isExhausted()) {
                iterator.remove();
            }
        }
    }

    private static boolean tryTrackAtPickup(ServerLevel world, UUID playerId, ItemEntity itemEntity) {
        if (world == null || playerId == null || itemEntity == null || itemEntity.getAge() > DROP_SOURCE_EXPIRE_TICKS) {
            return false;
        }

        cleanupExpiredSources();
        PendingDropSource bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (PendingDropSource source : PENDING_SOURCES) {
            if (source.world != world
                    || !Objects.equals(source.playerId, playerId)
                    || !source.matches(world, itemEntity)) {
                continue;
            }

            double distance = itemEntity.distanceToSqr(source.originX, source.originY, source.originZ);
            if (bestMatch == null || distance < bestDistance) {
                bestMatch = source;
                bestDistance = distance;
            }
        }

        if (bestMatch == null || !bestMatch.tryTrack(itemEntity)) {
            return false;
        }
        if (bestMatch.isExhausted()) {
            PENDING_SOURCES.remove(bestMatch);
        }
        return true;
    }

    private static void removeBlockBreakSource(ServerLevel world, UUID playerId, BlockPos pos) {
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PendingDropSource source = iterator.next();
            if (source.kind == PendingDropKind.BLOCK_BREAK
                    && source.world == world
                    && Objects.equals(source.playerId, playerId)
                    && source.originBlockPos != null
                    && source.originBlockPos.equals(pos)) {
                iterator.remove();
            }
        }
    }

    private static void removePendingShear(ServerLevel world, UUID sheepId) {
        Iterator<PendingShearSource> iterator = PENDING_SHEARS.iterator();
        while (iterator.hasNext()) {
            PendingShearSource source = iterator.next();
            if (source.world == world && Objects.equals(source.sheepId, sheepId)) {
                iterator.remove();
            }
        }
    }

    private static PendingShearSource findPendingShear(ServerLevel world, UUID sheepId) {
        for (PendingShearSource source : PENDING_SHEARS) {
            if (source.matches(world, sheepId)) {
                return source;
            }
        }
        return null;
    }

    private static void dispatchBlockBreakSource(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return;
        }
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PendingDropSource source = iterator.next();
            if (source.kind != PendingDropKind.BLOCK_BREAK
                    || source.world != world
                    || !Objects.equals(source.playerId, player.getUUID())
                    || source.originBlockPos == null
                    || !source.originBlockPos.equals(pos)) {
                continue;
            }

            if (source.capturedBlockResource) {
                iterator.remove();
            }
            return;
        }
    }

    private static Map<Item, Integer> getTrackedBlockDrops(ServerLevel world,
                                                           ServerPlayer player,
                                                           BlockPos pos,
                                                           BlockState state,
                                                           BlockEntity blockEntity) {
        if (world == null || player == null || pos == null || state == null) {
            return Map.of();
        }

        Item matureCropItem = matureCropItem(state);
        if (matureCropItem != null) {
            return Map.of(matureCropItem, 1);
        }

        List<ItemStack> drops = Block.getDrops(state, world, pos, blockEntity, player, player.getMainHandItem());
        if (drops.isEmpty()) {
            return Map.of();
        }

        Map<Item, Integer> trackedDrops = new HashMap<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            Item item = drop.getItem();
            if (isTrackedBlockDrop(item) && (!isActualHarvestDrop(item) || isMatureCrop(state))) {
                trackedDrops.merge(item, drop.getCount(), Integer::sum);
            }
        }
        return trackedDrops;
    }

    private static boolean isTrackedBlockDrop(Item item) {
        return item == Items.COAL
                || item == Items.RAW_IRON
                || item == Items.RAW_GOLD
                || item == Items.DIAMOND
                || item == Items.REDSTONE
                || item == Items.LAPIS_LAZULI
                || item == Items.AMETHYST_SHARD
                || item == Items.WHEAT
                || item == Items.POTATO
                || item == Items.CARROT;
    }

    private static boolean isActualHarvestDrop(Item item) {
        return item == Items.WHEAT || item == Items.POTATO || item == Items.CARROT;
    }

    private static boolean isMatureCrop(BlockState state) {
        return state != null
                && state.getBlock() instanceof CropBlock crop
                && state.hasProperty(CropBlock.AGE)
                && state.getValue(CropBlock.AGE) >= crop.getMaxAge();
    }

    private static Item matureCropItem(BlockState state) {
        if (!isMatureCrop(state)) {
            return null;
        }
        if (state.is(Blocks.WHEAT)) {
            return Items.WHEAT;
        }
        if (state.is(Blocks.POTATOES)) {
            return Items.POTATO;
        }
        if (state.is(Blocks.CARROTS)) {
            return Items.CARROT;
        }
        return null;
    }

    private static boolean isWool(Item item) {
        return WoolItems.isWool(item);
    }

    private enum PendingDropKind {
        BLOCK_BREAK,
        ENTITY_DROP
    }

    private static final class PendingShearSource {
        private final ServerLevel world;
        private final UUID playerId;
        private final UUID sheepId;
        private final long expiresAtTick;

        private PendingShearSource(ServerLevel world, UUID playerId, UUID sheepId, long expiresAtTick) {
            this.world = world;
            this.playerId = playerId;
            this.sheepId = sheepId;
            this.expiresAtTick = expiresAtTick;
        }

        private static PendingShearSource create(ServerLevel world, UUID playerId, UUID sheepId) {
            return new PendingShearSource(world, playerId, sheepId, world.getGameTime() + DROP_SOURCE_EXPIRE_TICKS);
        }

        private boolean matches(ServerLevel world, UUID sheepId) {
            return this.world == world && Objects.equals(this.sheepId, sheepId) && !isExpired();
        }

        private boolean isExpired() {
            return world == null || world.getGameTime() > expiresAtTick;
        }
    }

    private static final class PendingDropSource {
        private final PendingDropKind kind;
        private final ServerLevel world;
        private final UUID playerId;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final double radiusSqr;
        private final long expiresAtTick;
        private final BlockPos originBlockPos;
        private final Map<Item, Integer> remainingCounts;
        private boolean capturedBlockResource;

        private PendingDropSource(PendingDropKind kind,
                                  ServerLevel world,
                                  UUID playerId,
                                  double originX,
                                  double originY,
                                  double originZ,
                                  double radiusSqr,
                                  long expiresAtTick,
                                  BlockPos originBlockPos,
                                  Map<Item, Integer> remainingCounts) {
            this.kind = kind;
            this.world = world;
            this.playerId = playerId;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.radiusSqr = radiusSqr;
            this.expiresAtTick = expiresAtTick;
            this.originBlockPos = originBlockPos;
            this.remainingCounts = new HashMap<>(remainingCounts);
        }

        private static PendingDropSource blockBreak(ServerLevel world, UUID playerId, BlockPos pos, Map<Item, Integer> remainingCounts) {
            return new PendingDropSource(
                    PendingDropKind.BLOCK_BREAK,
                    world,
                    playerId,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    BLOCK_BREAK_RADIUS_SQR,
                    world.getGameTime() + DROP_SOURCE_EXPIRE_TICKS,
                    pos.immutable(),
                    remainingCounts
            );
        }

        private static PendingDropSource entityDrop(ServerLevel world,
                                                    UUID playerId,
                                                    double originX,
                                                    double originY,
                                                    double originZ,
                                                    double radiusSqr,
                                                    Map<Item, Integer> remainingCounts) {
            return new PendingDropSource(
                    PendingDropKind.ENTITY_DROP,
                    world,
                    playerId,
                    originX,
                    originY,
                    originZ,
                    radiusSqr,
                    world.getGameTime() + DROP_SOURCE_EXPIRE_TICKS,
                    null,
                    remainingCounts
            );
        }

        private boolean matches(ServerLevel world, ItemEntity itemEntity) {
            if (this.world != world || isExpired() || itemEntity == null) {
                return false;
            }

            Item item = itemEntity.getItem().getItem();
            int remaining = remainingCounts.getOrDefault(item, 0);
            return remaining > 0 && itemEntity.distanceToSqr(originX, originY, originZ) <= radiusSqr;
        }

        private boolean matchesBlockResource(ServerLevel world, BlockPos pos, Item item) {
            return kind == PendingDropKind.BLOCK_BREAK
                    && this.world == world
                    && !isExpired()
                    && originBlockPos != null
                    && originBlockPos.equals(pos)
                    && remainingCounts.containsKey(item);
        }

        private void captureBlockResource(Item item) {
            capturedBlockResource = true;
            remainingCounts.put(item, 0);
        }

        private void consume(Item item, int count) {
            if (item == null || count <= 0) {
                return;
            }
            int remaining = remainingCounts.getOrDefault(item, 0);
            if (remaining <= 0) {
                return;
            }
            remainingCounts.put(item, Math.max(0, remaining - count));
        }

        private boolean tryTrack(ItemEntity itemEntity) {
            if (!matches(world, itemEntity) || itemEntity.getAge() > DROP_SOURCE_EXPIRE_TICKS) {
                return false;
            }

            TrackedQuestDrop trackedDrop = (TrackedQuestDrop) itemEntity;
            if (trackedDrop.villageQuest$getTrackedPlayerId() != null) {
                return false;
            }

            trackedDrop.villageQuest$setTrackedPlayerId(playerId);
            consume(itemEntity.getItem().getItem(), itemEntity.getItem().getCount());
            return true;
        }

        private boolean isExpired() {
            return world == null || world.getGameTime() > expiresAtTick;
        }

        private boolean isExhausted() {
            for (int remaining : remainingCounts.values()) {
                if (remaining > 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
