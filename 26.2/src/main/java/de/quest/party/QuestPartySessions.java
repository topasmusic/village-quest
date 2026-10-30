package de.quest.party;

import de.quest.pilgrim.PilgrimContractType;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryLane;
import java.util.Map;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.util.TimeUtil;
import de.quest.util.QuestCalendarService;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Lifecycle, identity, and lookup rules for the four shareable quest session types. */
final class QuestPartySessions {
    private QuestPartySessions() {}

    static SharedQuestRuntime sessionFor(PartyRuntime party, StoryArcType arc) {
        return party == null || arc == null ? null
                : StoryLane.forArc(arc) == StoryLane.KINGDOM ? party.kingdomStory() : party.story();
    }

    static Map<UUID, QuestJoinOffer> offersFor(PartyRuntime party, StoryArcType arc) {
        return StoryLane.forArc(arc) == StoryLane.KINGDOM ? party.kingdomStoryOffers() : party.storyOffers();
    }

    static SharedQuestRuntime sessionFor(PartyRuntime party, StoryLane lane) {
        return lane == StoryLane.KINGDOM ? party.kingdomStory() : party.story();
    }

    static Map<UUID, QuestJoinOffer> offersFor(PartyRuntime party, StoryLane lane) {
        return lane == StoryLane.KINGDOM ? party.kingdomStoryOffers() : party.storyOffers();
    }

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
        if (arcType==StoryArcType.LIVING_KINGDOMS && !QuestPartyService.matchesKingdomVillageBinding(
                de.quest.data.QuestState.get(world.getServer()).getPlayerData(playerId),sessionFor(party,arcType))) return null;
        return sessionFor(party,arcType).hasSynced(playerId) ? sessionFor(party,arcType) : null;
    }

    static SharedQuestRuntime sharedStorySessionForType(ServerLevel world, UUID playerId,
                                                         StoryArcType arcType, int chapterIndex) {
        PartyRuntime party = QuestPartyService.partyFor(playerId);
        if (world == null || party == null || arcType == null || !matchesStorySession(party, arcType, chapterIndex)) {
            return null;
        }
        return sessionFor(party,arcType);
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
        long day = QuestCalendarService.currentDay();
        SharedQuestRuntime session = party.daily();
        if (session.questId() != null && !type.name().equals(session.questId())) return null;
        if (session.questId() == null) {
            session.bind(type.name(), day);
            party.dailyOffers().clear();
        }
        return session;
    }

    static SharedQuestRuntime ensureWeeklySession(PartyRuntime party, WeeklyQuestService.WeeklyQuestType type) {
        if (party == null || type == null) return null;
        long cycle = QuestCalendarService.currentWeekCycle();
        SharedQuestRuntime session = party.weekly();
        if (session.questId() != null && !type.name().equals(session.questId())) return null;
        if (session.questId() == null) {
            session.bind(type.name(), cycle);
            party.weeklyOffers().clear();
        }
        return session;
    }

    static SharedQuestRuntime ensureStorySession(PartyRuntime party, StoryArcType arcType, int chapterIndex) {
        if (party == null || arcType == null || chapterIndex < 0 || chapterIndex >= arcType.chapterCount()) return null;
        String questId = storySessionId(arcType, chapterIndex);
        SharedQuestRuntime session = sessionFor(party,arcType);
        if (!session.matches(questId, chapterIndex)) {
            session.bind(questId, chapterIndex);
            offersFor(party,arcType).clear();
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
        return parseDailyType(session.questId());
    }

    static WeeklyQuestService.WeeklyQuestType activeWeeklyType(PartyRuntime party) {
        if (party == null) return null;
        SharedQuestRuntime session = party.weekly();
        return parseWeeklyType(session.questId());
    }

    static StoryArcType activeStoryType(PartyRuntime party) {
        return party == null || party.story().questId() == null ? null : parseStoryType(party.story().questId());
    }

    static int activeStoryChapterIndex(PartyRuntime party) {
        return party == null || party.story().questId() == null ? -1 : parseStoryChapter(party.story().questId());
    }

    static StoryArcType activeStoryType(PartyRuntime party, StoryLane lane) {
        return party == null ? null : parseStoryType(sessionFor(party,lane).questId());
    }

    static int activeStoryChapterIndex(PartyRuntime party, StoryLane lane) {
        return party == null ? -1 : parseStoryChapter(sessionFor(party,lane).questId());
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
                && chapterIndex < arcType.chapterCount()
                && sessionFor(party,arcType).matches(storySessionId(arcType, chapterIndex), chapterIndex);
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
