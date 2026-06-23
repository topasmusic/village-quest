package de.quest.content.weekly;

import de.quest.quest.weekly.WeeklyQuestCompletion;
import de.quest.quest.weekly.WeeklyQuestDefinition;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

public final class NightWatchWeeklyQuest implements WeeklyQuestDefinition {
    @Override
    public WeeklyQuestService.WeeklyQuestType type() {
        return WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.weekly.nightwatch.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.weekly.nightwatch.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.weekly.nightwatch.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public List<Component> progressLines(ServerLevel world, UUID playerId) {
        return List.of(
                Component.translatable(
                        "quest.village-quest.weekly.nightwatch.progress.1",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES),
                        WeeklyQuestService.nightWatchZombieTarget()
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "quest.village-quest.weekly.nightwatch.progress.2",
                        WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS),
                        WeeklyQuestService.nightWatchSkeletonTarget()
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        return WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES) >= WeeklyQuestService.nightWatchZombieTarget()
                && WeeklyQuestService.getQuestInt(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS) >= WeeklyQuestService.nightWatchSkeletonTarget();
    }

    @Override
    public WeeklyQuestCompletion buildCompletion() {
        return WeeklyQuestService.buildCompletion(
                title(),
                Component.translatable("quest.village-quest.weekly.nightwatch.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.nightwatch.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.weekly.nightwatch.completion.3").withStyle(ChatFormatting.GRAY),
                WeeklyQuestService.reward(2, 0),
                WeeklyQuestService.magicShardReward(2),
                ItemStack.EMPTY,
                18,
                null,
                0
        );
    }

    @Override
    public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (!WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID()) || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID()) || killedEntity == null) {
            return;
        }

        UUID playerId = player.getUUID();
        boolean changed = false;
        if (killedEntity instanceof Zombie) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.NIGHTWATCH_ZOMBIES, 1, WeeklyQuestService.nightWatchZombieTarget());
            changed = true;
        }
        if (killedEntity instanceof AbstractSkeleton) {
            WeeklyQuestService.addQuestIntClamped(world, playerId, WeeklyQuestKeys.NIGHTWATCH_SKELETONS, 1, WeeklyQuestService.nightWatchSkeletonTarget());
            changed = true;
        }
        if (changed) {
            WeeklyQuestService.completeIfEligible(world, player);
        }
    }
}
