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
import net.minecraft.world.item.ItemStack;

public final class PetCollarDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.PET_COLLAR;
    }

    @Override
    public Component title() {
        return Component.translatable("quest.village-quest.daily.pet_collar.title");
    }

    @Override
    public Component offerParagraph1() {
        return Component.translatable("quest.village-quest.daily.pet_collar.offer.1").withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    public Component offerParagraph2() {
        return Component.translatable("quest.village-quest.daily.pet_collar.offer.2").withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    public Component progressLine(ServerLevel world, UUID playerId) {
        int target = DailyQuestService.petCollarTarget();
        return Component.translatable(
                "quest.village-quest.daily.pet_collar.progress",
                Math.min(target, DailyQuestService.getQuestInt(world, playerId, DailyQuestKeys.PET_COLLAR_PROGRESS)),
                target
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        int progress = DailyQuestService.getQuestInt(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_PROGRESS);
        return progress >= DailyQuestService.petCollarTarget()
                || (progress == 0 && DailyQuestService.hasQuestFlag(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_DONE));
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerLevel world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Component.translatable("quest.village-quest.daily.pet_collar.completion.1").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.pet_collar.completion.2").withStyle(ChatFormatting.GRAY),
                Component.translatable("quest.village-quest.daily.pet_collar.completion.3").withStyle(ChatFormatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    public static void trackSuccessfulRecolor(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        if (!DailyQuestService.isTrackingQuest(world, player.getUUID(), DailyQuestService.DailyQuestType.PET_COLLAR)) {
            return;
        }
        int progress = DailyQuestService.getQuestInt(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_PROGRESS) + 1;
        DailyQuestService.setQuestInt(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_PROGRESS, progress);
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
