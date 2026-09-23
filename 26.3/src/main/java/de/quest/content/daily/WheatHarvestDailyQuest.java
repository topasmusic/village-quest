package de.quest.content.daily;

import de.quest.quest.QuestCompletionMode;
import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class WheatHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public QuestCompletionMode completionMode() {
        return QuestCompletionMode.QUESTMASTER_TURN_IN;
    }

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
        int wheatProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (wheatProgress < DailyQuestService.wheatHarvestTarget()) {
            return Component.translatable(
                    "quest.village-quest.daily.wheat.stage.1",
                    wheatProgress,
                    DailyQuestService.wheatHarvestTarget()
            ).withStyle(ChatFormatting.GRAY);
        }

        int breadProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS);
        if (breadProgress < DailyQuestService.breadTarget()) {
            return Component.translatable(
                    "quest.village-quest.daily.wheat.stage.2",
                    breadProgress,
                    DailyQuestService.breadTarget()
            ).withStyle(ChatFormatting.GRAY);
        }

        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            Component blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Component.translatable("quest.village-quest.daily.wheat.stage.3").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) >= DailyQuestService.wheatHarvestTarget()
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
        return DailyQuestService.consumeCompletionItemRequirements(
                world,
                player,
                Map.of(
                        Items.WHEAT, DailyQuestService.wheatTarget(),
                        Items.BREAD, DailyQuestService.breadTarget()
                )
        );
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int wheatTarget = DailyQuestService.wheatTarget();
        int breadTarget = DailyQuestService.breadTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) < DailyQuestService.wheatHarvestTarget()
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) < breadTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.WHEAT.getDefaultInstance().getDisplayName(),
                DailyQuestService.countCompletionItem(world, player, Items.WHEAT),
                wheatTarget,
                Items.BREAD.getDefaultInstance().getDisplayName(),
                DailyQuestService.countCompletionItem(world, player, Items.BREAD),
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
        if (!DailyQuestService.isTrackingQuest(world, player.getUUID(), type())) return;

        UUID playerId = player.getUUID();
        int craftedBread = DailyQuestService.getCraftedStat(player, Items.BREAD);
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) < DailyQuestService.wheatHarvestTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
            return;
        }

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
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (!DailyQuestService.isTrackingQuest(world, player.getUUID(), type())) return;
        if (!stack.is(Items.WHEAT) || count <= 0) return;
        incrementProgress(world, player, count);
    }

    private void incrementProgress(ServerLevel world, ServerPlayer player, int amount) {
        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (current >= DailyQuestService.wheatHarvestTarget()) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS,
                Math.min(DailyQuestService.wheatHarvestTarget(), current + amount));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        return DailyQuestService.countCompletionItem(world, player, Items.WHEAT) >= DailyQuestService.wheatTarget()
                && DailyQuestService.countCompletionItem(world, player, Items.BREAD) >= DailyQuestService.breadTarget();
    }
}
