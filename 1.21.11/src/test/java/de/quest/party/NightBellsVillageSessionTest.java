package de.quest.party;

import de.quest.data.PlayerQuestData;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class NightBellsVillageSessionTest {
    @Test void markerCoordinatesAreMergedAsEvidenceInsteadOfAddingSignedIntegers() {
        var owner=village(100,200);var joiner=village(100,200);var other=village(350,200);
        var party=new PartyRuntime(UUID.randomUUID(),UUID.randomUUID());var session=party.story();
        party.members().add(party.leaderId());session.bind(QuestPartySessions.storySessionId(StoryArcType.NIGHT_BELLS,0),0);
        QuestPartyService.mergeNightBellsVillageBinding(owner,session);
        var point=new net.minecraft.core.BlockPos(-8,70,8);
        assertTrue(de.quest.content.story.NightBellsMarkerProgress.record(owner::getStoryInt,owner::setStoryInt,0,point,3));
        QuestPartyService.mergeNightBellsMarkers(owner,session,0);
        QuestPartyService.mergeNightBellsMarkers(owner,session,0);
        assertEquals(1,de.quest.content.story.NightBellsMarkerProgress.count(session::getInt,0));
        assertEquals(-8,de.quest.content.story.NightBellsMarkerProgress.exactSectors(session::getInt,0).getFirst().getX());
        assertTrue(de.quest.content.story.NightBellsMarkerProgress.record(joiner::getStoryInt,joiner::setStoryInt,0,new net.minecraft.core.BlockPos(8,70,8),3));
        QuestPartyService.mergeNightBellsMarkers(joiner,session,0);
        assertEquals(2,de.quest.content.story.NightBellsMarkerProgress.count(session::getInt,0));
        assertTrue(de.quest.content.story.NightBellsMarkerProgress.record(other::getStoryInt,other::setStoryInt,0,new net.minecraft.core.BlockPos(24,70,8),3));
        QuestPartyService.mergeNightBellsMarkers(other,session,0);
        assertEquals(2,de.quest.content.story.NightBellsMarkerProgress.count(session::getInt,0));
        for(var key:de.quest.content.story.NightBellsMarkerProgress.keys(0))
            assertTrue(QuestShareProfiles.sharesStoryInt(StoryArcType.NIGHT_BELLS,0,key));
        var restored=new HashMap<UUID,PartyRuntime>();
        QuestPartyPersistence.read(QuestPartyPersistence.write(Map.of(party.id(),party),Map.of()),restored,new HashMap<>(),new HashMap<>());
        assertEquals(session.intState(),restored.get(party.id()).story().intState());
    }
    @Test
    void partyInheritsFirstVillageAndRejectsLaterMemberBindingAcrossReload() {
        PlayerQuestData owner = village(100, 200);
        PlayerQuestData joiner = village(350, 200);
        PartyRuntime party = new PartyRuntime(UUID.randomUUID(), UUID.randomUUID());
        party.members().add(party.leaderId());
        UUID memberId = UUID.randomUUID();
        party.members().add(memberId);
        party.story().bind(QuestPartySessions.storySessionId(StoryArcType.NIGHT_BELLS, 0), 0);
        party.story().markSynced(party.leaderId());
        party.story().markSynced(memberId);

        QuestPartyService.mergeNightBellsVillageBinding(owner, party.story());
        QuestPartyService.mergeNightBellsVillageBinding(joiner, party.story());
        assertVillageA(party.story());
        assertTrue(QuestShareProfiles.sharesStoryInt(StoryArcType.NIGHT_BELLS, 0,
                StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND));

        Map<UUID, PartyRuntime> parties = Map.of(party.id(), party);
        CompoundTag saved = QuestPartyPersistence.write(parties, Map.of());
        Map<UUID, PartyRuntime> restored = new HashMap<>();
        QuestPartyPersistence.read(saved, restored, new HashMap<>(), new HashMap<>());
        assertVillageA(restored.get(party.id()).story());
        assertTrue(restored.get(party.id()).story().hasSynced(memberId));
    }

    @Test
    void everyNightBellsChapterSharesTheSameVillageKeys() {
        for (int chapter = 0; chapter < 4; chapter++) {
            assertTrue(QuestShareProfiles.sharesStoryInt(StoryArcType.NIGHT_BELLS, chapter,
                    StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND));
            assertTrue(QuestShareProfiles.sharesStoryInt(StoryArcType.NIGHT_BELLS, chapter,
                    StoryQuestKeys.NIGHT_BELLS_VILLAGE_X));
            assertTrue(QuestShareProfiles.sharesStoryInt(StoryArcType.NIGHT_BELLS, chapter,
                    StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z));
        }
    }

    private static PlayerQuestData village(int x, int z) {
        PlayerQuestData data = new PlayerQuestData();
        data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X, x);
        data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z, z);
        data.setStoryInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND, 1);
        return data;
    }

    private static void assertVillageA(SharedQuestRuntime session) {
        assertEquals(1, session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND));
        assertEquals(100, session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X));
        assertEquals(200, session.getInt(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z));
    }
}
