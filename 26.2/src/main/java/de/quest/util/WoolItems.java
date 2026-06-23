package de.quest.util;

import java.util.Set;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class WoolItems {
    public static final Item[] ALL = Items.WOOL.asList().toArray(Item[]::new);
    private static final Set<Item> ALL_SET = Set.copyOf(Items.WOOL.asList());

    private WoolItems() {}

    public static boolean isWool(Item item) {
        return item != null && ALL_SET.contains(item);
    }

    public static Item itemFor(DyeColor color) {
        return color == null ? null : Items.WOOL.pick(color);
    }
}
