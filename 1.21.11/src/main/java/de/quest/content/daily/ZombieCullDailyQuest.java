package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class ZombieCullDailyQuest implements DailyQuestDefinition {
    private static final int TARGET = 16;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.ZOMBIE_CULL;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.zombie_cull.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.zombie_cull.offer.1").formatted(Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.zombie_cull.offer.2").formatted(Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.zombie_cull.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS),
                TARGET
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.getQuestInt(world, player.getUuid(), DailyQuestKeys.ZOMBIE_CULL_PROGRESS) >= TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.zombie_cull.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.zombie_cull.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.zombie_cull.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onMonsterKill(ServerWorld world, ServerPlayerEntity player, Entity killedEntity) {
        if (!(killedEntity instanceof ZombieEntity)) {
            return;
        }
        if (DailyQuestService.hasCompletedToday(world, player.getUuid()) || !DailyQuestService.isAcceptedToday(world, player.getUuid())) {
            return;
        }

        UUID playerId = player.getUuid();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS);
        if (current >= TARGET) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS, Math.min(TARGET, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
