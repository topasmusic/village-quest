package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillageTradingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.VILLAGE_TRADING;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.trade.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.trade.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.trade.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.trade.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS),
                DailyQuestService.villageTradeTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS),
                DailyQuestService.villageTradeEmeraldTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS) >= DailyQuestService.villageTradeTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_EMERALD_PROGRESS) >= DailyQuestService.villageTradeEmeraldTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.trade.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.trade.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.trade.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        UUID playerId = player.getUUID();
        int currentTrades = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS);
        if (currentTrades < DailyQuestService.villageTradeTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.TRADE_PROGRESS, Math.min(DailyQuestService.villageTradeTarget(), currentTrades + 1));
        }

        if (stack.is(Items.EMERALD)) {
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
