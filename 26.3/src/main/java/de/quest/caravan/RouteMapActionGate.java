package de.quest.caravan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-clock session and mutation budget for the full route map. */
final class RouteMapActionGate {
    private static final long SESSION_TICKS = 600L;
    private static final long ACTION_TICKS = 10L;
    private static final long MINIMAP_TOGGLE_TICKS = 4L;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> minimapToggles = new HashMap<>();

    void open(UUID player, long tick) {
        sessions.put(player, new Session(tick, Long.MIN_VALUE));
    }

    void close(UUID player) {
        sessions.remove(player);
    }

    void clear() {
        sessions.clear();
        minimapToggles.clear();
    }

    void disconnect(UUID player) {
        close(player);
        minimapToggles.remove(player);
    }

    boolean acceptMinimapToggle(UUID player, long tick) {
        Long previous = minimapToggles.get(player);
        if (previous != null && (tick < previous || tick - previous < MINIMAP_TOGGLE_TICKS)) {
            return false;
        }
        minimapToggles.put(player, tick);
        return true;
    }

    boolean heartbeat(UUID player, long tick) {
        Session session = active(player, tick);
        if (session == null) {
            return false;
        }
        session.lastSeen = tick;
        return true;
    }

    boolean accept(UUID player, long tick) {
        Session session = active(player, tick);
        if (session == null || session.lastAction != Long.MIN_VALUE
                && tick - session.lastAction < ACTION_TICKS) {
            return false;
        }
        session.lastAction = tick;
        return true;
    }

    boolean hasActiveSession(UUID player, long tick) {
        return active(player, tick) != null;
    }

    private Session active(UUID player, long tick) {
        Session session = sessions.get(player);
        if (session != null && tick >= session.lastSeen
                && tick - session.lastSeen <= SESSION_TICKS) {
            return session;
        }
        sessions.remove(player);
        return null;
    }

    private static final class Session {
        private long lastSeen;
        private long lastAction;

        private Session(long lastSeen, long lastAction) {
            this.lastSeen = lastSeen;
            this.lastAction = lastAction;
        }
    }
}
