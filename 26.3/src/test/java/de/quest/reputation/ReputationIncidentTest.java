package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.ReputationDamageAdapter.*;
import de.quest.reputation.ReputationIncidentService.Outcome;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReputationIncidentTest {
    private static final UUID PLAYER = UUID.randomUUID(), VICTIM = UUID.randomUUID();
    private static final VillageKey VILLAGE = VillageKey.overworld(100, 200);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static DamageEvidence damage(UUID victim, float amount, boolean fatal, long tick) {
        return new DamageEvidence(PLAYER, victim, VictimKind.CREW, VILLAGE, amount, fatal, tick);
    }
    @Test void blockedAndUnattributedDamageDoesNotCreateAFall() {
        var data = new SocialReputationData();
        assertEquals(Outcome.IGNORED, ReputationIncidentService.record(data, damage(VICTIM, 0, false, 1)).outcome());
        assertEquals(Outcome.IGNORED, ReputationIncidentService.record(data,
                new DamageEvidence(null, VICTIM, VictimKind.CREW, VILLAGE, 20, true, 1)).outcome());
        assertNull(data.activeCase()); assertEquals(0, data.guildTrust());
    }
    @Test void firstWeakSweepIsOneWarningButALaterWeakHitIsAssaultAcrossReload() {
        var data = new SocialReputationData();
        assertEquals(Outcome.WARN, ReputationIncidentService.record(data, damage(VICTIM, 4, false, 100)).outcome());
        assertEquals(Outcome.IGNORED, ReputationIncidentService.record(data, damage(UUID.randomUUID(), 2, false, 100)).outcome());
        assertNull(data.activeCase());
        data = SocialReputationData.fromNbt(data.toNbt());
        assertEquals(Outcome.ASSAULT, ReputationIncidentService.record(data, damage(VICTIM, 1, false, 101)).outcome());
        assertEquals(-15, data.guildTrust()); assertEquals(-25, data.localTrust(VILLAGE));
    }
    @Test void strongOrFatalFirstHitOverridesTheWeakWarningWave() {
        var data = new SocialReputationData();
        ReputationIncidentService.record(data, damage(VICTIM, 2, false, 100));
        assertEquals(Outcome.ASSAULT, ReputationIncidentService.record(data, damage(VICTIM, 4.01f, false, 100)).outcome());
        assertEquals(Outcome.FATAL, ReputationIncidentService.record(data, damage(VICTIM, 1, true, 100)).outcome());
        assertEquals(-60, data.guildTrust()); assertEquals(-70, data.localTrust(VILLAGE));
        assertTrue(data.activeCase().major());
        assertEquals(Outcome.IGNORED, ReputationIncidentService.record(data, damage(VICTIM, 1, true, 101)).outcome());
    }
    @Test void escalationRemovesOnlyItsOwnAppliedLossAndPreservesAnotherVictimAndBenefit() {
        var data = new SocialReputationData(); data.setGuildTrust(80); data.setLocalTrust(VILLAGE, 80);
        ReputationIncidentService.record(data, damage(VICTIM, 5, false, 1));
        ReputationIncidentService.record(data, damage(UUID.randomUUID(), 5, false, 2));
        data.setGuildTrust(data.guildTrust() + 3); data.setLocalTrust(VILLAGE, data.localTrust(VILLAGE) + 6);
        data = SocialReputationData.fromNbt(data.toNbt());
        ReputationIncidentService.record(data, damage(VICTIM, 1, true, 3));
        assertEquals(-60, data.guildTrust()); assertEquals(-60, data.localTrust(VILLAGE));
        // Undoing both attacks or restoring the old total would also appear -60 because of the kill ceiling.
        var low = new SocialReputationData(); low.setGuildTrust(-30); low.setLocalTrust(VILLAGE, -30);
        ReputationIncidentService.record(low, damage(VICTIM, 5, false, 1));
        ReputationIncidentService.record(low, damage(UUID.randomUUID(), 5, false, 2));
        low.setGuildTrust(low.guildTrust() + 3); low.setLocalTrust(VILLAGE, low.localTrust(VILLAGE) + 6);
        ReputationIncidentService.record(low, damage(VICTIM, 1, true, 3));
        assertEquals(-100, low.guildTrust()); assertEquals(-100, low.localTrust(VILLAGE));
        assertEquals(-43, low.history().getLast().guildDelta());
    }
    @Test void assaultsAggregateForSixtySecondsButRenewAidAndTimer() {
        var data = new SocialReputationData();
        ReputationIncidentService.record(data, damage(VICTIM, 5, false, 1));
        UUID caseId = data.activeCase().id(); data.recordAid(MaterialOption.WHEAT, 32); data.advanceOnlineTicks(100);
        assertEquals(Outcome.ASSAULT, ReputationIncidentService.record(data, damage(VICTIM, 5, false, 100)).outcome());
        assertEquals(-15, data.guildTrust()); assertEquals(caseId, data.activeCase().id());
        assertEquals(0, data.activeCase().delivered(MaterialOption.WHEAT)); assertEquals(12_000, data.activeCase().onlineTicksRemaining());
        ReputationIncidentService.record(data, damage(VICTIM, 5, false, 1_202));
        assertEquals(-15, data.guildTrust());
        ReputationIncidentService.record(data, damage(VICTIM, 5, false, 2_403));
        assertEquals(-30, data.guildTrust());
    }
    @Test void aNewWeakWarningIsAvailableAfterTenServerMinutes() {
        var data = new SocialReputationData();
        assertEquals(Outcome.WARN, ReputationIncidentService.record(data, damage(VICTIM, 1, false, 10)).outcome());
        assertEquals(Outcome.WARN, ReputationIncidentService.record(data, damage(VICTIM, 1, false, 12_010)).outcome());
        assertNull(data.activeCase());
    }
}
