package de.quest.data;

import de.quest.economy.CaravanIncomeLedger;
import de.quest.economy.DeliveryEconomy;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DeliveryEconomyPersistenceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onlineCreditSurvivesReloadWithoutLoginOrOfflineWindfalls() {
        UUID id = UUID.randomUUID();
        var state = QuestState.fromNbt(new CompoundTag());
        var data = state.getPlayerData(id);
        data.setTradeRouteInt("network_income_day", 99);
        data.setTradeRouteInt("network_income_today", 60);
        data.setTradeRouteInt("network_escrow", 8);
        data.setTradeRouteInt("guild_contract_reward_quote", 17);
        assertEquals(0, CaravanIncomeLedger.withdraw(data, 15, true));
        for (int i = 0; i < 239; i++) CaravanIncomeLedger.onlineSecond(data);
        state = QuestState.fromNbt(QuestState.toNbt(state)); data = state.getPlayerData(id);
        assertEquals(8, data.getTradeRouteInt("network_escrow"));
        assertEquals(17, data.getTradeRouteInt("guild_contract_reward_quote"));
        assertEquals(0, CaravanIncomeLedger.withdraw(data, 15, false));
        assertEquals(0, CaravanIncomeLedger.withdraw(data, 15, true));
        CaravanIncomeLedger.onlineSecond(data);
        assertEquals(1, CaravanIncomeLedger.withdraw(data, 15, true));
        assertEquals(0, CaravanIncomeLedger.withdraw(data, 15, true));
        assertEquals(1, CaravanIncomeLedger.paidThisOnlineHour(data));
    }
    @Test void fiveRoutesShareFifteenMarksPerHourAndBankCannotGrowUnbounded() {
        var data = QuestState.fromNbt(new CompoundTag()).getPlayerData(UUID.randomUUID());
        for (int i = 0; i < 3600; i++) CaravanIncomeLedger.onlineSecond(data);
        int total = 0;
        for (int route = 0; route < 5; route++) total += CaravanIncomeLedger.withdraw(data, 15, true);
        assertEquals(15, total);
        for (int i = 0; i < 36000; i++) CaravanIncomeLedger.onlineSecond(data);
        assertEquals(15, CaravanIncomeLedger.withdraw(data, 1000, true));
    }
    @Test void freightAndDispatchReservationsShareSavedDailyLimitAndIgnoreClockRollback() {
        UUID id = UUID.randomUUID(); var state = QuestState.fromNbt(new CompoundTag());
        var data = state.getPlayerData(id);
        assertTrue(DeliveryEconomy.reserve(data, 100));
        assertTrue(DeliveryEconomy.reserve(data, 100));
        state = QuestState.fromNbt(QuestState.toNbt(state)); data = state.getPlayerData(id);
        assertTrue(DeliveryEconomy.reserve(data, 100));
        assertFalse(DeliveryEconomy.reserve(data, 100));
        assertFalse(DeliveryEconomy.reserve(data, 99));
        assertTrue(DeliveryEconomy.reserve(data, 101));
    }
    @Test void processedGrainPreservesIngredientValueAndBonusesDoNotMultiplyCargo() {
        assertEquals(DeliveryEconomy.materialValue(Items.WHEAT, 90), DeliveryEconomy.materialValue(Items.HAY_BLOCK, 10));
        assertEquals(DeliveryEconomy.materialValue(Items.WHEAT, 90), DeliveryEconomy.materialValue(Items.BREAD, 30));
        assertTrue(DeliveryEconomy.regionalReward(Items.IRON_INGOT, 8, 0)
                > DeliveryEconomy.regionalReward(Items.PAPER, 8, 0));
        assertEquals(9, DeliveryEconomy.transportBonus(Integer.MAX_VALUE, true, true, true));
    }
}
