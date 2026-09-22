package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PeacefulEscortProgressTest {
    @Test
    void escortRequiresEveryCheckpointAndASupplyHandoff() {
        assertFalse(PeacefulEscortProgress.complete(2, 3, true));
        assertFalse(PeacefulEscortProgress.complete(3, 3, false));
        assertTrue(PeacefulEscortProgress.complete(3, 3, true));
        assertFalse(PeacefulEscortProgress.complete(3, 4, true));
        assertTrue(PeacefulEscortProgress.complete(4, 4, true));
    }
}
