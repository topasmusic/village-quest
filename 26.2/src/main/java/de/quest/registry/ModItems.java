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
import de.quest.content.item.CartographersLensItem;
import de.quest.content.item.ShrineRelicItem;
import de.quest.content.item.GuildCourierSatchelItem;
import de.quest.content.item.WayfarersSigilItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

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
    public static Item GUILD_WAYSHRINE;
    public static Item GUILD_NOTICE_POST;
    public static Item EMBERGLASS_LANTERN;
    public static Item GUILD_MILESTONE;
    public static Item WAYFARERS_SIGIL;
    public static Item CARTOGRAPHERS_LENS;
    public static Item GUILD_COURIERS_SATCHEL;
    public static Item CRACKED_SHRINE_CORE;
    public static Item RESTORED_SHRINE_CORE;
    /** Save-facing compatibility only; never exposed through recipes or creative tabs. */
    public static Item LEGACY_ROADMENDERS_MALLET;
    public static Item LEGACY_DORMANT_WAYSTONE;
    public static Item LEGACY_ATTUNED_WAYSTONE;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path);
    }

    public static void register() {

        Identifier copperId = id("legacy_copper_penny");
        ResourceKey<Item> copperKey = ResourceKey.create(Registries.ITEM, copperId);
        LEGACY_COPPER_PENNY = new GroschenItem(new Item.Properties()
                .setId(copperKey)
                .component(DataComponents.LORE, lore("item." + VillageQuest.MOD_ID + ".legacy_copper_penny.lore")), ChatFormatting.DARK_GRAY);
        Registry.register(BuiltInRegistries.ITEM, copperId, LEGACY_COPPER_PENNY);

        Identifier ironId = id("silvermark");
        ResourceKey<Item> ironKey = ResourceKey.create(Registries.ITEM, ironId);
        SILVERMARK = new GroschenItem(new Item.Properties()
                .setId(ironKey)
                .component(DataComponents.LORE, lore("item." + VillageQuest.MOD_ID + ".silvermark.lore")), ChatFormatting.GRAY);
        Registry.register(BuiltInRegistries.ITEM, ironId, SILVERMARK);

        Identifier goldId = id("crown");
        ResourceKey<Item> goldKey = ResourceKey.create(Registries.ITEM, goldId);
        CROWN = new GroschenItem(new Item.Properties()
                .setId(goldKey)
                .component(DataComponents.LORE, lore("item." + VillageQuest.MOD_ID + ".crown.lore")), ChatFormatting.GOLD);
        Registry.register(BuiltInRegistries.ITEM, goldId, CROWN);

        Identifier shardId = id("magic_shard");
        ResourceKey<Item> shardKey = ResourceKey.create(Registries.ITEM, shardId);
        MAGIC_SHARD = new MagicShardItem(new Item.Properties()
                .setId(shardKey)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".magic_shard.lore", 4)));
        Registry.register(BuiltInRegistries.ITEM, shardId, MAGIC_SHARD);

        Identifier ringId = id("starreach_ring");
        ResourceKey<Item> ringKey = ResourceKey.create(Registries.ITEM, ringId);
        STARREACH_RING = new StarreachRingItem(new Item.Properties()
                .setId(ringKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".starreach_ring.lore", 2)));
        Registry.register(BuiltInRegistries.ITEM, ringId, STARREACH_RING);

        Identifier sealId = id("merchant_seal");
        ResourceKey<Item> sealKey = ResourceKey.create(Registries.ITEM, sealId);
        MERCHANT_SEAL = new MerchantSealItem(new Item.Properties()
                .setId(sealKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".merchant_seal.lore", 3)));
        Registry.register(BuiltInRegistries.ITEM, sealId, MERCHANT_SEAL);

        Identifier fluteId = id("shepherd_flute");
        ResourceKey<Item> fluteKey = ResourceKey.create(Registries.ITEM, fluteId);
        SHEPHERD_FLUTE = new ShepherdFluteItem(new Item.Properties()
                .setId(fluteKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".shepherd_flute.lore", 2)));
        Registry.register(BuiltInRegistries.ITEM, fluteId, SHEPHERD_FLUTE);

        Identifier smokerId = id("apiarists_smoker");
        ResourceKey<Item> smokerKey = ResourceKey.create(Registries.ITEM, smokerId);
        APIARISTS_SMOKER = new ApiaristSmokerItem(new Item.Properties()
                .setId(smokerKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".apiarists_smoker.lore", 3)));
        Registry.register(BuiltInRegistries.ITEM, smokerId, APIARISTS_SMOKER);

        Identifier compassId = id("surveyors_compass");
        ResourceKey<Item> compassKey = ResourceKey.create(Registries.ITEM, compassId);
        SURVEYORS_COMPASS = new SurveyorCompassItem(new Item.Properties()
                .setId(compassKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".surveyors_compass.lore", 3)));
        Registry.register(BuiltInRegistries.ITEM, compassId, SURVEYORS_COMPASS);

        Identifier ledgerId = id("caravan_ledger");
        ResourceKey<Item> ledgerKey = ResourceKey.create(Registries.ITEM, ledgerId);
        CARAVAN_LEDGER = new CaravanLedgerItem(new Item.Properties()
                .setId(ledgerKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".caravan_ledger.lore", 4)));
        Registry.register(BuiltInRegistries.ITEM, ledgerId, CARAVAN_LEDGER);

        Identifier hornId = id("roadwarden_horn");
        ResourceKey<Item> hornKey = ResourceKey.create(Registries.ITEM, hornId);
        ROADWARDEN_HORN = new RoadwardenHornItem(new Item.Properties()
                .setId(hornKey)
                .stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + ".roadwarden_horn.lore", 3)));
        Registry.register(BuiltInRegistries.ITEM, hornId, ROADWARDEN_HORN);

        APIARY_CHARTER_PLAQUE = registerBlockItem("apiary_charter_plaque", ModBlocks.APIARY_CHARTER_PLAQUE);
        VILLAGE_LEDGER_PLAQUE = registerBlockItem("village_ledger_plaque", ModBlocks.VILLAGE_LEDGER_PLAQUE);
        FORGE_CHARTER_PLAQUE = registerBlockItem("forge_charter_plaque", ModBlocks.FORGE_CHARTER_PLAQUE);
        MARKET_CHARTER_PLAQUE = registerBlockItem("market_charter_plaque", ModBlocks.MARKET_CHARTER_PLAQUE);
        PASTURE_CHARTER_PLAQUE = registerBlockItem("pasture_charter_plaque", ModBlocks.PASTURE_CHARTER_PLAQUE);
        WATCH_BELL_RELIQUARY = registerBlockItem("watch_bell_reliquary", ModBlocks.WATCH_BELL_RELIQUARY);
        GUILD_WAYSHRINE = registerBlockItem("guild_wayshrine", ModBlocks.GUILD_WAYSHRINE);
        GUILD_NOTICE_POST = registerBlockItem("guild_notice_post", ModBlocks.GUILD_NOTICE_POST, 16);
        EMBERGLASS_LANTERN = registerBlockItem("emberglass_lantern", ModBlocks.EMBERGLASS_LANTERN, 64);
        GUILD_MILESTONE = registerBlockItem("guild_milestone", ModBlocks.GUILD_MILESTONE, 64);

        WAYFARERS_SIGIL = registerSpecial("wayfarers_sigil", WayfarersSigilItem::new, 3);
        CARTOGRAPHERS_LENS = registerSpecial("cartographers_lens", CartographersLensItem::new, 3);
        GUILD_COURIERS_SATCHEL = registerSpecial("guild_couriers_satchel", GuildCourierSatchelItem::new, 3);
        CRACKED_SHRINE_CORE = registerSpecial("cracked_shrine_core", properties -> new ShrineRelicItem(properties, false), 2);
        RESTORED_SHRINE_CORE = registerSpecial("restored_shrine_core", properties -> new ShrineRelicItem(properties, true), 2);

        LEGACY_ROADMENDERS_MALLET = registerCompatibilityItem("roadmenders_mallet");
        LEGACY_DORMANT_WAYSTONE = registerCompatibilityItem("dormant_waystone");
        LEGACY_ATTUNED_WAYSTONE = registerCompatibilityItem("attuned_waystone");

        VillageQuest.LOGGER.info("Registered items");
    }

    private static ItemLore lore(String translationKey) {
        return new ItemLore(List.of(Component.translatable(translationKey).withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static ItemLore loreLines(String translationKey, int lineCount) {
        List<Component> lines = new java.util.ArrayList<>(lineCount);
        for (int line = 1; line <= lineCount; line++) {
            lines.add(Component.translatable(translationKey + "." + line).withStyle(ChatFormatting.DARK_GRAY));
        }
        return new ItemLore(List.copyOf(lines));
    }

    private static Item registerBlockItem(String path, net.minecraft.world.level.block.Block block) {
        return registerBlockItem(path, block, 1);
    }

    private static Item registerBlockItem(String path, net.minecraft.world.level.block.Block block, int stackSize) {
        Identifier itemId = id(path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        Item item = new BlockItem(block, new Item.Properties()
                .setId(itemKey)
                .stacksTo(stackSize)
                .component(DataComponents.LORE, lore("item." + VillageQuest.MOD_ID + "." + path + ".lore")));
        Registry.register(BuiltInRegistries.ITEM, itemId, item);
        return item;
    }

    private static Item registerSpecial(String path,
                                        java.util.function.Function<Item.Properties, Item> factory,
                                        int loreLineCount) {
        Identifier itemId = id(path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        Item item = factory.apply(new Item.Properties().setId(itemKey).stacksTo(1)
                .component(DataComponents.LORE, loreLines("item." + VillageQuest.MOD_ID + "." + path + ".lore", loreLineCount)));
        Registry.register(BuiltInRegistries.ITEM, itemId, item);
        return item;
    }

    private static Item registerCompatibilityItem(String path) {
        Identifier itemId = id(path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        Item item = new Item(new Item.Properties().setId(itemKey).stacksTo(1));
        Registry.register(BuiltInRegistries.ITEM, itemId, item);
        return item;
    }

}

