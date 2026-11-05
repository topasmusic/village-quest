package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RouteEmergencyProgressTest {
    @Test
    void progressOnlyAdvancesWhileEscortIsNearAndRequiresThreeCheckpoints() {
        int progress = RouteEmergencyProgress.advance(1, false);
        assertEquals(1, progress);

        for (int i = 0; i < 29; i++) {
            progress = RouteEmergencyProgress.advance(progress, true);
        }
        assertFalse(RouteEmergencyProgress.complete(progress));

        progress = RouteEmergencyProgress.advance(progress, true);
        assertTrue(RouteEmergencyProgress.complete(progress));
        assertEquals(3, RouteEmergencyProgress.checkpoints(progress));
    }
}
