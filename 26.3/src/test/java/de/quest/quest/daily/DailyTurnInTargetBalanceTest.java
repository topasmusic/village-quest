package de.quest.quest.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DailyTurnInTargetBalanceTest {
    @Test
    void smithMiningWorkLeavesTheDisplayedRawDeliveryAfterSmelting() {
        int workTarget = DailyQuestService.smithSmeltOreTarget();
        int deliveryTarget = DailyQuestService.smithSmeltRawDeliveryTarget();
        int smeltingInput = DailyQuestService.smithSmeltIngotTarget();

        assertEquals(deliveryTarget + smeltingInput, workTarget);
        assertEquals(deliveryTarget, workTarget - smeltingInput);
    }
}
