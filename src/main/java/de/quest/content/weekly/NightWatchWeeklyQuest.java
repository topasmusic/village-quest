package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class NightWatchWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.weekly.nightwatch.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.weekly.nightwatch.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.weekly.nightwatch.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public List<Text> progressLines(ServerWorld world, UUID playerId) {
        return List.of(
                Text.translatable(
                        "quest.village-quest.weekly.nightwatch.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES),
                        WeeklyQuestService.nightWatchZombieTarget()
                ).formatted(Formatting.GRAY),
                Text.translatable(
                        "quest.village-quest.weekly.nightwatch.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS),
                        WeeklyQuestService.nightWatchSkeletonTarget()
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES) >= WeeklyQuestService.nightWatchZombieTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS) >= WeeklyQuestService.nightWatchSkeletonTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Text.translatable("quest.village-quest.weekly.nightwatch.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.nightwatch.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.weekly.nightwatch.completion.3").formatted(Formatting.GRAY),
                WeeklyQuestService.reward(2, 0),
                WeeklyQuestService.magicShardReward(2),
                ItemStack.EMPTY,
                1200,
                null,
                0
        );
    }

    @Override
    public void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid()) || killedEntity == null) {
            return;
        }

        UUID playerId = player.getUuid();
        boolean changed = false;
        if (killedEntity instanceof ZombieEntity) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES, 1, WeeklyQuestService.nightWatchZombieTarget());
            changed = true;
        }
        if (killedEntity instanceof AbstractSkeletonEntity) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS, 1, WeeklyQuestService.nightWatchSkeletonTarget());
            changed = true;
        }
        if (changed) {
            WeeklyQuestService.completeIfEligible(world, player);
        }
    }
}
