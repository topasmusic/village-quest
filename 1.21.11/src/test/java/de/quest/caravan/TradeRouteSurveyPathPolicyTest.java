package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import org.junit.jupiter.api.Test;

final class TradeRouteSurveyPathPolicyTest {
    @Test
    void acceptsSafeTunnelStairsOffsetFromTheStraightSurveyLine() {
        assertTrue(TradeRouteSurveyPathPolicy.hasSafeLandSegment(
                new RoutePoint(0, 30, 0), new RoutePoint(24, 36, 0),
                (x, y, z) -> z == Math.min(3, Math.min(x, 24 - x))
                        && y == 30 + x / 4));
    }

    @Test
    void rejectsARealGapAndDoesNotEscapeToMountainSurface() {
        assertFalse(TradeRouteSurveyPathPolicy.hasSafeLandSegment(
                new RoutePoint(0, 30, 0), new RoutePoint(24, 36, 0),
                (x, y, z) -> x < 10 || x > 15));
        assertFalse(TradeRouteSurveyPathPolicy.hasSafeLandSegment(
                new RoutePoint(0, 30, 0), new RoutePoint(24, 36, 0),
                (x, y, z) -> y >= 80));
    }

    @Test
    void nearbyParallelRoadCannotSubstituteForAnUnsafeRecordedEndpoint() {
        assertFalse(TradeRouteSurveyPathPolicy.hasSafeLandSegment(
                new RoutePoint(0, 30, 0), new RoutePoint(24, 30, 0),
                (x, y, z) -> z == 3 && y == 30));
    }
}
