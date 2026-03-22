package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class PetCollarDailyQuest implements DailyQuestDefinition {
    @Override
    public DailyQuestService.DailyQuestType type() {
        return DailyQuestService.DailyQuestType.PET_COLLAR;
    }

    @Override
    public Text title() {
        return Text.translatable("quest.village-quest.daily.pet_collar.title");
    }

    @Override
    public Text offerParagraph1() {
        return Text.translatable("quest.village-quest.daily.pet_collar.offer.1").formatted(net.minecraft.util.Formatting.GRAY);
    }

    @Override
    public Text offerParagraph2() {
        return Text.translatable("quest.village-quest.daily.pet_collar.offer.2").formatted(net.minecraft.util.Formatting.GRAY);
    }

    @Override
    public Text progressLine(ServerWorld world, UUID playerId) {
        return Text.translatable(
                "quest.village-quest.daily.pet_collar.progress",
                DailyQuestService.hasQuestFlag(world, playerId, DailyQuestKeys.PET_COLLAR_DONE) ? 1 : 0
        ).formatted(Formatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerWorld world, ServerPlayerEntity player) {
        return DailyQuestService.hasQuestFlag(world, player.getUuid(), DailyQuestKeys.PET_COLLAR_DONE);
    }

    @Override
    public DailyQuestCompletion buildCompletion(ServerWorld world) {
        return DailyQuestService.buildCompletion(
                type(),
                title(),
                Text.translatable("quest.village-quest.daily.pet_collar.completion.1").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.pet_collar.completion.2").formatted(Formatting.GRAY),
                Text.translatable("quest.village-quest.daily.pet_collar.completion.3").formatted(Formatting.GRAY),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
    }

    @Override
    public void onEntityUse(ServerWorld world, ServerPlayerEntity player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUuid())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUuid())) return;
        if (!(entity instanceof TameableEntity tameable)) return;
        if (!tameable.isTamed()) return;
        if (!(inHand.getItem() instanceof DyeItem)) return;

        DailyQuestService.setQuestFlag(world, player.getUuid(), DailyQuestKeys.PET_COLLAR_DONE, true);
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
