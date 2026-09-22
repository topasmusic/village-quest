package de.quest.caravan;

import java.util.UUID;

/** Pure ownership boundary for solo interactive route incidents. */
final class TradeRouteIncidentPolicy {
    // Two playable Minecraft days, sampled once per owner-online second.
    static final int TIMEOUT_ONLINE_SECONDS = 2 * 20 * 60;

    private TradeRouteIncidentPolicy() {}

    static boolean canStart(boolean ownerOnline) {
        return ownerOnline;
    }

    static boolean canProgress(UUID ownerId, UUID actorId) {
        return ownerId != null && ownerId.equals(actorId);
    }

    static int advanceOnlineSeconds(int elapsed, boolean ownerPlayable) {
        int safeElapsed = Math.max(0, elapsed);
        return ownerPlayable && safeElapsed < TIMEOUT_ONLINE_SECONDS ? safeElapsed + 1 : safeElapsed;
    }

    static boolean timedOut(int elapsed) {
        return elapsed >= TIMEOUT_ONLINE_SECONDS;
    }

    static boolean canCompleteAmbush(UUID ownerId, UUID killerId) {
        return canProgress(ownerId, killerId);
    }
}
