package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SmithSmeltingDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.SMITH_SMELTING;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.smelt.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.smelt.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.smelt.offer.2").withStyle(ChatFormatting.GRAY);
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
                "quest.village-quest.daily.smelt.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS),
                DailyQuestService.smithSmeltOreTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS),
                DailyQuestService.smithSmeltIngotTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS) >= DailyQuestService.smithSmeltOreTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS) >= DailyQuestService.smithSmeltIngotTarget()
                && hasTurnInItems(player);
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.smelt.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.smelt.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.smelt.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeInventoryItem(player, Items.RAW_IRON, DailyQuestService.smithSmeltOreTarget())
                && DailyQuestService.consumeInventoryItem(player, Items.IRON_INGOT, DailyQuestService.smithSmeltIngotTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        int oreTarget = DailyQuestService.smithSmeltOreTarget();
        int ingotTarget = DailyQuestService.smithSmeltIngotTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS) < oreTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS) < ingotTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.RAW_IRON.getDefaultInstance().getHoverName(),
                DailyQuestService.countInventoryItem(player, Items.RAW_IRON),
                oreTarget,
                Items.IRON_INGOT.getDefaultInstance().getHoverName(),
                DailyQuestService.countInventoryItem(player, Items.IRON_INGOT),
                ingotTarget
        );
    }

    @Override
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!stack.is(Items.RAW_IRON)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS, DailyQuestService.smithSmeltOreTarget(), count);
    }

    @Override
    public void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!stack.is(Items.IRON_INGOT)) {
            return;
        }

        incrementProgress(world, player, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS, DailyQuestService.smithSmeltIngotTarget(), stack.getCount());
    }

    private void incrementProgress(ServerLevel world, ServerPlayer player, String key, int target, int amount) {
        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, key);
        if (current >= target) {
            return;
        }

        DailyQuestService.setQuestInt(world, playerId, key, Math.min(target, current + amount));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean hasTurnInItems(ServerPlayer player) {
        return DailyQuestService.countInventoryItem(player, Items.RAW_IRON) >= DailyQuestService.smithSmeltOreTarget()
                && DailyQuestService.countInventoryItem(player, Items.IRON_INGOT) >= DailyQuestService.smithSmeltIngotTarget();
    }
}
