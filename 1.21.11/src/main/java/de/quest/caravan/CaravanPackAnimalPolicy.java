package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import java.util.List;
import java.util.UUID;

/** Bounds the optional pack mule to the same recorded road as the crew. */
final class CaravanPackAnimalPolicy {
    private static final String[] NAMES = {
            "Bramble", "Clover", "Thistle", "Pebble", "Moss", "Juniper", "Hazel", "Ash"
    };
    private static final double MAX_FOLLOW_DISTANCE_SQUARED = 12.0 * 12.0;

    private CaravanPackAnimalPolicy() {}

    static boolean mayFollow(List<RouteSurveyPoint> surveyPath, RoutePoint leader, RoutePoint candidate) {
        if (leader == null || candidate == null || !leader.hasElevation() || !candidate.hasElevation()) {
            return false;
        }
        double dx = leader.x() - candidate.x();
        double dy = leader.y() - candidate.y();
        double dz = leader.z() - candidate.z();
        if (dx * dx + dy * dy + dz * dz > MAX_FOLLOW_DISTANCE_SQUARED) {
            return false;
        }
        return surveyPath == null || surveyPath.isEmpty()
                || TradeRouteNavigationPolicy.withinSurveyCorridor(surveyPath, candidate);
    }

    static String name(UUID connectionId) {
        return NAMES[Math.floorMod(connectionId.hashCode(), NAMES.length)];
    }
}
