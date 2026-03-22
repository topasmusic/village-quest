package de.quest.content.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MagicShardItem extends Item {
    public MagicShardItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey()).formatted(Formatting.LIGHT_PURPLE);
    }
}
