package de.quest.content.item;

import de.quest.shrine.VillageBondService;
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

public final class CartographersLensItem extends Item {
    public CartographersLensItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.GOLD); }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel world && player instanceof ServerPlayer serverPlayer) return VillageBondService.useLens(world, serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
