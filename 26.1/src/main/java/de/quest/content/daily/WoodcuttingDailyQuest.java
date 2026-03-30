package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WoodcuttingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WOODCUTTING;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.wood.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.wood.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.wood.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.wood.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOD_PROGRESS),
                DailyQuestService.woodTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COAL_PROGRESS),
                DailyQuestService.coalTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WOOD_PROGRESS) >= DailyQuestService.woodTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.COAL_PROGRESS) >= DailyQuestService.coalTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.wood.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wood.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wood.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (state.is(BlockTags.LOGS)) {
            incrementProgress(world, player, DailyQuestKeys.WOOD_PROGRESS, DailyQuestService.woodTarget());
        } else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            incrementProgress(world, player, DailyQuestKeys.COAL_PROGRESS, DailyQuestService.coalTarget());
        }
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
