package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CaravanPackAnimalPolicyTest {
    @Test
    void packMuleRejectsMountainShortcutAndOceanLegWhileFollowingTunnel() {
        List<RouteSurveyPoint> tunnel = List.of(
                new RouteSurveyPoint(new RoutePoint(0, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(20, 30, 0), false),
                new RouteSurveyPoint(new RoutePoint(40, 30, 0), true));
        RoutePoint leader = new RoutePoint(16, 30, 0);

        assertTrue(CaravanPackAnimalPolicy.mayFollow(tunnel, leader,
                new RoutePoint(13, 30, 1)));
        assertFalse(CaravanPackAnimalPolicy.mayFollow(tunnel, leader,
                new RoutePoint(13, 80, 1)));
        assertFalse(CaravanPackAnimalPolicy.mayFollow(tunnel,
                new RoutePoint(34, 30, 0),
                new RoutePoint(35, 30, 0)));
        assertFalse(CaravanPackAnimalPolicy.mayFollow(tunnel, leader,
                new RoutePoint(0, 30, 0)));
    }
}
