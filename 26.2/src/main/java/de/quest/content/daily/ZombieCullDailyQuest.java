package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

public final class ZombieCullDailyQuest implements DailyQuestDefinition {
    private static final int TARGET = 8;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.ZOMBIE_CULL;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.zombie_cull.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.zombie_cull.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.zombie_cull.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.zombie_cull.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS),
                TARGET
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.getQuestInt(world, player.getUUID(), DailyQuestKeys.ZOMBIE_CULL_PROGRESS) >= TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.zombie_cull.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.zombie_cull.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.zombie_cull.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (!(killedEntity instanceof Zombie)) {
            return;
        }
        if (DailyQuestService.hasCompletedToday(world, player.getUUID()) || !DailyQuestService.isAcceptedToday(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS);
        if (current >= TARGET) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.ZOMBIE_CULL_PROGRESS, Math.min(TARGET, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
