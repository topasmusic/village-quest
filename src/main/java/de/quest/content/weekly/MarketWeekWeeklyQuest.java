package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class MarketWeekWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.MARKET_WEEK;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.market.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.market.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.market.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.market.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_TRADES),
                        WeeklyQuestService.marketTradeTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS),
                        WeeklyQuestService.marketEmeraldTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.market.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES),
                        WeeklyQuestService.marketPilgrimPurchaseTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_TRADES) >= WeeklyQuestService.marketTradeTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS) >= WeeklyQuestService.marketEmeraldTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES) >= WeeklyQuestService.marketPilgrimPurchaseTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.market.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.market.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.market.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(2, 20),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                1100,
                ReputationService.ReputationTrack.TRADE,
                50
        );
    }

    @Override
    public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.MARKET_TRADES, 1, WeeklyQuestService.marketTradeTarget());
        if (stack.isOf(Items.EMERALD)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.MARKET_EMERALDS, stack.getCount(), WeeklyQuestService.marketEmeraldTarget());
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onPilgrimPurchase(ServerWorld world, ServerPlayerEntity player, String offerId) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES, 1, WeeklyQuestService.marketPilgrimPurchaseTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
