package de.quest.registry;

import de.quest.VillageQuest;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.List;

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
        Identifier itemId = Identifier.of(VillageQuest.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemId);
        Registry.register(Registries.ITEM, itemId, new Item(new Item.Settings().registryKey(key)));
    }
}
