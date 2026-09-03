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
}
