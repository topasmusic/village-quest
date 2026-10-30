package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import org.junit.jupiter.api.Test;

final class ReputationViewServiceTest {
    @Test void snapshotKeepsFirstSeenPageOrderAndUsesTheSmallestHistoryVillageWithItsOwnDelta() {
        var data = new SocialReputationData();
        var first = VillageKey.overworld(256,0); var second = VillageKey.overworld(0,0);
        data.appendHistory(new SocialReputationData.HistoryEntry(UUID.randomUUID(),"story",0,0,
                Map.of(first,4,second,2),7));
        var view = ReputationViewService.build(data,List.of(first,second,first),0,true);
        assertEquals(2,view.villageTotal());
        assertEquals(List.of(first,second),view.villages().stream().map(ReputationViewService.VillageView::key).toList());
        assertEquals(second,view.history().getFirst().village());
        assertEquals(2,view.history().getFirst().localDelta());
        assertEquals(2,view.history().getFirst().affectedCount());
    }
    @Test void pagesOnlyPersonalKnownVillagesAndKeepsCaseVisibleWithoutDiscoveringThem() {
        var data = new SocialReputationData();
        List<VillageKey> known = new ArrayList<>();
        for (int i = 0; i < 21; i++) known.add(new VillageKey("minecraft:overworld", i * 100, 0));
        var unknown = new VillageKey("minecraft:the_nether", 99, 99);
        data.setLocalTrust(unknown, -50);
        data.openCase(UUID.randomUUID(), SocialReputationRules.Offence.ASSAULT, unknown, 5);
        var view = ReputationViewService.build(data, known, 1, true);
        assertEquals(21, view.villageTotal());
        assertEquals(8, view.villages().size());
        assertEquals(known.get(8), view.villages().getFirst().key());
        assertFalse(view.villages().stream().anyMatch(v -> v.key().equals(unknown)));
        assertEquals(1, view.reparation().affectedCount());
        assertEquals(0, view.buyLimit());
    }
    @Test void boundedHistoryUsesActualDeltasAndDisableShowsNeutralServicesWithoutDeletingCase() {
        var data = new SocialReputationData();
        data.setGuildTrust(-70);
        data.openCase(UUID.randomUUID(), SocialReputationRules.Offence.CREW_KILL, null, 5);
        data.appendAdministrativeHistory(UUID.randomUUID(), -60, null, 0, 6);
        var view = ReputationViewService.build(data, List.of(), Integer.MAX_VALUE, false);
        assertEquals(-70, view.guildTrust());
        assertEquals(4, view.buyLimit());
        assertTrue(view.dispatchAllowed());
        assertEquals(-10, view.history().getFirst().guildDelta());
        assertNotNull(data.activeCase());
        assertNull(view.reparation());
        assertEquals(0, view.villagePage());
    }
}
