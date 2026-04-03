package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WheatHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WHEAT_HARVEST;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.wheat.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.wheat.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.wheat.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            Component blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Component.translatable(
                "quest.village-quest.daily.wheat.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS),
                DailyQuestService.wheatTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS),
                DailyQuestService.breadTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) >= DailyQuestService.wheatTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) >= DailyQuestService.breadTarget()
                && hasTurnInItems(player);
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.wheat.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wheat.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.wheat.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeInventoryItem(player, Items.WHEAT, DailyQuestService.wheatTarget())
                && DailyQuestService.consumeInventoryItem(player, Items.BREAD, DailyQuestService.breadTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int wheatTarget = DailyQuestService.wheatTarget();
        int breadTarget = DailyQuestService.breadTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) < wheatTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) < breadTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.WHEAT.getDefaultInstance().getHoverName(),
                DailyQuestService.countInventoryItem(player, Items.WHEAT),
                wheatTarget,
                Items.BREAD.getDefaultInstance().getHoverName(),
                DailyQuestService.countInventoryItem(player, Items.BREAD),
                breadTarget
        );
    }

    @Override
    public void onAccepted(ServerLevel world, ServerPlayer player) {
        DailyQuestService.setQuestInt(world, player.getUUID(), DailyQuestKeys.LAST_BREAD_CRAFTED,
                DailyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerLevel world, ServerPlayer player) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        UUID playerId = player.getUUID();
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
    public void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!state.is(Blocks.WHEAT)) return;
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!state.hasProperty(CropBlock.AGE) || state.getValue(CropBlock.AGE) < crop.getMaxAge()) return;

        incrementProgress(world, player);
    }

    private void incrementProgress(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (current >= DailyQuestService.wheatTarget()) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS, Math.min(DailyQuestService.wheatTarget(), current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        return DailyQuestService.countInventoryItem(player, Items.WHEAT) >= DailyQuestService.wheatTarget()
                && DailyQuestService.countInventoryItem(player, Items.BREAD) >= DailyQuestService.breadTarget();
    }
}
