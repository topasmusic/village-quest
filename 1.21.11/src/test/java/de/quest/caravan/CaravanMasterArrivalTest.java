package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CaravanMasterArrivalTest {
    @Test
    void estimateFollowsTheActualTravelDirectionAndWithholdsTimeWhileStopped() {
        assertEquals(37, CaravanMasterArrival.secondsToNextStop(6300, 1, 100, true));
        assertEquals(63, CaravanMasterArrival.secondsToNextStop(6300, -1, 100, true));
        assertEquals(-1, CaravanMasterArrival.secondsToNextStop(6300, 1, 100, false));
        assertEquals(-1, CaravanMasterArrival.secondsToNextStop(6300, 1, 0, true));
        assertEquals(100, CaravanMasterArrival.legPercent(0, -1));
        assertEquals(37, CaravanMasterArrival.legPercent(6300, -1));
    }
}
