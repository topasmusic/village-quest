package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RiverMealDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.RIVER_MEAL;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.river.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.river.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.river.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            Component blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Component.translatable(
                "quest.village-quest.daily.river.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS),
                DailyQuestService.riverFishTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS),
                DailyQuestService.riverCookedFishTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS) >= DailyQuestService.riverFishTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS) >= DailyQuestService.riverCookedFishTarget()
                && DailyQuestService.countInventoryItems(player, Items.COOKED_COD, Items.COOKED_SALMON) >= DailyQuestService.riverCookedFishTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.river.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.river.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.river.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.consumeInventoryItems(player, DailyQuestService.riverCookedFishTarget(), Items.COOKED_COD, Items.COOKED_SALMON);
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int fishTarget = DailyQuestService.riverCookedFishTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_FISH_PROGRESS) < DailyQuestService.riverFishTarget()
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS) < fishTarget
                || DailyQuestService.countInventoryItems(player, Items.COOKED_COD, Items.COOKED_SALMON) >= fishTarget) {
            return null;
        }
        return Texts.turnInMissing(
                Component.translatable("text.village-quest.turnin.label.cooked_fish"),
                DailyQuestService.countInventoryItems(player, Items.COOKED_COD, Items.COOKED_SALMON),
                fishTarget
        );
    }

    @Override
    public void onAccepted(ServerLevel world, ServerPlayer player) {
        DailyQuestService.setQuestInt(
                world,
                player.getUUID(),
                DailyQuestKeys.LAST_FISH_CAUGHT,
                DailyQuestService.getCustomStat(player, Stats.FISH_CAUGHT) + 1
        );
    }

    @Override
    public void onServerTick(ServerLevel world, ServerPlayer player) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        UUID playerId = player.getUUID();
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
    public void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        int cookedFish = cookedFishCount(stack);
        if (cookedFish <= 0) {
            return;
        }

        UUID playerId = player.getUUID();
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
        if (stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)) {
            return stack.getCount();
        }
        return 0;
    }
}
