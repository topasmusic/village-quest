package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MarketWeekWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.MARKET_WEEK;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.market.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.market.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.market.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        return List.of(
                Component.translatable(
                        "quest.village-quest.weekly.market.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_TRADES),
                        WeeklyQuestService.marketTradeTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS),
                        WeeklyQuestService.marketEmeraldTarget()
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.weekly.market.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES),
                        WeeklyQuestService.marketPilgrimPurchaseTarget()
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_TRADES) >= WeeklyQuestService.marketTradeTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS) >= WeeklyQuestService.marketEmeraldTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES) >= WeeklyQuestService.marketPilgrimPurchaseTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.market.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.market.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.market.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(2, 20),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                16,
                ReputationService.ReputationTrack.TRADE,
                50
        );
    }

    @Override
    public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.MARKET_TRADES, 1, WeeklyQuestService.marketTradeTarget());
        if (stack.is(Items.EMERALD)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS, stack.getCount(), WeeklyQuestService.marketEmeraldTarget());
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUUID(), WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES, 1, WeeklyQuestService.marketPilgrimPurchaseTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
