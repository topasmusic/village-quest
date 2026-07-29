package de.quest.content.daily;

import de.quest.quest.QuestCompletionMode;
import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.util.Texts;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class WheatHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public QuestCompletionMode completionMode() {
        return QuestCompletionMode.QUESTMASTER_TURN_IN;
    }

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.WHEAT_HARVEST;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.wheat.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.wheat.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.wheat.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        int wheatProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (wheatProgress < DailyQuestService.wheatTarget()) {
            return Text.translatable(
                    "quest.village-quest.daily.wheat.stage.1",
                    wheatProgress,
                    DailyQuestService.wheatTarget()
            ).formatted(Formatting.GRAY);
        }

        int breadProgress = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS);
        if (breadProgress < DailyQuestService.breadTarget()) {
            return Text.translatable(
                    "quest.village-quest.daily.wheat.stage.2",
                    breadProgress,
                    DailyQuestService.breadTarget()
            ).formatted(Formatting.GRAY);
        }

        ServerPlayerEntity player = world == null ? null : world.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null) {
            Text blocked = claimBlockedMessage(world, player);
            if (blocked != null) {
                return blocked;
            }
        }

        return Text.translatable("quest.village-quest.daily.wheat.stage.3").formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) >= DailyQuestService.wheatTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) >= DailyQuestService.breadTarget()
                && hasTurnInItems(player);
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.wheat.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wheat.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.wheat.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public boolean consumeCompletionRequirements(ServerWorld world, ServerPlayerEntity player) {
        if (!hasTurnInItems(player)) {
            return false;
        }
        return DailyQuestService.consumeCompletionItemRequirements(world, player, Map.of(
                Items.WHEAT, DailyQuestService.wheatTarget(),
                Items.BREAD, DailyQuestService.breadTarget()
        ));
    }

    @Override
    public Text claimBlockedMessage(ServerWorld world, ServerPlayerEntity player) {
        if (player == null || world == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        int wheatTarget = DailyQuestService.wheatTarget();
        int breadTarget = DailyQuestService.breadTarget();
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) < wheatTarget
                || DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS) < breadTarget
                || hasTurnInItems(player)) {
            return null;
        }
        return Texts.turnInMissing(
                Items.WHEAT.getName(),
                DailyQuestService.countCompletionItem(world, player, Items.WHEAT),
                wheatTarget,
                Items.BREAD.getName(),
                DailyQuestService.countCompletionItem(world, player, Items.BREAD),
                breadTarget
        );
    }

    @Override
    public void onAccepted(ServerWorld world, ServerPlayerEntity player) {
        DailyQuestService.setQuestInt(world, player.getUuid(), DailyQuestKeys.LAST_BREAD_CRAFTED,
                DailyQuestService.getCraftedStat(player, Items.BREAD) + 1);
    }

    @Override
    public void onServerTick(ServerWorld world, ServerPlayerEntity player) {
        if (!DailyQuestService.isTrackingQuest(world, player.getUuid(), type())) return;

        UUID playerId = player.getUuid();
        int craftedBread = DailyQuestService.getCraftedStat(player, Items.BREAD);
        if (DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS) < DailyQuestService.wheatTarget()) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
            return;
        }

        int storedLastCraftedBread = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED);
        if (storedLastCraftedBread == 0) {
            DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
            return;
        }
        int lastCraftedBread = storedLastCraftedBread - 1;
        int delta = craftedBread - lastCraftedBread;
        if (delta > 0) {
            int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS);
            int credit = Math.min(delta, DailyQuestService.breadTarget() - current);
            if (credit > 0) {
                DailyQuestService.addQuestInt(world, playerId, DailyQuestKeys.BREAD_PROGRESS, credit);
                DailyQuestService.completeIfEligible(world, player);
                DailyQuestService.sendCurrentProgressActionbar(world, player);
            }
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.LAST_BREAD_CRAFTED, craftedBread + 1);
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (!DailyQuestService.isTrackingQuest(world, player.getUuid(), type())) return;
        if (!stack.isOf(Items.WHEAT) || count <= 0) return;
        incrementProgress(world, player, count);
    }

    private void incrementProgress(ServerWorld world, ServerPlayerEntity player, int amount) {
        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS);
        if (current >= DailyQuestService.wheatTarget()) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.WHEAT_PROGRESS, Math.min(DailyQuestService.wheatTarget(), current + amount));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }

    private boolean hasTurnInItems(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        return DailyQuestService.countCompletionItem(world, player, Items.WHEAT) >= DailyQuestService.wheatTarget()
                && DailyQuestService.countCompletionItem(world, player, Items.BREAD) >= DailyQuestService.breadTarget();
    }
}
