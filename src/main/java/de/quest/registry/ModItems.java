package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.item.ApiaristSmokerItem;
import de.quest.content.item.GroschenItem;
import de.quest.content.item.MagicShardItem;
import de.quest.content.item.MerchantSealItem;
import de.quest.content.item.ShepherdFluteItem;
import de.quest.content.item.StarreachRingItem;
import de.quest.content.item.SurveyorCompassItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ModItems {
    public static Item KUPFER_GROSCHEN;
    public static Item EISEN_GROSCHEN;
    public static Item GOLD_GROSCHEN;
    public static Item MAGIC_SHARD;
    public static Item STARREACH_RING;
    public static Item MERCHANT_SEAL;
    public static Item SHEPHERD_FLUTE;
    public static Item APIARISTS_SMOKER;
    public static Item SURVEYORS_COMPASS;

    private static Identifier id(String path) {
        return Identifier.of(VillageQuest.MOD_ID, path);
    }

    public static void register() {

        Identifier copperId = id("kupfer_groschen");
        RegistryKey<Item> copperKey = RegistryKey.of(RegistryKeys.ITEM, copperId);
        KUPFER_GROSCHEN = new GroschenItem(new Item.Settings()
                .registryKey(copperKey)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".kupfer_groschen.lore")), Formatting.DARK_GRAY);
        Registry.register(Registries.ITEM, copperId, KUPFER_GROSCHEN);

        Identifier ironId = id("eisen_groschen");
        RegistryKey<Item> ironKey = RegistryKey.of(RegistryKeys.ITEM, ironId);
        EISEN_GROSCHEN = new GroschenItem(new Item.Settings()
                .registryKey(ironKey)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".eisen_groschen.lore")), Formatting.GRAY);
        Registry.register(Registries.ITEM, ironId, EISEN_GROSCHEN);

        Identifier goldId = id("gold_groschen");
        RegistryKey<Item> goldKey = RegistryKey.of(RegistryKeys.ITEM, goldId);
        GOLD_GROSCHEN = new GroschenItem(new Item.Settings()
                .registryKey(goldKey)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".gold_groschen.lore")), Formatting.GOLD);
        Registry.register(Registries.ITEM, goldId, GOLD_GROSCHEN);

        Identifier shardId = id("magic_shard");
        RegistryKey<Item> shardKey = RegistryKey.of(RegistryKeys.ITEM, shardId);
        MAGIC_SHARD = new MagicShardItem(new Item.Settings()
                .registryKey(shardKey)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".magic_shard.lore")));
        Registry.register(Registries.ITEM, shardId, MAGIC_SHARD);

        Identifier ringId = id("starreach_ring");
        RegistryKey<Item> ringKey = RegistryKey.of(RegistryKeys.ITEM, ringId);
        STARREACH_RING = new StarreachRingItem(new Item.Settings()
                .registryKey(ringKey)
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".starreach_ring.lore")));
        Registry.register(Registries.ITEM, ringId, STARREACH_RING);

        Identifier sealId = id("merchant_seal");
        RegistryKey<Item> sealKey = RegistryKey.of(RegistryKeys.ITEM, sealId);
        MERCHANT_SEAL = new MerchantSealItem(new Item.Settings()
                .registryKey(sealKey)
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".merchant_seal.lore")));
        Registry.register(Registries.ITEM, sealId, MERCHANT_SEAL);

        Identifier fluteId = id("shepherd_flute");
        RegistryKey<Item> fluteKey = RegistryKey.of(RegistryKeys.ITEM, fluteId);
        SHEPHERD_FLUTE = new ShepherdFluteItem(new Item.Settings()
                .registryKey(fluteKey)
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".shepherd_flute.lore")));
        Registry.register(Registries.ITEM, fluteId, SHEPHERD_FLUTE);

        Identifier smokerId = id("apiarists_smoker");
        RegistryKey<Item> smokerKey = RegistryKey.of(RegistryKeys.ITEM, smokerId);
        APIARISTS_SMOKER = new ApiaristSmokerItem(new Item.Settings()
                .registryKey(smokerKey)
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".apiarists_smoker.lore")));
        Registry.register(Registries.ITEM, smokerId, APIARISTS_SMOKER);

        Identifier compassId = id("surveyors_compass");
        RegistryKey<Item> compassKey = RegistryKey.of(RegistryKeys.ITEM, compassId);
        SURVEYORS_COMPASS = new SurveyorCompassItem(new Item.Settings()
                .registryKey(compassKey)
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".surveyors_compass.lore")));
        Registry.register(Registries.ITEM, compassId, SURVEYORS_COMPASS);

        // Kept separate so active gameplay items stay easy to reason about.
        LegacyCompatibilityItems.registerAll();

        VillageQuest.LOGGER.info("Registered items");
    }

    private static LoreComponent lore(String translationKey) {
        return new LoreComponent(List.of(Text.translatable(translationKey).formatted(Formatting.DARK_GRAY)));
    }

}

