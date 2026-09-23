package de.quest.caravan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static de.quest.caravan.TradeRouteGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

final class TradeRouteGeometryTest {
    @Test
    void interpolationUsesFullWaypointDistance() {
        List<RoutePoint> path = List.of(
                new RoutePoint(0, 0), new RoutePoint(100, 0), new RoutePoint(100, 100));

        assertEquals(new RoutePoint(0, 0), pointAlong(path, 0));
        assertEquals(new RoutePoint(100, 0), pointAlong(path, 5_000));
        assertEquals(new RoutePoint(100, 100), pointAlong(path, 10_000));
        assertEquals(200.0, pathDistance(path));
    }

    @Test
    void ferryBoundariesWorkInBothDirectionsWithoutBlockingLandNodes() {
        List<RouteSurveyPoint> path = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0), false),
                new RouteSurveyPoint(new RoutePoint(20, 0), true),
                new RouteSurveyPoint(new RoutePoint(80, 0), true),
                new RouteSurveyPoint(new RoutePoint(100, 0), false));

        FerryBoarding outward = crossedBoarding(path, 0, 1, 1);
        FerryBoarding returning = crossedBoarding(path, 10_000, 9_999, -1);
        assertNotNull(outward);
        assertNotNull(returning);
        assertEquals(new RoutePoint(0, 0), outward.point());
        assertEquals(new RoutePoint(100, 0), returning.point());
        assertFalse(ferryState(path, 0, 1, 2.0).active());
        assertTrue(ferryState(path, 5_000, 1, 2.0).active());
    }

    @Test
    void degeneratePathsRemainSafe() {
        assertEquals(new RoutePoint(0, 0), pointAlong(List.of(), 5_000));
        assertEquals(FerryState.NONE, ferryState(List.of(), 5_000, 1, 1.0));
        assertNull(crossedBoarding(List.of(), 0, 10_000, 1));
    }

    @Test
    void v2InterpolationPreservesElevationWithoutChangingEconomicDistance() {
        List<RoutePoint> path = List.of(
                new RoutePoint(0, 20, 0), new RoutePoint(100, 80, 0));

        assertEquals(new RoutePoint(50, 50, 0), pointAlong(path, 5_000));
        assertEquals(100.0, pathDistance(path));
    }

    @Test
    void legacyInterpolationRemainsElevationUnknown() {
        RoutePoint midpoint = pointAlong(List.of(new RoutePoint(0, 0), new RoutePoint(100, 0)), 5_000);

        assertEquals(new RoutePoint(50, 0), midpoint);
        assertFalse(midpoint.hasElevation());
    }

    @Test
    void flatV2RouteKeepsHorizontalAndTraversalLengthEqual() {
        List<RouteSurveyPoint> route = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 64, 0), false),
                new RouteSurveyPoint(new RoutePoint(30, 64, 0), false));
        assertEquals(30.0, traversalDistance(route));
        assertEquals(new RoutePoint(15, 64, 0), pointAlongTraversal(route, 5_000));
    }

    @Test
    void steepStairsAndVerticalSwitchbackConsumePhysicalProgress() {
        List<RouteSurveyPoint> stairs = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0, 0), false),
                new RouteSurveyPoint(new RoutePoint(3, 4, 0), false),
                new RouteSurveyPoint(new RoutePoint(6, 8, 0), false));
        assertEquals(10.0, traversalDistance(stairs));
        assertEquals(new RoutePoint(3, 4, 0), pointAlongTraversal(stairs, 5_000));

        List<RouteSurveyPoint> switchback = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0, 0), false),
                new RouteSurveyPoint(new RoutePoint(10, 10, 0), false),
                new RouteSurveyPoint(new RoutePoint(0, 20, 0), false));
        assertEquals(20.0 * Math.sqrt(2), traversalDistance(switchback), 0.0001);
        assertEquals(new RoutePoint(10, 10, 0), pointAlongTraversal(switchback, 5_000));
    }

    @Test
    void largeElevationChangeNearSameHorizontalPositionRemainsTraversable() {
        List<RouteSurveyPoint> route = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0, 0), false),
                new RouteSurveyPoint(new RoutePoint(1, 100, 0), false));
        assertEquals(Math.hypot(1, 100), traversalDistance(route), 0.0001);
        assertEquals(new RoutePoint(1, 50, 0), pointAlongTraversal(route, 5_000));
    }

    @Test
    void legacyAndFerrySegmentsKeepHorizontalDistance() {
        List<RouteSurveyPoint> legacy = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0), false),
                new RouteSurveyPoint(new RoutePoint(20, 0), false));
        assertEquals(20.0, traversalDistance(legacy));
        assertEquals(new RoutePoint(10, 0), pointAlongTraversal(legacy, 5_000));

        List<RouteSurveyPoint> ferry = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 20, 0), false),
                new RouteSurveyPoint(new RoutePoint(20, 90, 0), true),
                new RouteSurveyPoint(new RoutePoint(80, 90, 0), true),
                new RouteSurveyPoint(new RoutePoint(100, 20, 0), false));
        assertEquals(100.0, traversalDistance(ferry));
        assertEquals(0, crossedBoarding(ferry, 0, 1, 1).progress());
        assertTrue(ferryState(ferry, 5_000, 1, 2.0).active());
    }
}
