package de.quest.reputation;

import java.util.*;
import net.minecraft.network.chat.Component;

/** One bundled incident message per player and five server seconds; highest severity wins a sweep. */
public final class ReputationNotificationQueue {
    private record Notice(int severity, Component message) {}
    private final Map<UUID, Notice> pending = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();
    public void offer(UUID player, int severity, Component message) {
        Objects.requireNonNull(player); Objects.requireNonNull(message);
        var previous = pending.get(player);
        if (previous == null || severity >= previous.severity()) pending.put(player, new Notice(severity, message));
    }
    public Component poll(UUID player, long tick) {
        var next = pending.get(player); if (next == null) return null;
        Long previous = lastSent.get(player); if (previous != null && tick - previous < 100) return null;
        pending.remove(player); lastSent.put(player, tick); return next.message();
    }
    public Set<UUID> pendingPlayers() { return Set.copyOf(pending.keySet()); }
    public void remove(UUID player) { pending.remove(player); lastSent.remove(player); }
    public void clear() { pending.clear(); lastSent.clear(); }
}
