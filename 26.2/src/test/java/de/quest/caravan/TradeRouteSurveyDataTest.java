package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import de.quest.data.PlayerQuestData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class TradeRouteSurveyDataTest {
    @Test
    void waypointModesRoundTripAndOldEntriesAreCleared() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteSurveyData.setRouteWaypointsWithModes(data, 2, List.of(
                new RouteSurveyPoint(new RoutePoint(12, 34), false),
                new RouteSurveyPoint(new RoutePoint(56, 78), true)));

        assertEquals(List.of(
                new RouteSurveyPoint(new RoutePoint(12, 34), false),
                new RouteSurveyPoint(new RoutePoint(56, 78), true)),
                TradeRouteSurveyData.routeWaypointsWithModes(data, 2));

        TradeRouteSurveyData.setRouteWaypoints(data, 2, List.of(new RoutePoint(-5, 9)));
        assertEquals(List.of(new RouteSurveyPoint(new RoutePoint(-5, 9), false)),
                TradeRouteSurveyData.routeWaypointsWithModes(data, 2));
        assertFalse(data.hasTradeRouteFlag("route_2_waypoint_1_ocean"));
    }

    @Test
    void normalizedDraftDropsNearDuplicateEndpoints() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("home_x", 0);
        data.setTradeRouteInt("home_z", 0);
        data.setTradeRouteInt("route_0_x", 100);
        data.setTradeRouteInt("route_0_z", 0);
        data.setTradeRouteInt("survey_point_count", 3);
        TradeRouteSurveyData.setSurveyPoint(data, 0, new RoutePoint(2, 0), false);
        TradeRouteSurveyData.setSurveyPoint(data, 1, new RoutePoint(50, 0), true);
        TradeRouteSurveyData.setSurveyPoint(data, 2, new RoutePoint(98, 0), false);

        assertEquals(List.of(new RouteSurveyPoint(new RoutePoint(50, 0), true)),
                TradeRouteSurveyData.normalizedSurveyPoints(data, 0));
    }

    @Test
    void clearingDraftDoesNotRemoveSavedRouteWaypoints() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("survey_route", 1);
        data.setTradeRouteInt("survey_point_count", 1);
        TradeRouteSurveyData.setSurveyPoint(data, 0, new RoutePoint(4, 5), true);
        TradeRouteSurveyData.setRouteWaypoints(data, 0, List.of(new RoutePoint(20, 30)));

        TradeRouteSurveyData.clearSurveyDraft(data);

        assertEquals(-1, TradeRouteSurveyData.activeSurveyIndex(data));
        assertTrue(TradeRouteSurveyData.surveyPoints(data).isEmpty());
        assertEquals(List.of(new RoutePoint(20, 30)), TradeRouteSurveyData.routeWaypoints(data, 0));
    }
}
