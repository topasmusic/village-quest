package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class SmithSmeltingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.SMITH_SMELTING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.smelt.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.smelt.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.smelt.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.smelt.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS),
                DailyQuestService.smithSmeltOreTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS),
                DailyQuestService.smithSmeltIngotTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS) >= DailyQuestService.smithSmeltOreTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS) >= DailyQuestService.smithSmeltIngotTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.smelt.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.smelt.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.smelt.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!state.isOf(Blocks.IRON_ORE) && !state.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS, DailyQuestService.smithSmeltOreTarget(), 1);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!stack.isOf(Items.IRON_INGOT)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS, DailyQuestService.smithSmeltIngotTarget(), stack.getCount());
    }

    private void incrementProgress(ServerWorld world, ServerPlayerEntity player, String key, int target, int amount) {
        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }

        DailyQuestService.setQuestInt(world, playerId, key, Math.min(target, current + amount));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
