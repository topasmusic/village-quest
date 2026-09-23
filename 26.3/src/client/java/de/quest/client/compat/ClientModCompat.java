package de.quest.client.compat;

import de.quest.VillageQuest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.List;
import java.util.Locale;

public final class ClientModCompat {
    private static final List<String> BENDABLE_CUBOIDS_KEYS = List.of(
            "bendablecuboids",
            "bendable-cuboids",
            "bendable cuboids"
    );
    private static final List<String> MTG_CARD_KEYS = List.of(
            "mtgcard",
            "mtg-card",
            "mtg card"
    );

    private static final boolean BENDABLE_CUBOIDS_LOADED = hasKnownMod(BENDABLE_CUBOIDS_KEYS);
    private static final boolean MTG_CARD_LOADED = hasKnownMod(MTG_CARD_KEYS);
    private static boolean bootstrapped = false;

    private ClientModCompat() {}

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        if (BENDABLE_CUBOIDS_LOADED) {
            VillageQuest.LOGGER.warn(
                    "Detected Bendable Cuboids. Village Quest is using a safer quest-NPC held-item renderer to avoid known crashes while keeping quest NPC weapons and torches visible."
            );
        }
        if (MTG_CARD_LOADED) {
            VillageQuest.LOGGER.warn(
                    "Detected MTGCard. Village Quest is disabling the old inventory journal overlay to avoid known inventory-screen crashes and is using a fallback inventory Journal button instead."
            );
        }
    }

    public static boolean shouldUseSafeNpcHeldItemFallback() {
        return BENDABLE_CUBOIDS_LOADED;
    }

    public static boolean shouldDisableInventoryJournalOverlay() {
        return MTG_CARD_LOADED;
    }

    private static boolean hasKnownMod(List<String> keys) {
        FabricLoader loader = FabricLoader.getInstance();
        for (String key : keys) {
            if (loader.isModLoaded(key)) {
                return true;
            }
        }
        for (ModContainer mod : loader.getAllMods()) {
            String normalizedId = normalize(mod.getMetadata().getId());
            String normalizedName = normalize(mod.getMetadata().getName());
            for (String key : keys) {
                String normalizedKey = normalize(key);
                if (normalizedId.equals(normalizedKey)
                        || normalizedName.equals(normalizedKey)
                        || normalizedName.contains(normalizedKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
