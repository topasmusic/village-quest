package de.quest.content.item;

import de.quest.quest.special.ShepherdFluteQuestService;
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

public final class ShepherdFluteItem extends Item {
    public ShepherdFluteItem(Properties settings) {
        super(settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!(world instanceof ServerLevel serverWorld) || !(user instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return ShepherdFluteQuestService.useFlute(serverWorld, serverPlayer)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }
}
