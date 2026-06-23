package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class CoalMiningDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.COAL_MINING;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.coal.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.coal.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.coal.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        ServerPlayer player = world == null ? null : world.getServer().getPlayerList().getPlayer(playerId);
        int ironProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS);
        int coalProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS);
        int ironTarget = DailyQuestService.ironTarget();
        int coalTarget = DailyQuestService.smithCoalTarget();
        if (player != null) {
            Component blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Component.translatable(
                "quest.village-quest.daily.coal.progress",
                ironProgress,
                ironTarget,
                coalProgress,
                coalTarget
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS) >= DailyQuestService.ironTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS) >= DailyQuestService.smithCoalTarget()
                && hasTurnInItems(player);
    }

    @Override
    public boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeCompletionItem(world, player, Items.RAW_IRON, DailyQuestService.ironTarget())
                && DailyQuestService.consumeCompletionItem(world, player, Items.COAL, DailyQuestService.smithCoalTarget());
    }

    @Override
    public Component claimBlockedMessage(ServerLevel world, ServerPlayer player) {
        if (player == null || world == null) {
            return null;
        }

        UUID playerId = player.getUUID();
        int ironTarget = DailyQuestService.ironTarget();
        int coalTarget = DailyQuestService.smithCoalTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.IRON_PROGRESS) < ironTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SMITH_COAL_PROGRESS) < coalTarget
                || hasTurnInItems(player)) {
            return null;
        }

        return Texts.turnInMissing(
                Items.RAW_IRON.getDefaultInstance().getDisplayName(),
                DailyQuestService.countCompletionItem(world, player, Items.RAW_IRON),
                ironTarget,
                Items.COAL.getDefaultInstance().getDisplayName(),
                DailyQuestService.countCompletionItem(world, player, Items.COAL),
                coalTarget
        );
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.coal.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.coal.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.coal.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (stack.is(Items.RAW_IRON)) {
            incrementProgress(world, player, DailyQuestKeys.IRON_PROGRESS, DailyQuestService.ironTarget(), count);
        } else if (stack.is(Items.COAL)) {
            incrementProgress(world, player, DailyQuestKeys.SMITH_COAL_PROGRESS, DailyQuestService.smithCoalTarget(), count);
        }
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
        ServerLevel world = (ServerLevel) player.level();
        return DailyQuestService.countCompletionItem(world, player, Items.RAW_IRON) >= DailyQuestService.ironTarget()
                && DailyQuestService.countCompletionItem(world, player, Items.COAL) >= DailyQuestService.smithCoalTarget();
    }
}
