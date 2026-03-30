package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.VillagerProfession;

import java.util.UUID;

public final class MarketRoundsDailyQuest implements DailyQuestDefinition {
    private static final int VILLAGER_TARGET = 3;
    private static final int TRADE_TARGET = 1;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.MARKET_ROUNDS;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.market_rounds.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.market_rounds.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.market_rounds.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.market_rounds.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS),
                VILLAGER_TARGET,
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES),
                TRADE_TARGET
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS) >= VILLAGER_TARGET
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES) >= TRADE_TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.market_rounds.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.market_rounds.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.market_rounds.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid()) || !DailyQuestService.isAcceptedToday(world, player.getUuid())) {
            return;
        }
        if (!(entity instanceof VillagerEntity villager) || villager.isBaby()) {
            return;
        }
        RegistryEntry<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.matchesKey(VillagerProfession.NONE) || profession.matchesKey(VillagerProfession.NITWIT)) {
            return;
        }

        UUID playerId = player.getUuid();
        String visitFlag = DailyQuestKeys.MARKET_ROUNDS_VISITED_PREFIX + entity.getUuidAsString();
        if (DailyQuestService.hasQuestFlag(world, playerId, visitFlag)) {
            return;
        }

        DailyQuestService.setQuestFlag(world, playerId, visitFlag, true);
        int visits = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS);
        if (visits < VILLAGER_TARGET) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_VISITS, Math.min(VILLAGER_TARGET, visits + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    @Override
    public void onVillagerTrade(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid()) || !DailyQuestService.isAcceptedToday(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int trades = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES);
        if (trades < TRADE_TARGET) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.MARKET_ROUNDS_TRADES, Math.min(TRADE_TARGET, trades + 1));
        }
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
