package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TradeRouteIncidentPolicyTest {
    private static final UUID OWNER = UUID.fromString("d4384ef7-6598-477f-86fd-6ec4cac22841");
    private static final UUID STRANGER = UUID.fromString("4a8c52a6-58f8-43c0-b0f6-6a7aa640a48e");

    @Test
    void interactiveIncidentsOnlyStartWhileOwnerIsOnline() {
        assertFalse(TradeRouteIncidentPolicy.canStart(false));
        assertTrue(TradeRouteIncidentPolicy.canStart(true));
    }

    @Test
    void onlyOwnerCanProgressOrCompleteSoloIncident() {
        assertTrue(TradeRouteIncidentPolicy.canProgress(OWNER, OWNER));
        assertFalse(TradeRouteIncidentPolicy.canProgress(OWNER, STRANGER));
        assertFalse(TradeRouteIncidentPolicy.canProgress(OWNER, null));
    }

    @Test
    void offlineAndOtherDimensionTimeNeverConsumesIncidentWindow() {
        int elapsed = 2398;
        assertEquals(elapsed, TradeRouteIncidentPolicy.advanceOnlineSeconds(elapsed, false));
        assertFalse(TradeRouteIncidentPolicy.timedOut(elapsed));
        assertEquals(2399, TradeRouteIncidentPolicy.advanceOnlineSeconds(elapsed, true));
        assertTrue(TradeRouteIncidentPolicy.timedOut(
                TradeRouteIncidentPolicy.advanceOnlineSeconds(2399, true)));
    }

    @Test
    void helperCanClearAmbushButOnlyOwnerCanCompleteIt() {
        assertFalse(TradeRouteIncidentPolicy.canCompleteAmbush(OWNER, STRANGER));
        assertFalse(TradeRouteIncidentPolicy.canCompleteAmbush(OWNER, null));
        assertTrue(TradeRouteIncidentPolicy.canCompleteAmbush(OWNER, OWNER));
    }
}
