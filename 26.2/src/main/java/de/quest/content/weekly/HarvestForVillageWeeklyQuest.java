package de.quest.content.weekly;

import de.quest.quest.QuestCompletionMode;
import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HarvestForVillageWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public QuestCompletionMode completionMode() {
        return QuestCompletionMode.QUESTMASTER_TURN_IN;
    }

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
        if (!harvestStageComplete(world, playerId)) {
            return List.of(
                    Component.translatable(
                            "quest.village-quest.weekly.harvest.stage.1a",
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT),
                            WeeklyQuestService.harvestWheatTarget(),
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT),
                            WeeklyQuestService.harvestCarrotTarget()
                    ).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "quest.village-quest.weekly.harvest.stage.1b",
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO),
                            WeeklyQuestService.harvestPotatoTarget()
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        int breadProgress = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD);
        if (breadProgress < WeeklyQuestService.harvestBreadTarget()) {
            return List.of(Component.translatable(
                    "quest.village-quest.weekly.harvest.stage.2",
                    breadProgress,
                    WeeklyQuestService.harvestBreadTarget()
            ).withStyle(ChatFormatting.GRAY));
        }

        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        Component blocked = player == null ? null : claimBlockedMessage(world, player);
        return List.of(blocked == null
                ? Component.translatable("quest.village-quest.weekly.harvest.stage.3").withStyle(ChatFormatting.GRAY)
                : blocked);
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
                WeeklyQuestService.reward(2, 8),
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
        return WeeklyQuestService.consumeCompletionItemRequirements(
                world,
                player,
                Map.of(
                        Items.WHEAT, WeeklyQuestService.harvestWheatTarget(),
                        Items.CARROT, WeeklyQuestService.harvestCarrotTarget(),
                        Items.POTATO, WeeklyQuestService.harvestPotatoTarget(),
                        Items.BREAD, WeeklyQuestService.harvestBreadTarget()
                )
        );
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
                Items.WHEAT.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.WHEAT),
                wheatTarget,
                Items.CARROT.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.CARROT),
                carrotTarget,
                Items.POTATO.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.POTATO),
                potatoTarget,
                Items.BREAD.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.BREAD),
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
        if (!harvestStageComplete(world, playerId)) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_LAST_BREAD, craftedBread + 1);
            return;
        }

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
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (count <= 0) {
            return;
        }

        UUID playerId = player.getUUID();
        if (stack.is(Items.WHEAT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT, count, WeeklyQuestService.harvestWheatTarget());
        } else if (stack.is(Items.CARROT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_CARROT, count, WeeklyQuestService.harvestCarrotTarget());
        } else if (stack.is(Items.POTATO)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_POTATO, count, WeeklyQuestService.harvestPotatoTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        return WeeklyQuestService.countCompletionItem(world, player, Items.WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.POTATO) >= WeeklyQuestService.harvestPotatoTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.BREAD) >= WeeklyQuestService.harvestBreadTarget();
    }

    private boolean harvestStageComplete(ServerLevel world, UUID playerId) {
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO) >= WeeklyQuestService.harvestPotatoTarget();
    }
}
