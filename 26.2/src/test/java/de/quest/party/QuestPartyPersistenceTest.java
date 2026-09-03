package de.quest.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestKeys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestPartyPersistenceTest {
    private static final UUID PARTY = UUID.fromString("22fe2c9d-8d56-4a1b-9fef-91fdfafc5bc1");
    private static final UUID LEADER = UUID.fromString("995a88d3-e47f-4aae-828f-e386b40f13a7");
    private static final UUID MEMBER = UUID.fromString("ba518123-f624-4630-883c-b4083d5f9ed4");

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
        party.disconnectDeadlines().put(MEMBER, 9_999L);

        Map<UUID, PartyRuntime> parties = new HashMap<>();
        parties.put(PARTY, party);
        Map<UUID, PartyInvite> invites = new HashMap<>();
        invites.put(MEMBER, new PartyInvite(PARTY, LEADER, 12_345L));

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
        assertEquals(LEADER, loaded.dailyOffers().get(MEMBER).sourceId());
        assertEquals(9_999L, loaded.disconnectDeadlines().get(MEMBER));
        assertEquals(12_345L, loadedInvites.get(MEMBER).expiresAtMillis());
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
}
