package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class PotatoHarvestDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.POTATO_HARVEST;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.potato.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.potato.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.potato.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.potato.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.POTATO_PROGRESS),
                DailyQuestService.potatoTarget(),
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CARROT_PROGRESS),
                DailyQuestService.carrotTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.POTATO_PROGRESS) >= DailyQuestService.potatoTarget()
                && DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CARROT_PROGRESS) >= DailyQuestService.carrotTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.potato.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.potato.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.potato.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onTrackedItemPickup(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int count) {
        if (!DailyQuestService.isTrackingQuest(world, player.getUuid(), type())) return;
        if (count <= 0) return;

        if (stack.isOf(Items.POTATO)) {
            incrementProgress(world, player, DailyQuestKeys.POTATO_PROGRESS, DailyQuestService.potatoTarget(), count);
        } else if (stack.isOf(Items.CARROT)) {
            incrementProgress(world, player, DailyQuestKeys.CARROT_PROGRESS, DailyQuestService.carrotTarget(), count);
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
}
