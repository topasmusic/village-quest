package de.quest.client.ui;

import de.quest.VillageQuest;
import de.quest.client.compat.ClientModCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class InventoryJournalCompat {
    private static final String STATUS_EFFECT_BARS_MOD_ID = "status-effect-bars";
    private static final String STATUS_EFFECT_BARS_ALT_MOD_ID = "statuseffectbars";
    private static boolean overlayDisabledForSession = ClientModCompat.shouldDisableInventoryJournalOverlay();
    private static boolean overlayFailureLogged = false;

    private InventoryJournalCompat() {}

    public static boolean isInventoryJournalButtonEnabled(Minecraft client) {
        return client != null;
    }

    public static boolean shouldRenderInventoryJournalOverlay(Minecraft client) {
        return isInventoryJournalButtonEnabled(client) && !overlayDisabledForSession;
    }

    public static boolean shouldUseWidgetFallbackButton(Minecraft client) {
        return isInventoryJournalButtonEnabled(client) && overlayDisabledForSession;
    }

    public static boolean shouldUseTopRightFallback(Minecraft client) {
        if (!shouldRenderInventoryJournalOverlay(client) || client.player == null) {
            return false;
        }
        if (!isStatusEffectBarsLoaded()) {
            return false;
        }
        return !client.player.getActiveEffects().isEmpty();
    }

    public static void disableInventoryJournalOverlayForSession(String phase, Throwable throwable) {
        overlayDisabledForSession = true;
        if (overlayFailureLogged) {
            return;
        }
        overlayFailureLogged = true;
        VillageQuest.LOGGER.warn(
                "Disabling the Village Quest inventory journal overlay for this client session after a compatibility failure during {}.",
                phase,
                throwable
        );
    }

    private static boolean isStatusEffectBarsLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded(STATUS_EFFECT_BARS_MOD_ID)
                || loader.isModLoaded(STATUS_EFFECT_BARS_ALT_MOD_ID);
    }
}
