package de.quest.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.weekly.WeeklyQuestKeys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestPartyPersistenceTest {
    private static final UUID PARTY = UUID.fromString("22fe2c9d-8d56-4a1b-9fef-91fdfafc5bc1");
    private static final UUID LEADER = UUID.fromString("995a88d3-e47f-4aae-828f-e386b40f13a7");
    private static final UUID MEMBER = UUID.fromString("ba518123-f624-4630-883c-b4083d5f9ed4");
    private static final UUID INVITED = UUID.fromString("377e5c49-5082-48a9-a851-e5a2fb9f8356");

    @Test
    void consumedDailyTurnInFreezesEligibilityAndInvalidatesStaleOffer() {
        SharedQuestRuntime session = new SharedQuestRuntime();
        session.bind("HARVEST_WHEAT", 41L);
        session.markSynced(LEADER);
        Map<UUID, QuestJoinOffer> offers = new HashMap<>();
        offers.put(MEMBER, new QuestJoinOffer("HARVEST_WHEAT", 41L, LEADER));

        assertTrue(session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
        session.setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);

        assertTrue(session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, LEADER));
        assertFalse(session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
        assertEquals(1, session.removeUnsyncedOffersAfterTurnIn(
                DailyQuestKeys.SHARED_TURN_IN_CONSUMED, offers));
        assertTrue(offers.isEmpty());
    }

    @Test
    void consumedWeeklyTurnInRejectsLateJoinButKeepsSyncedReconnectEligible() {
        SharedQuestRuntime session = new SharedQuestRuntime();
        session.bind("MARKET_WEEK", 8L);
        session.markSynced(LEADER);
        session.setFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);

        assertTrue(session.canJoinAfterTurnIn(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, LEADER));
        assertFalse(session.canJoinAfterTurnIn(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
        session.markSynced(LEADER);
        assertEquals(1, session.syncedMembers().size());
    }

    @Test
    void leavingConsumedSessionDoesNotCreateFreshEligibility() {
        SharedQuestRuntime session = new SharedQuestRuntime();
        session.bind("HARVEST_WHEAT", 41L);
        session.markSynced(MEMBER);
        session.setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);

        session.unmarkSynced(MEMBER);

        assertFalse(session.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
        assertTrue(session.hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED));
    }

    @Test
    void membershipSessionsOffersAndReconnectGraceRoundTrip() {
        PartyRuntime party = new PartyRuntime(PARTY, LEADER);
        party.members().add(LEADER);
        party.members().add(MEMBER);
        party.daily().bind("HARVEST_WHEAT", 41L);
        party.daily().setInt("wheat", 17);
        party.daily().setFlag("started", true);
        party.daily().setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        party.daily().markSynced(LEADER);
        party.weekly().bind("MARKET_WEEK", 8L);
        party.weekly().setFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        party.dailyOffers().put(MEMBER, new QuestJoinOffer("HARVEST_WHEAT", 41L, LEADER));
        party.daily().removeUnsyncedOffersAfterTurnIn(
                DailyQuestKeys.SHARED_TURN_IN_CONSUMED, party.dailyOffers());
        party.disconnectDeadlines().put(MEMBER, 9_999L);

        Map<UUID, PartyRuntime> parties = new HashMap<>();
        parties.put(PARTY, party);
        Map<UUID, PartyInvite> invites = new HashMap<>();
        invites.put(INVITED, new PartyInvite(PARTY, LEADER, 12_345L));

        CompoundTag encoded = QuestPartyPersistence.write(parties, invites);
        Map<UUID, PartyRuntime> loadedParties = new HashMap<>();
        Map<UUID, UUID> loadedMemberships = new HashMap<>();
        Map<UUID, PartyInvite> loadedInvites = new HashMap<>();
        QuestPartyPersistence.read(encoded, loadedParties, loadedMemberships, loadedInvites);

        PartyRuntime loaded = loadedParties.get(PARTY);
        assertEquals(LEADER, loaded.leaderId());
        assertEquals(2, loaded.members().size());
        assertEquals(PARTY, loadedMemberships.get(MEMBER));
        assertEquals(17, loaded.daily().getInt("wheat"));
        assertTrue(loaded.daily().hasFlag("started"));
        assertTrue(loaded.daily().hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertTrue(loaded.weekly().hasFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertTrue(loaded.daily().hasSynced(LEADER));
        assertTrue(loaded.dailyOffers().isEmpty());
        assertEquals(9_999L, loaded.disconnectDeadlines().get(MEMBER));
        assertEquals(12_345L, loadedInvites.get(INVITED).expiresAtMillis());
        assertTrue(loaded.daily().canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, LEADER));
        assertFalse(loaded.daily().canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
    }

    @Test
    void malformedOrMemberlessPartiesAreIgnored() {
        CompoundTag malformed = new CompoundTag();
        malformed.put("parties", new net.minecraft.nbt.ListTag());
        Map<UUID, PartyRuntime> parties = new HashMap<>();
        Map<UUID, UUID> memberships = new HashMap<>();
        Map<UUID, PartyInvite> invites = new HashMap<>();

        QuestPartyPersistence.read(malformed, parties, memberships, invites);

        assertTrue(parties.isEmpty());
        assertTrue(memberships.isEmpty());
        assertFalse(invites.containsKey(MEMBER));
    }

    @Test
    void contradictoryMembershipIsRebuiltAsExactlyOneCanonicalParty() {
        UUID secondPartyId = UUID.fromString("63785529-cc22-4240-8790-e0d4a267351a");
        UUID secondLeader = UUID.fromString("2fa68a32-bf1e-4ea0-b057-8c9e2471447b");
        PartyRuntime first = new PartyRuntime(PARTY, LEADER);
        first.members().add(LEADER);
        first.members().add(MEMBER);
        PartyRuntime second = new PartyRuntime(secondPartyId, secondLeader);
        second.members().add(secondLeader);
        second.members().add(MEMBER);
        Map<UUID, PartyRuntime> corrupt = new java.util.LinkedHashMap<>();
        corrupt.put(PARTY, first);
        corrupt.put(secondPartyId, second);

        Map<UUID, PartyRuntime> loadedParties = new HashMap<>();
        Map<UUID, UUID> loadedMemberships = new HashMap<>();
        QuestPartyPersistence.read(QuestPartyPersistence.write(corrupt, Map.of()),
                loadedParties, loadedMemberships, new HashMap<>());

        long containingParties = loadedParties.values().stream().filter(party -> party.members().contains(MEMBER)).count();
        assertEquals(1L, containingParties);
        UUID canonical = loadedMemberships.get(MEMBER);
        assertTrue(canonical != null && loadedParties.get(canonical).members().contains(MEMBER));
        assertEquals(loadedParties.values().stream().mapToInt(party -> party.members().size()).sum(),
                loadedMemberships.size());
    }

    @Test
    void storyTurnInReceiptSurvivesReloadFreezesEligibilityAndClearsForNextChapter() {
        PartyRuntime party = new PartyRuntime(PARTY, LEADER);
        party.members().add(LEADER);
        party.members().add(MEMBER);
        party.members().add(INVITED);
        String chapter = StoryArcType.FAILING_HARVEST.id() + "#2";
        party.story().bind(chapter, 2L);
        party.story().markSynced(LEADER);
        party.story().markSynced(MEMBER);
        party.storyOffers().put(INVITED, new QuestJoinOffer(chapter, 2L, LEADER));
        party.story().setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        assertEquals(1, party.story().removeUnsyncedOffersAfterTurnIn(
                StoryQuestKeys.SHARED_TURN_IN_CONSUMED, party.storyOffers()));

        Map<UUID, PartyRuntime> parties = new HashMap<>();
        parties.put(PARTY, party);
        CompoundTag encoded = QuestPartyPersistence.write(parties, Map.of());
        Map<UUID, PartyRuntime> loadedParties = new HashMap<>();
        QuestPartyPersistence.read(encoded, loadedParties, new HashMap<>(), new HashMap<>());

        SharedQuestRuntime restored = loadedParties.get(PARTY).story();
        assertTrue(restored.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertTrue(restored.canJoinAfterTurnIn(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, LEADER));
        assertTrue(restored.canJoinAfterTurnIn(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, MEMBER));
        assertFalse(restored.canJoinAfterTurnIn(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, INVITED));
        assertTrue(loadedParties.get(PARTY).storyOffers().isEmpty());

        restored.bind(StoryArcType.FAILING_HARVEST.id() + "#3", 3L);
        assertFalse(restored.hasFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED));
        assertTrue(restored.syncedMembers().isEmpty());
    }
}
