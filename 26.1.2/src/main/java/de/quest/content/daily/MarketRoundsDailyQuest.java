package de.quest.content.daily;

import de.quest.content.story.VillagerDialogueService;
import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

public final class MarketRoundsDailyQuest implements DailyQuestDefinition {
    private static final int VILLAGER_TARGET = 10;
    private static final int TRADE_TARGET = 5;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.MARKET_ROUNDS;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.market_rounds.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.market_rounds.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.market_rounds.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.market_rounds.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS),
                VILLAGER_TARGET,
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES),
                TRADE_TARGET
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS) >= VILLAGER_TARGET
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES) >= TRADE_TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.market_rounds.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.market_rounds.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.market_rounds.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID()) || !DailyQuestService.isAcceptedToday(world, player.getUUID())) {
            return;
        }
        if (!(entity instanceof Villager villager) || villager.isBaby()) {
            return;
        }
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) {
            return;
        }

        UUID playerId = player.getUUID();
        String visitFlag = DailyQuestKeys.MARKET_ROUNDS_VISITED_PREFIX + entity.getStringUUID();
        if (DailyQuestService.hasQuestFlag(world, playerId, visitFlag)) {
            return;
        }

        VillagerDialogueService.sendDialogue(player, villager, VillagerDialogueService.marketRounds(villager));
        DailyQuestService.setQuestFlag(world, playerId, visitFlag, true);
        int visits = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS);
        if (visits < VILLAGER_TARGET) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS, Math.min(VILLAGER_TARGET, visits + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    @Override
    public void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID()) || !DailyQuestService.isAcceptedToday(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        int trades = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES);
        if (trades < TRADE_TARGET) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES, Math.min(TRADE_TARGET, trades + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
