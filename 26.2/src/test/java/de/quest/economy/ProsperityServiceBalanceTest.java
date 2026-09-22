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
    void festivalGuaranteedBonusRemainsASinkAcrossThreeMinimumRewards() {
        long totalMinimumBonus = 3L * ProsperityService.festivalBonusAmount(1L);
        assertTrue(totalMinimumBonus < ProsperityService.VillageService.VILLAGE_FESTIVAL.cost());
        assertEquals(3L, ProsperityService.festivalBonusAmount(3L));
        assertEquals(25L, ProsperityService.festivalBonusAmount(100L));
    }

    @Test
    void ceremonyGuaranteedBonusRemainsASinkAcrossThreeMinimumRewards() {
        long totalMinimumBonus = 3L * ProsperityService.ceremonyBonusAmount(1L);
        assertTrue(totalMinimumBonus < ProsperityService.VillageService.GUILD_CEREMONY.cost());
        assertEquals(6L, ProsperityService.ceremonyBonusAmount(20L));
        assertEquals(25L, ProsperityService.ceremonyBonusAmount(100L));
    }
}
