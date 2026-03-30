package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import java.util.List;
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

public final class HarvestForVillageWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.HARVEST_FOR_VILLAGE;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.harvest.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.harvest.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.harvest.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        Component line1 = Component.translatable(
                "quest.village-quest.weekly.harvest.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT),
                WeeklyQuestService.harvestWheatTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT),
                WeeklyQuestService.harvestCarrotTarget()
        ).withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "quest.village-quest.weekly.harvest.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO),
                WeeklyQuestService.harvestPotatoTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD),
                WeeklyQuestService.harvestBreadTarget()
        ).withStyle(ChatFormatting.GRAY);
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        Component blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO) >= WeeklyQuestService.harvestPotatoTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD) >= WeeklyQuestService.harvestBreadTarget()
                && hasTurnInItems(player);
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.harvest.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.harvest.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.harvest.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(1, 18),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.FARMING,
                40
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return WeeklyQuestService.consumeInventoryItem(player, Items.WHEAT, WeeklyQuestService.harvestWheatTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.CARROT, WeeklyQuestService.harvestCarrotTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.POTATO, WeeklyQuestService.harvestPotatoTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.BREAD, WeeklyQuestService.harvestBreadTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int wheatTarget = WeeklyQuestService.harvestWheatTarget();
        int carrotTarget = WeeklyQuestService.harvestCarrotTarget();
        int potatoTarget = WeeklyQuestService.harvestPotatoTarget();
        int breadTarget = WeeklyQuestService.harvestBreadTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT) < wheatTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT) < carrotTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO) < potatoTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD) < breadTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.WHEAT.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.WHEAT),
                wheatTarget,
                Items.CARROT.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.CARROT),
                carrotTarget,
                Items.POTATO.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.POTATO),
                potatoTarget,
                Items.BREAD.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.BREAD),
                breadTarget
        );
    }

    @Override
    public void onAccepted(ServerLevel world, ServerPlayer player) {
        WeeklyQuestService.setQuestInt(world, player.getUUID(), WeeklyQuestKeys.HARVEST_LAST_BREAD, WeeklyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerLevel world, ServerPlayer player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        int craftedBread = WeeklyQuestService.getCraftedStat(player, Items.BREAD);
        int stored = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD);
        if (stored == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD, craftedBread + 1);
            return;
        }

        int delta = craftedBread - (stored - 1);
        if (delta > 0) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_BREAD, delta, WeeklyQuestService.harvestBreadTarget());
            WeeklyQuestService.completeIfEligible(world, player);
        }
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD, craftedBread + 1);
    }

    @Override
    public void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (!(state.getBlock() instanceof CropBlock crop) || !state.hasProperty(CropBlock.AGE) || state.getValue(CropBlock.AGE) < crop.getMaxAge()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (state.is(Blocks.WHEAT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT, 1, WeeklyQuestService.harvestWheatTarget());
        } else if (state.is(Blocks.CARROTS)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_CARROT, 1, WeeklyQuestService.harvestCarrotTarget());
        } else if (state.is(Blocks.POTATOES)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_POTATO, 1, WeeklyQuestService.harvestPotatoTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        return WeeklyQuestService.countInventoryItem(player, Items.WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.POTATO) >= WeeklyQuestService.harvestPotatoTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.BREAD) >= WeeklyQuestService.harvestBreadTarget();
    }
}
