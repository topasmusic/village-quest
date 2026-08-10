package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.item.ApiaristSmokerItem;
import de.quest.content.item.CaravanLedgerItem;
import de.quest.content.item.GroschenItem;
import de.quest.content.item.MagicShardItem;
import de.quest.content.item.MerchantSealItem;
import de.quest.content.item.RoadwardenHornItem;
import de.quest.content.item.ShepherdFluteItem;
import de.quest.content.item.StarreachRingItem;
import de.quest.content.item.SurveyorCompassItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ModItems {
    public static Item LEGACY_COPPER_PENNY;
    public static Item SILVERMARK;
    public static Item CROWN;
    public static Item MAGIC_SHARD;
    public static Item STARREACH_RING;
    public static Item MERCHANT_SEAL;
    public static Item SHEPHERD_FLUTE;
    public static Item APIARISTS_SMOKER;
    public static Item SURVEYORS_COMPASS;
    public static Item CARAVAN_LEDGER;
    public static Item ROADWARDEN_HORN;
    public static Item APIARY_CHARTER_PLAQUE;
    public static Item VILLAGE_LEDGER_PLAQUE;
    public static Item FORGE_CHARTER_PLAQUE;
    public static Item MARKET_CHARTER_PLAQUE;
    public static Item PASTURE_CHARTER_PLAQUE;
    public static Item WATCH_BELL_RELIQUARY;

    private static Identifier id(String path) {
        return Identifier.of(VillageQuest.MOD_ID, path);
    }

    public static void register() {

        Identifier copperId = id("legacy_copper_penny");
        LEGACY_COPPER_PENNY = new GroschenItem(new Item.Settings()
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".legacy_copper_penny.lore")), Formatting.DARK_GRAY);
        Registry.register(Registries.ITEM, copperId, LEGACY_COPPER_PENNY);

        Identifier ironId = id("silvermark");
        SILVERMARK = new GroschenItem(new Item.Settings()
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".silvermark.lore")), Formatting.GRAY);
        Registry.register(Registries.ITEM, ironId, SILVERMARK);

        Identifier goldId = id("crown");
        CROWN = new GroschenItem(new Item.Settings()
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + ".crown.lore")), Formatting.GOLD);
        Registry.register(Registries.ITEM, goldId, CROWN);

        Identifier shardId = id("magic_shard");
        MAGIC_SHARD = new MagicShardItem(new Item.Settings()
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".magic_shard.lore", 3)));
        Registry.register(Registries.ITEM, shardId, MAGIC_SHARD);

        Identifier ringId = id("starreach_ring");
        STARREACH_RING = new StarreachRingItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".starreach_ring.lore", 2)));
        Registry.register(Registries.ITEM, ringId, STARREACH_RING);

        Identifier sealId = id("merchant_seal");
        MERCHANT_SEAL = new MerchantSealItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".merchant_seal.lore", 3)));
        Registry.register(Registries.ITEM, sealId, MERCHANT_SEAL);

        Identifier fluteId = id("shepherd_flute");
        SHEPHERD_FLUTE = new ShepherdFluteItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".shepherd_flute.lore", 2)));
        Registry.register(Registries.ITEM, fluteId, SHEPHERD_FLUTE);

        Identifier smokerId = id("apiarists_smoker");
        APIARISTS_SMOKER = new ApiaristSmokerItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".apiarists_smoker.lore", 3)));
        Registry.register(Registries.ITEM, smokerId, APIARISTS_SMOKER);

        Identifier compassId = id("surveyors_compass");
        SURVEYORS_COMPASS = new SurveyorCompassItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".surveyors_compass.lore", 3)));
        Registry.register(Registries.ITEM, compassId, SURVEYORS_COMPASS);

        Identifier ledgerId = id("caravan_ledger");
        CARAVAN_LEDGER = new CaravanLedgerItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".caravan_ledger.lore", 4)));
        Registry.register(Registries.ITEM, ledgerId, CARAVAN_LEDGER);

        Identifier hornId = id("roadwarden_horn");
        ROADWARDEN_HORN = new RoadwardenHornItem(new Item.Settings()
                 .maxCount(1)
                .component(DataComponentTypes.LORE, loreLines("item." + VillageQuest.MOD_ID + ".roadwarden_horn.lore", 3)));
        Registry.register(Registries.ITEM, hornId, ROADWARDEN_HORN);

        APIARY_CHARTER_PLAQUE = registerBlockItem("apiary_charter_plaque", ModBlocks.APIARY_CHARTER_PLAQUE);
        VILLAGE_LEDGER_PLAQUE = registerBlockItem("village_ledger_plaque", ModBlocks.VILLAGE_LEDGER_PLAQUE);
        FORGE_CHARTER_PLAQUE = registerBlockItem("forge_charter_plaque", ModBlocks.FORGE_CHARTER_PLAQUE);
        MARKET_CHARTER_PLAQUE = registerBlockItem("market_charter_plaque", ModBlocks.MARKET_CHARTER_PLAQUE);
        PASTURE_CHARTER_PLAQUE = registerBlockItem("pasture_charter_plaque", ModBlocks.PASTURE_CHARTER_PLAQUE);
        WATCH_BELL_RELIQUARY = registerBlockItem("watch_bell_reliquary", ModBlocks.WATCH_BELL_RELIQUARY);

        VillageQuest.LOGGER.info("Registered items");
    }

    private static LoreComponent lore(String translationKey) {
        return new LoreComponent(List.of(Text.translatable(translationKey).formatted(Formatting.DARK_GRAY)));
    }

    private static LoreComponent loreLines(String translationKey, int lineCount) {
        List<Text> lines = new java.util.ArrayList<>(lineCount);
        for (int line = 1; line <= lineCount; line++) {
            lines.add(Text.translatable(translationKey + "." + line) .formatted(Formatting.DARK_GRAY));
        }
        return new LoreComponent(List.copyOf(lines));
    }

    private static Item registerBlockItem(String path, net.minecraft.block.Block block) {
        Identifier itemId = id(path);
        Item item = new BlockItem(block, new Item.Settings()
                .maxCount(1)
                .component(DataComponentTypes.LORE, lore("item." + VillageQuest.MOD_ID + "." + path + ".lore")));
        Registry.register(Registries.ITEM, itemId, item);
        return item;
    }

}

