package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.entity.PilgrimEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class SpecialQuestService {
    public static final int TOTAL_SPECIAL_QUESTS = 5;

    private SpecialQuestService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean hasPendingOffer(ServerWorld world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).getPendingSpecialOfferKind() != null;
    }

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffers) {
        return ShardRelicQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || MerchantSealQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || ShepherdFluteQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || ApiaristSmokerQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || SurveyorCompassQuestService.handleQuestMasterInteraction(world, player, skipOffers);
    }

    public static boolean acceptPendingOffer(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        UUID playerId = player.getUuid();
        if (ShardRelicQuestService.consumePendingSpecialOffer(world, playerId)) {
            return ShardRelicQuestService.acceptSpecialQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.MERCHANT_SEAL) {
            return MerchantSealQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SHEPHERD_FLUTE) {
            return ShepherdFluteQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.APIARIST_SMOKER) {
            return ApiaristSmokerQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SURVEYOR_COMPASS) {
            return SurveyorCompassQuestService.acceptQuest(world, player);
        }
        return false;
    }

    public static int discoveredCount(ServerWorld world, UUID playerId) {
        int count = 0;
        if (ShardRelicQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        return count;
    }

    public static int completedCount(ServerWorld world, UUID playerId) {
        int count = 0;
        if (ShardRelicQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isCompleted(world, playerId)) {
            count++;
        }
        return count;
    }

    public static int activeCount(ServerWorld world, UUID playerId) {
        int count = 0;
        if (AdminCoreTestQuestService.isActive(world, playerId)) {
            count++;
        }
        if (ShardRelicQuestService.isActive(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isActive(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isActive(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isActive(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isActive(world, playerId)) {
            count++;
        }
        return count;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
        SpecialQuestStatus status = AdminCoreTestQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = ShardRelicQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = MerchantSealQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = ShepherdFluteQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = ApiaristSmokerQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        return SurveyorCompassQuestService.openStatus(world, playerId);
    }

    public static void onServerTick(MinecraftServer server) {
        ShardRelicQuestService.onServerTick(server);
        ShepherdFluteQuestService.onServerTick(server);
        SurveyorCompassQuestService.onServerTick(server);
    }

    public static ActionResult onUseBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        AdminCoreTestQuestService.onUseBlock(world, player, pos);
        return ShardRelicQuestService.onUseBlock(world, player, pos);
    }

    public static boolean allowBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        return ShardRelicQuestService.allowBlockBreak(world, player, pos);
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        AdminCoreTestQuestService.onBlockBreak(world, player, pos, state);
        ShardRelicQuestService.onBlockBreak(world, player, pos, state);
        SurveyorCompassQuestService.onBlockBreak(world, player, pos, state);
    }

    public static void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        AdminCoreTestQuestService.onTrackedItemPickup(world, player, stack, count);
        ShardRelicQuestService.onTrackedItemPickup(world, player, stack, count);
        ShepherdFluteQuestService.onTrackedItemPickup(world, player, stack, count);
        SurveyorCompassQuestService.onTrackedItemPickup(world, player, stack, count);
    }

    public static ActionResult onUseEntity(ServerWorld world, ServerPlayerEntity player, Hand hand, Entity entity, ItemStack stack) {
        if (entity instanceof PilgrimEntity pilgrim) {
            ActionResult sealResult = MerchantSealQuestService.tryUseOnPilgrim(world, player, pilgrim, stack);
            if (sealResult != ActionResult.PASS) {
                return sealResult;
            }
        }
        if (entity instanceof WanderingTraderEntity trader) {
            ActionResult sealResult = MerchantSealQuestService.tryUseOnWanderingTrader(world, player, trader, stack);
            if (sealResult != ActionResult.PASS) {
                return sealResult;
            }
        }
        AdminCoreTestQuestService.onEntityUse(world, player, entity, stack);
        return ShepherdFluteQuestService.onEntityUse(world, player, entity, stack);
    }

    public static void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        AdminCoreTestQuestService.onFurnaceOutput(world, player, stack);
    }

    public static void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        AdminCoreTestQuestService.onVillagerTrade(world, player, stack);
        MerchantSealQuestService.onVillagerTrade(world, player, stack);
    }

    public static void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        AdminCoreTestQuestService.onAnimalLove(world, player, animal);
        ShepherdFluteQuestService.onAnimalLove(world, player, animal);
    }

    public static void onBeeNestInteract(ServerWorld world, ServerPlayerEntity player, BlockState state, ItemStack stack) {
        AdminCoreTestQuestService.onBeeNestInteract(world, player, state, stack);
        ApiaristSmokerQuestService.onBeeNestInteract(world, player, state, stack);
    }

    public static void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {
        AdminCoreTestQuestService.onPilgrimPurchase(world, player, offerId);
        MerchantSealQuestService.onPilgrimPurchase(world, player, offerId);
    }

    public static void onAnvilOutput(ServerWorld world,
                                     ServerPlayerEntity player,
                                     ItemStack leftInput,
                                     ItemStack rightInput,
                                     ItemStack output) {
        AdminCoreTestQuestService.onAnvilOutput(world, player, leftInput, rightInput, output);
    }

    public static void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        AdminCoreTestQuestService.onMonsterKill(world, player, killedEntity);
    }
}
