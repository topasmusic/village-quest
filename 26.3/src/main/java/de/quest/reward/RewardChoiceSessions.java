package de.quest.reward;

import java.util.*;

/** Bounded, one-shot server selection tickets. The client cannot supply package contents. */
public final class RewardChoiceSessions {
    public record Ticket(UUID token, String identity, Set<String> options, long expires) { }
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    public Ticket open(UUID player, String identity, Set<String> options, long now) {
        var ticket = new Ticket(UUID.randomUUID(), identity, Set.copyOf(options), now + 2400);
        tickets.put(player, ticket); return ticket;
    }
    public boolean consume(UUID player, UUID token, String identity, String option, long now) {
        var ticket = tickets.get(player);
        if (ticket == null || !ticket.token().equals(token) || !ticket.identity().equals(identity)
                || !ticket.options().contains(option) || now > ticket.expires()) return false;
        tickets.remove(player); return true;
    }
    public void clear(UUID player) { tickets.remove(player); }
}
