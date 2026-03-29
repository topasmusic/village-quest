package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class HarvestForVillageWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.HARVEST_FOR_VILLAGE;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.harvest.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.harvest.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.harvest.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.harvest.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT),
                        WeeklyQuestService.harvestWheatTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT),
                        WeeklyQuestService.harvestCarrotTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.harvest.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO),
                        WeeklyQuestService.harvestPotatoTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD),
                        WeeklyQuestService.harvestBreadTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO) >= WeeklyQuestService.harvestPotatoTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD) >= WeeklyQuestService.harvestBreadTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.BREAD) >= WeeklyQuestService.harvestBreadTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.harvest.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.harvest.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.harvest.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(1, 18),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.FARMING,
                40
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        return WeeklyQuestService.consumeInventoryItem(player, Items.BREAD, WeeklyQuestService.harvestBreadTarget());
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        WeeklyQuestService.setQuestInt(world, player.getUuid(), WeeklyQuestKeys.HARVEST_LAST_BREAD, WeeklyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int craftedBread = WeeklyQuestService.getCraftedStat(player, Items.BREAD);
        int stored = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD);
        if (stored == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD, craftedBread + 1);
            return;
        }

        int delta = craftedBread - (stored - 1);
        if (delta > 0) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_BREAD, delta, WeeklyQuestService.harvestBreadTarget());
            WeeklyQuestService.completeIfEligible(world, player);
        }
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD, craftedBread + 1);
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!(state.getBlock() instanceof CropBlock crop) || !state.contains(CropBlock.AGE) || state.get(CropBlock.AGE) < crop.getMaxAge()) {
            return;
        }

        UUID playerId = player.getUuid();
        if (state.isOf(Blocks.WHEAT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT, 1, WeeklyQuestService.harvestWheatTarget());
        } else if (state.isOf(Blocks.CARROTS)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_CARROT, 1, WeeklyQuestService.harvestCarrotTarget());
        } else if (state.isOf(Blocks.POTATOES)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_POTATO, 1, WeeklyQuestService.harvestPotatoTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
