package de.quest.party;

import de.quest.pilgrim.PilgrimContractType;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.util.TimeUtil;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Lifecycle, identity, and lookup rules for the four shareable quest session types. */
final class QuestPartySessions {
    private QuestPartySessions() {}

    static SharedQuestRuntime sharedDailySession(ServerLevel world, UUID playerId,
                                                  DailyQuestService.DailyQuestType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activeDailyType(party) != type) return null;
        return party.daily().hasSynced(playerId) ? party.daily() : null;
    }

    static SharedQuestRuntime sharedWeeklySession(ServerLevel world, UUID playerId,
                                                   WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activeWeeklyType(party) != type) return null;
        return party.weekly().hasSynced(playerId) ? party.weekly() : null;
    }

    static SharedQuestRuntime sharedDailySessionForType(ServerLevel world, UUID playerId,
                                                         DailyQuestService.DailyQuestType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activeDailyType(party) != type) return null;
        return party.daily();
    }

    static SharedQuestRuntime sharedWeeklySessionForType(ServerLevel world, UUID playerId,
                                                          WeeklyQuestService.WeeklyQuestType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activeWeeklyType(party) != type) return null;
        return party.weekly();
    }

    static SharedQuestRuntime sharedStorySession(ServerLevel world, UUID playerId,
                                                  StoryArcType arcType, int chapterIndex) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || arcType == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return null;
        }
        return party.story().hasSynced(playerId) ? party.story() : null;
    }

    static SharedQuestRuntime sharedStorySessionForType(ServerLevel world, UUID playerId,
                                                         StoryArcType arcType, int chapterIndex) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || arcType == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return null;
        }
        return party.story();
    }

    static SharedQuestRuntime sharedPilgrimSession(ServerLevel world, UUID playerId, PilgrimContractType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activePilgrimType(party) != type) return null;
        return party.pilgrim().hasSynced(playerId) ? party.pilgrim() : null;
    }

    static SharedQuestRuntime sharedPilgrimSessionForType(ServerLevel world, UUID playerId,
                                                           PilgrimContractType type) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || type == null || activePilgrimType(party) != type) return null;
        return party.pilgrim();
    }

    static SharedQuestRuntime ensureDailySession(PartyRuntime party, DailyQuestService.DailyQuestType type) {
        if (party == null || type == null) return null;
        long day = TimeUtil.currentDay();
        SharedQuestRuntime session = party.daily();
        if (!session.matches(type.name(), day)) {
            session.bind(type.name(), day);
            party.dailyOffers().clear();
        }
        return session;
    }

    static SharedQuestRuntime ensureWeeklySession(PartyRuntime party, WeeklyQuestService.WeeklyQuestType type) {
        if (party == null || type == null) return null;
        long cycle = TimeUtil.currentWeekCycle();
        SharedQuestRuntime session = party.weekly();
        if (!session.matches(type.name(), cycle)) {
            session.bind(type.name(), cycle);
            party.weeklyOffers().clear();
        }
        return session;
    }

    static SharedQuestRuntime ensureStorySession(PartyRuntime party, StoryArcType arcType, int chapterIndex) {
        if (party == null || arcType == null || chapterIndex < 0) return null;
        String questId = storySessionId(arcType, chapterIndex);
        SharedQuestRuntime session = party.story();
        if (!session.matches(questId, chapterIndex)) {
            session.bind(questId, chapterIndex);
            party.storyOffers().clear();
        }
        return session;
    }

    static SharedQuestRuntime ensurePilgrimSession(PartyRuntime party, PilgrimContractType type) {
        if (party == null || type == null) return null;
        long day = TimeUtil.currentDay();
        SharedQuestRuntime session = party.pilgrim();
        if (!session.matches(type.id(), day)) {
            session.bind(type.id(), day);
            party.pilgrimOffers().clear();
        }
        return session;
    }

    static DailyQuestService.DailyQuestType activeDailyType(PartyRuntime party) {
        if (party == null) return null;
        SharedQuestRuntime session = party.daily();
        if (!session.isCurrent(TimeUtil.currentDay())) {
            session.clear();
            return null;
        }
        return parseDailyType(session.questId());
    }

    static WeeklyQuestService.WeeklyQuestType activeWeeklyType(PartyRuntime party) {
        if (party == null) return null;
        SharedQuestRuntime session = party.weekly();
        if (!session.isCurrent(TimeUtil.currentWeekCycle())) {
            session.clear();
            return null;
        }
        return parseWeeklyType(session.questId());
    }

    static StoryArcType activeStoryType(PartyRuntime party) {
        return party == null || party.story().questId() == null ? null : parseStoryType(party.story().questId());
    }

    static int activeStoryChapterIndex(PartyRuntime party) {
        return party == null || party.story().questId() == null ? -1 : parseStoryChapter(party.story().questId());
    }

    static PilgrimContractType activePilgrimType(PartyRuntime party) {
        if (party == null) return null;
        SharedQuestRuntime session = party.pilgrim();
        if (!session.isCurrent(TimeUtil.currentDay())) {
            session.clear();
            return null;
        }
        return PilgrimContractType.fromId(session.questId());
    }

    static DailyQuestService.DailyQuestType parseDailyType(String questId) {
        if (questId == null || questId.isEmpty()) return null;
        try {
            return DailyQuestService.DailyQuestType.valueOf(questId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static WeeklyQuestService.WeeklyQuestType parseWeeklyType(String questId) {
        if (questId == null || questId.isEmpty()) return null;
        try {
            return WeeklyQuestService.WeeklyQuestType.valueOf(questId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String storySessionId(StoryArcType arcType, int chapterIndex) {
        return arcType == null ? "" : arcType.id() + "#" + chapterIndex;
    }

    static boolean matchesStorySession(PartyRuntime party, StoryArcType arcType, int chapterIndex) {
        return party != null && arcType != null && chapterIndex >= 0
                && party.story().matches(storySessionId(arcType, chapterIndex), chapterIndex);
    }

    static StoryArcType parseStoryType(String questId) {
        if (questId == null || questId.isEmpty()) return null;
        int split = questId.indexOf('#');
        return StoryArcType.fromId(split >= 0 ? questId.substring(0, split) : questId);
    }

    static int parseStoryChapter(String questId) {
        if (questId == null || questId.isEmpty()) return -1;
        int split = questId.indexOf('#');
        if (split < 0 || split + 1 >= questId.length()) return -1;
        try {
            return Integer.parseInt(questId.substring(split + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
