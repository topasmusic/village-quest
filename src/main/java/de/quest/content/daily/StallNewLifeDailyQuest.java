package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class StallNewLifeDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.STALL_NEW_LIFE;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.stall.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.stall.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.stall.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.stall.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS),
                DailyQuestService.stallBreedTarget()
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.getQuestInt(world, player.getUuid(), DailyQuestKeys.STALL_BREED_PROGRESS) >= DailyQuestService.stallBreedTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.stall.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.stall.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.stall.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onAnimalLove(ServerWorld world, ServerPlayerEntity player, AnimalEntity animal) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;

        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS);
        if (current >= DailyQuestService.stallBreedTarget()) {
            return;
        }

        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS, Math.min(DailyQuestService.stallBreedTarget(), current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
