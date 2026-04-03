package de.quest.client.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class InventoryJournalCompat {
    private static final String STATUS_EFFECT_BARS_MOD_ID = "status-effect-bars";
    private static final String STATUS_EFFECT_BARS_ALT_MOD_ID = "statuseffectbars";

    private InventoryJournalCompat() {}

    public static boolean shouldUseTopRightFallback(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }
        if (!isStatusEffectBarsLoaded()) {
            return false;
        }
        return !client.player.getActiveEffects().isEmpty();
    }

    private static boolean isStatusEffectBarsLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded(STATUS_EFFECT_BARS_MOD_ID)
                || loader.isModLoaded(STATUS_EFFECT_BARS_ALT_MOD_ID);
    }
}
