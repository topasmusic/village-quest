package de.quest.content.weekly;

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
import java.util.UUID;

public final class SmithWeekWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.SMITH_WEEK;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.smith.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.smith.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.smith.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        Text line1 = Text.translatable(
                "quest.village-quest.weekly.smith.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE),
                WeeklyQuestService.smithOreTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD_ORE),
                WeeklyQuestService.smithGoldOreTarget()
        ).formatted(Formatting.GRAY);
        Text line2 = Text.translatable(
                "quest.village-quest.weekly.smith.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON),
                WeeklyQuestService.smithIronTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD),
                WeeklyQuestService.smithGoldTarget()
        ).formatted(Formatting.GRAY);
        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        Text blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE) >= WeeklyQuestService.smithOreTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD_ORE) >= WeeklyQuestService.smithGoldOreTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD) >= WeeklyQuestService.smithGoldTarget()
                && hasTurnInItems(player);
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.smith.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.smith.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.smith.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(3, 0),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                16,
                ReputationService.ReputationTrack.CRAFTING,
                45
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return WeeklyQuestService.consumeInventoryItem(player, Items.RAW_IRON, WeeklyQuestService.smithOreTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.RAW_GOLD, WeeklyQuestService.smithGoldOreTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.IRON_INGOT, WeeklyQuestService.smithIronTarget())
                && WeeklyQuestService.consumeInventoryItem(player, Items.GOLD_INGOT, WeeklyQuestService.smithGoldTarget());
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        int oreTarget = WeeklyQuestService.smithOreTarget();
        int goldOreTarget = WeeklyQuestService.smithGoldOreTarget();
        int ironTarget = WeeklyQuestService.smithIronTarget();
        int goldTarget = WeeklyQuestService.smithGoldTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_ORE) < oreTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD_ORE) < goldOreTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_IRON) < ironTarget
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.SMITH_GOLD) < goldTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.RAW_IRON.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countInventoryItem(player, Items.RAW_IRON),
                oreTarget,
                Items.RAW_GOLD.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countInventoryItem(player, Items.RAW_GOLD),
                goldOreTarget,
                Items.IRON_INGOT.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT),
                ironTarget,
                Items.GOLD_INGOT.getDefaultStack().toHoverableText(),
                WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT),
                goldTarget
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!stack.isOf(Items.RAW_IRON) && !stack.isOf(Items.RAW_GOLD)) {
            return;
        }
        if (stack.isOf(Items.RAW_IRON)) {
            WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.SMITH_ORE, count, WeeklyQuestService.smithOreTarget());
        } else {
            WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.SMITH_GOLD_ORE, count, WeeklyQuestService.smithGoldOreTarget());
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        if (stack.isOf(Items.IRON_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_IRON, stack.getCount(), WeeklyQuestService.smithIronTarget());
        } else if (stack.isOf(Items.GOLD_INGOT)) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.SMITH_GOLD, stack.getCount(), WeeklyQuestService.smithGoldTarget());
        } else {
            return;
        }
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean hasTurnInItems(ServerPlayerEntity player) {
        return WeeklyQuestService.countInventoryItem(player, Items.RAW_IRON) >= WeeklyQuestService.smithOreTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.RAW_GOLD) >= WeeklyQuestService.smithGoldOreTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.IRON_INGOT) >= WeeklyQuestService.smithIronTarget()
                && WeeklyQuestService.countInventoryItem(player, Items.GOLD_INGOT) >= WeeklyQuestService.smithGoldTarget();
    }
}
