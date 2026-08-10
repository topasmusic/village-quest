package de.quest.quest;

import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.StoryQuestService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.CropBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Verifies crop harvests which reset or replace a mature crop without firing the
 * normal Fabric block-break AFTER event. This keeps quest progress compatible
 * with right-click harvest mods while avoiding credit for a harmless click on a
 * ripe crop.
 */
public final class QuestHarvestTracker {
    private static final long HARVEST_VERIFY_TICKS = 2L;
    private static final List<PendingHarvest> PENDING_HARVESTS = new ArrayList<>();

    private QuestHarvestTracker() {}

    public static void clear() {
        PENDING_HARVESTS.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        Iterator<PendingHarvest> iterator = PENDING_HARVESTS.iterator();
        while (iterator.hasNext()) {
            PendingHarvest pending = iterator.next();
            if (pending.isExpired()) {
                iterator.remove();
                continue;
            }
            if (!pending.hasVerifiedHarvestTransition()) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.playerId);
            if (player != null && player.getEntityWorld() == pending.world) {
                dispatchHarvest(pending.world, player, pending.pos, pending.originalState);
                dispatchTrackedCropDrops(pending.world, player, pending.trackedDrops);
            }
            iterator.remove();
        }
    }

    public static void onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        rememberPotentialHarvest(world, player, pos, state, false);
    }

    public static void onBlockBreakStart(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        rememberPotentialHarvest(world, player, pos, state, true);
    }

    public static void onBlockBreakCanceled(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        removePotentialHarvest(world, player, pos);
    }

    public static void onBlockBreakFinished(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        // Vanilla/Fabric AFTER handlers already dispatched this harvest directly.
        removePotentialHarvest(world, player, pos);
    }

    private static void rememberPotentialHarvest(ServerWorld world,
                                                 ServerPlayerEntity player,
                                                 BlockPos pos,
                                                 BlockState state,
                                                 boolean allowReplacement) {
        if (world == null || player == null || pos == null || !isMatureCrop(state)) {
            return;
        }

        PendingHarvest existing = findPotentialHarvest(world, player.getUuid(), pos);
        if (existing != null) {
            existing.allowReplacement |= allowReplacement;
            existing.expiresAtTick = Math.max(existing.expiresAtTick, world.getTime() + HARVEST_VERIFY_TICKS);
            return;
        }

        PENDING_HARVESTS.add(new PendingHarvest(
                world,
                player.getUuid(),
                pos.toImmutable(),
                state,
                state.get(CropBlock.AGE),
                allowReplacement,
                trackedCropDrops(world, player, pos, state),
                world.getTime() + HARVEST_VERIFY_TICKS
        ));
    }

    private static boolean isMatureCrop(BlockState state) {
        return state != null
                && state.getBlock() instanceof CropBlock crop
                && state.contains(CropBlock.AGE)
                && state.get(CropBlock.AGE) >= crop.getMaxAge();
    }

    private static PendingHarvest findPotentialHarvest(ServerWorld world, UUID playerId, BlockPos pos) {
        for (PendingHarvest pending : PENDING_HARVESTS) {
            if (pending.matches(world, playerId, pos)) {
                return pending;
            }
        }
        return null;
    }

    private static void removePotentialHarvest(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (world == null || player == null || pos == null) {
            return;
        }
        UUID playerId = player.getUuid();
        PENDING_HARVESTS.removeIf(pending -> pending.matches(world, playerId, pos));
    }

    private static void dispatchHarvest(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        DailyQuestService.onBlockBreak(world, player, pos, state);
        StoryQuestService.onBlockBreak(world, player, pos, state);
        SpecialQuestService.onBlockBreak(world, player, pos, state);
    }

    private static Map<Item, Integer> trackedCropDrops(ServerWorld world,
                                                       ServerPlayerEntity player,
                                                       BlockPos pos,
                                                       BlockState state) {
        Map<Item, Integer> tracked = new HashMap<>();
        for (ItemStack drop : Block.getDroppedStacks(
                state,
                world,
                pos,
                world.getBlockEntity(pos),
                player,
                player.getMainHandStack()
        )) {
            if (drop.isOf(Items.WHEAT) || drop.isOf(Items.POTATO) || drop.isOf(Items.CARROT)) {
                tracked.merge(drop.getItem(), drop.getCount(), Integer::sum);
            }
        }
        return Map.copyOf(tracked);
    }

    private static void dispatchTrackedCropDrops(ServerWorld world,
                                                 ServerPlayerEntity player,
                                                 Map<Item, Integer> trackedDrops) {
        for (Map.Entry<Item, Integer> entry : trackedDrops.entrySet()) {
            if (entry.getValue() > 0) {
                DailyQuestService.onTrackedItemPickup(
                        world,
                        player,
                        new ItemStack(entry.getKey(), entry.getValue()),
                        entry.getValue()
                );
            }
        }
    }

    private static final class PendingHarvest {
        private final ServerWorld world;
        private final UUID playerId;
        private final BlockPos pos;
        private final BlockState originalState;
        private final int originalAge;
        private final Map<Item, Integer> trackedDrops;
        private boolean allowReplacement;
        private long expiresAtTick;

        private PendingHarvest(ServerWorld world,
                               UUID playerId,
                               BlockPos pos,
                               BlockState originalState,
                               int originalAge,
                               boolean allowReplacement,
                               Map<Item, Integer> trackedDrops,
                               long expiresAtTick) {
            this.world = world;
            this.playerId = playerId;
            this.pos = pos;
            this.originalState = originalState;
            this.originalAge = originalAge;
            this.allowReplacement = allowReplacement;
            this.trackedDrops = trackedDrops == null ? Map.of() : trackedDrops;
            this.expiresAtTick = expiresAtTick;
        }

        private boolean matches(ServerWorld world, UUID playerId, BlockPos pos) {
            return this.world == world
                    && Objects.equals(this.playerId, playerId)
                    && this.pos.equals(pos);
        }

        private boolean hasVerifiedHarvestTransition() {
            BlockState currentState = world.getBlockState(pos);
            if (currentState.getBlock() == originalState.getBlock()) {
                return currentState.contains(CropBlock.AGE)
                        && currentState.get(CropBlock.AGE) < originalAge;
            }
            return allowReplacement;
        }

        private boolean isExpired() {
            return world == null || world.getTime() > expiresAtTick;
        }
    }
}
