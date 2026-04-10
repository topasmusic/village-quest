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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

public final class StallNewLifeDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.STALL_NEW_LIFE;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.stall.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.stall.offer.1").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.stall.offer.2").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        return Component.translatable(
                "quest.village-quest.daily.stall.progress",
                DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS),
                DailyQuestService.stallBreedTarget()
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.getQuestInt(world, player.getUUID(), DailyQuestKeys.STALL_BREED_PROGRESS) >= DailyQuestService.stallBreedTarget();
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.stall.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.stall.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.stall.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;

        UUID playerId = player.getUUID();
        int current = DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS);
        if (current >= DailyQuestService.stallBreedTarget()) {
            return;
        }

        DailyQuestService.setQuestInt(world, playerId, DailyQuestKeys.STALL_BREED_PROGRESS, Math.min(DailyQuestService.stallBreedTarget(), current + 1));
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
