package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.quest.data.PlayerQuestData;
import org.junit.jupiter.api.Test;

final class CaravanCrewDataTest {
    @Test
    void crewNamesPersistAndFollowConnectionThroughSlotCompaction() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_count", 2);
        CaravanCrewData.Crew first = CaravanCrewData.ensure(data, 0);
        CaravanCrewData.Crew second = CaravanCrewData.ensure(data, 1);

        assertEquals(first, CaravanCrewData.ensure(data, 0));
        assertEquals(second, CaravanCrewData.ensure(data, 1));
        TradeRouteData.removeRoute(data, 0, TradeRouteService.MAX_ROUTES);
        assertEquals(second, CaravanCrewData.ensure(data, 0));
    }

    @Test
    void legacyRouteGetsDistinctStableRoleNames() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_count", 1);
        CaravanCrewData.Crew crew = CaravanCrewData.ensure(data, 0);
        assertFalse(crew.master().isBlank());
        assertFalse(crew.trader().isBlank());
        assertFalse(crew.guard().isBlank());
        assertFalse(crew.courier().isBlank());
        assertFalse(crew.master().equals(crew.trader()));
        assertFalse(crew.trader().equals(crew.guard()));
    }
}
