package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class CreeperWatchDailyQuest implements DailyQuestDefinition {
    private static final int TARGET = 12;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.CREEPER_WATCH;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.creeper_watch.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.creeper_watch.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.creeper_watch.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.creeper_watch.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CREEPER_WATCH_PROGRESS),
                TARGET
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.getQuestInt(world, player.getUuid(), DailyQuestKeys.CREEPER_WATCH_PROGRESS) >= TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.creeper_watch.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.creeper_watch.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.creeper_watch.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        if (!(killedEntity instanceof CreeperEntity)) {
            return;
        }
        if (DailyQuestService.hasCompletedToday(world, player.getUuid()) || !DailyQuestService.isAcceptedToday(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.CREEPER_WATCH_PROGRESS);
        if (current >= TARGET) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.CREEPER_WATCH_PROGRESS, Math.min(TARGET, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
