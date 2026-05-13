package de.quest.content.weekly;

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
import java.util.UUID;

public final class BakehouseStockWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.BAKEHOUSE_STOCK;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.bakehouse.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.bakehouse.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.bakehouse.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        Component line1 = Component.translatable(
                "quest.village-quest.weekly.bakehouse.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD),
                WeeklyQuestService.bakehouseBreadTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE),
                WeeklyQuestService.bakehousePieTarget()
        ).withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "quest.village-quest.weekly.bakehouse.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_POTATO),
                WeeklyQuestService.bakehousePotatoTarget()
        ).withStyle(ChatFormatting.GRAY);
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        Component blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD) >= WeeklyQuestService.bakehouseBreadTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE) >= WeeklyQuestService.bakehousePieTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_POTATO) >= WeeklyQuestService.bakehousePotatoTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.BREAD) >= WeeklyQuestService.bakehouseBreadTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.PUMPKIN_PIE) >= WeeklyQuestService.bakehousePieTarget()
                && WeeklyQuestService.countCompletionItem(world, player, Items.BAKED_POTATO) >= WeeklyQuestService.bakehousePotatoTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.bakehouse.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.bakehouse.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.bakehouse.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(2, 6),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.FARMING,
                35
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (WeeklyQuestService.countCompletionItem(world, player, Items.BREAD) < WeeklyQuestService.bakehouseBreadTarget()
                || WeeklyQuestService.countCompletionItem(world, player, Items.PUMPKIN_PIE) < WeeklyQuestService.bakehousePieTarget()
                || WeeklyQuestService.countCompletionItem(world, player, Items.BAKED_POTATO) < WeeklyQuestService.bakehousePotatoTarget()) {
            return false;
        }
        return WeeklyQuestService.consumeCompletionItem(world, player, Items.BREAD, WeeklyQuestService.bakehouseBreadTarget())
                && WeeklyQuestService.consumeCompletionItem(world, player, Items.PUMPKIN_PIE, WeeklyQuestService.bakehousePieTarget())
                && WeeklyQuestService.consumeCompletionItem(world, player, Items.BAKED_POTATO, WeeklyQuestService.bakehousePotatoTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int breadTarget = WeeklyQuestService.bakehouseBreadTarget();
        int pieTarget = WeeklyQuestService.bakehousePieTarget();
        int potatoTarget = WeeklyQuestService.bakehousePotatoTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD) < breadTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE) < pieTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_POTATO) < potatoTarget
                || (WeeklyQuestService.countCompletionItem(world, player, Items.BREAD) >= breadTarget
                && WeeklyQuestService.countCompletionItem(world, player, Items.PUMPKIN_PIE) >= pieTarget
                && WeeklyQuestService.countCompletionItem(world, player, Items.BAKED_POTATO) >= potatoTarget)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.BREAD.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.BREAD),
                breadTarget,
                Items.PUMPKIN_PIE.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.PUMPKIN_PIE),
                pieTarget,
                Items.BAKED_POTATO.getDefaultInstance().getDisplayName(),
                WeeklyQuestService.countCompletionItem(world, player, Items.BAKED_POTATO),
                potatoTarget
        );
    }

    @Override
    public void onAccepted(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, WeeklyQuestService.getCraftedStat(player, Items.BREAD) + 1);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, WeeklyQuestService.getCraftedStat(player, Items.PUMPKIN_PIE) + 1);
    }

    @Override
    public void onServerTick(ServerLevel world, ServerPlayer player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        int craftedBread = WeeklyQuestService.getCraftedStat(player, Items.BREAD);
        int storedBread = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD);
        if (storedBread == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, craftedBread + 1);
        } else {
            int deltaBread = craftedBread - (storedBread - 1);
            if (deltaBread > 0) {
                WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.BAKEHOUSE_BREAD, deltaBread, WeeklyQuestService.bakehouseBreadTarget());
            }
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_BREAD, craftedBread + 1);
        }

        int craftedPie = WeeklyQuestService.getCraftedStat(player, Items.PUMPKIN_PIE);
        int storedPie = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE);
        if (storedPie == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, craftedPie + 1);
        } else {
            int deltaPie = craftedPie - (storedPie - 1);
            if (deltaPie > 0) {
                WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.BAKEHOUSE_PIE, deltaPie, WeeklyQuestService.bakehousePieTarget());
            }
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.BAKEHOUSE_LAST_PIE, craftedPie + 1);
        }

        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (!stack.is(Items.BAKED_POTATO)) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUUID(), WeeklyQuestKeys.BAKEHOUSE_POTATO, stack.getCount(), WeeklyQuestService.bakehousePotatoTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
