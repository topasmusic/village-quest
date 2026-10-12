package de.quest.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProsperityServiceBalanceTest {
    @Test
    void serverboundEconomyActionsRequireProsperityAccess() {
        assertFalse(ProsperityService.isActionAuthorized(false, "prosperity:market"));
        assertFalse(ProsperityService.isActionAuthorized(false, "service:village_festival"));
        assertTrue(ProsperityService.isActionAuthorized(true, "prosperity:market"));
        assertFalse(ProsperityService.isActionAuthorized(true, ""));
    }

    @Test
    void festivalDeliversItsAdvertisedFourSilvermarkMinimum() {
        long totalMinimumBonus = 3L * ProsperityService.festivalBonusAmount(1L);
        assertEquals(12L, totalMinimumBonus);
        assertEquals(4L, ProsperityService.festivalBonusAmount(3L));
        assertEquals(25L, ProsperityService.festivalBonusAmount(100L));
    }

    @Test
    void ceremonyDeliversItsAdvertisedEightSilvermarkMinimum() {
        long totalMinimumBonus = 3L * ProsperityService.ceremonyBonusAmount(1L);
        assertEquals(24L, totalMinimumBonus);
        assertEquals(8L, ProsperityService.ceremonyBonusAmount(20L));
        assertEquals(25L, ProsperityService.ceremonyBonusAmount(100L));
    }
}
