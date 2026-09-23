package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TradeRouteNavigationPolicyTest {
    @Test
    void sharpCornerAndSwitchbackUseShorterLookaheadThanStraightRoad() {
        List<RoutePoint> straight = List.of(
                new RoutePoint(0, 64, 0), new RoutePoint(100, 64, 0), new RoutePoint(200, 64, 0));
        List<RoutePoint> corner = List.of(
                new RoutePoint(0, 64, 0), new RoutePoint(100, 64, 0), new RoutePoint(100, 64, 100));
        List<RoutePoint> switchback = List.of(
                new RoutePoint(0, 64, 0), new RoutePoint(100, 72, 0), new RoutePoint(20, 84, 8));

        int straightLookahead = TradeRouteNavigationPolicy.lookaheadProgress(straight, 4_900, 1);
        int cornerLookahead = TradeRouteNavigationPolicy.lookaheadProgress(corner, 4_900, 1);
        int switchbackLookahead = TradeRouteNavigationPolicy.lookaheadProgress(switchback, 4_900, 1);

        assertTrue(cornerLookahead < straightLookahead);
        assertTrue(switchbackLookahead <= cornerLookahead);
    }

    @Test
    void observedDriftOrSustainedStuckStateHoldsVirtualProgress() {
        assertFalse(TradeRouteNavigationPolicy.holdVirtualProgress(false, 200.0, 99));
        assertFalse(TradeRouteNavigationPolicy.holdVirtualProgress(true, 12.0, 2));
        assertTrue(TradeRouteNavigationPolicy.holdVirtualProgress(true, 33.0, 0));
        assertTrue(TradeRouteNavigationPolicy.holdVirtualProgress(true, 4.0, 5));
    }

    @Test
    void formationFallsBackFromWideSpacingToSingleFileSpacingDeterministically() {
        assertTrue(TradeRouteNavigationPolicy.formationSpacings().getFirst()
                > TradeRouteNavigationPolicy.formationSpacings().getLast());
        assertTrue(TradeRouteNavigationPolicy.formationSpacings().getLast() >= 1.0);
    }

    @Test
    void verticalTraversalShortensProgressLookaheadForSameHorizontalSpan() {
        List<RouteSurveyPoint> flat = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0, 0), false),
                new RouteSurveyPoint(new RoutePoint(100, 0, 0), false));
        List<RouteSurveyPoint> steep = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 0, 0), false),
                new RouteSurveyPoint(new RoutePoint(100, 100, 0), false));
        assertTrue(TradeRouteNavigationPolicy.lookaheadProgressForTraversal(steep, 1_000, 1)
                < TradeRouteNavigationPolicy.lookaheadProgressForTraversal(flat, 1_000, 1));
    }

    @Test
    void tunnelTurnTargetStopsAtTheRecordedCorner() {
        List<RouteSurveyPoint> route = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(10, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(10, 30, 40), false));
        assertTrue(TradeRouteNavigationPolicy.lookaheadProgressForTraversal(route, 1_800, 1)
                <= 200);
    }

    @Test
    void detourOntoMountainIsOutsideRecordedTunnelCorridor() {
        List<RouteSurveyPoint> route = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(40, 30, 0), false));
        assertTrue(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(20, 30, 2)));
        assertFalse(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(20, 80, 2)));
        assertFalse(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(20, 30, 8)));
    }

    @Test
    void dryFerryDockRemainsValidWhenItsAdjacentSegmentIsOcean() {
        List<RouteSurveyPoint> route = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(20, 30, 0), true),
                new RouteSurveyPoint(new RoutePoint(80, 30, 0), true),
                new RouteSurveyPoint(new RoutePoint(100, 30, 0), false));
        assertTrue(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(2, 30, 1)));
        assertTrue(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(98, 30, 1)));
        assertTrue(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(0, 30, 7)));
        assertFalse(TradeRouteNavigationPolicy.withinSurveyCorridor(route,
                new RoutePoint(50, 30, 0)));
    }
}
