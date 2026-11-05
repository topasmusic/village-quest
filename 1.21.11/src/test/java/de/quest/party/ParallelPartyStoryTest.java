package de.quest.party;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ParallelPartyStoryTest {
    @Test void acceptingKingdomKeepsCoreProgressReceiptsMembersAndOffers() {
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        var party = new PartyRuntime(UUID.randomUUID(), leader);
        party.members().addAll(java.util.List.of(leader, member));
        var core = QuestPartySessions.ensureStorySession(party, StoryArcType.FAILING_HARVEST, 2);
        core.setInt("delivered", 9);
        core.setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        core.markSynced(leader);
        var offer = new QuestJoinOffer(core.questId(), core.revision(), leader);
        party.storyOffers().put(member, offer);

        var kingdom = QuestPartySessions.ensureStorySession(party, StoryArcType.LIVING_KINGDOMS, 0);

        assertNotSame(core, kingdom);
        assertEquals(9, core.getInt("delivered"));
        assertTrue(core.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertTrue(core.hasSynced(leader));
        assertEquals(offer, party.storyOffers().get(member));
        assertTrue(QuestPartySessions.matchesStorySession(party, StoryArcType.FAILING_HARVEST, 2));
        assertTrue(QuestPartySessions.matchesStorySession(party, StoryArcType.LIVING_KINGDOMS, 0));
    }

    @Test void bothSessionsAndOfflineEligibilitySurviveNativeNbtRoundTrip() {
        UUID leader = UUID.randomUUID(), offline = UUID.randomUUID();
        var party = new PartyRuntime(UUID.randomUUID(), leader);
        party.members().addAll(java.util.List.of(leader, offline));
        var core = QuestPartySessions.ensureStorySession(party, StoryArcType.FAILING_HARVEST, 2);
        core.markSynced(offline);
        core.setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        var kingdom = QuestPartySessions.ensureStorySession(party, StoryArcType.LIVING_KINGDOMS, 0);
        kingdom.markSynced(leader);
        party.disconnectDeadlines().put(offline, Long.MAX_VALUE);
        Map<UUID, PartyRuntime> loaded = new HashMap<>();
        QuestPartyPersistence.read(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()),
                loaded, new HashMap<>(), new HashMap<>());
        var restored = loaded.get(party.id());
        assertTrue(QuestPartySessions.matchesStorySession(restored, StoryArcType.FAILING_HARVEST, 2));
        assertTrue(QuestPartySessions.matchesStorySession(restored, StoryArcType.LIVING_KINGDOMS, 0));
        assertTrue(restored.story().hasSynced(offline));
        assertTrue(restored.story().hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertEquals(Long.MAX_VALUE, restored.disconnectDeadlines().get(offline));
    }
}
