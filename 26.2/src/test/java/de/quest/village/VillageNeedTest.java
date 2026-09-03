package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import org.junit.jupiter.api.Test;

final class VillageNeedTest {
    @Test
    void everyVillageIdentityHasTwoBoundedNeeds() {
        for (VillageBondType type : VillageBondType.values()) {
            assertEquals(2, VillageNeed.countFor(type));
            VillageNeed first = VillageNeed.forVillage(type, 0);
            VillageNeed second = VillageNeed.next(type, first);
            assertNotEquals(first, second);
            assertEquals(first, VillageNeed.next(type, second));
        }
    }

    @Test
    void matchingSuppliesHelpMoreWithoutInvalidatingOtherDeliveries() {
        VillageNeed need = VillageNeed.GRANARY_SEED_RESERVES;
        assertTrue(need.matches(VillageRequestType.GRANARY_SEED));
        assertTrue(need.matches(VillageRequestType.GRANARY_WHEAT));
        assertFalse(need.matches(VillageRequestType.GRANARY_BREAD));
        assertEquals(LivingVillageNetworkService.MATCHING_DELIVERY_SUPPORT,
                LivingVillageNetworkService.deliverySupport(need, VillageRequestType.GRANARY_SEED));
        assertEquals(LivingVillageNetworkService.GENERAL_DELIVERY_SUPPORT,
                LivingVillageNetworkService.deliverySupport(need, VillageRequestType.GRANARY_BREAD));
    }
}
