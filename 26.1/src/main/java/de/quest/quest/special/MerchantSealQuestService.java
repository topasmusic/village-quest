package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.entity.PilgrimEntity;
import de.quest.mixin.MerchantEntityAccessor;
import de.quest.mixin.WanderingTraderEntityInvoker;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimService;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.registry.ModItems;
import de.quest.shop.ShopService;
import de.quest.util.Texts;
import de.quest.util.TimeUtil;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MerchantSealQuestService {
    public static final int REQUIRED_TRADE_REPUTATION = 200;
    private static final int TRADE_TARGET = 5;
    private static final int EMERALD_TARGET = 8;
    private static final int PILGRIM_PURCHASE_TARGET = 1;
    private static final ConcurrentMap<UUID, Long> LAST_PILGRIM_SEAL_USE_TICK = new ConcurrentHashMap<>();

    private MerchantSealQuestService() {}

    private static long currentDay() {
        return TimeUtil.currentDay();
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerLevel world) {
        if (world != null) {
            QuestState.get(world.getServer()).setDirty();
        }
    }

    private static void refreshQuestUi(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUUID());
        return switch (data.getMerchantSealQuestStage()) {
            case ACTIVE -> {
                showProgress(player, data);
                yield true;
            }
            case READY -> {
                completeQuest(world, player, data);
                yield true;
            }
            case COMPLETED -> false;
            case NONE -> {
                if (!skipOffer && shouldOfferQuest(world, player, data)) {
                    showOffer(world, player);
                    yield true;
                }
                yield false;
            }
        };
    }

    public static boolean acceptQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null || ModItems.MERCHANT_SEAL == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetMerchantSealQuest();
        data.setMerchantSealQuestStage(RelicQuestStage.ACTIVE);
        markDirty(world);
        player.sendSystemMessage(Texts.acceptedTitle(title(), ChatFormatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getMerchantSealQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getMerchantSealQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.MERCHANT_SEAL)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.MERCHANT_SEAL
                || data.getMerchantSealQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return null;
        }
        PlayerQuestData data = data(world, playerId);
        RelicQuestStage stage = data.getMerchantSealQuestStage();
        if (stage != RelicQuestStage.ACTIVE && stage != RelicQuestStage.READY) {
            return null;
        }
        return new SpecialQuestStatus(title(), progressLines(data));
    }

    public static boolean claimFromQuestMaster(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        completeQuest(world, player, data);
        return true;
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeTrades = data.getMerchantSealTradeProgress();
        int beforeEmeralds = data.getMerchantSealEmeraldProgress();
        int beforePilgrim = data.getMerchantSealPilgrimPurchaseProgress();
        data.setMerchantSealTradeProgress(Math.min(TRADE_TARGET, beforeTrades + 1));
        if (stack != null && stack.is(Items.EMERALD)) {
            data.setMerchantSealEmeraldProgress(Math.min(EMERALD_TARGET, beforeEmeralds + stack.getCount()));
        }
        updateProgress(world, player, data, beforeTrades, beforeEmeralds, beforePilgrim);
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        if (world == null || player == null || offerId == null || offerId.isBlank()) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }
        int beforeTrades = data.getMerchantSealTradeProgress();
        int beforeEmeralds = data.getMerchantSealEmeraldProgress();
        int beforePilgrim = data.getMerchantSealPilgrimPurchaseProgress();
        data.setMerchantSealPilgrimPurchaseProgress(Math.min(PILGRIM_PURCHASE_TARGET, beforePilgrim + 1));
        updateProgress(world, player, data, beforeTrades, beforeEmeralds, beforePilgrim);
    }

    public static InteractionResult tryUseOnPilgrim(ServerLevel world, ServerPlayer player, PilgrimEntity pilgrim, ItemStack stack) {
        if (world == null || player == null || pilgrim == null || stack == null || ModItems.MERCHANT_SEAL == null) {
            return InteractionResult.PASS;
        }
        if (!stack.is(ModItems.MERCHANT_SEAL) || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        InteractionResult precheck = beginSealUse(world, player, stack);
        if (precheck != null) {
            return precheck;
        }
        if (pilgrim.hasCustomer() && !pilgrim.isCustomer(player)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.merchant.target_busy").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        List<String> currentOffers = pilgrim.getOfferIds();
        List<String> rerolledOffers = currentOffers;
        for (int attempt = 0; attempt < 6; attempt++) {
            rerolledOffers = ShopService.rollPilgrimOfferIds(world.getRandom(), world, player.getUUID(), PilgrimEntity.DEFAULT_OFFER_COUNT);
            if (!Objects.equals(currentOffers, rerolledOffers)) {
                break;
            }
        }

        if (pilgrim.hasCustomer()) {
            ServerPlayer customer = pilgrim.getCustomer();
            if (customer != null) {
                PilgrimService.closeTrade(customer);
            }
            pilgrim.clearCustomer();
        }

        pilgrim.setOfferIds(rerolledOffers);
        boolean rerolledContract = PilgrimContractService.rerollOffer(world, player);
        return finalizeSealUse(world, player, rerolledContract);
    }

    public static InteractionResult tryUseOnWanderingTrader(ServerLevel world, ServerPlayer player, WanderingTrader trader, ItemStack stack) {
        if (world == null || player == null || trader == null || stack == null || ModItems.MERCHANT_SEAL == null) {
            return InteractionResult.PASS;
        }
        if (!stack.is(ModItems.MERCHANT_SEAL) || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        InteractionResult precheck = beginSealUse(world, player, stack);
        if (precheck != null) {
            return precheck;
        }
        if (trader.isTrading() && trader.getTradingPlayer() != player) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.merchant.target_busy").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }

        ((MerchantEntityAccessor) trader).villageQuest$setOffers(null);
        ((WanderingTraderEntityInvoker) trader).villageQuest$invokeFillRecipes(world);
        return finalizeSealUse(world, player, false);
    }

    private static boolean shouldOfferQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        return data.getMerchantSealQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.MERCHANT_SEAL != null
                && DailyQuestService.openQuestStatus(world, player.getUUID()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUUID(), SpecialQuestKind.MERCHANT_SEAL);
    }

    private static void showOffer(ServerLevel world, ServerPlayer player) {
        PlayerQuestData data = data(world, player.getUUID());
        data.setPendingSpecialOfferKind(SpecialQuestKind.MERCHANT_SEAL);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        Component accept = Component.translatable("text.village-quest.special.merchant.offer.accept").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/dailyquest accept")));

        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.dailyTitle(title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.merchant.offer.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.merchant.offer.2")).append(Component.literal("\n\n\n"))
                .append(accept).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
    }

    private static void showProgress(ServerPlayer player, PlayerQuestData data) {
        player.sendSystemMessage(Texts.dailyTitle(title(), ChatFormatting.GOLD), false);
        for (Component line : progressLines(data)) {
            player.sendSystemMessage(line, false);
        }
    }

    private static List<Component> progressLines(PlayerQuestData data) {
        return List.of(
                Component.translatable("quest.village-quest.special.merchant.progress.trades", data.getMerchantSealTradeProgress(), TRADE_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.merchant.progress.emeralds", data.getMerchantSealEmeraldProgress(), EMERALD_TARGET).withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.special.merchant.progress.pilgrim", data.getMerchantSealPilgrimPurchaseProgress(), PILGRIM_PURCHASE_TARGET).withStyle(ChatFormatting.GRAY)
        );
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getMerchantSealTradeProgress() >= TRADE_TARGET
                && data.getMerchantSealEmeraldProgress() >= EMERALD_TARGET
                && data.getMerchantSealPilgrimPurchaseProgress() >= PILGRIM_PURCHASE_TARGET;
    }

    private static void updateProgress(ServerLevel world, ServerPlayer player, PlayerQuestData data, int beforeTrades, int beforeEmeralds, int beforePilgrim) {
        Component actionbar = null;
        boolean completedStep = false;
        if (beforeTrades != data.getMerchantSealTradeProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.merchant.progress.trades", data.getMerchantSealTradeProgress(), TRADE_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeTrades < TRADE_TARGET && data.getMerchantSealTradeProgress() >= TRADE_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeEmeralds != data.getMerchantSealEmeraldProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.merchant.progress.emeralds", data.getMerchantSealEmeraldProgress(), EMERALD_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforeEmeralds < EMERALD_TARGET && data.getMerchantSealEmeraldProgress() >= EMERALD_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforePilgrim != data.getMerchantSealPilgrimPurchaseProgress()) {
            actionbar = Component.translatable("quest.village-quest.special.merchant.progress.pilgrim", data.getMerchantSealPilgrimPurchaseProgress(), PILGRIM_PURCHASE_TARGET).withStyle(ChatFormatting.GOLD);
            if (beforePilgrim < PILGRIM_PURCHASE_TARGET && data.getMerchantSealPilgrimPurchaseProgress() >= PILGRIM_PURCHASE_TARGET) {
                player.sendSystemMessage(Component.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).withStyle(ChatFormatting.GOLD), false);
                completedStep = true;
            }
        }

        if (data.getMerchantSealQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setMerchantSealQuestStage(RelicQuestStage.READY);
            player.sendSystemMessage(Component.translatable("message.village-quest.special.merchant.ready").withStyle(ChatFormatting.GOLD), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendSystemMessage(actionbar, true);
        }
        world.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static void completeQuest(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.MERCHANT_SEAL));
        data.setPendingSpecialOfferKind(null);
        data.setMerchantSealQuestStage(RelicQuestStage.COMPLETED);
        data.setMerchantSealTradeProgress(TRADE_TARGET);
        data.setMerchantSealEmeraldProgress(EMERALD_TARGET);
        data.setMerchantSealPilgrimPurchaseProgress(PILGRIM_PURCHASE_TARGET);
        markDirty(world);

        Component divider = Component.literal("------------------------------").withStyle(ChatFormatting.GRAY);
        MutableComponent body = Component.empty()
                .append(divider.copy()).append(Component.literal("\n"))
                .append(Texts.completedTitle(title(), ChatFormatting.GOLD)).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.merchant.completed.1")).append(Component.literal("\n\n"))
                .append(Component.translatable("quest.village-quest.special.merchant.completed.2")).append(Component.literal("\n"))
                .append(divider.copy());
        player.sendSystemMessage(body, false);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.05f);
        refreshQuestUi(world, player);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static Component title() {
        return Component.translatable("quest.village-quest.special.merchant.title");
    }

    private static InteractionResult beginSealUse(ServerLevel world, ServerPlayer player, ItemStack stack) {
        PlayerQuestData data = data(world, player.getUUID());
        Long previousTick = LAST_PILGRIM_SEAL_USE_TICK.put(player.getUUID(), world.getGameTime());
        if (previousTick != null && previousTick == world.getGameTime()) {
            return InteractionResult.SUCCESS;
        }
        if (data.getMerchantSealLastUseDay() == currentDay()) {
            player.sendSystemMessage(Component.translatable("message.village-quest.special.merchant.used_today").withStyle(ChatFormatting.RED), false);
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    private static InteractionResult finalizeSealUse(ServerLevel world, ServerPlayer player, boolean rerolledContract) {
        PlayerQuestData data = data(world, player.getUUID());
        data.setMerchantSealLastUseDay(currentDay());
        markDirty(world);
        world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.9f, 1.1f);
        player.sendSystemMessage(Component.translatable(
                rerolledContract
                        ? "message.village-quest.special.merchant.rerolled_rumor"
                        : "message.village-quest.special.merchant.rerolled"
        ).withStyle(ChatFormatting.GOLD), false);
        return InteractionResult.SUCCESS;
    }
}
