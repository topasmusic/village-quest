package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.quest.village.VillageLifeState;
import org.junit.jupiter.api.Test;

final class TradeRouteAvailabilityTest {
    @Test
    void abandonedDestinationSuspendsOnlyItsConnectedRoute() {
        assertEquals(TradeRouteAvailability.Reason.DESTINATION_ABANDONED,
                TradeRouteAvailability.reason(false, false, false, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ABANDONED));
        assertEquals(TradeRouteAvailability.Reason.ACTIVE,
                TradeRouteAvailability.reason(false, false, false, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ACTIVE));
    }

    @Test
    void villageHubLossSuspendsDeparturesButHomesteadHubIsExempt() {
        assertEquals(TradeRouteAvailability.Reason.HUB_UNAVAILABLE,
                TradeRouteAvailability.reason(false, false, false, false,
                        VillageLifeState.Status.ABANDONED, VillageLifeState.Status.ACTIVE));
        assertEquals(TradeRouteAvailability.Reason.ACTIVE,
                TradeRouteAvailability.reason(false, false, false, true,
                        VillageLifeState.Status.ABANDONED, VillageLifeState.Status.ACTIVE));
    }

    @Test
    void playerPauseSurveyIncidentAndRecoveryHaveDistinctReasons() {
        assertEquals(TradeRouteAvailability.Reason.PLAYER_PAUSED,
                TradeRouteAvailability.reason(true, false, false, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ACTIVE));
        assertEquals(TradeRouteAvailability.Reason.SURVEY_IN_PROGRESS,
                TradeRouteAvailability.reason(false, true, false, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ACTIVE));
        assertEquals(TradeRouteAvailability.Reason.INCIDENT,
                TradeRouteAvailability.reason(false, false, true, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ACTIVE));
        assertEquals(TradeRouteAvailability.Reason.RECOVERING_SETTLEMENT,
                TradeRouteAvailability.reason(false, false, false, false,
                VillageLifeState.Status.ACTIVE, VillageLifeState.Status.RECOVERING));
        assertEquals(TradeRouteAvailability.Reason.DESTINATION_ABANDONED,
                TradeRouteAvailability.reason(true, false, false, false,
                        VillageLifeState.Status.ACTIVE, VillageLifeState.Status.ABANDONED));
    }
}
