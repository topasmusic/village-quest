package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RegionalDispatchOffersTest {
    @Test
    void everyActivePairIsOfferedEvenWhenAllVillagesShareOneIdentity() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_count", 5);
        for (int i = 0; i < 5; i++) {
            data.setTradeRouteInt("route_" + i + "_x", i * 100);
            data.setTradeRouteInt("route_" + i + "_z", i * 120);
            data.setTradeRouteInt("route_" + i + "_village_index", i + 1);
            data.setTradeRouteInt("bond_village_" + i + "_type", 1);
        }
        assertEquals(List.of(1, 2, 3, 4), RegionalDispatchService.destinationIndices(data, 0,
                index -> true));
        assertEquals(List.of(1, 3, 4), RegionalDispatchService.destinationIndices(data, 0,
                index -> index != 2));
        assertTrue(RegionalDispatchService.destinationIndices(data, 0, index -> false).isEmpty());
    }

    @Test
    void unrelatedRouteCompactionCannotReassignFreightToAnotherConnection() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteInt("route_count", 3);
        UUID source = TradeRouteData.ensureConnectionId(data, 0);
        UUID unrelated = TradeRouteData.ensureConnectionId(data, 1);
        UUID target = TradeRouteData.ensureConnectionId(data, 2);
        assertNotEquals(unrelated, target);
        var freight = new RegionalDispatchLedger.Dispatch(UUID.randomUUID(), source, target,
                "minecraft:overworld", 10, 20, 100, 200, 1, 1,
                "minecraft:bread", 8, 12, RegionalDispatchLedger.Stage.WAITING_AT_SOURCE);
        assertTrue(RegionalDispatchLedger.start(data, freight));
        assertTrue(TradeRouteService.removeRoute(data, 1));
        assertEquals(target.toString(), data.getTradeRouteString("route_1_connection_id"));
        assertEquals(RegionalDispatchLedger.Stage.WAITING_AT_SOURCE,
                RegionalDispatchLedger.read(data).stage());
        assertTrue(TradeRouteService.removeRoute(data, 1));
        assertEquals(RegionalDispatchLedger.Stage.CANCELLED_SAFE,
                RegionalDispatchLedger.read(data).stage());
    }
}
