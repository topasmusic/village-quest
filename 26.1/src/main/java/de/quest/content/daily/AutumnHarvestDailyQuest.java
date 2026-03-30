package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class AutumnHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.AUTUMN_HARVEST;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.autumn.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.autumn.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.autumn.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.autumn.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS),
                DailyQuestService.autumnPumpkinTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_MELON_PROGRESS),
                DailyQuestService.autumnMelonTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS) >= DailyQuestService.autumnPumpkinTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.AUTUMN_MELON_PROGRESS) >= DailyQuestService.autumnMelonTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.autumn.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.autumn.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.autumn.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        if (state.is(Blocks.PUMPKIN) && hasAdjacentStem(world, pos, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM)) {
            incrementProgress(world, player, DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS, DailyQuestService.autumnPumpkinTarget());
        } else if (state.is(Blocks.MELON) && hasAdjacentStem(world, pos, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM)) {
            incrementProgress(world, player, DailyQuestKeys.AUTUMN_MELON_PROGRESS, DailyQuestService.autumnMelonTarget());
        }
    }

    private boolean hasAdjacentStem(ServerLevel world, BlockPos fruitPos, Block stemBlock, Block attachedStemBlock) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState adjacent = world.getBlockState(fruitPos.relative(direction));
            if (adjacent.is(attachedStemBlock)) {
                return true;
            }
            if (adjacent.is(stemBlock) && adjacent.getBlock() instanceof StemBlock
                    && adjacent.hasProperty(StemBlock.AGE) && adjacent.getValue(StemBlock.AGE) >= 7) {
                return true;
            }
        }
        return false;
    }

    private void incrementProgress(ServerLevel world, ServerPlayer player, String key, int target) {
        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }

        DailyQuestService.setQuestInt(world, playerId, key, Math.min(target, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
