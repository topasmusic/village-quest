package de.quest.party;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.kingdom.KingdomStoryProgress;
import de.quest.quest.story.StoryArcType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class KingdomVillageSessionTest {
    @Test void pendingChapterClaimCannotBeTransferredToAnotherVillage() {
        UUID town=UUID.randomUUID(), next=UUID.randomUUID(), owner=UUID.randomUUID();
        var data=member(town,3);
        data.setStoryFlag(de.quest.quest.story.StoryQuestKeys.sharedTurnInConsumed(StoryArcType.LIVING_KINGDOMS,3),true);
        assertFalse(KingdomStoryProgress.bind(data,next,true));
        assertEquals(town,KingdomStoryProgress.target(data));
        var session=new SharedQuestRuntime(); session.bind("living_kingdoms#3",3); session.markSynced(owner);
        session.setFlag("kingdoms.bound."+town,true);
        session.setFlag(de.quest.quest.story.StoryQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        assertFalse(QuestPartyService.updateKingdomVillageBinding(Map.of(owner,data),session,owner,owner,next,true,3));
        assertEquals(town,QuestPartyService.kingdomVillageBinding(session));
    }
    @Test void firstSharedVillageSurvivesPersistenceAndRejectsASecondPersonalTarget() {
        UUID town=UUID.randomUUID(), other=UUID.randomUUID(), owner=UUID.randomUUID();
        var party=new PartyRuntime(UUID.randomUUID(),owner); party.members().add(owner);
        party.kingdomStory().bind(QuestPartySessions.storySessionId(StoryArcType.LIVING_KINGDOMS,1),1);
        party.kingdomStory().markSynced(owner);
        var alice=new PlayerQuestData(); KingdomStoryProgress.bind(alice,town,false);
        var bob=new PlayerQuestData(); KingdomStoryProgress.bind(bob,other,false);
        assertTrue(QuestPartyService.mergeKingdomVillageBinding(alice,party.kingdomStory()));
        assertFalse(QuestPartyService.mergeKingdomVillageBinding(bob,party.kingdomStory()));
        assertEquals(town,QuestPartyService.kingdomVillageBinding(party.kingdomStory()));
        var saved=QuestPartyPersistence.write(Map.of(party.id(),party),Map.of());
        var restored=new HashMap<UUID,PartyRuntime>();
        QuestPartyPersistence.read(saved,restored,new HashMap<>(),new HashMap<>());
        assertEquals(town,QuestPartyService.kingdomVillageBinding(restored.get(party.id()).kingdomStory()));
        assertTrue(restored.get(party.id()).kingdomStory().hasSynced(owner));
        assertEquals(other,KingdomStoryProgress.target(bob));
    }
    @Test void anUnboundAdmittedMemberInheritsOnlyTheExistingSharedTarget() {
        UUID town=UUID.randomUUID(); var session=new SharedQuestRuntime(); var source=new PlayerQuestData();
        session.bind(QuestPartySessions.storySessionId(StoryArcType.LIVING_KINGDOMS,0),0);
        KingdomStoryProgress.bind(source,town,false); QuestPartyService.mergeKingdomVillageBinding(source,session);
        var joiner=new PlayerQuestData();
        assertTrue(QuestPartyService.mergeKingdomVillageBinding(joiner,session));
        assertEquals(town,KingdomStoryProgress.target(joiner));
    }
    @Test void corruptedConflictingSharedTargetsFailClosedWithoutErasingOtherFlags() {
        var session=new SharedQuestRuntime(); session.setFlag("kingdoms.bound."+UUID.randomUUID(),true);
        session.setFlag("kingdoms.bound."+UUID.randomUUID(),true); session.setFlag("future.extra",true);
        var data=new PlayerQuestData(); KingdomStoryProgress.bind(data,UUID.randomUUID(),false);
        assertNull(QuestPartyService.kingdomVillageBinding(session));
        assertFalse(QuestPartyService.mergeKingdomVillageBinding(data,session));
        assertEquals(3,session.flags().size());
    }
    @Test void explicitLeaderRebindUpdatesOfflineAdmittedMembersAndKeepsTheirClaimedChapters() {
        UUID old=UUID.randomUUID(), target=UUID.randomUUID(), leader=UUID.randomUUID(), offline=UUID.randomUUID();
        var session=new SharedQuestRuntime(); session.bind(QuestPartySessions.storySessionId(StoryArcType.LIVING_KINGDOMS,3),3);
        var source=member(old,3); var recipient=member(old,3);
        session.markSynced(leader); session.markSynced(offline); QuestPartyService.mergeKingdomVillageBinding(source,session);
        KingdomStoryProgress.record(recipient,old,KingdomStoryProgress.Deed.MARKET_TRADE,"old-town:trade",true);
        var data=Map.of(leader,source,offline,recipient);
        assertFalse(QuestPartyService.updateKingdomVillageBinding(data,session,offline,leader,target,true,3));
        assertFalse(QuestPartyService.updateKingdomVillageBinding(data,session,leader,leader,target,false,3));
        assertEquals(old,KingdomStoryProgress.target(recipient));
        assertTrue(QuestPartyService.updateKingdomVillageBinding(data,session,leader,leader,target,true,3));
        assertEquals(target,KingdomStoryProgress.target(source)); assertEquals(target,KingdomStoryProgress.target(recipient));
        assertEquals(3,recipient.getStoryChapterProgress("living_kingdoms"));
        assertFalse(KingdomStoryProgress.has(recipient,KingdomStoryProgress.Deed.MARKET_TRADE));
        assertEquals(target,QuestPartyService.kingdomVillageBinding(session));
    }
    private static PlayerQuestData member(UUID town,int chapter) {
        var data=new PlayerQuestData(); data.setActiveStoryArc(de.quest.quest.story.StoryLane.KINGDOM,StoryArcType.LIVING_KINGDOMS);
        data.setStoryChapterProgress("living_kingdoms",chapter); KingdomStoryProgress.bind(data,town,false); return data;
    }
}
