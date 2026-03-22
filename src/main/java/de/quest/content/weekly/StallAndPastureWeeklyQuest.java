package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class StallAndPastureWeeklyQuest implements WeeklyQuestDefinition {
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
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.pasture.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED),
                        WeeklyQuestService.pastureBreedTarget(),
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR),
                        WeeklyQuestService.pastureShearTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.pasture.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL),
                        WeeklyQuestService.pastureWoolTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_BREED) >= WeeklyQuestService.pastureBreedTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_SHEAR) >= WeeklyQuestService.pastureShearTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_WOOL) >= WeeklyQuestService.pastureWoolTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.pasture.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.pasture.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.pasture.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(1, 18),
                WeeklyQuestService.magicShardReward(1),
                ItemStack.EMPTY,
                900,
                ReputationService.ReputationTrack.ANIMALS,
                40
        );
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_LAST_WOOL, WeeklyQuestService.totalWoolPickedUp(player) + 1);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL, 0);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL_EXPIRE, 0);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int currentPickedUp = WeeklyQuestService.totalWoolPickedUp(player);
        int storedLastPickedUp = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_LAST_WOOL);
        if (storedLastPickedUp == 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_LAST_WOOL, currentPickedUp + 1);
            return;
        }

        int expectedWool = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL);
        int delta = currentPickedUp - (storedLastPickedUp - 1);
        if (expectedWool > 0 && delta > 0) {
            int credit = Math.min(delta, expectedWool);
            if (credit > 0) {
                WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.PASTURE_WOOL, credit, WeeklyQuestService.pastureWoolTarget());
                WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL, Math.max(0, expectedWool - credit));
                WeeklyQuestService.completeIfEligible(world, player);
            }
        }
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_LAST_WOOL, currentPickedUp + 1);

        int left = WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL_EXPIRE) - 1;
        if (left <= 0) {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL, 0);
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL_EXPIRE, 0);
        } else {
            WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL_EXPIRE, left);
        }
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
        WeeklyQuestService.addQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL, 3);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_EXPECTED_WOOL_EXPIRE, 40);
        WeeklyQuestService.setQuestInt(world, playerId, WeeklyQuestKeys.PASTURE_LAST_WOOL, WeeklyQuestService.totalWoolPickedUp(player) + 1);
        WeeklyQuestService.completeIfEligible(world, player);
    }
}
