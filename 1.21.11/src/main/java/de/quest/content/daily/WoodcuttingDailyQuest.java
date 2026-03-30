package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class WoodcuttingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WOODCUTTING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.wood.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.wood.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.wood.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.wood.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOD_PROGRESS),
                DailyQuestService.woodTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COAL_PROGRESS),
                DailyQuestService.coalTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOD_PROGRESS) >= DailyQuestService.woodTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COAL_PROGRESS) >= DailyQuestService.coalTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.wood.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wood.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wood.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (state.isIn(BlockTags.LOGS)) {
            incrementProgress(world, player, DailyQuestKeys.WOOD_PROGRESS, DailyQuestService.woodTarget());
        } else if (state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE)) {
            incrementProgress(world, player, DailyQuestKeys.COAL_PROGRESS, DailyQuestService.coalTarget());
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
