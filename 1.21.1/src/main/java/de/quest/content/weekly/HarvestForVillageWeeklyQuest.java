package de.quest.content.weekly;

import de.quest.quest.QuestCompletionMode;
import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.util.Texts;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.harvest.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.harvest.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.harvest.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        if (!harvestStageComplete(world, playerId)) {
            return List.of(
                    Text.translatable(
                            "quest.village-quest.weekly.harvest.stage.1a",
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT),
                            WeeklyQuestService.harvestWheatTarget(),
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT),
                            WeeklyQuestService.harvestCarrotTarget()
                    ).formatted(Formatting.GRAY),
                    Text.translatable(
                            "quest.village-quest.weekly.harvest.stage.1b",
                            WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO),
                            WeeklyQuestService.harvestPotatoTarget()
                    ).formatted(Formatting.GRAY)
            );
        }

        int breadProgress = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_BREAD);
        if (breadProgress < WeeklyQuestService.harvestBreadTarget()) {
            return List.of(Text.translatable(
                    "quest.village-quest.weekly.harvest.stage.2",
                    breadProgress,
                    WeeklyQuestService.harvestBreadTarget()
            ).formatted(Formatting.GRAY));
        }

        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        Text blocked = player == null ? null : claimBlockedMessage(world, player);
        return List.of(blocked == null
                ? Text.translatable("quest.village-quest.weekly.harvest.stage.3").formatted(Formatting.GRAY)
                : blocked);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
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
                Text.translatable("quest.village-quest.weekly.harvest.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.harvest.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.harvest.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(2, 8),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.FARMING,
                40
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return WeeklyQuestService.consumeCompletionItemRequirements(world, player, Map.of(
                Items.WHEAT, WeeklyQuestService.harvestWheatTarget(),
                Items.CARROT, WeeklyQuestService.harvestCarrotTarget(),
                Items.POTATO, WeeklyQuestService.harvestPotatoTarget(),
                Items.BREAD, WeeklyQuestService.harvestBreadTarget()
        ));
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
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
                Items.WHEAT.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countCompletionItem(world, player, Items.WHEAT),
                wheatTarget,
                Items.CARROT.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countCompletionItem(world, player, Items.CARROT),
                carrotTarget,
                Items.POTATO.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countCompletionItem(world, player, Items.POTATO),
                potatoTarget,
                Items.BREAD.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countCompletionItem(world, player, Items.BREAD),
                breadTarget
        );
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        WeeklyQuestService.setQuestInt(world, player.getUuid(), WeeklyQuestKeys.HARVEST_LAST_BREAD, WeeklyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
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
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (count <= 0) {
            return;
        }

        UUID playerId = player.getUuid();
        if (stack.isOf(Items.WHEAT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT, count, WeeklyQuestService.harvestWheatTarget());
        } else if (stack.isOf(Items.CARROT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_CARROT, count, WeeklyQuestService.harvestCarrotTarget());
        } else if (stack.isOf(Items.POTATO)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.HARVEST_POTATO, count, WeeklyQuestService.harvestPotatoTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean hasTurnInItems(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        return WeeklyQuestService.countCompletionItem(world, player, Items.WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.POTATO) >= WeeklyQuestService.harvestPotatoTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.BREAD) >= WeeklyQuestService.harvestBreadTarget();
    }

    private boolean harvestStageComplete(ServerWorld world, UUID playerId) {
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_WHEAT) >= WeeklyQuestService.harvestWheatTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_CARROT) >= WeeklyQuestService.harvestCarrotTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.HARVEST_POTATO) >= WeeklyQuestService.harvestPotatoTarget();
    }
}
