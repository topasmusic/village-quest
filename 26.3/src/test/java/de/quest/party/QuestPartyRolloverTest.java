package de.quest.party;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService.DailyQuestType;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService.WeeklyQuestType;
import de.quest.util.QuestCalendarService;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.reward.VanillaRewardService;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;

final class QuestPartyRolloverTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void reset() { QuestPartyService.resetRuntimeState(); }

    @Test void personalRestartKeepsRemainingOfflineSessionAndConsumedRequirements() {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var players=mock(PlayerList.class);
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        when(world.getServer()).thenReturn(server);when(server.isDedicatedServer()).thenReturn(true);when(server.getPlayerList()).thenReturn(players);
        var mine=new PlayerQuestData();var theirs=new PlayerQuestData();
        for(var d:List.of(mine,theirs)) {d.setDailyChoice(DailyQuestType.WHEAT_HARVEST);d.setAcceptedDay(20000);d.setTradeRouteFlag("guild_intro.first_daily_completed",true);}
        var party=new PartyRuntime(UUID.randomUUID(),owner);party.members().addAll(List.of(owner,other));
        party.daily().bind("WHEAT_HARVEST",20000);party.daily().markSynced(owner);party.daily().markSynced(other);
        party.daily().setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED,true);
        var state=mock(QuestState.class);when(state.getPlayerData(owner)).thenReturn(mine);when(state.getPlayerData(other)).thenReturn(theirs);
        when(state.storyLanesWritable()).thenReturn(true);
        when(state.getQuestPartyState()).thenReturn(QuestPartyPersistence.write(Map.of(party.id(),party),Map.of()));
        try(var states=mockStatic(QuestState.class);var calendar=mockStatic(QuestCalendarService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);calendar.when(QuestCalendarService::currentDay).thenReturn(20001L);
            QuestPartyService.loadPersistentState(server);QuestPartyService.detachForPersonalRestart(world,owner);
            assertNull(QuestPartyService.partyFor(owner));var remaining=QuestPartyService.partyFor(other);
            assertNotNull(remaining);assertEquals(other,remaining.leaderId());assertEquals(1,remaining.members().size());
            assertTrue(remaining.daily().hasSynced(other));assertFalse(remaining.daily().hasSynced(owner));
            assertTrue(remaining.daily().hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED));
            assertFalse(remaining.daily().canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED,owner));
            assertTrue(theirs.hasActiveDaily());assertEquals(20000,theirs.getAcceptedDay());
        }
    }

    @Test void existingDailyOfferKeepsAcceptedRewardIdentity() {
        checkOfferIdentity(false);
    }
    @Test void completedMemberCannotRejoinConsumedDailyWhileOfflineMemberStillNeedsItsReward() {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server); when(server.isDedicatedServer()).thenReturn(true);
        when(server.getPlayerList()).thenReturn(mock(PlayerList.class));
        UUID owner = UUID.randomUUID(), offline = UUID.randomUUID();
        var finished = new PlayerQuestData(); var unfinished = new PlayerQuestData();
        for (var data : List.of(finished, unfinished)) {
            data.setDailyChoice(DailyQuestType.WHEAT_HARVEST); data.setAcceptedDay(20000);
            data.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        }
        finished.setLastRewardDay(20005);
        var party = new PartyRuntime(UUID.randomUUID(), owner); party.members().addAll(List.of(owner, offline));
        party.daily().bind("WHEAT_HARVEST", 20000); party.daily().markSynced(owner); party.daily().markSynced(offline);
        party.daily().setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        var state = mock(QuestState.class);
        when(state.getPlayerData(owner)).thenReturn(finished); when(state.getPlayerData(offline)).thenReturn(unfinished);
        when(state.getQuestPartyState()).thenReturn(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()));
        try (var states = mockStatic(QuestState.class); var calendar = mockStatic(QuestCalendarService.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            calendar.when(QuestCalendarService::currentDay).thenReturn(20006L);
            QuestPartyService.loadPersistentState(server);
            QuestPartyService.clearDailySessionIfFinished(world, owner, DailyQuestType.WHEAT_HARVEST);
            var saved = QuestPartyService.partyFor(owner).daily();
            assertTrue(saved.hasSynced(offline)); assertFalse(saved.hasSynced(owner));
            assertEquals(List.of(offline), QuestPartyService.activeDailyMembers(world, offline, DailyQuestType.WHEAT_HARVEST, false));
            assertTrue(QuestPartyService.dailyTurnInRequirementsConsumed(world, offline, DailyQuestType.WHEAT_HARVEST));
            assertFalse(saved.canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, owner));
            unfinished.setLastRewardDay(20006);
            QuestPartyService.clearDailySessionIfFinished(world, offline, DailyQuestType.WHEAT_HARVEST);
            assertNull(saved.questId());
        }
    }
    @Test void existingWeeklyOfferKeepsAcceptedRewardIdentity() {
        checkOfferIdentity(true);
    }
    private void checkOfferIdentity(boolean weekly) {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        var players = mock(PlayerList.class); var player = mock(ServerPlayer.class);
        UUID id = UUID.randomUUID(); var data = new PlayerQuestData();
        data.setDailyDiscovered(true);
        data.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        data.setDailyChoice(DailyQuestType.WHEAT_HARVEST); data.setAcceptedDay(20000);
        data.setWeeklyChoice(WeeklyQuestType.SMITH_WEEK); data.setWeeklyAcceptedCycle(19999);
        when(server.isDedicatedServer()).thenReturn(true); when(server.getPlayerList()).thenReturn(players);
        when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(id);
        when(players.getPlayer(id)).thenReturn(player);
        var party = new PartyRuntime(UUID.randomUUID(), id); party.members().add(id);
        var session = weekly ? party.weekly() : party.daily();
        String type = weekly ? "SMITH_WEEK" : "WHEAT_HARVEST";
        session.bind(type, weekly ? 19999 : 20000);
        (weekly ? party.weeklyOffers() : party.dailyOffers()).put(id, new QuestJoinOffer(type, session.revision(), id));
        var state = mock(QuestState.class);
        when(state.getPlayerData(id)).thenReturn(data);
        when(state.getQuestPartyState()).thenReturn(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()));
        var identities = new ArrayList<String>();
        try (var states = mockStatic(QuestState.class); var calendar = mockStatic(QuestCalendarService.class);
             var rewards = mockStatic(VanillaRewardService.class, CALLS_REAL_METHODS)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            calendar.when(QuestCalendarService::currentDay).thenReturn(20010L);
            calendar.when(QuestCalendarService::currentWeekCycle).thenReturn(20020L);
            rewards.when(() -> VanillaRewardService.prepare(any(), any(), anyString(), anyString(), anyString(), any(), any()))
                    .thenAnswer(call -> { identities.add(call.getArgument(4)); return false; });
            QuestPartyService.loadPersistentState(server);
            if (weekly) QuestPartyService.acceptWeeklyOffer(world, player);
            else QuestPartyService.acceptDailyOffer(world, player);
            assertEquals(List.of(weekly ? "SMITH_WEEK:19999" : "WHEAT_HARVEST:20000"), identities);
        }
    }
    @Test void unfinishedSharedDailyRetainsProgressAndConsumedItemsAfterRolloverAndReload() {
        var day = new AtomicLong(20000);
        try (var calendar = mockStatic(QuestCalendarService.class)) {
            calendar.when(QuestCalendarService::currentDay).thenAnswer(call -> day.get());
            UUID owner = UUID.randomUUID(), offline = UUID.randomUUID();
            var party = new PartyRuntime(UUID.randomUUID(), owner);
            party.members().addAll(List.of(owner, offline));
            var session = QuestPartySessions.ensureDailySession(party, DailyQuestType.WHEAT_HARVEST);
            session.markSynced(owner); session.markSynced(offline);
            session.setInt("saved", 9); session.setFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
            var loaded = new HashMap<UUID, PartyRuntime>();
            QuestPartyPersistence.read(QuestPartyPersistence.write(Map.of(party.id(), party), Map.of()), loaded, new HashMap<>(), new HashMap<>());
            party = loaded.get(party.id()); day.addAndGet(10);
            assertEquals(DailyQuestType.WHEAT_HARVEST, QuestPartySessions.activeDailyType(party));
            assertSame(party.daily(), QuestPartySessions.ensureDailySession(party, DailyQuestType.WHEAT_HARVEST));
            assertEquals(20000, party.daily().revision());
            assertEquals(9, party.daily().getInt("saved"));
            assertTrue(party.daily().hasFlag(DailyQuestKeys.SHARED_TURN_IN_CONSUMED));
            assertTrue(party.daily().hasSynced(offline));
            assertFalse(party.daily().canJoinAfterTurnIn(DailyQuestKeys.SHARED_TURN_IN_CONSUMED, UUID.randomUUID()));
        }
    }
    @Test void unfinishedSharedWeeklyKeepsItsBindingAcrossWeeks() {
        var cycle = new AtomicLong(19999);
        try (var calendar = mockStatic(QuestCalendarService.class)) {
            calendar.when(QuestCalendarService::currentWeekCycle).thenAnswer(call -> cycle.get());
            var party = new PartyRuntime(UUID.randomUUID(), UUID.randomUUID());
            var session = QuestPartySessions.ensureWeeklySession(party, WeeklyQuestType.SMITH_WEEK);
            session.setInt("saved", 13); session.setFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED, true);
            cycle.addAndGet(21);
            assertEquals(WeeklyQuestType.SMITH_WEEK, QuestPartySessions.activeWeeklyType(party));
            assertSame(session, QuestPartySessions.ensureWeeklySession(party, WeeklyQuestType.SMITH_WEEK));
            assertEquals(19999, session.revision());
            assertEquals(13, session.getInt("saved"));
            assertTrue(session.hasFlag(WeeklyQuestKeys.SHARED_TURN_IN_CONSUMED));
        }
    }
}
