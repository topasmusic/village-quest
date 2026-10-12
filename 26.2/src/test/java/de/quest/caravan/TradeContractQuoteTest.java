package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TradeContractQuoteTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void oldAcceptedFreightKeepsItsOriginalRewardAndBonuses() {
        var data = new PlayerQuestData();
        assertEquals(20, TradeGuildService.acceptedReward(data, TradeContractType.BAKERS_DELIVERY, 1.0));
        assertEquals(37, TradeGuildService.acceptedReward(data, TradeContractType.BAKERS_DELIVERY, 1.85));
        assertEquals(50, TradeGuildService.acceptedReward(data, TradeContractType.FESTIVAL_BREAD, 1.0));
    }
    @Test void acceptedQuoteDoesNotChangeWhenRouteBonusesOrDefinitionsChange() {
        var data = new PlayerQuestData(); data.setTradeRouteInt("guild_contract_reward_quote", 17);
        assertEquals(17, TradeGuildService.acceptedReward(data, TradeContractType.BAKERS_DELIVERY, 1.85));
        assertEquals(17, TradeGuildService.acceptedReward(data, TradeContractType.FESTIVAL_BREAD, 1.0));
    }
}
