package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TradeRouteLastRelayTest {
    @Test
    void guaranteeStartsOnlyAfterFreshContractAndBeforeFreshSuccess() {
        assertFalse(TradeRouteService.shouldGuaranteeLastRelayIncident(true, 5, 3, 3, 8, 8));
        assertTrue(TradeRouteService.shouldGuaranteeLastRelayIncident(true, 5, 4, 3, 8, 8));
        assertFalse(TradeRouteService.shouldGuaranteeLastRelayIncident(true, 5, 4, 3, 9, 8));
    }

    @Test
    void guaranteeNeverLeaksIntoAnotherStoryOrChapter() {
        assertFalse(TradeRouteService.shouldGuaranteeLastRelayIncident(false, 5, 4, 3, 8, 8));
        assertFalse(TradeRouteService.shouldGuaranteeLastRelayIncident(true, 4, 4, 3, 8, 8));
    }
}
