package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class SmithWeekWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.SMITH_WEEK;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.smith.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.smith.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.smith.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.smith.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE),
                        WeeklyQuestService.smithOreTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.smith.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON),
                        WeeklyQuestService.smithIronTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD),
                        WeeklyQuestService.smithGoldTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE) >= WeeklyQuestService.smithOreTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD) >= WeeklyQuestService.smithGoldTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT) >= WeeklyQuestService.smithGoldTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.smith.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.smith.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.smith.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(1, 20),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                1000,
                ReputationService.ReputationTrack.CRAFTING,
                45
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT) < WeeklyQuestService.smithIronTarget()
                || WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT) < WeeklyQuestService.smithGoldTarget()) {
            return false;
        }
        return WeeklyQuestService.consumeInventoryItem(player, Items.IRON_INGOT, WeeklyQuestService.smithIronTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.GOLD_INGOT, WeeklyQuestService.smithGoldTarget());
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!state.isOf(Blocks.IRON_ORE) && !state.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
            return;
        }
        WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.SMITH_ORE, 1, WeeklyQuestService.smithOreTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        if (stack.isOf(Items.IRON_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_IRON, stack.getCount(), WeeklyQuestService.smithIronTarget());
        } else if (stack.isOf(Items.GOLD_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_GOLD, stack.getCount(), WeeklyQuestService.smithGoldTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
