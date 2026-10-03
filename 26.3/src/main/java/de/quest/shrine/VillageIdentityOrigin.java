package de.quest.shrine;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** A short saved explanation of the evidence at first village discovery. */
final class VillageIdentityOrigin {
    private VillageIdentityOrigin() {}

    record Classification(VillageBondType type, String origin) {}

    static Classification classify(int farmers, int smiths, int shepherds, int archives, String biomeId) {
        if (smiths > farmers && smiths >= shepherds && smiths >= archives) {
            return new Classification(VillageBondType.FORGE, "smiths");
        }
        if (shepherds > farmers && shepherds >= archives) {
            return new Classification(VillageBondType.PASTURE, "herders");
        }
        if (archives > farmers) return new Classification(VillageBondType.ARCHIVE, "scholars");
        String biome = biomeId == null ? "" : biomeId.toLowerCase(Locale.ROOT);
        if (biome.contains("flower") || biome.contains("meadow")
                || biome.contains("forest") || biome.contains("cherry")) {
            return new Classification(VillageBondType.APIARY, "blooms");
        }
        return new Classification(VillageBondType.GRANARY, farmers > 0 ? "farmers" : "general");
    }

    static Component label(String stored) {
        String origin = switch (stored) {
            case "smiths", "herders", "scholars", "blooms", "farmers", "general" -> stored;
            default -> "historical";
        };
        return Component.translatable("screen.village-quest.trade_route.identity_origin." + origin);
    }
}
