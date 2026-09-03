package de.quest.party;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Package-private runtime model shared by the party coordination domains. */
final class PartyRuntime {
    private final UUID id;
    private UUID leaderId;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final SharedQuestRuntime daily = new SharedQuestRuntime();
    private final SharedQuestRuntime weekly = new SharedQuestRuntime();
    private final SharedQuestRuntime story = new SharedQuestRuntime();
    private final SharedQuestRuntime pilgrim = new SharedQuestRuntime();
    private final Map<UUID, QuestJoinOffer> dailyOffers = new HashMap<>();
    private final Map<UUID, QuestJoinOffer> weeklyOffers = new HashMap<>();
    private final Map<UUID, QuestJoinOffer> storyOffers = new HashMap<>();
    private final Map<UUID, QuestJoinOffer> pilgrimOffers = new HashMap<>();
    private final Map<UUID, Long> disconnectDeadlines = new HashMap<>();

    PartyRuntime(UUID id, UUID leaderId) {
        this.id = id;
        this.leaderId = leaderId;
    }

    UUID id() { return id; }
    UUID leaderId() { return leaderId; }
    void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }
    LinkedHashSet<UUID> members() { return members; }
    SharedQuestRuntime daily() { return daily; }
    SharedQuestRuntime weekly() { return weekly; }
    SharedQuestRuntime story() { return story; }
    SharedQuestRuntime pilgrim() { return pilgrim; }
    Map<UUID, QuestJoinOffer> dailyOffers() { return dailyOffers; }
    Map<UUID, QuestJoinOffer> weeklyOffers() { return weeklyOffers; }
    Map<UUID, QuestJoinOffer> storyOffers() { return storyOffers; }
    Map<UUID, QuestJoinOffer> pilgrimOffers() { return pilgrimOffers; }
    Map<UUID, Long> disconnectDeadlines() { return disconnectDeadlines; }
}

final class SharedQuestRuntime {
    private String questId;
    private long revision = Long.MIN_VALUE;
    private final Map<String, Integer> intState = new HashMap<>();
    private final Set<String> flags = new HashSet<>();
    private final Set<UUID> syncedMembers = new HashSet<>();

    String questId() { return questId; }
    long revision() { return revision; }
    Map<String, Integer> intState() { return intState; }
    Set<String> flags() { return flags; }
    Set<UUID> syncedMembers() { return syncedMembers; }

    boolean matches(String questId, long revision) {
        return Objects.equals(this.questId, questId) && this.revision == revision;
    }

    boolean isCurrent(long revision) {
        return questId != null && this.revision == revision;
    }

    void bind(String questId, long revision) {
        this.questId = questId;
        this.revision = revision;
        intState.clear();
        flags.clear();
        syncedMembers.clear();
    }

    void clear() {
        questId = null;
        revision = Long.MIN_VALUE;
        intState.clear();
        flags.clear();
        syncedMembers.clear();
    }

    int getInt(String key) {
        return key == null ? 0 : intState.getOrDefault(key, 0);
    }

    void setInt(String key, int value) {
        if (key == null || key.isEmpty()) return;
        if (value == 0) intState.remove(key);
        else intState.put(key, value);
    }

    void addInt(String key, int amount) {
        if (amount != 0) setInt(key, getInt(key) + amount);
    }

    boolean hasFlag(String key) {
        return key != null && flags.contains(key);
    }

    void setFlag(String key, boolean enabled) {
        if (key == null || key.isEmpty()) return;
        if (enabled) flags.add(key);
        else flags.remove(key);
    }

    boolean hasSynced(UUID memberId) {
        return memberId != null && syncedMembers.contains(memberId);
    }

    void markSynced(UUID memberId) {
        if (memberId != null) syncedMembers.add(memberId);
    }

    void unmarkSynced(UUID memberId) {
        if (memberId != null) syncedMembers.remove(memberId);
    }
}

record ExpiryTarget(UUID partyId, UUID memberId) {}
record PartyInvite(UUID partyId, UUID inviterId, long expiresAtMillis) {}
record QuestJoinOffer(String questId, long revision, UUID sourceId) {}
