package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TradeRouteSurfaceResolverTest {
    @Test
    void yAwareSearchNeverEscapesItsRecordedElevationBand() {
        assertEquals(List.of(35, 36, 34, 37, 33, 38, 32),
                TradeRouteSurfaceResolver.verticalCandidates(35, 3));
    }

    @Test
    void zeroToleranceChecksOnlyTheRecordedTunnelOrBridgeLevel() {
        assertEquals(List.of(-22), TradeRouteSurfaceResolver.verticalCandidates(-22, 0));
    }
}
