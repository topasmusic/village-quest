package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class VillageTradingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.VILLAGE_TRADING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.trade.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.trade.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.trade.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.trade.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS),
                DailyQuestService.villageTradeTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS),
                DailyQuestService.villageTradeEmeraldTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS) >= DailyQuestService.villageTradeTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS) >= DailyQuestService.villageTradeEmeraldTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.trade.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.trade.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.trade.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        UUID playerId = player.getUuid();
        int currentTrades = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS);
        if (currentTrades < DailyQuestService.villageTradeTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS, Math.min(DailyQuestService.villageTradeTarget(), currentTrades + 1));
        }

        if (stack.isOf(Items.EMERALD)) {
            int currentEmeralds = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS);
            int credit = Math.min(stack.getCount(), DailyQuestService.villageTradeEmeraldTarget() - currentEmeralds);
            if (credit > 0) {
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS, credit);
            }
        }

        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
