package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SocialReputationBenefitsTest {
    private static final UUID PLAYER = UUID.fromString("71a84590-53d5-446e-8505-72f9caadf680");
    private static final VillageKey SOURCE = VillageKey.overworld(128, -224), TARGET = VillageKey.overworld(400, 200);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static BenefitEvent event(BenefitKind kind, long reset, VillageKey... villages) {
        return new BenefitEvent(UUID.randomUUID(), kind, PLAYER, List.of(villages), reset);
    }

    @Test void repeatableGuildAndLocalCapsPersistAndNeverBankUnusedPoints() {
        SocialReputationData data = new SocialReputationData();
        var first = SocialReputationService.recordBenefit(data, event(BenefitKind.CONVOY, 10, SOURCE, TARGET), 100);
        assertEquals(6, first.guildDelta()); assertEquals(2, first.localDeltas().get(SOURCE));
        var dispatch = SocialReputationService.recordBenefit(data, event(BenefitKind.DISPATCH, 10, SOURCE, TARGET), 200);
        assertEquals(2, dispatch.guildDelta()); assertEquals(4, dispatch.localDeltas().get(TARGET));
        SocialReputationService.recordBenefit(data, event(BenefitKind.NOTICE, 10, TARGET), 300);
        assertEquals(8, data.guildTrust()); assertEquals(8, data.localTrust(TARGET));
        SocialReputationData loaded = SocialReputationData.fromNbt(data.toNbt());
        assertEquals(0, SocialReputationService.recordBenefit(loaded, event(BenefitKind.WEEKLY, 10), 400).guildDelta());
        assertEquals(3, SocialReputationService.recordBenefit(loaded, event(BenefitKind.WEEKLY, 11), 500).guildDelta());
        assertEquals(11, loaded.guildTrust());
    }

    @Test void perSourceLimitsStopContractGrindingWithoutSuppressingTheReceipt() {
        SocialReputationData data = new SocialReputationData();
        assertEquals(1, SocialReputationService.recordBenefit(data, event(BenefitKind.DAILY, 10), 1).guildDelta());
        assertEquals(1, SocialReputationService.recordBenefit(data, event(BenefitKind.DAILY, 10), 2).guildDelta());
        assertEquals(0, SocialReputationService.recordBenefit(data, event(BenefitKind.DAILY, 10), 3).guildDelta());
        SocialReputationService.recordBenefit(data, event(BenefitKind.DISPATCH, 10, SOURCE, TARGET), 4);
        SocialReputationService.recordBenefit(data, event(BenefitKind.DISPATCH, 10, SOURCE, TARGET), 5);
        var limited = event(BenefitKind.DISPATCH, 10, SOURCE, TARGET);
        assertEquals(0, SocialReputationService.recordBenefit(data, limited, 6).guildDelta());
        assertEquals(8, data.localTrust(TARGET));
        SocialReputationData loaded = SocialReputationData.fromNbt(data.toNbt());
        assertFalse(SocialReputationService.recordBenefit(loaded, limited, 7).applied());
        assertEquals(1, SocialReputationService.recordBenefit(loaded, event(BenefitKind.DAILY, 11), 8).guildDelta());
    }

    @Test void onceOnlyStoriesBypassDailyCapsButSurviveVisibleHistoryEviction() {
        SocialReputationData data = new SocialReputationData();
        BenefitEvent old = event(BenefitKind.STORY, 10, TARGET);
        assertEquals(3, SocialReputationService.recordBenefit(data, old, 1).guildDelta());
        assertEquals(6, data.localTrust(TARGET));
        for (int i = 0; i < 40; i++) SocialReputationService.recordBenefit(data, event(BenefitKind.AFTERSTORY, 10, TARGET), i + 2);
        assertEquals(32, data.history().size());
        SocialReputationData loaded = SocialReputationData.fromNbt(data.toNbt());
        assertFalse(SocialReputationService.recordBenefit(loaded, old, 100).applied());
        assertEquals(32, loaded.history().size());
    }

    @Test void onlyActuallyCreditedBenefitsAdvanceProbation() {
        SocialReputationData data = new SocialReputationData(); data.setProbationRemaining(2);
        SocialReputationService.recordBenefit(data, event(BenefitKind.CONVOY, 10, SOURCE), 1);
        assertEquals(1, data.probationRemaining());
        SocialReputationService.recordBenefit(data, event(BenefitKind.CONVOY, 10, SOURCE), 2);
        assertEquals(1, data.probationRemaining());
        SocialReputationService.recordBenefit(data, event(BenefitKind.NOTICE, 10, SOURCE), 3);
        assertEquals(0, data.probationRemaining());
    }

    @Test void resetClockGoingBackwardDoesNotRefillCaps() {
        SocialReputationData data = new SocialReputationData();
        SocialReputationService.recordBenefit(data, event(BenefitKind.CONVOY, 10, SOURCE), 1);
        SocialReputationService.recordBenefit(data, event(BenefitKind.WEEKLY, 10), 2);
        assertEquals(0, SocialReputationService.recordBenefit(data, event(BenefitKind.WEEKLY, 9), 3).guildDelta());
        assertEquals(8, data.guildTrust());
    }

    @Test void assistingManyDifferentVillagesRemainsAValidSavedState() {
        SocialReputationData data = new SocialReputationData();
        for (int i = 0; i < 20; i++) SocialReputationService.recordBenefit(data,
                event(BenefitKind.NOTICE, 10, VillageKey.overworld(i * 100, 200)), i);
        SocialReputationData loaded = SocialReputationData.fromNbt(data.toNbt());
        assertTrue(loaded.writable());
        assertEquals(8, loaded.guildTrust());
        assertEquals(4, loaded.localTrust(VillageKey.overworld(1900, 200)));
    }

    @Test void convoyWithoutAnyActualVillageArrivalCannotGenerateTrust() {
        SocialReputationData data = new SocialReputationData();
        var invalid = event(BenefitKind.CONVOY, 10);
        assertThrows(IllegalArgumentException.class, () -> SocialReputationService.recordBenefit(data, invalid, 1));
        assertFalse(data.hasReceipt(BenefitKind.CONVOY, invalid.sourceId()));
    }

    @Test void repeatableCompletionSlotsHaveDurableIndependentIdentities() {
        SocialReputationData data = new SocialReputationData();
        assertEquals(1, SocialReputationService.recordQuestCompletion(data, PLAYER, BenefitKind.DAILY, 10, "normal", 10, 1).guildDelta());
        assertEquals(1, SocialReputationService.recordQuestCompletion(data, PLAYER, BenefitKind.DAILY, 10, "bonus", 10, 2).guildDelta());
        SocialReputationData loaded = SocialReputationData.fromNbt(data.toNbt());
        assertFalse(SocialReputationService.recordQuestCompletion(loaded, PLAYER, BenefitKind.DAILY, 10, "normal", 11, 3).applied());
        assertEquals(1, SocialReputationService.recordQuestCompletion(loaded, PLAYER, BenefitKind.DAILY, 11, "normal", 11, 4).guildDelta());
        assertEquals(3, SocialReputationService.recordQuestCompletion(loaded, PLAYER, BenefitKind.WEEKLY, 7, "normal", 11, 5).guildDelta());
        assertFalse(SocialReputationService.recordQuestCompletion(loaded, PLAYER, BenefitKind.WEEKLY, 7, "normal", 12, 6).applied());
    }
    @Test void aDelayedRepeatableSourceCannotBeBankedIntoANewerReset() {
        var data = new SocialReputationData();
        SocialReputationService.recordBenefit(data, event(BenefitKind.DAILY, 11), 100);
        var delayed = event(BenefitKind.CONVOY, 10, SOURCE);
        var result = SocialReputationService.recordBenefit(data, delayed, 200);
        assertTrue(result.applied()); assertEquals(0, result.guildDelta()); assertTrue(result.localDeltas().isEmpty());
        assertEquals(1, data.guildTrust()); assertTrue(data.hasReceipt(BenefitKind.CONVOY, delayed.sourceId()));
        assertFalse(SocialReputationService.recordBenefit(data, delayed, 300).applied());
    }

}
