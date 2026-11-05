package de.quest.village;

import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Identity-specific needs used by the living village supply cycle. */
public enum VillageNeed {
    GRANARY_SEED_RESERVES(0, VillageBondType.GRANARY, "granary_seed_reserves"),
    GRANARY_PANTRY(1, VillageBondType.GRANARY, "granary_pantry"),
    FORGE_FUEL_AND_ORE(2, VillageBondType.FORGE, "forge_fuel_and_ore"),
    FORGE_BUILDING_STOCK(3, VillageBondType.FORGE, "forge_building_stock"),
    PASTURE_FODDER(4, VillageBondType.PASTURE, "pasture_fodder"),
    PASTURE_HUSBANDRY(5, VillageBondType.PASTURE, "pasture_husbandry"),
    APIARY_FORAGE(6, VillageBondType.APIARY, "apiary_forage"),
    APIARY_WAX_STOCK(7, VillageBondType.APIARY, "apiary_wax_stock"),
    ARCHIVE_WRITING_SUPPLIES(8, VillageBondType.ARCHIVE, "archive_writing_supplies"),
    ARCHIVE_PRESERVATION(9, VillageBondType.ARCHIVE, "archive_preservation");

    private static final VillageNeed[] VALUES = values();

    private final int id;
    private final VillageBondType villageType;
    private final String key;

    VillageNeed(int id, VillageBondType villageType, String key) {
        this.id = id;
        this.villageType = villageType;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public VillageBondType villageType() {
        return villageType;
    }

    public String key() {
        return key;
    }

    public Component label() {
        return Component.translatable("text.village-quest.village_network.need." + key);
    }

    public boolean matches(VillageRequestType request) {
        if (request == null || request.bondType() != villageType) return false;
        return switch (this) {
            case GRANARY_SEED_RESERVES -> request == VillageRequestType.GRANARY_SEED
                    || request == VillageRequestType.GRANARY_WHEAT;
            case GRANARY_PANTRY -> request == VillageRequestType.GRANARY_BREAD
                    || request == VillageRequestType.GRANARY_POTATOES;
            case FORGE_FUEL_AND_ORE -> request == VillageRequestType.FORGE_COAL
                    || request == VillageRequestType.FORGE_COPPER;
            case FORGE_BUILDING_STOCK -> request == VillageRequestType.FORGE_IRON
                    || request == VillageRequestType.FORGE_STONE_BRICKS;
            case PASTURE_FODDER -> request == VillageRequestType.PASTURE_FODDER
                    || request == VillageRequestType.PASTURE_EGGS;
            case PASTURE_HUSBANDRY -> request == VillageRequestType.PASTURE_WOOL
                    || request == VillageRequestType.PASTURE_LEATHER;
            case APIARY_FORAGE -> request == VillageRequestType.APIARY_FLOWERS
                    || request == VillageRequestType.APIARY_HONEY;
            case APIARY_WAX_STOCK -> request == VillageRequestType.APIARY_HONEYCOMB
                    || request == VillageRequestType.APIARY_CANDLES;
            case ARCHIVE_WRITING_SUPPLIES -> request == VillageRequestType.ARCHIVE_PAPER
                    || request == VillageRequestType.ARCHIVE_FEATHERS;
            case ARCHIVE_PRESERVATION -> request == VillageRequestType.ARCHIVE_BOOKS
                    || request == VillageRequestType.ARCHIVE_INK;
        };
    }

    public boolean matches(Item item) {
        if (item == null) return false;
        for (VillageRequestType request : VillageRequestType.values()) {
            if (request.item() == item && matches(request)) return true;
        }
        return false;
    }

    public static VillageNeed forVillage(VillageBondType type, int seed) {
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        int target = Math.floorMod(seed, countFor(safeType));
        int match = 0;
        for (VillageNeed value : VALUES) {
            if (value.villageType != safeType) continue;
            if (match++ == target) return value;
        }
        return GRANARY_SEED_RESERVES;
    }

    public static VillageNeed next(VillageBondType type, VillageNeed current) {
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        VillageNeed first = null;
        boolean returnNext = false;
        for (VillageNeed value : VALUES) {
            if (value.villageType != safeType) continue;
            if (first == null) first = value;
            if (returnNext) return value;
            if (value == current) returnNext = true;
        }
        return first == null ? GRANARY_SEED_RESERVES : first;
    }

    public static VillageNeed byId(int id, VillageBondType fallbackType, int seed) {
        for (VillageNeed value : VALUES) {
            if (value.id == id && value.villageType == fallbackType) return value;
        }
        return forVillage(fallbackType, seed);
    }

    static int countFor(VillageBondType type) {
        int count = 0;
        for (VillageNeed value : VALUES) if (value.villageType == type) count++;
        return Math.max(1, count);
    }
}
