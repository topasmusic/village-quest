package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CoalMiningDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.COAL_MINING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.coal.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.coal.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.coal.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        int ironProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS);
        int coalProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS);
        int ironTarget = DailyQuestService.ironTarget();
        int coalTarget = DailyQuestService.smithCoalTarget();
        if (player != null) {
            Text blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Text.translatable(
                "quest.village-quest.daily.coal.progress",
                ironProgress,
                ironTarget,
                coalProgress,
                coalTarget
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS) >= DailyQuestService.ironTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS) >= DailyQuestService.smithCoalTarget()
                && hasTurnInItems(player);
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeInventoryItem(player, Items.RAW_IRON, DailyQuestService.ironTarget())
                && DailyQuestService.consumeInventoryItem(player, Items.COAL, DailyQuestService.smithCoalTarget());
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }

        UUID playerId = player.getUuid();
        int ironTarget = DailyQuestService.ironTarget();
        int coalTarget = DailyQuestService.smithCoalTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS) < ironTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS) < coalTarget
                || hasTurnInItems(player)) {
            return null;
        }

        return Texts.turnInMissing(
                Items.RAW_IRON.getName(),
                DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                ironTarget,
                Items.COAL.getName(),
                DailyQuestService.countInventoryItem(player, Items.COAL),
                coalTarget
        );
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.coal.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.coal.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.coal.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (stack.isOf(Items.RAW_IRON)) {
            incrementProgress(world, player, DailyQuestKeys.IRON_PROGRESS, DailyQuestService.ironTarget(), count);
        } else if (stack.isOf(Items.COAL)) {
            incrementProgress(world, player, DailyQuestKeys.SMITH_COAL_PROGRESS, DailyQuestService.smithCoalTarget(), count);
        }
    }

    private void incrementProgress(ServerWorld world, ServerPlayerEntity player, String key, int target, int amount) {
        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, key, Math.min(target, current + amount));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean hasTurnInItems(ServerPlayerEntity player) {
        return DailyQuestService.countInventoryItem(player, Items.RAW_IRON) >= DailyQuestService.ironTarget()
                && DailyQuestService.countInventoryItem(player, Items.COAL) >= DailyQuestService.smithCoalTarget();
    }
}
