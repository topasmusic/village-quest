package de.quest.registry;

import de.quest.VillageQuest;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

final class LegacyCompatibilityItems {
    private static final List<String> ITEM_IDS = List.of(
            "cloud_cape",
            "dekoblock_acacia_log",
            "dekoblock_beacon",
            "dekoblock_honigfass",
            "dekoblock_honigwabe",
            "honig_eimer",
            "honig_fass",
            "honigfass",
            "mini_amethyst_block",
            "mini_honey_block",
            "mini_honey_head",
            "mini_stone",
            "questgemaelde_hund",
            "test_honig"
    );

    private LegacyCompatibilityItems() {
    }

    static void registerAll() {
        for (String itemId : ITEM_IDS) {
            register(itemId);
        }

        VillageQuest.LOGGER.info("Registered {} legacy compatibility items", ITEM_IDS.size());
    }

    private static void register(String path) {
        Identifier itemId = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, itemId);
        Registry.register(BuiltInRegistries.ITEM, itemId, new Item(new Item.Properties().setId(key)));
    }
}
