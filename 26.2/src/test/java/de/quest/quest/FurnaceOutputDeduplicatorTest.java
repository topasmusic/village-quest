package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FurnaceOutputDeduplicatorTest {
    @Test
    void normalTakeDispatchesExactlyOnceFromResultSlot() {
        int dispatches = FurnaceOutputDeduplicator.recordResultSlotRemoval(3) ? 1 : 0;
        assertEquals(1, dispatches);
        assertFalse(FurnaceOutputDeduplicator.hasOpenQuickMove());
    }

    @Test
    void shiftClickSuppressesNestedSlotHookAndDispatchesExactlyOnceAtMenuReturn() {
        FurnaceOutputDeduplicator.beginQuickMove();
        int dispatches = FurnaceOutputDeduplicator.recordResultSlotRemoval(8) ? 1 : 0;
        dispatches += FurnaceOutputDeduplicator.finishQuickMoveCount(8) > 0 ? 1 : 0;
        assertEquals(1, dispatches);
        assertFalse(FurnaceOutputDeduplicator.hasOpenQuickMove());
    }

    @Test
    void shiftClickFallbackStillDispatchesWhenVanillaDoesNotCallResultSlotRemove() {
        FurnaceOutputDeduplicator.beginQuickMove();
        assertEquals(5, FurnaceOutputDeduplicator.finishQuickMoveCount(5));
        assertFalse(FurnaceOutputDeduplicator.hasOpenQuickMove());
    }

    @Test
    void partialShiftClickUsesOnlyAmountActuallyRemovedByResultSlot() {
        FurnaceOutputDeduplicator.beginQuickMove();
        assertFalse(FurnaceOutputDeduplicator.recordResultSlotRemoval(3));
        assertEquals(3, FurnaceOutputDeduplicator.finishQuickMoveCount(64));
        assertFalse(FurnaceOutputDeduplicator.hasOpenQuickMove());
    }

    @Test
    void partialShiftClickFallbackUsesOutputSlotDeltaInsteadOfOriginalStack() {
        FurnaceOutputDeduplicator.beginQuickMove(64);
        assertEquals(3, FurnaceOutputDeduplicator.finishQuickMoveCount(64, 61));
        assertFalse(FurnaceOutputDeduplicator.hasOpenQuickMove());
    }
}
