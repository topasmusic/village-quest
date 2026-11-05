package de.quest.quest.weekly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WeeklyTurnInTargetBalanceTest {
    @Test
    void smithMiningWorkLeavesBothRawDeliveryAmountsAfterSmelting() {
        assertEquals(
                WeeklyQuestService.smithOreDeliveryTarget() + WeeklyQuestService.smithIronTarget(),
                WeeklyQuestService.smithOreTarget()
        );
        assertEquals(
                WeeklyQuestService.smithGoldOreDeliveryTarget() + WeeklyQuestService.smithGoldTarget(),
                WeeklyQuestService.smithGoldOreTarget()
        );
        assertEquals(
                WeeklyQuestService.smithOreDeliveryTarget(),
                WeeklyQuestService.smithOreTarget() - WeeklyQuestService.smithIronTarget()
        );
        assertEquals(
                WeeklyQuestService.smithGoldOreDeliveryTarget(),
                WeeklyQuestService.smithGoldOreTarget() - WeeklyQuestService.smithGoldTarget()
        );
    }

    @Test
    void harvestWorkLeavesTheWheatDeliveryAfterBakingBread() {
        int breadInput = WeeklyQuestService.harvestBreadTarget() * 3;

        assertEquals(
                WeeklyQuestService.harvestWheatDeliveryTarget() + breadInput,
                WeeklyQuestService.harvestWheatTarget()
        );
        assertEquals(
                WeeklyQuestService.harvestWheatDeliveryTarget(),
                WeeklyQuestService.harvestWheatTarget() - breadInput
        );
    }
}
