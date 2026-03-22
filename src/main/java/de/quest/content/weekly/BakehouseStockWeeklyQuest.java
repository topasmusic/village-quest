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

public final class BakehouseStockWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.BAKEHOUSE_STOCK;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.bakehouse.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.bakehouse.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.bakehouse.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.bakehouse.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD),
                        WeeklyQuestService.bakehouseBreadTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE),
                        WeeklyQuestService.bakehousePieTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.bakehouse.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_POTATO),
                        WeeklyQuestService.bakehousePotatoTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD) >= WeeklyQuestService.bakehouseBreadTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE) >= WeeklyQuestService.bakehousePieTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_POTATO) >= WeeklyQuestService.bakehousePotatoTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.bakehouse.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.bakehouse.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.bakehouse.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(1, 16),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                850,
                ReputationService.ReputationTrack.FARMING,
                35
        );
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, WeeklyQuestService.getCraftedStat(player, Items.BREAD) + 1);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, WeeklyQuestService.getCraftedStat(player, Items.PUMPKIN_PIE) + 1);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int craftedBread = WeeklyQuestService.getCraftedStat(player, Items.BREAD);
        int storedBread = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD);
        if (storedBread == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, craftedBread + 1);
        } else {
            int deltaBread = craftedBread - (storedBread - 1);
            if (deltaBread > 0) {
                WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD, deltaBread, WeeklyQuestService.bakehouseBreadTarget());
            }
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, craftedBread + 1);
        }

        int craftedPie = WeeklyQuestService.getCraftedStat(player, Items.PUMPKIN_PIE);
        int storedPie = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE);
        if (storedPie == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, craftedPie + 1);
        } else {
            int deltaPie = craftedPie - (storedPie - 1);
            if (deltaPie > 0) {
                WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE, deltaPie, WeeklyQuestService.bakehousePieTarget());
            }
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, craftedPie + 1);
        }

        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!stack.isOf(Items.BAKED_POTATO)) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.BAKEHOUSE_POTATO, stack.getCount(), WeeklyQuestService.bakehousePotatoTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
