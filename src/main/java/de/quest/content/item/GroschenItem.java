package de.quest.content.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GroschenItem extends Item {
    private final Formatting nameColor;

    public GroschenItem(Settings settings, Formatting nameColor) {
        super(settings);
        this.nameColor = nameColor;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey()).formatted(nameColor);
    }
}



