package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class RiverMealDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.RIVER_MEAL;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.river.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.river.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.river.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.river.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS),
                DailyQuestService.riverFishTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS),
                DailyQuestService.riverCookedFishTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS) >= DailyQuestService.riverFishTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS) >= DailyQuestService.riverCookedFishTarget()
                && DailyQuestService.countInventoryItems(player, Items.COOKED_COD, Items.COOKED_SALMON) >= DailyQuestService.riverCookedFishTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.river.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.river.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.river.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.consumeInventoryItems(player, DailyQuestService.riverCookedFishTarget(), Items.COOKED_COD, Items.COOKED_SALMON);
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        DailyQuestService.setQuestInt(
                world,
                player.getUuid(),
                DailyQuestKeys.LAST_FISH_CAUGHT,
                DailyQuestService.getCustomStat(player, Stats.FISH_CAUGHT) + 1
        );
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        UUID playerId = player.getUuid();
        int fishCaught = DailyQuestService.getCustomStat(player, Stats.FISH_CAUGHT);
        int storedLastFishCaught = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_FISH_CAUGHT);
        if (storedLastFishCaught == 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_FISH_CAUGHT, fishCaught + 1);
            return;
        }

        int lastFishCaught = storedLastFishCaught - 1;
        int delta = fishCaught - lastFishCaught;
        if (delta > 0) {
            int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS);
            int credit = Math.min(delta, DailyQuestService.riverFishTarget() - current);
            if (credit > 0) {
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS, credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }

        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_FISH_CAUGHT, fishCaught + 1);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        int cookedFish = cookedFishCount(stack);
        if (cookedFish <= 0) {
            return;
        }

        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS);
        int credit = Math.min(cookedFish, DailyQuestService.riverCookedFishTarget() - current);
        if (credit <= 0) {
            return;
        }

        DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS, credit);
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private int cookedFishCount(ItemStack stack) {
        if (stack.isOf(Items.COOKED_COD) || stack.isOf(Items.COOKED_SALMON)) {
            return stack.getCount();
        }
        return 0;
    }
}
