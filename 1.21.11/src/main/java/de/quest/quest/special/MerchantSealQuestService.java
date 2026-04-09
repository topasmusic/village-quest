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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MerchantSealQuestService {
    public static final int REQUIRED_TRADE_REPUTATION = 200;
    private static final int TRADE_TARGET = 20;
    private static final int EMERALD_TARGET = 128;
    private static final int VILLAGER_PURCHASE_TARGET = 10;
    private static final int PILGRIM_PURCHASE_TARGET = 2;
    private static final ConcurrentMap<UUID, Long> LAST_PILGRIM_SEAL_USE_TICK = new ConcurrentHashMap<>();

    private MerchantSealQuestService() {}

    private static long currentDay() {
        return TimeUtil.currentDay();
    }

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    private static void markDirty(ServerWorld world) {
        if (world != null) {
            QuestState.get(world.getServer()).markDirty();
        }
    }

    private static void refreshQuestUi(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestTrackerService.refresh(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static boolean handleQuestMasterInteraction(ServerWorld world, ServerPlayerEntity player, boolean skipOffer) {
        if (world == null || player == null) {
            return false;
        }

        PlayerQuestData data = data(world, player.getUuid());
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

    public static boolean acceptQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null || ModItems.MERCHANT_SEAL == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(null);
        if (!shouldOfferQuest(world, player, data)) {
            markDirty(world);
            return false;
        }

        data.resetMerchantSealQuest();
        data.setMerchantSealQuestStage(RelicQuestStage.ACTIVE);
        markDirty(world);
        player.sendMessage(Texts.acceptedTitle(title(), Formatting.GOLD), false);
        QuestTrackerService.enableForAcceptedQuest(world, player);
        showProgress(player, data);
        refreshQuestUi(world, player);
        return true;
    }

    public static boolean isActive(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        RelicQuestStage stage = data(world, playerId).getMerchantSealQuestStage();
        return stage == RelicQuestStage.ACTIVE || stage == RelicQuestStage.READY;
    }

    public static boolean isCompleted(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).getMerchantSealQuestStage() == RelicQuestStage.COMPLETED;
    }

    public static boolean isDiscovered(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return RelicQuestProgressionService.hasStoryRequirement(world, playerId, SpecialQuestKind.MERCHANT_SEAL)
                || data.getPendingSpecialOfferKind() == SpecialQuestKind.MERCHANT_SEAL
                || data.getMerchantSealQuestStage() != RelicQuestStage.NONE;
    }

    public static SpecialQuestStatus openStatus(ServerWorld world, UUID playerId) {
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

    public static boolean claimFromQuestMaster(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.READY) {
            return false;
        }
        completeQuest(world, player, data);
        return true;
    }

    public static void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (world == null || player == null) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }

        int beforeTrades = data.getMerchantSealTradeProgress();
        int beforeEmeralds = data.getMerchantSealEmeraldProgress();
        int beforeVillagerPurchases = data.getMerchantSealVillagerPurchaseProgress();
        int beforePilgrim = data.getMerchantSealPilgrimPurchaseProgress();
        data.setMerchantSealTradeProgress(Math.min(TRADE_TARGET, beforeTrades + 1));
        if (stack != null && stack.isOf(Items.EMERALD)) {
            data.setMerchantSealEmeraldProgress(Math.min(EMERALD_TARGET, beforeEmeralds + stack.getCount()));
        } else if (stack != null && !stack.isEmpty()) {
            data.setMerchantSealVillagerPurchaseProgress(Math.min(VILLAGER_PURCHASE_TARGET, beforeVillagerPurchases + 1));
        }
        updateProgress(world, player, data, beforeTrades, beforeEmeralds, beforeVillagerPurchases, beforePilgrim);
    }

    public static void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {
        if (world == null || player == null || offerId == null || offerId.isBlank()) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.getMerchantSealQuestStage() != RelicQuestStage.ACTIVE) {
            return;
        }
        int beforeTrades = data.getMerchantSealTradeProgress();
        int beforeEmeralds = data.getMerchantSealEmeraldProgress();
        int beforeVillagerPurchases = data.getMerchantSealVillagerPurchaseProgress();
        int beforePilgrim = data.getMerchantSealPilgrimPurchaseProgress();
        data.setMerchantSealPilgrimPurchaseProgress(Math.min(PILGRIM_PURCHASE_TARGET, beforePilgrim + 1));
        updateProgress(world, player, data, beforeTrades, beforeEmeralds, beforeVillagerPurchases, beforePilgrim);
    }

    public static ActionResult tryUseOnPilgrim(ServerWorld world, ServerPlayerEntity player, PilgrimEntity pilgrim, ItemStack stack) {
        if (world == null || player == null || pilgrim == null || stack == null || ModItems.MERCHANT_SEAL == null) {
            return ActionResult.PASS;
        }
        if (!stack.isOf(ModItems.MERCHANT_SEAL) || !player.isSneaking()) {
            return ActionResult.PASS;
        }

        ActionResult precheck = beginSealUse(world, player, stack);
        if (precheck != null) {
            return precheck;
        }
        if (pilgrim.hasCustomer() && !pilgrim.isCustomer(player)) {
            player.sendMessage(Text.translatable("message.village-quest.special.merchant.target_busy").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        List<String> currentOffers = pilgrim.getOfferIds();
        List<String> rerolledOffers = currentOffers;
        for (int attempt = 0; attempt < 6; attempt++) {
            rerolledOffers = ShopService.rollPilgrimOfferIds(world.random, world, player.getUuid(), PilgrimEntity.DEFAULT_OFFER_COUNT);
            if (!Objects.equals(currentOffers, rerolledOffers)) {
                break;
            }
        }

        if (pilgrim.hasCustomer()) {
            ServerPlayerEntity customer = pilgrim.getCustomer();
            if (customer != null) {
                PilgrimService.closeTrade(customer);
            }
            pilgrim.clearCustomer();
        }

        pilgrim.setOfferIds(rerolledOffers);
        boolean rerolledContract = PilgrimContractService.rerollOffer(world, player);
        return finalizeSealUse(world, player, rerolledContract);
    }

    public static ActionResult tryUseOnWanderingTrader(ServerWorld world, ServerPlayerEntity player, WanderingTraderEntity trader, ItemStack stack) {
        if (world == null || player == null || trader == null || stack == null || ModItems.MERCHANT_SEAL == null) {
            return ActionResult.PASS;
        }
        if (!stack.isOf(ModItems.MERCHANT_SEAL) || !player.isSneaking()) {
            return ActionResult.PASS;
        }

        ActionResult precheck = beginSealUse(world, player, stack);
        if (precheck != null) {
            return precheck;
        }
        if (trader.hasCustomer() && trader.getCustomer() != player) {
            player.sendMessage(Text.translatable("message.village-quest.special.merchant.target_busy").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        ((MerchantEntityAccessor) trader).villageQuest$setOffers(null);
        ((WanderingTraderEntityInvoker) trader).villageQuest$invokeFillRecipes(world);
        return finalizeSealUse(world, player, false);
    }

    private static boolean shouldOfferQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        return data.getMerchantSealQuestStage() == RelicQuestStage.NONE
                && !data.isPendingSpecialOffer()
                && ModItems.MERCHANT_SEAL != null
                && DailyQuestService.openQuestStatus(world, player.getUuid()) == null
                && RelicQuestProgressionService.isUnlocked(world, player.getUuid(), SpecialQuestKind.MERCHANT_SEAL);
    }

    private static void showOffer(ServerWorld world, ServerPlayerEntity player) {
        PlayerQuestData data = data(world, player.getUuid());
        data.setPendingSpecialOfferKind(SpecialQuestKind.MERCHANT_SEAL);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        Text accept = Text.translatable("text.village-quest.special.merchant.offer.accept").styled(style -> style
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/vq daily accept")));

        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.dailyTitle(title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.merchant.offer.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.merchant.offer.2")).append(Text.literal("\n\n\n"))
                .append(accept).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
    }

    private static void showProgress(ServerPlayerEntity player, PlayerQuestData data) {
        player.sendMessage(Texts.dailyTitle(title(), Formatting.GOLD), false);
        for (Text line : progressLines(data)) {
            player.sendMessage(line, false);
        }
    }

    private static List<Text> progressLines(PlayerQuestData data) {
        return List.of(
                Text.translatable("quest.village-quest.special.merchant.progress.trades", data.getMerchantSealTradeProgress(), TRADE_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.merchant.progress.emeralds", data.getMerchantSealEmeraldProgress(), EMERALD_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.merchant.progress.villager_purchases", data.getMerchantSealVillagerPurchaseProgress(), VILLAGER_PURCHASE_TARGET).formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.special.merchant.progress.pilgrim", data.getMerchantSealPilgrimPurchaseProgress(), PILGRIM_PURCHASE_TARGET).formatted(Formatting.GRAY)
        );
    }

    private static boolean isComplete(PlayerQuestData data) {
        return data.getMerchantSealTradeProgress() >= TRADE_TARGET
                && data.getMerchantSealEmeraldProgress() >= EMERALD_TARGET
                && data.getMerchantSealVillagerPurchaseProgress() >= VILLAGER_PURCHASE_TARGET
                && data.getMerchantSealPilgrimPurchaseProgress() >= PILGRIM_PURCHASE_TARGET;
    }

    private static void updateProgress(ServerWorld world,
                                       ServerPlayerEntity player,
                                       PlayerQuestData data,
                                       int beforeTrades,
                                       int beforeEmeralds,
                                       int beforeVillagerPurchases,
                                       int beforePilgrim) {
        Text actionbar = null;
        boolean completedStep = false;
        if (beforeTrades != data.getMerchantSealTradeProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.merchant.progress.trades", data.getMerchantSealTradeProgress(), TRADE_TARGET).formatted(Formatting.GOLD);
            if (beforeTrades < TRADE_TARGET && data.getMerchantSealTradeProgress() >= TRADE_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeEmeralds != data.getMerchantSealEmeraldProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.merchant.progress.emeralds", data.getMerchantSealEmeraldProgress(), EMERALD_TARGET).formatted(Formatting.GOLD);
            if (beforeEmeralds < EMERALD_TARGET && data.getMerchantSealEmeraldProgress() >= EMERALD_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforeVillagerPurchases != data.getMerchantSealVillagerPurchaseProgress()) {
            actionbar = Text.translatable(
                    "quest.village-quest.special.merchant.progress.villager_purchases",
                    data.getMerchantSealVillagerPurchaseProgress(),
                    VILLAGER_PURCHASE_TARGET
            ).formatted(Formatting.GOLD);
            if (beforeVillagerPurchases < VILLAGER_PURCHASE_TARGET
                    && data.getMerchantSealVillagerPurchaseProgress() >= VILLAGER_PURCHASE_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }
        if (beforePilgrim != data.getMerchantSealPilgrimPurchaseProgress()) {
            actionbar = Text.translatable("quest.village-quest.special.merchant.progress.pilgrim", data.getMerchantSealPilgrimPurchaseProgress(), PILGRIM_PURCHASE_TARGET).formatted(Formatting.GOLD);
            if (beforePilgrim < PILGRIM_PURCHASE_TARGET && data.getMerchantSealPilgrimPurchaseProgress() >= PILGRIM_PURCHASE_TARGET) {
                player.sendMessage(Text.translatable("message.village-quest.quest.progress.step_complete", actionbar.copy()).formatted(Formatting.GOLD), false);
                completedStep = true;
            }
        }

        if (data.getMerchantSealQuestStage() == RelicQuestStage.ACTIVE && isComplete(data)) {
            data.setMerchantSealQuestStage(RelicQuestStage.READY);
            player.sendMessage(Text.translatable("message.village-quest.special.merchant.ready").formatted(Formatting.GOLD), false);
            completedStep = true;
        }

        markDirty(world);
        if (actionbar != null) {
            player.sendMessage(actionbar, true);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, completedStep ? 0.28f : 0.18f, completedStep ? 1.7f : 1.35f);
        refreshQuestUi(world, player);
    }

    private static void completeQuest(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        giveOrDrop(player, new ItemStack(ModItems.MERCHANT_SEAL));
        data.setPendingSpecialOfferKind(null);
        data.setMerchantSealQuestStage(RelicQuestStage.COMPLETED);
        data.setMerchantSealTradeProgress(TRADE_TARGET);
        data.setMerchantSealEmeraldProgress(EMERALD_TARGET);
        data.setMerchantSealVillagerPurchaseProgress(VILLAGER_PURCHASE_TARGET);
        data.setMerchantSealPilgrimPurchaseProgress(PILGRIM_PURCHASE_TARGET);
        markDirty(world);

        Text divider = Text.literal("------------------------------").formatted(Formatting.GRAY);
        MutableText body = Text.empty()
                .append(divider.copy()).append(Text.literal("\n"))
                .append(Texts.completedTitle(title(), Formatting.GOLD)).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.merchant.completed.1")).append(Text.literal("\n\n"))
                .append(Text.translatable("quest.village-quest.special.merchant.completed.2")).append(Text.literal("\n"))
                .append(divider.copy());
        player.sendMessage(body, false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.05f);
        refreshQuestUi(world, player);
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().insertStack(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.dropItem(remainder, false);
            }
            player.sendMessage(Text.translatable("message.village-quest.daily.inventory_full.prefix").formatted(Formatting.GRAY)
                    .append(stack.toHoverableText())
                    .append(Text.translatable("message.village-quest.daily.inventory_full.suffix").formatted(Formatting.GRAY)), false);
        } else {
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private static Text title() {
        return Text.translatable("quest.village-quest.special.merchant.title");
    }

    private static ActionResult beginSealUse(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        PlayerQuestData data = data(world, player.getUuid());
        Long previousTick = LAST_PILGRIM_SEAL_USE_TICK.put(player.getUuid(), world.getTime());
        if (previousTick != null && previousTick == world.getTime()) {
            return ActionResult.SUCCESS;
        }
        if (data.getMerchantSealLastUseDay() == currentDay()) {
            player.sendMessage(Text.translatable("message.village-quest.special.merchant.used_today").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }
        return null;
    }

    private static ActionResult finalizeSealUse(ServerWorld world, ServerPlayerEntity player, boolean rerolledContract) {
        PlayerQuestData data = data(world, player.getUuid());
        data.setMerchantSealLastUseDay(currentDay());
        markDirty(world);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.PLAYERS, 0.9f, 1.1f);
        player.sendMessage(Text.translatable(
                rerolledContract
                        ? "message.village-quest.special.merchant.rerolled_rumor"
                        : "message.village-quest.special.merchant.rerolled"
        ).formatted(Formatting.GOLD), false);
        return ActionResult.SUCCESS;
    }
}
