package de.quest.pilgrim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PilgrimExpeditionProgressTest {
    @Test
    void dimensionExpeditionNeedsEntryTravelThreeCheckpointsResourceAndReturn() {
        PilgrimExpeditionProgress.State state = PilgrimExpeditionProgress.State.empty();
        state = PilgrimExpeditionProgress.tick(state, true, false, 0, 0, false, false);
        state = PilgrimExpeditionProgress.tick(state, true, false, 100, 0, false, false);
        state = PilgrimExpeditionProgress.tick(state, true, false, 200, 0, false, false);
        state = PilgrimExpeditionProgress.tick(state, true, false, 330, 0, true, false);

        assertTrue(state.entered());
        assertEquals(3, state.checkpoints());
        assertFalse(PilgrimExpeditionProgress.complete(state, true));

        state = PilgrimExpeditionProgress.tick(state, false, true, 0, 0, true, false);
        assertTrue(PilgrimExpeditionProgress.complete(state, true));
    }

    @Test
    void overworldExpeditionMustReturnNearItsRecordedOrigin() {
        PilgrimExpeditionProgress.State state = PilgrimExpeditionProgress.State.empty();
        state = PilgrimExpeditionProgress.tick(state, true, true, 10, 20, false, true);
        state = PilgrimExpeditionProgress.tick(state, true, true, 340, 20, true, true);

        assertFalse(PilgrimExpeditionProgress.complete(state, true));
        state = PilgrimExpeditionProgress.tick(state, true, true, 20, 24, true, true);
        assertTrue(PilgrimExpeditionProgress.complete(state, true));
    }

    @Test
    void teleportSizedJumpDoesNotAwardTravelOrCheckpoints() {
        PilgrimExpeditionProgress.State state = PilgrimExpeditionProgress.State.empty();
        state = PilgrimExpeditionProgress.tick(state, true, false, 0, 0, true, false);
        state = PilgrimExpeditionProgress.tick(state, true, false, 1000, 1000, true, false);

        assertEquals(0, state.maxDistance());
        assertEquals(0, state.checkpoints());
    }
}
