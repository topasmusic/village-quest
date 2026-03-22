package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
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

import java.util.UUID;

public final class WheatHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WHEAT_HARVEST;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.wheat.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.wheat.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.wheat.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.wheat.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS),
                DailyQuestService.wheatTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS),
                DailyQuestService.breadTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) >= DailyQuestService.wheatTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) >= DailyQuestService.breadTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.wheat.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wheat.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wheat.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        DailyQuestService.setQuestInt(world, player.getUuid(), DailyQuestKeys.LAST_BREAD_CRAFTED,
                DailyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        UUID playerId = player.getUuid();
        int craftedBread = DailyQuestService.getCraftedStat(player, Items.BREAD);
        int storedLastCraftedBread = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED);
        if (storedLastCraftedBread == 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
            return;
        }
        int lastCraftedBread = storedLastCraftedBread - 1;
        int delta = craftedBread - lastCraftedBread;
        if (delta > 0) {
            int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS);
            int credit = Math.min(delta, DailyQuestService.breadTarget() - current);
            if (credit > 0) {
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS, credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
    }

    @Override
    public void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!state.isOf(Blocks.WHEAT)) return;
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!state.contains(CropBlock.AGE) || state.get(CropBlock.AGE) < crop.getMaxAge()) return;

        incrementProgress(world, player);
    }

    private void incrementProgress(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (current >= DailyQuestService.wheatTarget()) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS, Math.min(DailyQuestService.wheatTarget(), current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
