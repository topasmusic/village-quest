package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import org.junit.jupiter.api.Test;

final class CaravanFreightLossTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void deathOnAnotherRouteDoesNotCompactTheAssignedFreightRoute() {
        var data = new PlayerQuestData(); data.setTradeRouteInt("guild_contract_type", 1);
        data.setTradeRouteInt("guild_contract_route", 3); data.setTradeRouteFlag("guild_contract_supplied", true);
        assertFalse(TradeGuildService.recordCrewLoss(data, 0));
        assertEquals(3, data.getTradeRouteInt("guild_contract_route")); assertTrue(TradeContractRefundLedger.peek(data).isEmpty());
        assertTrue(TradeGuildService.recordCrewLoss(data, 2));
        assertEquals(0, data.getTradeRouteInt("guild_contract_type"));
        assertFalse(TradeGuildService.recordCrewLoss(data, 2));
        assertEquals(1, TradeContractRefundLedger.peek(data).size());
    }
    @Test void partialClaimRetainsTheRestAndADuplicateCannotOverdraw() {
        var data = new PlayerQuestData(); assertTrue(TradeContractRefundLedger.queue(data, 1, 32));
        assertEquals(16, TradeContractRefundLedger.claim(data, 1, 16));
        assertEquals(16, TradeContractRefundLedger.peek(data).getFirst().amount());
        assertEquals(16, TradeContractRefundLedger.claim(data, 1, 32));
        assertEquals(0, TradeContractRefundLedger.claim(data, 1, 32)); assertTrue(TradeContractRefundLedger.peek(data).isEmpty());
    }
}
