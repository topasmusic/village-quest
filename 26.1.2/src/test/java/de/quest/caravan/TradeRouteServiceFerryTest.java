package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class TradeRouteServiceFerryTest {
    @Test
    void landNodesBoundTheVirtualFerryAndSupplyDirectionalDocks() throws Exception {
        PlayerQuestData data = islandRoute();

        assertFalse(ferryActive(data, 2_000, 1));
        assertTrue(ferryActive(data, 5_000, 1));
        assertFalse(ferryActive(data, 8_000, 1));

        assertNotNull(boardingAt(data, 2_000, 1));
        assertNull(boardingAt(data, 8_000, 1));
        assertNotNull(boardingAt(data, 8_000, -1));
        assertNull(boardingAt(data, 2_000, -1));
    }

    @Test
    void boardingIsDetectedOnlyWhenMovementCrossesTowardTheSea() throws Exception {
        PlayerQuestData data = islandRoute();

        assertNotNull(crossedBoarding(data, 1_950, 2_050, 1));
        assertNull(crossedBoarding(data, 2_050, 2_150, 1));
        assertNotNull(crossedBoarding(data, 8_050, 7_950, -1));
        assertNull(crossedBoarding(data, 7_950, 7_850, -1));
    }

    private static PlayerQuestData islandRoute() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("home_x", 0);
        data.setTradeRouteInt("home_z", 0);
        data.setTradeRouteInt("route_0_x", 100);
        data.setTradeRouteInt("route_0_z", 0);
        data.setTradeRouteInt("route_0_waypoint_count", 3);
        waypoint(data, 0, 20, false);
        waypoint(data, 1, 50, true);
        waypoint(data, 2, 80, false);
        return data;
    }

    private static void waypoint(PlayerQuestData data, int index, int x, boolean ocean) {
        data.setTradeRouteInt("route_0_waypoint_" + index + "_x", x);
        data.setTradeRouteInt("route_0_waypoint_" + index + "_z", 0);
        data.setTradeRouteFlag("route_0_waypoint_" + index + "_ocean", ocean);
    }

    private static boolean ferryActive(PlayerQuestData data, int progress, int direction) throws Exception {
        Method method = TradeRouteService.class.getDeclaredMethod(
                "ferryState", PlayerQuestData.class, int.class, int.class, int.class);
        method.setAccessible(true);
        Object state = method.invoke(null, data, 0, progress, direction);
        Method active = state.getClass().getDeclaredMethod("active");
        active.setAccessible(true);
        return (boolean) active.invoke(state);
    }

    private static Object boardingAt(PlayerQuestData data, int progress, int direction) throws Exception {
        Method method = TradeRouteService.class.getDeclaredMethod(
                "ferryBoardingAtProgress", PlayerQuestData.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return method.invoke(null, data, 0, progress, direction);
    }

    private static Object crossedBoarding(PlayerQuestData data,
                                           int progress,
                                           int proposedProgress,
                                           int direction) throws Exception {
        Method method = TradeRouteService.class.getDeclaredMethod(
                "crossedFerryBoarding", PlayerQuestData.class, int.class,
                int.class, int.class, int.class);
        method.setAccessible(true);
        return method.invoke(null, data, 0, progress, proposedProgress, direction);
    }
}
