package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.data.PlayerQuestData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class TradeGuildEconomyDistanceTest {
    @Test
    void verticalProfileChangesTraversalButNotGuildDistanceReward() {
        PlayerQuestData flat = route(0);
        PlayerQuestData mountain = route(100);

        assertTrue(TradeRouteService.routeTraversalDistanceBlocks(mountain, 0)
                > TradeRouteService.routeTraversalDistanceBlocks(flat, 0));
        assertEquals(TradeRouteService.routeEconomyDistanceBlocks(flat, 0),
                TradeRouteService.routeEconomyDistanceBlocks(mountain, 0));
        assertEquals(TradeGuildService.distanceRewardMultiplier(
                        TradeRouteService.routeEconomyDistanceBlocks(flat, 0)),
                TradeGuildService.distanceRewardMultiplier(
                        TradeRouteService.routeEconomyDistanceBlocks(mountain, 0)));
    }

    private static PlayerQuestData route(int waypointY) {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("home_x", 0);
        data.setTradeRouteInt("home_z", 0);
        data.setTradeRouteInt("route_0_x", 100);
        data.setTradeRouteInt("route_0_z", 0);
        TradeRouteSurveyData.markRouteGeometryV2(data, 0, 0, 0);
        TradeRouteSurveyData.setRouteWaypoints(data, 0, List.of(new RoutePoint(50, waypointY, 0)));
        return data;
    }
}
