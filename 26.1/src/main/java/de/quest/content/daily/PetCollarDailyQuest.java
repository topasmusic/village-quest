package de.quest.content.daily;

import de.quest.quest.daily.DailyQuestCompletion;
import de.quest.quest.daily.DailyQuestDefinition;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
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
        return Component.translatable(
                "quest.village-quest.daily.pet_collar.progress",
                DailyQuestService.hasQuestFlag(world, playerId, DailyQuestKeys.PET_COLLAR_DONE) ? 1 : 0
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isComplete(ServerLevel world, ServerPlayer player) {
        return DailyQuestService.hasQuestFlag(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_DONE);
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

    @Override
    public void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {
        if (DailyQuestService.hasCompletedToday(world, player.getUUID())) return;
        if (!DailyQuestService.isAcceptedToday(world, player.getUUID())) return;
        if (!(entity instanceof TamableAnimal tameable)) return;
        if (!tameable.isTame() || !tameable.isOwnedBy(player)) return;
        if (!(inHand.getItem() instanceof DyeItem)) return;
        DyeColor dyeColor = inHand.get(DataComponents.DYE);
        if (dyeColor == null) return;

        boolean validCollarRecolor = false;
        if (entity instanceof Wolf wolf) {
            validCollarRecolor = dyeColor != wolf.getCollarColor();
        } else if (entity instanceof Cat cat) {
            validCollarRecolor = dyeColor != cat.getCollarColor();
        }
        if (!validCollarRecolor) return;

        DailyQuestService.setQuestFlag(world, player.getUUID(), DailyQuestKeys.PET_COLLAR_DONE, true);
        DailyQuestService.completeIfEligible(world, player);
        DailyQuestService.sendCurrentProgressActionbar(world, player);
    }
}
