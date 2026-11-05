package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DistinctObjectiveProgressTest {
    @Test
    void repeatedInteractionCannotFillMultipleSlots() {
        int[] slots = {0, 0, 0};

        slots = DistinctObjectiveProgress.record(slots, 41);
        slots = DistinctObjectiveProgress.record(slots, 41);
        slots = DistinctObjectiveProgress.record(slots, 41);

        assertArrayEquals(new int[] {41, 0, 0}, slots);
        assertEquals(1, DistinctObjectiveProgress.count(slots));
    }

    @Test
    void distinctTokensFillSlotsWithoutOverwritingProgress() {
        int[] slots = DistinctObjectiveProgress.record(new int[] {11, 22, 0}, 33);
        int[] full = DistinctObjectiveProgress.record(slots, 44);

        assertArrayEquals(new int[] {11, 22, 33}, slots);
        assertArrayEquals(slots, full);
        assertEquals(3, DistinctObjectiveProgress.count(full));
    }

    @Test
    void zeroTokenIsNormalizedBecauseZeroMeansEmptyInPersistentMaps() {
        assertArrayEquals(new int[] {1, 0}, DistinctObjectiveProgress.record(new int[] {0, 0}, 0));
    }
}
