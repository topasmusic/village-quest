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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class PotatoHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.POTATO_HARVEST;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.potato.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.potato.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.potato.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.potato.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.POTATO_PROGRESS),
                DailyQuestService.potatoTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CARROT_PROGRESS),
                DailyQuestService.carrotTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.POTATO_PROGRESS) >= DailyQuestService.potatoTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CARROT_PROGRESS) >= DailyQuestService.carrotTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.potato.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.potato.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.potato.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!state.hasProperty(CropBlock.AGE) || state.getValue(CropBlock.AGE) < crop.getMaxAge()) return;

        if (state.is(Blocks.POTATOES)) {
            incrementProgress(world, player, DailyQuestKeys.POTATO_PROGRESS, DailyQuestService.potatoTarget());
        } else if (state.is(Blocks.CARROTS)) {
            incrementProgress(world, player, DailyQuestKeys.CARROT_PROGRESS, DailyQuestService.carrotTarget());
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
