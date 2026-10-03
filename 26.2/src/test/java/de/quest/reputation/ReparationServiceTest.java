package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReparationServiceTest {
    private static final VillageKey VILLAGE = VillageKey.overworld(100, 200);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static SocialReputationData caseData(Offence offence) {
        var data = new SocialReputationData(); data.setGuildTrust(-80); data.setLocalTrust(VILLAGE, -90);
        data.openCase(UUID.randomUUID(), offence, VILLAGE, 1); return data;
    }
    @Test void aMinorCaseConsumesOnlyTheSelectedAidAndOnlyTheMissingQuantity() {
        var data = caseData(Offence.ASSAULT); var id = data.activeCase().id();
        assertTrue(ReparationService.choose(data, id, data.revision(), MaterialOption.PLANKS));
        assertEquals(Map.of(MaterialOption.PLANKS, 9), ReparationService.submit(data, id, data.revision(),
                Map.of(MaterialOption.WHEAT, 64, MaterialOption.PLANKS, 9)).accepted());
        assertFalse(ReparationService.choose(data, id, data.revision(), MaterialOption.IRON));
        data = SocialReputationData.fromNbt(data.toNbt());
        assertEquals(Map.of(MaterialOption.PLANKS, 7), ReparationService.submit(data, id, data.revision(),
                Map.of(MaterialOption.PLANKS, 20)).accepted());
        assertEquals(-80, data.guildTrust()); assertNotNull(data.activeCase());
        ReparationService.tick(data, 12_000, true);
        assertNull(data.activeCase()); assertEquals(-10, data.guildTrust()); assertEquals(-10, data.localTrust(VILLAGE));
        assertEquals(2, data.probationRemaining()); assertFalse(ReparationService.complete(data));
    }
    @Test void aMajorCaseRequiresAllThreeMaterialsEvenWhenItsTimerHasElapsed() {
        var data = caseData(Offence.CREW_KILL); var id = data.activeCase().id();
        assertFalse(ReparationService.choose(data, id, data.revision(), MaterialOption.PLANKS));
        ReparationService.tick(data, 36_000, true);
        assertNotNull(data.activeCase());
        ReparationService.submit(data, id, data.revision(), Map.of(MaterialOption.WHEAT, 64, MaterialOption.PLANKS, 32));
        assertNotNull(data.activeCase());
        assertEquals(Map.of(MaterialOption.IRON, 8), ReparationService.submit(data, id, data.revision(), Map.of(MaterialOption.IRON, 64)).accepted());
        assertNull(data.activeCase()); assertEquals(2, data.probationRemaining());
    }
    @Test void staleDuplicateAndForeignCaseActionsChangeNothing() {
        var data = caseData(Offence.ASSAULT); UUID id = data.activeCase().id(); long revision = data.revision();
        assertFalse(ReparationService.submit(data, UUID.randomUUID(), revision, Map.of(MaterialOption.WHEAT, 32)).applied());
        assertTrue(ReparationService.choose(data, id, revision, MaterialOption.IRON));
        assertFalse(ReparationService.submit(data, id, revision, Map.of(MaterialOption.IRON, 4)).applied());
        long current = data.revision();
        assertTrue(ReparationService.submit(data, id, current, Map.of(MaterialOption.IRON, 4)).applied());
        assertFalse(ReparationService.submit(data, id, current, Map.of(MaterialOption.IRON, 4)).applied());
    }
    @Test void offlineDeadAndSpectatorTimePausesAndAnAttackResetsPartialAid() {
        var data = caseData(Offence.CREW_KILL); UUID id = data.activeCase().id();
        ReparationService.tick(data, 999_999, false); assertEquals(36_000, data.activeCase().onlineTicksRemaining());
        ReparationService.submit(data, id, data.revision(), Map.of(MaterialOption.WHEAT, 16));
        ReparationService.tick(data, 1_000, true);
        data.openCase(UUID.randomUUID(), Offence.ASSAULT, VILLAGE, 100);
        assertTrue(data.activeCase().major()); assertEquals(id, data.activeCase().id());
        assertEquals(36_000, data.activeCase().onlineTicksRemaining()); assertEquals(0, data.activeCase().delivered(MaterialOption.WHEAT));
    }
    @Test void administrativeLowTrustHasAReachableMinorReconciliationWithoutCreatingExtraLoss() {
        var data = new SocialReputationData(); data.setGuildTrust(-70); data.setLocalTrust(VILLAGE, -80);
        assertTrue(ReparationService.beginReconciliation(data, 1)); assertFalse(ReparationService.beginReconciliation(data, 2));
        assertEquals(-70, data.guildTrust()); assertEquals(java.util.Set.of(VILLAGE), data.activeCase().affected());
        ReparationService.pardon(data, 3); assertNull(data.activeCase());
        assertEquals(-10, data.guildTrust()); assertEquals(-10, data.localTrust(VILLAGE));
    }
}
