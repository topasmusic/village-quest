package de.quest.shrine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum VillageRequestType {
    // Existing IDs remain stable for unreleased saves; their delivery amounts
    // are deliberately five times the original quick-preview balance.
    GRANARY_SEED(0, VillageBondType.GRANARY, Items.WHEAT_SEEDS, 160, 24, "granary_seed"),
    GRANARY_BREAD(1, VillageBondType.GRANARY, Items.BREAD, 80, 36, "granary_bread"),
    FORGE_COAL(2, VillageBondType.FORGE, Items.COAL, 160, 27, "forge_coal"),
    FORGE_IRON(3, VillageBondType.FORGE, Items.IRON_INGOT, 80, 42, "forge_iron"),
    PASTURE_FODDER(4, VillageBondType.PASTURE, Items.HAY_BLOCK, 60, 30, "pasture_fodder"),
    PASTURE_WOOL(5, VillageBondType.PASTURE, Items.WOOL.white(), 120, 39, "pasture_wool"),
    APIARY_FLOWERS(6, VillageBondType.APIARY, Items.DANDELION, 120, 27, "apiary_flowers"),
    APIARY_HONEY(7, VillageBondType.APIARY, Items.HONEY_BOTTLE, 40, 42, "apiary_honey"),
    ARCHIVE_PAPER(8, VillageBondType.ARCHIVE, Items.PAPER, 160, 30, "archive_paper"),
    ARCHIVE_BOOKS(9, VillageBondType.ARCHIVE, Items.BOOK, 60, 45, "archive_books"),

    GRANARY_WHEAT(10, VillageBondType.GRANARY, Items.WHEAT, 160, 30, "granary_wheat"),
    GRANARY_POTATOES(11, VillageBondType.GRANARY, Items.POTATO, 160, 32, "granary_potatoes"),
    FORGE_COPPER(12, VillageBondType.FORGE, Items.COPPER_INGOT, 80, 36, "forge_copper"),
    FORGE_STONE_BRICKS(13, VillageBondType.FORGE, Items.STONE_BRICKS, 160, 28, "forge_stone_bricks"),
    PASTURE_LEATHER(14, VillageBondType.PASTURE, Items.LEATHER, 60, 42, "pasture_leather"),
    PASTURE_EGGS(15, VillageBondType.PASTURE, Items.EGG, 120, 30, "pasture_eggs"),
    APIARY_HONEYCOMB(16, VillageBondType.APIARY, Items.HONEYCOMB, 60, 38, "apiary_honeycomb"),
    APIARY_CANDLES(17, VillageBondType.APIARY, Items.CANDLE, 40, 45, "apiary_candles"),
    ARCHIVE_INK(18, VillageBondType.ARCHIVE, Items.INK_SAC, 40, 40, "archive_ink"),
    ARCHIVE_FEATHERS(19, VillageBondType.ARCHIVE, Items.FEATHER, 120, 34, "archive_feathers");

    private static final VillageRequestType[] VALUES = values();

    private final int id;
    private final VillageBondType bondType;
    private final Item item;
    private final int amount;
    private final int reward;
    private final String key;

    VillageRequestType(int id, VillageBondType bondType, Item item, int amount, int reward, String key) {
        this.id = id;
        this.bondType = bondType;
        this.item = item;
        this.amount = amount;
        this.reward = reward;
        this.key = key;
    }

    public int id() { return id; }
    public VillageBondType bondType() { return bondType; }
    public Item item() { return item; }
    public int amount() { return amount; }
    public int reward() { return reward; }
    public Component title() { return Component.translatable("text.village-quest.village_request." + key); }

    public static VillageRequestType forVillage(VillageBondType type, int seed) {
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        int target = Math.floorMod(seed, countFor(safeType));
        int count = 0;
        for (VillageRequestType value : VALUES) {
            if (value.bondType != safeType) continue;
            if (count++ == target) return value;
        }
        return GRANARY_SEED;
    }

    /** Deterministic four-entry cycle with no immediate repeat. */
    public static VillageRequestType nextAfter(VillageBondType type, VillageRequestType current) {
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        VillageRequestType first = null;
        boolean returnNext = false;
        for (VillageRequestType value : VALUES) {
            if (value.bondType != safeType) continue;
            if (first == null) first = value;
            if (returnNext) return value;
            if (value == current) returnNext = true;
        }
        return first == null ? GRANARY_SEED : first;
    }

    public static VillageRequestType byId(int id, VillageBondType fallbackType, int seed) {
        for (VillageRequestType value : VALUES) {
            if (value.id == id) return value;
        }
        return forVillage(fallbackType, seed);
    }

    static int countFor(VillageBondType type) {
        int count = 0;
        for (VillageRequestType value : VALUES) {
            if (value.bondType == type) count++;
        }
        return Math.max(1, count);
    }
}
