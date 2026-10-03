package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SocialReputationRulesTest {
    private static final VillageKey VILLAGE = VillageKey.overworld(128, -224);

    @Test void ranksChangeOnlyAtThePublishedBoundaries() {
        int[] values = {-100, -60, -59, -20, -19, 19, 20, 59, 60, 100};
        SocialRank[] expected = {SocialRank.OSTRACISED, SocialRank.OSTRACISED,
                SocialRank.DISTRUSTED, SocialRank.DISTRUSTED, SocialRank.NEUTRAL, SocialRank.NEUTRAL,
                SocialRank.RELIABLE, SocialRank.RELIABLE, SocialRank.RESPECTED, SocialRank.RESPECTED};
        for (int i = 0; i < values.length; i++) assertEquals(expected[i], SocialReputationRules.rankFor(values[i]));
    }

    @Test void earnedTrustCannotBuyPermissionToKill() {
        assertEquals(-60, SocialReputationRules.lossAfter(100, Offence.CREW_KILL, TrustScope.GUILD));
        assertEquals(-100, SocialReputationRules.lossAfter(-90, Offence.CREW_KILL, TrustScope.GUILD));
        assertEquals(-20, SocialReputationRules.lossAfter(100, Offence.VILLAGER_KILL, TrustScope.GUILD));
        assertEquals(-60, SocialReputationRules.lossAfter(100, Offence.VILLAGER_KILL, TrustScope.VILLAGE));
        assertEquals(-20, SocialReputationRules.lossAfter(100, Offence.MULE_KILL, TrustScope.GUILD));
        int[] before = {100, 20, 0, -30, -90};
        int[] after = {-45, -45, -45, -75, -100};
        for (int i = 0; i < before.length; i++)
            assertEquals(after[i], SocialReputationRules.lossAfter(before[i], Offence.MULE_KILL, TrustScope.VILLAGE));
    }

    @Test void openCaseOverridesPositiveRankWithoutLockingAnUnrelatedVillage() {
        SocialReputationData data = new SocialReputationData();
        data.setGuildTrust(80);
        assertEquals(6, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).caravanBuyLimit());
        assertEquals(5, SocialReputationRules.decision(ServiceKind.DISPATCH_START, data, null).rewardPercent());
        data.openCase(UUID.randomUUID(), Offence.ASSAULT, VILLAGE, 25);
        assertFalse(SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).allowed());
        assertFalse(SocialReputationRules.decision(ServiceKind.NOTICE_ACCEPT, data, VILLAGE).allowed());
        assertTrue(SocialReputationRules.decision(ServiceKind.NOTICE_ACCEPT, data, VillageKey.overworld(1024, 1024)).allowed());
        assertEquals(12_000, data.activeCase().onlineTicksRemaining());
        assertEquals(1, data.activeCase().requiredDeliveries());
    }

    @Test void baselineAndProbationPreserveOrdinaryAccess() {
        SocialReputationData data = new SocialReputationData();
        assertEquals(4, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).caravanBuyLimit());
        data.setGuildTrust(20);
        assertEquals(5, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).caravanBuyLimit());
        data.setGuildTrust(-20);
        assertEquals(2, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).caravanBuyLimit());
        assertFalse(SocialReputationRules.decision(ServiceKind.CONVOY_JOIN, data, null).allowed());
        data.setGuildTrust(-60);
        assertFalse(SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).allowed());
        data.setGuildTrust(80);
        data.setProbationRemaining(2);
        assertTrue(SocialReputationRules.decision(ServiceKind.DISPATCH_START, data, null).allowed());
        assertEquals(0, SocialReputationRules.decision(ServiceKind.DISPATCH_START, data, null).rewardPercent());
        assertEquals(4, SocialReputationRules.decision(ServiceKind.CARAVAN_TRADE, data, null).caravanBuyLimit());
    }

    @Test void deathEscalatesToThreeSpecificAidDeliveriesAndThirtyOnlineMinutes() {
        SocialReputationData data = new SocialReputationData();
        data.openCase(UUID.randomUUID(), Offence.CREW_KILL, VILLAGE, 25);
        assertEquals(36_000, data.activeCase().onlineTicksRemaining());
        assertEquals(3, data.activeCase().requiredDeliveries());
        assertEquals(64, data.activeCase().required(MaterialOption.WHEAT));
        assertEquals(32, data.activeCase().required(MaterialOption.PLANKS));
        assertEquals(8, data.activeCase().required(MaterialOption.IRON));
        assertThrows(IllegalArgumentException.class, () -> new StandingKey(UUID.randomUUID(), TrustScope.VILLAGE, null));
    }

    @Test void silvermarkBonusUsesTheExactRoundedAmount() {
        assertEquals(21, SocialReputationRules.rewardWithBonus(20, 5));
        assertEquals(20, SocialReputationRules.rewardWithBonus(20, 0));
        assertEquals(1, SocialReputationRules.rewardWithBonus(1, 5));
        assertThrows(IllegalArgumentException.class, () -> SocialReputationRules.rewardWithBonus(-1, 5));
    }
}
