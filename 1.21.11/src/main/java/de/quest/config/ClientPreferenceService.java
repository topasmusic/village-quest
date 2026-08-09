package de.quest.config;

import de.quest.network.Payloads;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

/** Runtime copy of preferences a connected client explicitly shared with the server. */
public final class ClientPreferenceService {
    private static final Preferences DEFAULTS = new Preferences(true, true, true, true, 1.0f);
    private static final Map<UUID, Preferences> PREFERENCES = new ConcurrentHashMap<>();

    private ClientPreferenceService() {}

    public static void update(ServerPlayerEntity player, Payloads.ClientPreferencesPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        PREFERENCES.put(player.getUuid(), new Preferences(
                payload.questTrackerEnabledByDefault(),
                payload.questAvailableChatNotifications(),
                payload.caravanEventNotifications(),
                payload.questProgressSounds(),
                Math.max(0.0f, Math.min(1.0f, payload.questProgressSoundVolume()))
        ));
    }

    public static boolean questTrackerEnabledByDefault(ServerPlayerEntity player) {
        return preferences(player).questTrackerEnabledByDefault();
    }

    public static boolean questAvailableChatNotifications(ServerPlayerEntity player) {
        return preferences(player).questAvailableChatNotifications();
    }

    public static boolean caravanEventNotifications(ServerPlayerEntity player) {
        return preferences(player).caravanEventNotifications();
    }

    public static boolean questProgressSounds(ServerPlayerEntity player) {
        return preferences(player).questProgressSounds();
    }

    public static float questProgressSoundVolume(ServerPlayerEntity player) {
        return preferences(player).questProgressSoundVolume();
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId != null) PREFERENCES.remove(playerId);
    }

    public static void reset() {
        PREFERENCES.clear();
    }

    private static Preferences preferences(ServerPlayerEntity player) {
        return player == null ? DEFAULTS : PREFERENCES.getOrDefault(player.getUuid(), DEFAULTS);
    }

    private record Preferences(boolean questTrackerEnabledByDefault,
                               boolean questAvailableChatNotifications,
                               boolean caravanEventNotifications,
                               boolean questProgressSounds,
                               float questProgressSoundVolume) {}
}
