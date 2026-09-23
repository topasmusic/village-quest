package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TradeRouteSchedulingPolicyTest {
    @Test
    void offlineCatchUpIsDeterministicAndBounded() {
        assertEquals(0, TradeRouteSchedulingPolicy.catchUpSeconds(0, 1_000, 300));
        assertEquals(40, TradeRouteSchedulingPolicy.catchUpSeconds(960, 1_000, 300));
        assertEquals(300, TradeRouteSchedulingPolicy.catchUpSeconds(10, 1_000, 300));
        assertEquals(0, TradeRouteSchedulingPolicy.catchUpSeconds(1_100, 1_000, 300));
    }

    @Test
    void pausedCleanupOnlyRunsOnTransitionOrExplicitRecovery() {
        assertTrue(TradeRouteSchedulingPolicy.shouldCleanupPausedRoute(false, true, false));
        assertFalse(TradeRouteSchedulingPolicy.shouldCleanupPausedRoute(true, true, false));
        assertTrue(TradeRouteSchedulingPolicy.shouldCleanupPausedRoute(true, true, true));
    }

    @Test
    void orphanSweepFrequencyRetainsRecoveryWithoutRunningEverySecond() {
        assertTrue(TradeRouteSchedulingPolicy.shouldRunOrphanSweep(600, 600));
        assertFalse(TradeRouteSchedulingPolicy.shouldRunOrphanSweep(620, 600));
    }
}
