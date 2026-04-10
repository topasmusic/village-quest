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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.pasture.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.pasture.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.pasture.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        Component line1 = Component.translatable(
                "quest.village-quest.weekly.pasture.progress.1",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED),
                WeeklyQuestService.pastureBreedTarget(),
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR),
                WeeklyQuestService.pastureShearTarget()
        ).withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "quest.village-quest.weekly.pasture.progress.2",
                WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL),
                WeeklyQuestService.pastureWoolTarget()
        ).withStyle(ChatFormatting.GRAY);
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        Component blocked = player == null ? null : claimBlockedMessage(world, player);
        return blocked == null ? List.of(line1, line2) : List.of(line1, line2, blocked);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED) >= WeeklyQuestService.pastureBreedTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR) >= WeeklyQuestService.pastureShearTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL) >= WeeklyQuestService.pastureWoolTarget()
                && countInventoryWool(player) >= WeeklyQuestService.pastureWoolTarget();
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        return consumeInventoryWool(player, WeeklyQuestService.pastureWoolTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int woolTarget = WeeklyQuestService.pastureWoolTarget();
        if (WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED) < WeeklyQuestService.pastureBreedTarget()
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR) < WeeklyQuestService.pastureShearTarget()
                || WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL) < woolTarget
                || countInventoryWool(player) >= woolTarget) {
            return null;
        }
        return Texts.turnInMissing(
                Component.translatable("text.village-quest.turnin.label.wool"),
                countInventoryWool(player),
                woolTarget
        );
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.pasture.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.pasture.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.pasture.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(2, 8),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                14,
                ReputationService.ReputationTrack.ANIMALS,
                40
        );
    }

    @Override
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (!isWool(stack)) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUUID(), WeeklyQuestKeys.PASTURE_WOOL, count, WeeklyQuestService.pastureWoolTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }

        WeeklyQuestService.addQuestIntClamped(world, player.getUUID(), WeeklyQuestKeys.PASTURE_BREED, 1, WeeklyQuestService.pastureBreedTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    @Override
    public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())) {
            return;
        }
        if (!(entity instanceof Sheep sheep) || !inHand.is(Items.SHEARS) || !sheep.readyForShearing()) {
            return;
        }

        UUID playerId = player.getUUID();
        WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR, 1, WeeklyQuestService.pastureShearTarget());
        WeeklyQuestService.completeIfEligible(world, player);
    }

    private boolean isWool(ItemStack stack) {
        for (Item woolItem : WOOL_ITEMS) {
            if (stack.is(woolItem)) {
                return true;
            }
        }
        return false;
    }

    private int countInventoryWool(ServerPlayer player) {
        int total = 0;
        for (Item woolItem : WOOL_ITEMS) {
            total += WeeklyQuestService.countInventoryItem(player, woolItem);
        }
        return total;
    }

    private boolean consumeInventoryWool(ServerPlayer player, int amount) {
        if (countInventoryWool(player) < amount) {
            return false;
        }

        int remaining = amount;
        for (Item woolItem : WOOL_ITEMS) {
            if (remaining <= 0) {
                break;
            }
            int available = WeeklyQuestService.countInventoryItem(player, woolItem);
            if (available <= 0) {
                continue;
            }
            int toConsume = Math.min(remaining, available);
            if (!WeeklyQuestService.consumeInventoryItem(player, woolItem, toConsume)) {
                return false;
            }
            remaining -= toConsume;
        }
        return remaining <= 0;
    }
}
