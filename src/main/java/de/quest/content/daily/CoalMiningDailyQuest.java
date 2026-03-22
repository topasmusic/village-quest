package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class CoalMiningDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.COAL_MINING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.coal.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.coal.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.coal.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.coal.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS),
                DailyQuestService.ironTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS),
                DailyQuestService.smithCoalTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS) >= DailyQuestService.ironTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS) >= DailyQuestService.smithCoalTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.coal.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.coal.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.coal.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
            incrementProgress(world, player, DailyQuestKeys.IRON_PROGRESS, DailyQuestService.ironTarget());
        } else if (state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE)) {
            incrementProgress(world, player, DailyQuestKeys.SMITH_COAL_PROGRESS, DailyQuestService.smithCoalTarget());
        }
    }

    private void incrementProgress(ServerWorld world, ServerPlayerEntity player, String key, int target) {
        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, key, Math.min(target, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
