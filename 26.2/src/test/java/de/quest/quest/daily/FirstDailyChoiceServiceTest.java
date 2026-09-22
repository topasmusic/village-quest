package de.quest.quest.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FirstDailyChoiceServiceTest {
    @Test
    void freshPlayerReceivesThreeSafeDistinctChoices() {
        PlayerQuestData data = new PlayerQuestData();

        assertTrue(FirstDailyChoiceService.canChoose(data));
        assertEquals(3, FirstDailyChoiceService.choices().size());
        assertEquals(3, Set.copyOf(FirstDailyChoiceService.choices()).size());
        assertTrue(FirstDailyChoiceService.choices().contains(DailyQuestService.DailyQuestType.WHEAT_HARVEST));
        assertTrue(FirstDailyChoiceService.choices().contains(DailyQuestService.DailyQuestType.WOODCUTTING));
        assertTrue(FirstDailyChoiceService.choices().contains(DailyQuestService.DailyQuestType.WOOL_WEAVING));
    }

    @Test
    void existingDailyPlayersAreNotForcedBackThroughTheIntroduction() {
        PlayerQuestData data = new PlayerQuestData();
        data.setDailyDiscovered(true);

        assertFalse(FirstDailyChoiceService.canChoose(data));
    }

    @Test
    void introductoryTargetsAreShortButRegularTargetsStayUntouched() {
        assertEquals(8, FirstDailyChoiceService.introductoryTarget("daily.wheat.crop", 24));
        assertEquals(2, FirstDailyChoiceService.introductoryTarget("daily.wheat.bread", 6));
        assertEquals(12, FirstDailyChoiceService.introductoryTarget("daily.wood.log", 64));
        assertEquals(4, FirstDailyChoiceService.introductoryTarget("daily.coal.coal", 32));
        assertEquals(4, FirstDailyChoiceService.introductoryTarget("daily.wool.sheep", 10));
        assertEquals(30, FirstDailyChoiceService.introductoryTarget("daily.potato.potato", 30));
    }

    @Test
    void completionIsIdempotent() {
        PlayerQuestData data = new PlayerQuestData();
        data.setTradeRouteFlag("guild_intro.first_daily_active", true);

        assertTrue(FirstDailyChoiceService.complete(data));
        assertTrue(FirstDailyChoiceService.isCompleted(data));
        assertFalse(FirstDailyChoiceService.isActive(data));
        assertFalse(FirstDailyChoiceService.complete(data));
    }

    @Test
    void pre24ProgressMigratesOnlyTheFirstDailyGateAndIsIdempotent() {
        PlayerQuestData data = new PlayerQuestData();
        data.setDailyDiscovered(true);

        assertTrue(FirstDailyChoiceService.migratePre24Progress(data));
        assertTrue(FirstDailyChoiceService.isCompleted(data));
        assertFalse(FirstDailyChoiceService.isActive(data));
        assertFalse(data.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertFalse(data.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(PlayerQuestData.UNSET_DAY, data.getAcceptedDay());
        assertFalse(FirstDailyChoiceService.migratePre24Progress(data));
    }

    @Test
    void freshPlayerCannotJoinSharedDailyBeforeCuratedIntroductionCompletes() {
        PlayerQuestData fresh = new PlayerQuestData();

        assertTrue(FirstDailyChoiceService.canChoose(fresh));
        assertFalse(FirstDailyChoiceService.canUseSharedDaily(fresh));
        assertTrue(FirstDailyChoiceService.migratePre24Progress(fresh));
        assertTrue(FirstDailyChoiceService.canChoose(fresh));
        assertFalse(FirstDailyChoiceService.canUseSharedDaily(fresh));

        fresh.setTradeRouteFlag("guild_intro.first_daily_active", true);
        fresh.setDailyDiscovered(true);
        assertFalse(FirstDailyChoiceService.migratePre24Progress(fresh));
        assertFalse(FirstDailyChoiceService.canUseSharedDaily(fresh));

        assertTrue(FirstDailyChoiceService.complete(fresh));
        assertTrue(FirstDailyChoiceService.canUseSharedDaily(fresh));
    }

    @Test
    void routeOnlyLegacyProgressAlsoMigratesButContactOnlyStateDoesNot() {
        PlayerQuestData legacyRoute = new PlayerQuestData();
        legacyRoute.setTradeRouteInt("route_count", 1);
        assertTrue(FirstDailyChoiceService.migratePre24Progress(legacyRoute));

        PlayerQuestData newContact = new PlayerQuestData();
        newContact.setTradeRouteInt("bond_village_count", 1);
        newContact.setTradeRouteFlag("bond_village_0_contact", true);
        assertTrue(FirstDailyChoiceService.migratePre24Progress(newContact));
        assertFalse(FirstDailyChoiceService.isCompleted(newContact));
        assertTrue(FirstDailyChoiceService.canChoose(newContact));
    }

    @Test
    void migrationMarkerPreventsFreshMilestonesFromBeingRetargetedAsLegacyProgress() {
        PlayerQuestData fresh = new PlayerQuestData();

        assertTrue(FirstDailyChoiceService.migratePre24Progress(fresh));
        fresh.setMilestoneFlag("guild_corner.discovered", true);

        assertFalse(FirstDailyChoiceService.migratePre24Progress(fresh));
        assertFalse(FirstDailyChoiceService.isCompleted(fresh));
        assertTrue(FirstDailyChoiceService.canChoose(fresh));
        assertEquals(0L, fresh.getCurrencyBalance());
        assertEquals(PlayerQuestData.UNSET_DAY, fresh.getAcceptedDay());
        assertFalse(fresh.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertFalse(fresh.hasTradeRouteFlag("guild_intro.welcome_completed"));
    }
}
