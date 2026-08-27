package de.quest.content.item;

import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.quest.special.SurveyorCompassQuestService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SurveyorCompassItem extends Item {
    public SurveyorCompassItem(Properties settings) {
        super(settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !GuildArchiveService.isSuperseded(stack);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld) || !(user instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!GuildArchiveService.validateUse(serverWorld, serverPlayer,
                user.getItemInHand(hand), ArchiveItem.SURVEYORS_COMPASS)) return InteractionResult.FAIL;
        return SurveyorCompassQuestService.useCompass(serverWorld, serverPlayer, user.getItemInHand(hand));
    }
}
