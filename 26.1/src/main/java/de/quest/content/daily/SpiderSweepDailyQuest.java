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
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.ItemStack;

public final class SpiderSweepDailyQuest implements DailyQuestDefinition {
    private static final int TARGET = 6;

    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.SPIDER_SWEEP;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.spider_sweep.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.spider_sweep.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.spider_sweep.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.spider_sweep.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SPIDER_SWEEP_PROGRESS),
                TARGET
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.getQuestInt(world, player.getUUID(), DailyQuestKeys.SPIDER_SWEEP_PROGRESS) >= TARGET;
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.spider_sweep.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.spider_sweep.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.spider_sweep.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {
        if (!(killedEntity instanceof Spider)) {
            return;
        }
        if (DailyQuestService.hasCompletedToday(world, player.getUUID()) || !DailyQuestService.isAcceptedToday(world, player.getUUID())) {
            return;
        }

        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.SPIDER_SWEEP_PROGRESS);
        if (current >= TARGET) {
            return;
        }
        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.SPIDER_SWEEP_PROGRESS, Math.min(TARGET, current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
