package de.quest.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GroschenItem extends Item {
    private final ChatFormatting nameColor;

    public GroschenItem(Properties settings, ChatFormatting nameColor) {
        super(settings);
        this.nameColor = nameColor;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(nameColor);
    }
}



