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

public final class SmithWeekWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.SMITH_WEEK;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.smith.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.smith.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.smith.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        Component line1 = Component.translatable(
                "quest.village-quest.weekly.smith.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE),
                WeeklyQuestService.smithOreTarget()
        ).withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "quest.village-quest.weekly.smith.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON),
                WeeklyQuestService.smithIronTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD),
                WeeklyQuestService.smithGoldTarget()
        ).withStyle(ChatFormatting.GRAY);
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        Component blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE) >= WeeklyQuestService.smithOreTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD) >= WeeklyQuestService.smithGoldTarget()
                && hasTurnInItems(player);
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.smith.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.smith.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.smith.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(1, 20),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                16,
                ReputationService.ReputationTrack.CRAFTING,
                45
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return WeeklyQuestService.consumeInventoryItem(player, Items.RAW_IRON, WeeklyQuestService.smithOreTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.IRON_INGOT, WeeklyQuestService.smithIronTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.GOLD_INGOT, WeeklyQuestService.smithGoldTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int oreTarget = WeeklyQuestService.smithOreTarget();
        int ironTarget = WeeklyQuestService.smithIronTarget();
        int goldTarget = WeeklyQuestService.smithGoldTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE) < oreTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON) < ironTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD) < goldTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.RAW_IRON.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.RAW_IRON),
                oreTarget,
                Items.IRON_INGOT.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT),
                ironTarget,
                Items.GOLD_INGOT.getDefaultInstance().getHoverName(),
                WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT),
                goldTarget
        );
    }

    @Override
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (!stack.is(Items.RAW_IRON)) {
            return;
        }
        WeeklyQuestService.addQuestIntClamped(world, player.getUUID(), WeeklyQuestKeys.SMITH_ORE, count, WeeklyQuestService.smithOreTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        if (stack.is(Items.IRON_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_IRON, stack.getCount(), WeeklyQuestService.smithIronTarget());
        } else if (stack.is(Items.GOLD_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_GOLD, stack.getCount(), WeeklyQuestService.smithGoldTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        return WeeklyQuestService.countInventoryItem(player, Items.RAW_IRON) >= WeeklyQuestService.smithOreTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT) >= WeeklyQuestService.smithGoldTarget();
    }
}
