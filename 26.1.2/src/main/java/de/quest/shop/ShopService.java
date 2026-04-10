package de.quest.shop;

import de.quest.VillageQuest;
import de.quest.economy.CurrencyService;
import de.quest.painting.PaintingStackFactory;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.pilgrim.PilgrimContractType;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.story.VillageProjectService;
import de.quest.quest.story.VillageProjectType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import de.quest.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class ShopService {
    private static final long PRICE_MULTIPLIER = 3L;
    private static final long MULTI_ITEM_BUNDLE_PRICE = price(6L);
    private static final long PREMIUM_HEAD_PRICE = CurrencyService.toBase(5L, CurrencyService.CurrencyUnit.CROWN);
    private static final long PLAQUE_PRICE = CurrencyService.toBase(3L, CurrencyService.CurrencyUnit.CROWN)
            + CurrencyService.toBase(5L, CurrencyService.CurrencyUnit.SILVERMARK);
    private static final String BARREL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdhOGRiMjFhNDUxOTQ0NGZkZDM2OTNmN2NmMjExMTFjMjhhNjk5NmNiZDNhYmM0MTZiM2QxNWM1YmFlN2VmMyJ9fX0=";
    private static final String CHISELED_BOOKSHELF_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWUxZTEyNzg1YjlmZGFiMTllMWIxNGMwNjEwNDZhMzk3YjNmZDM4ZmMzYmU2NzJkMWQ3ZGIyZTJhNTU1MzZiYyJ9fX0=";
    private static final String HAYBALE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzYyYjcyZWJmZGU0ZjY0YzRhNTNjNzlmY2MwZWE0YmE3NzA3N2NkNmViNTkwMzg3OGU3OGRjN2Q2NzViYjNmZCJ9fX0=";
    private static final String WATER_BUCKET_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQxOWJhMjU1ZmIxODM2M2ZlYmUwMzgxMGNhMGU1ZDY1NjgxYWUzODMzOWRlNGYzNTQ3MzBiOTVmOTVhZjJkNiJ9fX0=";
    private static final String LAVA_BUCKET_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmYzNzRjODYzODQ3N2QyM2U2MGMxMzQxZjAwN2FiMDE0ZjBjOTVkYzZkNWQzZTU0MGUyOGRlZTM1MWE1MGE0MyJ9fX0=";
    private static final String FURNACE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWQzYzUzMTVlZWM3NGVjOGU1NmUxM2JkMTI5ZDY2YWY5ZWQ5N2NjNDdjNzQ3YjQ2NWFlNjUxNjA3MDFhZjUwYyJ9fX0=";
    private static final String BARREL_OF_WATER_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGFhM2RjMmQ0NWQzMjc5ZGI3ZGYzZjY5M2Y3YzQ5M2RkMWIyOGE0NmE3ZWZmYmMxMmQ0ZjZjZDhiNDE5MDZjZCJ9fX0=";
    private static final String SKELETON_PLUSHIE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWY4ZWYxN2RiNTU0YTY4OGQ2MjVhMTkwYmFmOWE1MjI0YjY4MzcxZWY5NjRkMzZmOWNiMTM2NDViZmY0NWYyYSJ9fX0=";
    private static final String ZOMBIE_PLUSHIE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWZiNGJmZGRjMTM1Mjk4ZDE5OTg1ZTkwMWNkOWI5ZWVkMTdjYjUyOTQzYTA0MjY2YTVlZmNhYzYyYzkxNmQ5ZiJ9fX0=";
    private static final String KOALA_PLUSHIE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzI4ZTM3OTY0MmZkODYzMDc3MTYwNjA1OGUxNWZmNGZhNjE0N2U3YmY1MmQwODRlZTU0ZGNlZmQxOTdhNzY0MSJ9fX0=";
    private static final String CREEPER_PLUSHIE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFlOWI3MDFlNTg3ZjRkNDJhZWMyOGQzM2MzMmZlNDBkOGMwMmQ2ZGYwM2U3M2M1ODczOWY4MTQwNWE4MDJkMCJ9fX0=";
    private static final String BARREL_OF_WINE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGJjMzU1ZWY0MWJjOWY4MTkyNjBhODFlNzBhN2JmMjg5MmIyOTUyMzgyNmVkNGQwZmE0MDBjNTgzMzRjY2ZlMCJ9fX0=";
    private static final Set<String> HEAD_DECOR_OFFERS = Set.of(
            "honigfass",
            "bienenwabe",
            "decor_barrel",
            "decor_chiseled_bookshelf",
            "decor_haybale",
            "decor_water_bucket",
            "decor_lava_bucket",
            "decor_furnace",
            "decor_barrel_of_water",
            "decor_skeleton_plushie",
            "decor_zombie_plushie",
            "decor_koala_plushie",
            "decor_creeper_plushie",
            "decor_barrel_of_wine"
    );
    private static final Map<String, PilgrimContractType> PILGRIM_CONTRACT_UNLOCKS = Map.of(
            "decor_skeleton_plushie", PilgrimContractType.QUENCH_FOR_THE_HALL,
            "decor_zombie_plushie", PilgrimContractType.TRACKS_IN_THE_DARK,
            "decor_creeper_plushie", PilgrimContractType.WOOL_BEFORE_RAIN
    );
    private static final Map<String, VillageProjectType> PROJECT_UNLOCKS = Map.ofEntries(
            Map.entry("village_ledger_desk", VillageProjectType.VILLAGE_LEDGER),
            Map.entry("apiary_charter_plaque", VillageProjectType.APIARY_CHARTER),
            Map.entry("village_ledger_plaque", VillageProjectType.VILLAGE_LEDGER),
            Map.entry("forge_charter_plaque", VillageProjectType.FORGE_CHARTER),
            Map.entry("market_charter_plaque", VillageProjectType.MARKET_CHARTER),
            Map.entry("pasture_charter_plaque", VillageProjectType.PASTURE_CHARTER),
            Map.entry("watch_bell_reliquary", VillageProjectType.WATCH_BELL),
            Map.entry("apiary_supply_crate", VillageProjectType.APIARY_CHARTER),
            Map.entry("smithing_supply_rack", VillageProjectType.FORGE_CHARTER),
            Map.entry("market_stall_kit", VillageProjectType.MARKET_CHARTER),
            Map.entry("pasture_tack_bundle", VillageProjectType.PASTURE_CHARTER),
            Map.entry("watch_post_kit", VillageProjectType.WATCH_BELL)
    );
    private static final Map<String, ShopOffer> OFFERS = createOffers();

    private ShopService() {}

    private static Map<String, ShopOffer> createOffers() {
        Map<String, ShopOffer> offers = new LinkedHashMap<>();
        offers.put("proviantbeutel", new ShopOffer(
                "proviantbeutel",
                "travel",
                Component.translatable("text.village-quest.shop.offer.proviantbeutel.title"),
                Component.translatable("text.village-quest.shop.offer.proviantbeutel.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BUNDLE, "text.village-quest.shop.offer.proviantbeutel.title"),
                (world, player) -> deliver(player, createProvisionsSatchelLoot(world))
        ));
        offers.put("hunters_satchel", new ShopOffer(
                "hunters_satchel",
                "travel",
                Component.translatable("text.village-quest.shop.offer.hunters_satchel.title"),
                Component.translatable("text.village-quest.shop.offer.hunters_satchel.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BUNDLE, "text.village-quest.shop.offer.hunters_satchel.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.ARROW, 32),
                        new ItemStack(Items.COOKED_BEEF, 6))
        ));
        offers.put("honigfass", new ShopOffer(
                "honigfass",
                "decor",
                Component.translatable("text.village-quest.shop.offer.honigfass.title"),
                Component.translatable("text.village-quest.shop.offer.honigfass.description"),
                PREMIUM_HEAD_PRICE,
                world -> VillageQuest.createHoneyBarrelHead(),
                (world, player) -> deliver(player, VillageQuest.createHoneyBarrelHead())
        ));
        offers.put("bienenwabe", new ShopOffer(
                "bienenwabe",
                "decor",
                Component.translatable("text.village-quest.shop.offer.bienenwabe.title"),
                Component.translatable("text.village-quest.shop.offer.bienenwabe.description"),
                PREMIUM_HEAD_PRICE,
                world -> VillageQuest.createAltRewardHead(),
                (world, player) -> deliver(player, VillageQuest.createAltRewardHead())
        ));
        addDecorHeadOffer(
                offers,
                "decor_barrel",
                "text.village-quest.shop.offer.decor_barrel.title",
                "text.village-quest.shop.offer.decor_barrel.description",
                BARREL_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_chiseled_bookshelf",
                "text.village-quest.shop.offer.decor_chiseled_bookshelf.title",
                "text.village-quest.shop.offer.decor_chiseled_bookshelf.description",
                CHISELED_BOOKSHELF_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_haybale",
                "text.village-quest.shop.offer.decor_haybale.title",
                "text.village-quest.shop.offer.decor_haybale.description",
                HAYBALE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_water_bucket",
                "text.village-quest.shop.offer.decor_water_bucket.title",
                "text.village-quest.shop.offer.decor_water_bucket.description",
                WATER_BUCKET_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_lava_bucket",
                "text.village-quest.shop.offer.decor_lava_bucket.title",
                "text.village-quest.shop.offer.decor_lava_bucket.description",
                LAVA_BUCKET_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_furnace",
                "text.village-quest.shop.offer.decor_furnace.title",
                "text.village-quest.shop.offer.decor_furnace.description",
                FURNACE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_barrel_of_water",
                "text.village-quest.shop.offer.decor_barrel_of_water.title",
                "text.village-quest.shop.offer.decor_barrel_of_water.description",
                BARREL_OF_WATER_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_skeleton_plushie",
                "text.village-quest.shop.offer.decor_skeleton_plushie.title",
                "text.village-quest.shop.offer.decor_skeleton_plushie.description",
                SKELETON_PLUSHIE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_zombie_plushie",
                "text.village-quest.shop.offer.decor_zombie_plushie.title",
                "text.village-quest.shop.offer.decor_zombie_plushie.description",
                ZOMBIE_PLUSHIE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_koala_plushie",
                "text.village-quest.shop.offer.decor_koala_plushie.title",
                "text.village-quest.shop.offer.decor_koala_plushie.description",
                KOALA_PLUSHIE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_creeper_plushie",
                "text.village-quest.shop.offer.decor_creeper_plushie.title",
                "text.village-quest.shop.offer.decor_creeper_plushie.description",
                CREEPER_PLUSHIE_TEXTURE
        );
        addDecorHeadOffer(
                offers,
                "decor_barrel_of_wine",
                "text.village-quest.shop.offer.decor_barrel_of_wine.title",
                "text.village-quest.shop.offer.decor_barrel_of_wine.description",
                BARREL_OF_WINE_TEXTURE
        );
        offers.put("beekeepers_kit", new ShopOffer(
                "beekeepers_kit",
                "utility",
                Component.translatable("text.village-quest.shop.offer.beekeepers_kit.title"),
                Component.translatable("text.village-quest.shop.offer.beekeepers_kit.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BEEHIVE, "text.village-quest.shop.offer.beekeepers_kit.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.BEEHIVE),
                        new ItemStack(Items.CAMPFIRE),
                        new ItemStack(Items.GLASS_BOTTLE, 8))
        ));
        offers.put("road_camp_kit", new ShopOffer(
                "road_camp_kit",
                "travel",
                Component.translatable("text.village-quest.shop.offer.road_camp_kit.title"),
                Component.translatable("text.village-quest.shop.offer.road_camp_kit.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.CAMPFIRE, "text.village-quest.shop.offer.road_camp_kit.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.WHITE_BED),
                        new ItemStack(Items.CAMPFIRE, 2),
                        new ItemStack(Items.LANTERN, 4),
                        new ItemStack(Items.CHEST, 2),
                        new ItemStack(Items.TORCH, 32),
                        new ItemStack(Items.BREAD, 16),
                        new ItemStack(Items.COOKED_BEEF, 16))
        ));
        offers.put("village_ledger_desk", new ShopOffer(
                "village_ledger_desk",
                "decor",
                Component.translatable("text.village-quest.shop.offer.village_ledger_desk.title"),
                Component.translatable("text.village-quest.shop.offer.village_ledger_desk.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.LECTERN, "text.village-quest.shop.offer.village_ledger_desk.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.LECTERN),
                        new ItemStack(Items.CHISELED_BOOKSHELF, 2),
                        new ItemStack(Items.BOOKSHELF, 4),
                        new ItemStack(Items.ITEM_FRAME, 8),
                        new ItemStack(Items.LANTERN, 4),
                        new ItemStack(Items.WRITABLE_BOOK))
        ));
        addBlockOffer(
                offers,
                "apiary_charter_plaque",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.apiary_charter_plaque.title",
                "text.village-quest.shop.offer.apiary_charter_plaque.description",
                ModItems.APIARY_CHARTER_PLAQUE
        );
        addBlockOffer(
                offers,
                "village_ledger_plaque",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.village_ledger_plaque.title",
                "text.village-quest.shop.offer.village_ledger_plaque.description",
                ModItems.VILLAGE_LEDGER_PLAQUE
        );
        addBlockOffer(
                offers,
                "forge_charter_plaque",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.forge_charter_plaque.title",
                "text.village-quest.shop.offer.forge_charter_plaque.description",
                ModItems.FORGE_CHARTER_PLAQUE
        );
        addBlockOffer(
                offers,
                "market_charter_plaque",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.market_charter_plaque.title",
                "text.village-quest.shop.offer.market_charter_plaque.description",
                ModItems.MARKET_CHARTER_PLAQUE
        );
        addBlockOffer(
                offers,
                "pasture_charter_plaque",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.pasture_charter_plaque.title",
                "text.village-quest.shop.offer.pasture_charter_plaque.description",
                ModItems.PASTURE_CHARTER_PLAQUE
        );
        addBlockOffer(
                offers,
                "watch_bell_reliquary",
                "decor",
                PLAQUE_PRICE,
                "text.village-quest.shop.offer.watch_bell_reliquary.title",
                "text.village-quest.shop.offer.watch_bell_reliquary.description",
                ModItems.WATCH_BELL_RELIQUARY
        );
        offers.put("apiary_supply_crate", new ShopOffer(
                "apiary_supply_crate",
                "utility",
                Component.translatable("text.village-quest.shop.offer.apiary_supply_crate.title"),
                Component.translatable("text.village-quest.shop.offer.apiary_supply_crate.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BEEHIVE, "text.village-quest.shop.offer.apiary_supply_crate.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.BEEHIVE, 2),
                        new ItemStack(Items.CAMPFIRE, 2),
                        new ItemStack(Items.GLASS_BOTTLE, 32),
                        new ItemStack(Items.CANDLE, 8),
                        new ItemStack(Items.OAK_TRAPDOOR, 16))
        ));
        offers.put("smithing_supply_rack", new ShopOffer(
                "smithing_supply_rack",
                "utility",
                Component.translatable("text.village-quest.shop.offer.smithing_supply_rack.title"),
                Component.translatable("text.village-quest.shop.offer.smithing_supply_rack.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.SMITHING_TABLE, "text.village-quest.shop.offer.smithing_supply_rack.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.SMITHING_TABLE, 2),
                        new ItemStack(Items.GRINDSTONE, 2),
                        new ItemStack(Items.LANTERN, 4),
                        new ItemStack(Items.IRON_CHAIN, 24),
                        new ItemStack(Items.IRON_BARS, 32))
        ));
        offers.put("market_stall_kit", new ShopOffer(
                "market_stall_kit",
                "utility",
                Component.translatable("text.village-quest.shop.offer.market_stall_kit.title"),
                Component.translatable("text.village-quest.shop.offer.market_stall_kit.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BARREL, "text.village-quest.shop.offer.market_stall_kit.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.BARREL, 4),
                        new ItemStack(Items.LANTERN, 8),
                        new ItemStack(Items.RED_WOOL, 32),
                        new ItemStack(Items.WHITE_WOOL, 32),
                        new ItemStack(Items.SPRUCE_FENCE, 16),
                        new ItemStack(Items.SPRUCE_SIGN, 8))
        ));
        offers.put("pasture_tack_bundle", new ShopOffer(
                "pasture_tack_bundle",
                "utility",
                Component.translatable("text.village-quest.shop.offer.pasture_tack_bundle.title"),
                Component.translatable("text.village-quest.shop.offer.pasture_tack_bundle.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.LEAD, "text.village-quest.shop.offer.pasture_tack_bundle.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.LEAD, 8),
                        new ItemStack(Items.HAY_BLOCK, 16),
                        new ItemStack(Items.OAK_FENCE, 32),
                        new ItemStack(Items.OAK_FENCE_GATE, 4),
                        new ItemStack(Items.LANTERN, 4),
                        new ItemStack(Items.WHITE_CARPET, 8))
        ));
        offers.put("watch_post_kit", new ShopOffer(
                "watch_post_kit",
                "utility",
                Component.translatable("text.village-quest.shop.offer.watch_post_kit.title"),
                Component.translatable("text.village-quest.shop.offer.watch_post_kit.description"),
                MULTI_ITEM_BUNDLE_PRICE,
                world -> previewStack(Items.BELL, "text.village-quest.shop.offer.watch_post_kit.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.BELL, 2),
                        new ItemStack(Items.SPYGLASS),
                        new ItemStack(Items.LANTERN, 8),
                        new ItemStack(Items.SOUL_LANTERN, 4),
                        new ItemStack(Items.TORCH, 64),
                        new ItemStack(Items.DARK_OAK_FENCE, 32))
        ));
        offers.put("gemaelde_begleiter", new ShopOffer(
                "gemaelde_begleiter",
                "decor",
                Component.translatable("text.village-quest.shop.offer.gemaelde_begleiter.title"),
                Component.translatable("text.village-quest.shop.offer.gemaelde_begleiter.description"),
                price(15L),
                world -> DailyQuestService.createCompanionPainting(world, false),
                (world, player) -> deliver(player, DailyQuestService.createCompanionPainting(world, false))
        ));
        offers.put("gemaelde_majestaet", new ShopOffer(
                "gemaelde_majestaet",
                "decor",
                Component.translatable("text.village-quest.shop.offer.gemaelde_majestaet.title"),
                Component.translatable("text.village-quest.shop.offer.gemaelde_majestaet.description"),
                price(15L),
                world -> DailyQuestService.createCompanionPainting(world, true),
                (world, player) -> deliver(player, DailyQuestService.createCompanionPainting(world, true))
        ));
        addPaintingOffer(
                offers,
                "gemaelde_pepe_the_almighty",
                price(10L),
                "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.title",
                "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.description",
                "pepe_the_almighty",
                "item.village-quest.painting.pepe_the_almighty",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_over_there",
                price(10L),
                "text.village-quest.shop.offer.gemaelde_over_there.title",
                "text.village-quest.shop.offer.gemaelde_over_there.description",
                "over_there",
                "item.village-quest.painting.over_there",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_good_doge",
                price(5L),
                "text.village-quest.shop.offer.gemaelde_good_doge.title",
                "text.village-quest.shop.offer.gemaelde_good_doge.description",
                "good_doge",
                "item.village-quest.painting.good_doge",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_something_is_sus",
                price(5L),
                "text.village-quest.shop.offer.gemaelde_something_is_sus.title",
                "text.village-quest.shop.offer.gemaelde_something_is_sus.description",
                "something_is_sus",
                "item.village-quest.painting.something_is_sus",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_the_legend",
                price(10L),
                "text.village-quest.shop.offer.gemaelde_the_legend.title",
                "text.village-quest.shop.offer.gemaelde_the_legend.description",
                "the_legend",
                "item.village-quest.painting.the_legend",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_ancient_warrior",
                price(5L),
                "text.village-quest.shop.offer.gemaelde_ancient_warrior.title",
                "text.village-quest.shop.offer.gemaelde_ancient_warrior.description",
                "ancient_warrior",
                "item.village-quest.painting.ancient_warrior",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_happy_doge",
                price(15L),
                "text.village-quest.shop.offer.gemaelde_happy_doge.title",
                "text.village-quest.shop.offer.gemaelde_happy_doge.description",
                "happy_doge",
                "item.village-quest.painting.happy_doge",
                "item.village-quest.painting.landscape.lore"
        );
        offers.put("zombie_trophy", new ShopOffer(
                "zombie_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.zombie_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.zombie_trophy.description"),
                price(10L),
                world -> previewStack(Items.ZOMBIE_HEAD, "text.village-quest.shop.offer.zombie_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.ZOMBIE_HEAD))
        ));
        offers.put("skeleton_trophy", new ShopOffer(
                "skeleton_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.skeleton_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.skeleton_trophy.description"),
                price(12L),
                world -> previewStack(Items.SKELETON_SKULL, "text.village-quest.shop.offer.skeleton_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.SKELETON_SKULL))
        ));
        offers.put("creeper_trophy", new ShopOffer(
                "creeper_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.creeper_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.creeper_trophy.description"),
                price(16L),
                world -> previewStack(Items.CREEPER_HEAD, "text.village-quest.shop.offer.creeper_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.CREEPER_HEAD))
        ));
        return offers;
    }

    private static long price(long basePrice) {
        return Math.max(0L, basePrice) * PRICE_MULTIPLIER;
    }

    public static ShopOffer offer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        return OFFERS.get(offerId);
    }

    public static boolean hasOffers() {
        return !OFFERS.isEmpty();
    }

    public static List<String> rollPilgrimOfferIds(net.minecraft.util.RandomSource random, int count) {
        List<String> ids = new ArrayList<>(OFFERS.keySet());
        return shuffleAndTrim(random, ids, count);
    }

    public static List<String> rollPilgrimOfferIds(net.minecraft.util.RandomSource random,
                                                   ServerLevel world,
                                                   UUID playerId,
                                                   int count) {
        List<String> ids = availableOfferIds(world, playerId);
        if (ids.isEmpty()) {
            ids = new ArrayList<>(OFFERS.keySet());
        }
        return shuffleAndTrim(random, ids, count);
    }

    public static List<String> availableOfferIds(ServerLevel world, UUID playerId) {
        List<String> ids = new ArrayList<>();
        for (String offerId : OFFERS.keySet()) {
            if (isOfferUnlocked(world, playerId, offerId)) {
                ids.add(offerId);
            }
        }
        return ids;
    }

    public static boolean isOfferUnlocked(ServerLevel world, UUID playerId, String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }
        if (world == null || playerId == null) {
            return true;
        }
        VillageProjectType projectUnlock = PROJECT_UNLOCKS.get(offerId);
        if (projectUnlock != null && !VillageProjectService.isUnlocked(world, playerId, projectUnlock)) {
            return false;
        }
        PilgrimContractType contractUnlock = PILGRIM_CONTRACT_UNLOCKS.get(offerId);
        if (contractUnlock != null && !PilgrimContractService.hasCompletedContract(world, playerId, contractUnlock)) {
            return false;
        }
        return ReputationService.isOfferUnlocked(world, playerId, offerId);
    }

    private static boolean isProjectUnlocked(ServerLevel world, UUID playerId, String offerId) {
        VillageProjectType projectUnlock = PROJECT_UNLOCKS.get(offerId);
        return projectUnlock == null || VillageProjectService.isUnlocked(world, playerId, projectUnlock);
    }

    private static Component lockedMessage(ServerLevel world, UUID playerId, String offerId) {
        VillageProjectType projectUnlock = PROJECT_UNLOCKS.get(offerId);
        ReputationService.OfferUnlock unlock = ReputationService.unlockForOffer(offerId);
        boolean projectLocked = projectUnlock != null && !isProjectUnlocked(world, playerId, offerId);
        boolean reputationLocked = unlock != null && !ReputationService.isOfferUnlocked(world, playerId, offerId);
        if (projectLocked && reputationLocked) {
            return Component.translatable(
                    "command.village-quest.shop.locked.project_reputation",
                    Component.translatable("quest.village-quest.project." + projectUnlock.id() + ".title"),
                    ReputationService.displayName(unlock.track()),
                    ReputationService.displayUnlockTitle(unlock),
                    Component.literal(Integer.toString(ReputationService.get(world, playerId, unlock.track()))).withStyle(unlock.track().color()),
                    Component.literal(Integer.toString(unlock.requiredReputation())).withStyle(unlock.track().color())
            );
        }
        if (projectLocked) {
            return Component.translatable(
                    "command.village-quest.shop.locked.project",
                    Component.translatable("quest.village-quest.project." + projectUnlock.id() + ".title")
            );
        }
        if (reputationLocked) {
            return Component.translatable(
                    "command.village-quest.shop.locked",
                    ReputationService.displayName(unlock.track()),
                    ReputationService.displayUnlockTitle(unlock),
                    Component.literal(Integer.toString(ReputationService.get(world, playerId, unlock.track()))).withStyle(unlock.track().color()),
                    Component.literal(Integer.toString(unlock.requiredReputation())).withStyle(unlock.track().color())
            );
        }
        return null;
    }

    private static List<String> shuffleAndTrim(net.minecraft.util.RandomSource random, List<String> ids, int count) {
        if (ids.isEmpty() || count <= 0) {
            return List.of();
        }
        for (int i = ids.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            Collections.swap(ids, i, swapIndex);
        }

        List<String> selected = new ArrayList<>(Math.min(count, ids.size()));
        boolean usedHeadDecor = false;
        for (String offerId : ids) {
            boolean isHeadDecor = HEAD_DECOR_OFFERS.contains(offerId);
            if (isHeadDecor && usedHeadDecor) {
                continue;
            }
            selected.add(offerId);
            if (isHeadDecor) {
                usedHeadDecor = true;
            }
            if (selected.size() >= count) {
                break;
            }
        }
        return selected;
    }

    public static int buy(ServerLevel world, ServerPlayer player, String offerId) {
        if (world == null || player == null) {
            return 0;
        }

        ShopOffer offer = offer(offerId);
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("command.village-quest.shop.offer_missing").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        Component lockedMessage = lockedMessage(world, player.getUUID(), offerId);
        if (lockedMessage != null) {
            player.sendSystemMessage(lockedMessage.copy().withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!CurrencyService.canAfford(world, player.getUUID(), offer.price())) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.shop.not_enough",
                    CurrencyService.formatBalance(offer.price()),
                    CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUUID()))
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ShopOffer.PurchaseResult result = offer.purchaseHandler().purchase(world, player);
        if (!result.success()) {
            player.sendSystemMessage(result.message(), false);
            return 0;
        }

        if (!CurrencyService.removeBalance(world, player.getUUID(), offer.price())) {
            player.sendSystemMessage(Component.translatable("command.village-quest.shop.not_enough",
                    CurrencyService.formatBalance(offer.price()),
                    CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUUID()))
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        SpecialQuestService.onPilgrimPurchase(world, player, offerId);
        WeeklyQuestService.onPilgrimPurchase(world, player, offerId);
        long newBalance = CurrencyService.getBalance(world, player.getUUID());
        player.sendSystemMessage(Component.translatable(
                "command.village-quest.shop.buy.success",
                offer.title(),
                CurrencyService.formatBalance(offer.price()),
                CurrencyService.formatBalance(newBalance)
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static ShopOffer.PurchaseResult deliver(ServerPlayer player, ItemStack... stacks) {
        if (player == null || stacks == null || stacks.length == 0) {
            return ShopOffer.PurchaseResult.fail(Component.translatable("command.village-quest.shop.offer_missing").withStyle(ChatFormatting.RED));
        }

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            giveOrDrop(player, stack.copy());
        }
        return ShopOffer.PurchaseResult.success(Component.empty());
    }

    private static ItemStack[] createProvisionsSatchelLoot(ServerLevel world) {
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(new ItemStack(Items.GOLDEN_CARROT, 32));

        if (world != null && world.getRandom().nextFloat() < 0.10f) {
            rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
        }

        rewards.add(rollProvisionsSatchelBonus(world));
        if (world != null && world.getRandom().nextFloat() < 0.65f) {
            rewards.add(rollProvisionsSatchelBonus(world));
        }

        rewards.removeIf(stack -> stack == null || stack.isEmpty());
        return rewards.toArray(ItemStack[]::new);
    }

    private static ItemStack rollProvisionsSatchelBonus(ServerLevel world) {
        int roll = world == null ? 0 : world.getRandom().nextInt(100);
        if (roll < 24) {
            return new ItemStack(Items.GOLD_INGOT, randomRange(world, 8, 20));
        }
        if (roll < 42) {
            return new ItemStack(Items.IRON_INGOT, randomRange(world, 12, 24));
        }
        if (roll < 56) {
            return new ItemStack(Items.EMERALD, randomRange(world, 4, 10));
        }
        if (roll < 64) {
            return new ItemStack(Items.DIAMOND, randomRange(world, 1, 4));
        }
        if (roll < 76) {
            return new ItemStack(Items.TORCH, randomRange(world, 24, 48));
        }
        if (roll < 88) {
            return new ItemStack(Items.COOKED_BEEF, randomRange(world, 8, 16));
        }
        return new ItemStack(Items.COAL, randomRange(world, 16, 32));
    }

    private static int randomRange(ServerLevel world, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        if (world == null) {
            return minInclusive;
        }
        return minInclusive + world.getRandom().nextInt(maxInclusive - minInclusive + 1);
    }

    private static ItemStack previewStack(Item item, String titleKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(titleKey).withStyle(ChatFormatting.GREEN));
        return stack;
    }

    private static void addBlockOffer(Map<String, ShopOffer> offers,
                                      String offerId,
                                      String category,
                                      long price,
                                      String offerTitleKey,
                                      String offerDescriptionKey,
                                      Item item) {
        offers.put(offerId, new ShopOffer(
                offerId,
                category,
                Component.translatable(offerTitleKey),
                Component.translatable(offerDescriptionKey),
                price,
                world -> previewStack(item, offerTitleKey),
                (world, player) -> deliver(player, new ItemStack(item))
        ));
    }

    private static void addDecorHeadOffer(Map<String, ShopOffer> offers,
                                          String offerId,
                                          String offerTitleKey,
                                          String offerDescriptionKey,
                                          String textureValue) {
        String itemTitleKey = decorHeadTitleKey(offerId);
        String itemLoreKey = decorHeadLoreKey(offerId);
        offers.put(offerId, new ShopOffer(
                offerId,
                "decor",
                Component.translatable(offerTitleKey),
                Component.translatable(offerDescriptionKey),
                PREMIUM_HEAD_PRICE,
                world -> VillageQuest.createDecorHead(offerId, itemTitleKey, itemLoreKey, textureValue),
                (world, player) -> deliver(player, VillageQuest.createDecorHead(offerId, itemTitleKey, itemLoreKey, textureValue))
        ));
    }

    private static String decorHeadTitleKey(String offerId) {
        return "item." + VillageQuest.MOD_ID + "." + offerId;
    }

    private static String decorHeadLoreKey(String offerId) {
        return decorHeadTitleKey(offerId) + ".lore";
    }

    private static void addPaintingOffer(Map<String, ShopOffer> offers,
                                         String offerId,
                                         long price,
                                         String offerTitleKey,
                                         String offerDescriptionKey,
                                         String variantPath,
                                         String itemTitleKey,
                                         String loreKey) {
        offers.put(offerId, new ShopOffer(
                offerId,
                "art",
                Component.translatable(offerTitleKey),
                Component.translatable(offerDescriptionKey),
                price,
                world -> createPaintingStack(world, variantPath, itemTitleKey, loreKey),
                (world, player) -> deliver(player, createPaintingStack(world, variantPath, itemTitleKey, loreKey))
        ));
    }

    private static ItemStack createPaintingStack(ServerLevel world, String variantPath, String titleKey, String loreKey) {
        return PaintingStackFactory.create(world, variantPath);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
        }
    }
}
