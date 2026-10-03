package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ReputationSessionTest {
    @Test void anotherPlayerCannotReuseASessionAndExpiredOrReplacedSessionsDoNotAuthorizeActions() {
        var sessions = new ReputationSessionRegistry();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var anchor = new ReparationService.InteractionAnchor(ReparationService.AnchorKind.BOARD,
                "minecraft:overworld", BlockPos.ZERO, null);
        var original = sessions.open(owner, anchor, 100);
        assertNull(sessions.current(other, original.id(), 101));
        assertEquals(original, sessions.current(owner, original.id(), 101));
        assertNull(sessions.current(owner, original.id(), 100 + ReputationSessionRegistry.LIFETIME + 1));
        var fresh = sessions.open(owner, anchor, 2000);
        var replacement = sessions.open(owner, anchor, 2001);
        assertNull(sessions.current(owner, fresh.id(), 2002));
        assertEquals(replacement, sessions.current(owner, replacement.id(), 2002));
        sessions.close(owner, fresh.id());
        assertEquals(replacement, sessions.current(owner, replacement.id(), 2002));
        sessions.close(owner, replacement.id());
        assertNull(sessions.current(owner, replacement.id(), 2002));
    }
}
