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

public final class SmithSmeltingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.SMITH_SMELTING;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.smelt.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.smelt.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.smelt.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null) {
            Text blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Text.translatable(
                "quest.village-quest.daily.smelt.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS),
                DailyQuestService.smithSmeltOreTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS),
                DailyQuestService.smithSmeltIngotTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS) >= DailyQuestService.smithSmeltOreTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS) >= DailyQuestService.smithSmeltIngotTarget()
                && hasTurnInItems(player);
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.smelt.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.smelt.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.smelt.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeInventoryItem(player, Items.RAW_IRON, DailyQuestService.smithSmeltOreTarget())
                && DailyQuestService.consumeInventoryItem(player, Items.IRON_INGOT, DailyQuestService.smithSmeltIngotTarget());
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        int oreTarget = DailyQuestService.smithSmeltOreTarget();
        int ingotTarget = DailyQuestService.smithSmeltIngotTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS) < oreTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS) < ingotTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.RAW_IRON.getName(),
                DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                oreTarget,
                Items.IRON_INGOT.getName(),
                DailyQuestService.countInventoryItem(player, Items.IRON_INGOT),
                ingotTarget
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!stack.isOf(Items.RAW_IRON)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS, DailyQuestService.smithSmeltOreTarget(), count);
    }

    @Override
    public void onFurnaceOutput(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!stack.isOf(Items.IRON_INGOT)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS, DailyQuestService.smithSmeltIngotTarget(), stack.getCount());
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
        return DailyQuestService.countInventoryItem(player, Items.RAW_IRON) >= DailyQuestService.smithSmeltOreTarget()
                && DailyQuestService.countInventoryItem(player, Items.IRON_INGOT) >= DailyQuestService.smithSmeltIngotTarget();
    }
}
