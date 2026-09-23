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

    @Test
    void legacyXzRouteLoadsUnchanged() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_0_waypoint_count", 2);
        data.setTradeRouteInt("route_0_waypoint_0_x", 12);
        data.setTradeRouteInt("route_0_waypoint_0_z", 34);
        data.setTradeRouteInt("route_0_waypoint_1_x", 56);
        data.setTradeRouteInt("route_0_waypoint_1_z", 78);

        assertFalse(TradeRouteSurveyData.hasV2Geometry(data, 0));
        assertEquals(List.of(new RoutePoint(12, 34), new RoutePoint(56, 78)),
                TradeRouteSurveyData.routeWaypoints(data, 0));
        assertTrue(TradeRouteSurveyData.routeWaypoints(data, 0).stream()
                .noneMatch(RoutePoint::hasElevation));
    }

    @Test
    void newSurveyPersistsXyz() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteSurveyData.setRouteWaypointsWithModes(data, 0, List.of(
                new RouteSurveyPoint(new RoutePoint(12, 41, 34), false),
                new RouteSurveyPoint(new RoutePoint(56, 19, 78), true)));

        assertTrue(TradeRouteSurveyData.hasV2Geometry(data, 0));
        assertEquals(List.of(
                new RouteSurveyPoint(new RoutePoint(12, 41, 34), false),
                new RouteSurveyPoint(new RoutePoint(56, 19, 78), true)),
                TradeRouteSurveyData.routeWaypointsWithModes(data, 0));
    }

    @Test
    void resurveyUpgradesLegacyRouteWithoutMutatingItBeforeFinish() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_0_waypoint_count", 1);
        data.setTradeRouteInt("route_0_waypoint_0_x", 8);
        data.setTradeRouteInt("route_0_waypoint_0_z", 9);

        assertEquals(List.of(new RoutePoint(8, 9)), TradeRouteSurveyData.routeWaypoints(data, 0));
        assertFalse(TradeRouteSurveyData.hasV2Geometry(data, 0));

        TradeRouteSurveyData.setRouteWaypointsWithModes(data, 0, List.of(
                new RouteSurveyPoint(new RoutePoint(8, -22, 9), false)));

        assertTrue(TradeRouteSurveyData.hasV2Geometry(data, 0));
        assertEquals(List.of(new RoutePoint(8, -22, 9)), TradeRouteSurveyData.routeWaypoints(data, 0));
    }

    @Test
    void surveyDraftPersistsTwoElevationsAtTheSameHorizontalCoordinate() {
        PlayerQuestData data = new PlayerQuestData();
        TradeRouteSurveyData.setSurveyPoint(data, 0, new RoutePoint(20, 35, 30), false);
        TradeRouteSurveyData.setSurveyPoint(data, 1, new RoutePoint(20, 78, 30), false);
        data.setTradeRouteInt("survey_point_count", 2);

        assertEquals(List.of(new RoutePoint(20, 35, 30), new RoutePoint(20, 78, 30)),
                TradeRouteSurveyData.surveyPoints(data));
    }

    @Test
    void completedV2SurveyPersistsBothPhysicalEndpointElevations() {
        PlayerQuestData data = new PlayerQuestData();

        TradeRouteSurveyData.markRouteGeometryV2(data, 1, -28, 137);

        assertTrue(TradeRouteSurveyData.hasV2Geometry(data, 1));
        assertEquals(-28, TradeRouteSurveyData.routeHomeElevation(data, 1));
        assertEquals(137, TradeRouteSurveyData.routeDestinationElevation(data, 1));
    }
}
