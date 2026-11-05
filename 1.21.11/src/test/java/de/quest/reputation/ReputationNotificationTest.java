package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class ReputationNotificationTest {
    @Test void aSweepProducesOneMostSevereMessageAndRepeatedHitsCannotFloodChat() {
        var queue = new ReputationNotificationQueue(); UUID player = UUID.randomUUID();
        queue.offer(player, 0, Component.literal("warning")); queue.offer(player, 1, Component.literal("assault")); queue.offer(player, 2, Component.literal("fatal"));
        assertEquals("fatal", queue.poll(player, 10).getString()); assertNull(queue.poll(player, 10));
        queue.offer(player, 1, Component.literal("next assault")); assertNull(queue.poll(player, 109));
        assertEquals("next assault", queue.poll(player, 110).getString()); assertNull(queue.poll(player, 111));
        queue.remove(player); queue.offer(player, 0, Component.literal("new session")); assertEquals("new session", queue.poll(player, 112).getString());
    }
}
