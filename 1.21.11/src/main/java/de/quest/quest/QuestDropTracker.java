package de.quest.quest;

import de.quest.quest.daily.DailyQuestService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

public final class QuestDropTracker {
    private static final long DROP_SOURCE_EXPIRE_TICKS = 5L;
    private static final int MAX_WOOL_SHEAR_DROP = 3;
    private static final int MAX_MOB_DROP = 4;
    private static final double BLOCK_BREAK_RADIUS_SQUARED = 4.0D;
    private static final double ENTITY_DROP_RADIUS_SQUARED = 9.0D;

    private static final List<PendingDropSource> PENDING_SOURCES = new ArrayList<>();
    private static final List<PendingShearSource> PENDING_SHEARS = new ArrayList<>();

    private QuestDropTracker() {}

    public static void clear() {
        PENDING_SOURCES.clear();
        PENDING_SHEARS.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        matchNearbyEntityDrops();
        cleanupExpiredSources();
        cleanupExpiredShears();
    }

    public static void onBlockBreakStart(ServerWorld world,
                                         ServerPlayerEntity player,
                                         BlockPos pos,
                                         BlockState state,
                                         BlockEntity blockEntity) {
        cleanupExpiredSources();
        Map<Item, Integer> expectedDrops = getTrackedBlockDrops(world, player, pos, state, blockEntity);
        if (expectedDrops.isEmpty()) {
            return;
        }
        PENDING_SOURCES.add(PendingDropSource.blockBreak(world, player.getUuid(), pos, expectedDrops));
    }

    public static void onBlockBreakCanceled(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        removeBlockBreakSource(world, player.getUuid(), pos);
    }

    public static void onBlockBreakFinished(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return;
        }
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PendingDropSource source = iterator.next();
            if (source.kind != PendingDropKind.BLOCK_BREAK
                    || source.world != world
                    || !Objects.equals(source.playerId, player.getUuid())
                    || source.originBlockPos == null
                    || !source.originBlockPos.equals(pos)) {
                continue;
            }

            for (Map.Entry<Item, Integer> entry : source.remainingCounts.entrySet()) {
                int count = entry.getValue();
                if (count <= 0) {
                    continue;
                }
                DailyQuestService.onTrackedItemPickup(world, player, new ItemStack(entry.getKey(), count), count);
            }
            iterator.remove();
            return;
        }
    }

    public static void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack stack) {
        cleanupExpiredSources();
        if (!(entity instanceof SheepEntity sheep) || stack == null || !stack.isOf(Items.SHEARS) || !sheep.isShearable()) {
            return;
        }
        removePendingShear(world, sheep.getUuid());
        PENDING_SHEARS.add(PendingShearSource.create(world, player.getUuid(), sheep.getUuid()));
    }

    public static void onKilledOtherEntity(ServerWorld world, ServerPlayerEntity player, LivingEntity killedEntity) {
        cleanupExpiredSources();
        if (killedEntity instanceof EndermanEntity) {
            PENDING_SOURCES.add(PendingDropSource.entityDrop(
                    world,
                    player.getUuid(),
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    ENTITY_DROP_RADIUS_SQUARED,
                    Map.of(Items.ENDER_PEARL, MAX_MOB_DROP)
            ));
        } else if (killedEntity instanceof BlazeEntity) {
            PENDING_SOURCES.add(PendingDropSource.entityDrop(
                    world,
                    player.getUuid(),
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    ENTITY_DROP_RADIUS_SQUARED,
                    Map.of(Items.BLAZE_ROD, MAX_MOB_DROP)
            ));
        }
    }

    public static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        cleanupExpiredSources();
        PendingDropSource bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (PendingDropSource source : PENDING_SOURCES) {
            if (source.kind != PendingDropKind.ENTITY_DROP || !source.matches(world, itemEntity)) {
                continue;
            }

            double distance = itemEntity.squaredDistanceTo(source.originX, source.originY, source.originZ);
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

    public static void onTrackedItemPickup(ServerPlayerEntity player, ItemEntity itemEntity, int count) {
        if (player == null || itemEntity == null || count <= 0) {
            return;
        }

        TrackedQuestDrop trackedDrop = (TrackedQuestDrop) itemEntity;
        UUID trackedPlayerId = trackedDrop.villageQuest$getTrackedPlayerId();
        if (!Objects.equals(trackedPlayerId, player.getUuid())
                && !tryTrackAtPickup((ServerWorld) player.getEntityWorld(), player.getUuid(), itemEntity)) {
            return;
        }

        ItemStack pickedUpStack = itemEntity.getStack().copy();
        pickedUpStack.setCount(count);
        DailyQuestService.onTrackedItemPickup((ServerWorld) player.getEntityWorld(), player, pickedUpStack, count);
    }

    public static void onShearedDrop(ServerWorld world, SheepEntity sheep, ItemStack stack) {
        if (world == null || sheep == null || stack == null || stack.isEmpty() || !isWool(stack.getItem())) {
            return;
        }

        cleanupExpiredShears();
        PendingShearSource match = null;
        for (PendingShearSource source : PENDING_SHEARS) {
            if (source.matches(world, sheep.getUuid())) {
                match = source;
                break;
            }
        }
        if (match == null) {
            return;
        }

        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(match.playerId);
        if (player == null) {
            return;
        }
        DailyQuestService.onTrackedItemPickup(world, player, stack.copy(), stack.getCount());
    }

    public static void onShearedFinished(ServerWorld world, SheepEntity sheep) {
        if (world == null || sheep == null) {
            return;
        }
        removePendingShear(world, sheep.getUuid());
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

    private static void matchNearbyEntityDrops() {
        Iterator<PendingDropSource> iterator = PENDING_SOURCES.iterator();
        while (iterator.hasNext()) {
            PendingDropSource source = iterator.next();
            if (source.kind != PendingDropKind.ENTITY_DROP || source.world == null || source.isExpired()) {
                continue;
            }

            Box searchBox = new Box(source.originX, source.originY, source.originZ, source.originX, source.originY, source.originZ)
                    .expand(Math.sqrt(source.radiusSquared));
            List<ItemEntity> nearbyItems = source.world.getEntitiesByClass(
                    ItemEntity.class,
                    searchBox,
                    itemEntity -> itemEntity != null
                            && !itemEntity.isRemoved()
                            && itemEntity.getItemAge() <= DROP_SOURCE_EXPIRE_TICKS
            );
            nearbyItems.sort(Comparator.comparingDouble(
                    itemEntity -> itemEntity.squaredDistanceTo(source.originX, source.originY, source.originZ)
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

    private static boolean tryTrackAtPickup(ServerWorld world, UUID playerId, ItemEntity itemEntity) {
        if (world == null || playerId == null || itemEntity == null || itemEntity.getItemAge() > DROP_SOURCE_EXPIRE_TICKS) {
            return false;
        }

        cleanupExpiredSources();
        PendingDropSource bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (PendingDropSource source : PENDING_SOURCES) {
            if (source.kind != PendingDropKind.ENTITY_DROP
                    || source.world != world
                    || !Objects.equals(source.playerId, playerId)
                    || !source.matches(world, itemEntity)) {
                continue;
            }

            double distance = itemEntity.squaredDistanceTo(source.originX, source.originY, source.originZ);
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

    private static void removeBlockBreakSource(ServerWorld world, UUID playerId, BlockPos pos) {
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

    private static void removePendingShear(ServerWorld world, UUID sheepId) {
        Iterator<PendingShearSource> iterator = PENDING_SHEARS.iterator();
        while (iterator.hasNext()) {
            PendingShearSource source = iterator.next();
            if (source.world == world && Objects.equals(source.sheepId, sheepId)) {
                iterator.remove();
            }
        }
    }

    private static Map<Item, Integer> getTrackedBlockDrops(ServerWorld world,
                                                           ServerPlayerEntity player,
                                                           BlockPos pos,
                                                           BlockState state,
                                                           BlockEntity blockEntity) {
        if (world == null || player == null || pos == null || state == null) {
            return Map.of();
        }

        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, blockEntity, player, player.getMainHandStack());
        if (drops.isEmpty()) {
            return Map.of();
        }

        Map<Item, Integer> trackedDrops = new HashMap<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            Item item = drop.getItem();
            if (isTrackedBlockDrop(item)) {
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
                || item == Items.AMETHYST_SHARD;
    }

    private static boolean isWool(Item item) {
        return item == Items.WHITE_WOOL
                || item == Items.ORANGE_WOOL
                || item == Items.MAGENTA_WOOL
                || item == Items.LIGHT_BLUE_WOOL
                || item == Items.YELLOW_WOOL
                || item == Items.LIME_WOOL
                || item == Items.PINK_WOOL
                || item == Items.GRAY_WOOL
                || item == Items.LIGHT_GRAY_WOOL
                || item == Items.CYAN_WOOL
                || item == Items.PURPLE_WOOL
                || item == Items.BLUE_WOOL
                || item == Items.BROWN_WOOL
                || item == Items.GREEN_WOOL
                || item == Items.RED_WOOL
                || item == Items.BLACK_WOOL;
    }

    private static Item woolItemFor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }

    private enum PendingDropKind {
        BLOCK_BREAK,
        ENTITY_DROP
    }

    private static final class PendingShearSource {
        private final ServerWorld world;
        private final UUID playerId;
        private final UUID sheepId;
        private final long expiresAtTick;

        private PendingShearSource(ServerWorld world, UUID playerId, UUID sheepId, long expiresAtTick) {
            this.world = world;
            this.playerId = playerId;
            this.sheepId = sheepId;
            this.expiresAtTick = expiresAtTick;
        }

        private static PendingShearSource create(ServerWorld world, UUID playerId, UUID sheepId) {
            return new PendingShearSource(world, playerId, sheepId, world.getTime() + DROP_SOURCE_EXPIRE_TICKS);
        }

        private boolean matches(ServerWorld world, UUID sheepId) {
            return this.world == world && Objects.equals(this.sheepId, sheepId) && !isExpired();
        }

        private boolean isExpired() {
            return world == null || world.getTime() > expiresAtTick;
        }
    }

    private static final class PendingDropSource {
        private final PendingDropKind kind;
        private final ServerWorld world;
        private final UUID playerId;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final double radiusSquared;
        private final long expiresAtTick;
        private final BlockPos originBlockPos;
        private final Map<Item, Integer> remainingCounts;

        private PendingDropSource(PendingDropKind kind,
                                  ServerWorld world,
                                  UUID playerId,
                                  double originX,
                                  double originY,
                                  double originZ,
                                  double radiusSquared,
                                  long expiresAtTick,
                                  BlockPos originBlockPos,
                                  Map<Item, Integer> remainingCounts) {
            this.kind = kind;
            this.world = world;
            this.playerId = playerId;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.radiusSquared = radiusSquared;
            this.expiresAtTick = expiresAtTick;
            this.originBlockPos = originBlockPos;
            this.remainingCounts = new HashMap<>(remainingCounts);
        }

        private static PendingDropSource blockBreak(ServerWorld world, UUID playerId, BlockPos pos, Map<Item, Integer> remainingCounts) {
            return new PendingDropSource(
                    PendingDropKind.BLOCK_BREAK,
                    world,
                    playerId,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    BLOCK_BREAK_RADIUS_SQUARED,
                    world.getTime() + DROP_SOURCE_EXPIRE_TICKS,
                    pos.toImmutable(),
                    remainingCounts
            );
        }

        private static PendingDropSource entityDrop(ServerWorld world,
                                                    UUID playerId,
                                                    double originX,
                                                    double originY,
                                                    double originZ,
                                                    double radiusSquared,
                                                    Map<Item, Integer> remainingCounts) {
            return new PendingDropSource(
                    PendingDropKind.ENTITY_DROP,
                    world,
                    playerId,
                    originX,
                    originY,
                    originZ,
                    radiusSquared,
                    world.getTime() + DROP_SOURCE_EXPIRE_TICKS,
                    null,
                    remainingCounts
            );
        }

        private boolean matches(ServerWorld world, ItemEntity itemEntity) {
            if (this.world != world || isExpired() || itemEntity == null) {
                return false;
            }

            Item item = itemEntity.getStack().getItem();
            int remaining = remainingCounts.getOrDefault(item, 0);
            return remaining > 0 && itemEntity.squaredDistanceTo(originX, originY, originZ) <= radiusSquared;
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
            if (!matches(world, itemEntity) || itemEntity.getItemAge() > DROP_SOURCE_EXPIRE_TICKS) {
                return false;
            }

            TrackedQuestDrop trackedDrop = (TrackedQuestDrop) itemEntity;
            if (trackedDrop.villageQuest$getTrackedPlayerId() != null) {
                return false;
            }

            trackedDrop.villageQuest$setTrackedPlayerId(playerId);
            consume(itemEntity.getStack().getItem(), itemEntity.getStack().getCount());
            return true;
        }

        private boolean isExpired() {
            return world == null || world.getTime() > expiresAtTick;
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
