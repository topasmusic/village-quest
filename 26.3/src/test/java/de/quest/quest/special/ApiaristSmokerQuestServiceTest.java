package de.quest.quest.special;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ApiaristSmokerQuestServiceTest {
    @Test
    void honeyWorkLeavesTheBottleDeliveryAfterCraftingBlocks() {
        int honeyBlockInput = ApiaristSmokerQuestService.honeyBlockTarget() * 4;

        assertEquals(
                ApiaristSmokerQuestService.honeyDeliveryTarget() + honeyBlockInput,
                ApiaristSmokerQuestService.honeyWorkTarget()
        );
        assertEquals(
                ApiaristSmokerQuestService.honeyDeliveryTarget(),
                ApiaristSmokerQuestService.honeyWorkTarget() - honeyBlockInput
        );
    }
}
