package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.UUID;

public final class AutumnHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.AUTUMN_HARVEST;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.autumn.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.autumn.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.autumn.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.autumn.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS),
                DailyQuestService.autumnPumpkinTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_MELON_PROGRESS),
                DailyQuestService.autumnMelonTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS) >= DailyQuestService.autumnPumpkinTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_MELON_PROGRESS) >= DailyQuestService.autumnMelonTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.autumn.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.autumn.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.autumn.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        if (state.isOf(Blocks.PUMPKIN) && hasAdjacentStem(world, pos, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM)) {
            incrementProgress(world, player, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS, DailyQuestService.autumnPumpkinTarget());
        } else if (state.isOf(Blocks.MELON) && hasAdjacentStem(world, pos, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM)) {
            incrementProgress(world, player, DailyQuestKeys.AUTUMN_MELON_PROGRESS, DailyQuestService.autumnMelonTarget());
        }
    }

    private boolean hasAdjacentStem(ServerWorld world, BlockPos fruitPos, Block stemBlock, Block attachedStemBlock) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockState adjacent = world.getBlockState(fruitPos.offset(direction));
            if (adjacent.isOf(attachedStemBlock)) {
                return true;
            }
            if (adjacent.isOf(stemBlock) && adjacent.getBlock() instanceof StemBlock
                    && adjacent.contains(StemBlock.AGE) && adjacent.get(StemBlock.AGE) >= 7) {
                return true;
            }
        }
        return false;
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
