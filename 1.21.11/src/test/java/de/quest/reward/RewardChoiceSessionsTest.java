package de.quest.reward;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
final class RewardChoiceSessionsTest {
    @Test void ticketsBindPlayerIdentityAllowedOptionAndExpire() {
        var sessions = new RewardChoiceSessions(); var id = UUID.randomUUID();
        var ticket = sessions.open(id, "daily:10", Set.of("coins", "supplies"), 10);
        assertFalse(sessions.consume(UUID.randomUUID(), ticket.token(), "daily:10", "coins", 20));
        assertFalse(sessions.consume(id, ticket.token(), "daily:11", "coins", 20));
        assertFalse(sessions.consume(id, ticket.token(), "daily:10", "diamonds", 20));
        assertTrue(sessions.consume(id, ticket.token(), "daily:10", "supplies", 20));
        assertFalse(sessions.consume(id, ticket.token(), "daily:10", "supplies", 20));
        ticket = sessions.open(id, "daily:10", Set.of("coins"), 10);
        assertFalse(sessions.consume(id, ticket.token(), "daily:10", "coins", 2411));
    }
    @Test void replacementInvalidatesPreviousTicket() {
        var sessions = new RewardChoiceSessions(); var id = UUID.randomUUID();
        var old = sessions.open(id, "daily:10", Set.of("coins"), 0);
        var current = sessions.open(id, "daily:10", Set.of("coins"), 1);
        assertFalse(sessions.consume(id, old.token(), "daily:10", "coins", 2));
        assertTrue(sessions.consume(id, current.token(), "daily:10", "coins", 2));
    }
}
