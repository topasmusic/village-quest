package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.ReputationDamageAdapter.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import org.junit.jupiter.api.Test;

final class ReputationHistoryTest {
    @Test void fatalEscalationKeepsTheFirstCrimeLocationWhenTheVictimMoves() {
        var data = new SocialReputationData(); UUID attacker = UUID.randomUUID(), victim = UUID.randomUUID();
        var oldVillage = VillageKey.overworld(0, 0); var newVillage = VillageKey.overworld(100, 0);
        ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, oldVillage, 5, false, 10));
        var fatal = ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, newVillage, 20, true, 20));
        assertEquals(-70, data.localTrust(oldVillage)); assertEquals(0, data.localTrust(newVillage));
        assertEquals(Map.of(oldVillage, -45), fatal.localDeltas());
        assertEquals(fatal.localDeltas(), data.history().getLast().localDeltas());
        assertEquals(Set.of(oldVillage), data.activeCase().affected());
    }
    @Test void warnedFirstHitFreezesItsLocationWithoutSuppressingTheFirstRealAssault() {
        var data = new SocialReputationData(); UUID attacker = UUID.randomUUID(), victim = UUID.randomUUID();
        var oldVillage = VillageKey.overworld(0, 0); var newVillage = VillageKey.overworld(100, 0);
        ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, oldVillage, 2, false, 10));
        data = SocialReputationData.fromNbt(data.toNbt());
        ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, newVillage, 5, false, 20));
        assertEquals(-15, data.guildTrust()); assertEquals(-25, data.localTrust(oldVillage)); assertEquals(0, data.localTrust(newVillage));
        assertEquals(Set.of(oldVillage), data.activeCase().affected());
    }
    @Test void completingAmendsRecordsActualLocalRecoveryAtTheCompletionTime() {
        var data = new SocialReputationData(); var village = VillageKey.overworld(0, 0);
        data.setGuildTrust(-60); data.setLocalTrust(village, -70); data.openCase(UUID.randomUUID(), Offence.ASSAULT, village, 10);
        data.recordAid(MaterialOption.WHEAT, 32); data.advanceOnlineTicks(12_000);
        assertTrue(ReparationService.complete(data, 12_010));
        assertEquals(12_010, data.history().getLast().tick());
        assertEquals(Map.of(village, 60), data.history().getLast().localDeltas());
    }
    @Test void caseCauseSurvivesHistoryEvictionAndReloadWithoutInventingACrewDeath() {
        var data = new SocialReputationData(); var village = VillageKey.overworld(0, 0);
        data.openCase(UUID.randomUUID(), Offence.MULE_KILL, village, 10);
        for (int i = 0; i < 40; i++) data.appendAdministrativeHistory(UUID.randomUUID(), 0, null, 0, 11 + i);
        data.openCase(UUID.randomUUID(), Offence.ASSAULT, village, 60);
        var loaded = SocialReputationData.fromNbt(data.toNbt());
        assertEquals(Offence.MULE_KILL, loaded.activeCase().offence());
        assertEquals(Offence.MULE_KILL.ordinal(), ReputationViewService.build(loaded, List.of(village), 0, true).reparation().offence());
    }
    @Test void attacksLessThanSixtySecondsApartRemainOneIncidentAcrossReload() {
        var data = new SocialReputationData(); UUID attacker = UUID.randomUUID(), victim = UUID.randomUUID();
        var village = VillageKey.overworld(0, 0);
        for (long tick : new long[]{0, 1_000, 2_000}) {
            ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, village, 5, false, tick));
            data = SocialReputationData.fromNbt(data.toNbt());
        }
        assertEquals(-15, data.guildTrust()); assertEquals(-25, data.localTrust(village));
        assertEquals(1, data.history().size());
        ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, village, 20, true, 3_000));
        assertEquals(-60, data.guildTrust()); assertEquals(-70, data.localTrust(village));
    }
    @Test void warnedTraderBreaksOffOnlyForThatPlayerAndThirtySecondsAcrossReload() {
        var data = new SocialReputationData(); UUID attacker = UUID.randomUUID(), victim = UUID.randomUUID(), other = UUID.randomUUID();
        ReputationIncidentService.record(data, new DamageEvidence(attacker, victim, VictimKind.CREW, null, 2, false, 10));
        data = SocialReputationData.fromNbt(data.toNbt());
        assertTrue(data.traderWarningActive(victim, 609));
        assertFalse(data.traderWarningActive(victim, 610)); assertFalse(data.traderWarningActive(other, 11));
        assertFalse(new SocialReputationData().traderWarningActive(victim, 11)); assertNull(data.activeCase());
    }

}
