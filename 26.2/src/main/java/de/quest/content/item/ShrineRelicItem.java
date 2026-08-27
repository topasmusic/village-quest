package de.quest.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ShrineRelicItem extends Item {
    private final boolean foil;
    public ShrineRelicItem(Properties properties, boolean foil) { super(properties); this.foil = foil; }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.GOLD); }
    @Override public boolean isFoil(ItemStack stack) { return foil; }
}
