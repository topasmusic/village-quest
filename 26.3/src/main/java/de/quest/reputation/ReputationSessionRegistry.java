package de.quest.reputation;

import java.util.*;

/** Ephemeral interaction authority; never saved with player progression. */
public final class ReputationSessionRegistry {
    public static final long LIFETIME = 1_800;
    public record Session(UUID id, ReparationService.InteractionAnchor anchor, long expires, int page) {}
    private final Map<UUID, Session> sessions = new HashMap<>();
    public Session open(UUID player, ReparationService.InteractionAnchor anchor, long tick) {
        var session = new Session(UUID.randomUUID(), anchor, tick + LIFETIME, 0); sessions.put(player, session); return session;
    }
    public Session current(UUID player, UUID id, long tick) {
        var session = sessions.get(player);
        if (session == null || !session.id().equals(id)) return null;
        if (tick > session.expires()) { sessions.remove(player); return null; }
        return session;
    }
    public void page(UUID player, UUID id, int page) {
        var session = sessions.get(player); if (session == null || !session.id().equals(id)) return;
        sessions.put(player, new Session(id, session.anchor(), session.expires(), Math.max(0, page)));
    }
    public void close(UUID player, UUID id) { var session = sessions.get(player); if (session != null && session.id().equals(id)) sessions.remove(player); }
    public void remove(UUID player) { sessions.remove(player); }
    public void clear() { sessions.clear(); }
    public Map<UUID, Session> view() { return Map.copyOf(sessions); }
}
