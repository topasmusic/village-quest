package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class StallAndPastureWeeklyQuest implements WeeklyQuestDefinition {
    private static final Item[] WOOL_ITEMS = new Item[] {
            Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.GRAY_WOOL, Items.BLACK_WOOL,
            Items.BROWN_WOOL, Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL
    };

    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.STALL_AND_PASTURE;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.pasture.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.pasture.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.pasture.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        Text line1 = Text.translatable(
                "quest.village-quest.weekly.pasture.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED),
                WeeklyQuestService.pastureBreedTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR),
                WeeklyQuestService.pastureShearTarget()
        ).formatted(Formatting.GRAY);
        Text line2 = Text.translatable(
                "quest.village-quest.weekly.pasture.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL),
                WeeklyQuestService.pastureWoolTarget()
        ).formatted(Formatting.GRAY);
        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        Text blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED) >= WeeklyQuestService.pastureBreedTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR) >= WeeklyQuestService.pastureShearTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL) >= WeeklyQuestService.pastureWoolTarget()
                && countInventoryWool(player) >= WeeklyQuestService.pastureWoolTarget();
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        return consumeInventoryWool(player, WeeklyQuestService.pastureWoolTarget());
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        int woolTarget = WeeklyQuestService.pastureWoolTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED) < WeeklyQuestService.pastureBreedTarget()
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR) < WeeklyQuestService.pastureShearTarget()
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL) < woolTarget
                || countInventoryWool(player) >= woolTarget) {
            return null;
        }
        return Texts.turnInMissing(
                Text.translatable("text.village-quest.turnin.label.wool"),
                countInventoryWool(player),
                woolTarget
        );
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.pasture.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.pasture.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.pasture.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(2, 8),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.ANIMALS,
                40
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!isWool(stack)) {
            return;
        }
        WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.PASTURE_WOOL, count, WeeklyQuestService.pastureWoolTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUuid(), WeeklyQuestKeys.PASTURE_BREED, 1, WeeklyQuestService.pastureBreedTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }
        if (!(entity instanceof SheepEntity sheep) || !inHand.isOf(Items.SHEARS) || !sheep.isShearable()) {
            return;
        }

        UUID playerId = player.getUuid();
        WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR, 1, WeeklyQuestService.pastureShearTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.isOf(woolItem)) {
                return true;
            }
        }
        return false;
    }

    private int countInventoryWool(ServerPlayerEntity player) {
        return DailyQuestService.countInventoryItems(player, WOOL_ITEMS);
    }

    private boolean consumeInventoryWool(ServerPlayerEntity player, int amount) {
        return DailyQuestService.consumeInventoryItems(player, amount, WOOL_ITEMS);
    }
}
