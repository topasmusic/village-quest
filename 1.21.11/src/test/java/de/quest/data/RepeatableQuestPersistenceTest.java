package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.party.QuestPartyService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.reward.VanillaRewardService;
import de.quest.util.QuestCalendarService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

final class RepeatableQuestPersistenceTest {
    private final UUID id = UUID.randomUUID();
    private final AtomicLong day = new AtomicLong(20000), week = new AtomicLong(19999);
    private ServerLevel world;
    private ServerPlayer player;
    private QuestState state;
    private MockedStatic<QuestCalendarService> calendar;
    private MockedStatic<QuestState> states;
    private MockedStatic<QuestPartyService> parties;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        world = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        player = mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(mock(PlayerList.class));
        when(player.getUUID()).thenReturn(id);
        state = QuestState.fromNbt(new CompoundTag());
        calendar = mockStatic(QuestCalendarService.class);
        calendar.when(QuestCalendarService::currentDay).thenAnswer(call -> day.get());
        calendar.when(QuestCalendarService::currentWeekCycle).thenAnswer(call -> week.get());
        states = mockStatic(QuestState.class, call -> call.getMethod().getName().equals("get") ? state : call.callRealMethod());
        parties = mockStatic(QuestPartyService.class);
    }
    @AfterEach void cleanup() { parties.close(); states.close(); calendar.close(); }

    private PlayerQuestData daily(boolean bonus) {
        var data = state.getPlayerData(id);
        data.setDailyDiscovered(true);
        data.setDailyChoice(DailyQuestService.DailyQuestType.WHEAT_HARVEST);
        data.setDailyChoiceDay(day.get());
        data.setDailyTargetProfile(RepeatableTargetProfile.LIGHT);
        data.setProgressDay(day.get());
        data.setDailyInt("saved", 7);
        if (bonus) {
            data.setLastRewardDay(day.get());
            data.setAcceptedDay(day.get());
            data.setBonusAcceptedDay(day.get());
            data.setBonusChoice(DailyQuestService.DailyQuestType.WOODCUTTING);
            data.setBonusChoiceDay(day.get());
            data.setBonusTargetProfile(RepeatableTargetProfile.LIGHT);
        } else data.setAcceptedDay(day.get());
        return data;
    }
    private PlayerQuestData weekly() {
        var data = state.getPlayerData(id);
        data.setWeeklyChoice(WeeklyQuestService.WeeklyQuestType.SMITH_WEEK);
        data.setWeeklyChoiceCycle(week.get());
        data.setWeeklyAcceptedCycle(week.get());
        data.setWeeklyProgressCycle(week.get());
        data.setWeeklyTargetProfile(RepeatableTargetProfile.LIGHT);
        data.setWeeklyInt("saved", 11);
        return data;
    }

    @Test void dailyChoiceProfileAndProgressSurviveSeveralDaysAndSaveReload() {
        daily(false);
        state = QuestState.fromNbt(QuestState.toNbt(state));
        day.addAndGet(10);
        assertTrue(DailyQuestService.isDailyActive(world, id));
        assertEquals(DailyQuestService.DailyQuestType.WHEAT_HARVEST, DailyQuestService.previewQuestChoice(world, id));
        assertEquals(7, DailyQuestService.getQuestInt(world, id, "saved"));
        assertEquals(RepeatableTargetProfile.LIGHT, state.getPlayerData(id).getDailyTargetProfile());
        assertFalse(DailyQuestService.canShowDailyOffer(world, id));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 3})
    void resetIntroWaitsForFreeCycleAndSurvivesCancellationAndReload(int elapsedDays) throws Exception {
        when(player.getStats()).thenReturn(mock(net.minecraft.stats.ServerStatsCounter.class));
        var old=state.getPlayerData(id);old.setLastRewardDay(day.get());old.setBonusRewardDay(day.get());old.setWeeklyRewardCycle(week.get());
        state.replacePersonalProgress(id,old.personalRestart());
        var type=DailyQuestService.DailyQuestType.WHEAT_HARVEST;
        try(var rewards=mockStatic(VanillaRewardService.class);
            var sounds=mockStatic(de.quest.quest.QuestSoundFeedback.class);
            var tracker=mockStatic(de.quest.quest.QuestTrackerService.class)) {
            assertFalse(de.quest.quest.daily.FirstDailyChoiceService.accept(world,player,type));
            rewards.verifyNoInteractions();
            day.addAndGet(elapsedDays);
            if(elapsedDays==0) {
                assertFalse(de.quest.quest.daily.FirstDailyChoiceService.canChoose(state.getPlayerData(id)));
                assertEquals(PlayerQuestData.UNSET_DAY,state.getPlayerData(id).getAcceptedDay());
            } else {
                rewards.when(()->VanillaRewardService.dailyIdentity(anyString())).thenCallRealMethod();
                rewards.when(()->VanillaRewardService.prepare(any(),any(),anyString(),anyString(),anyString(),any(),any())).thenReturn(true);
                assertTrue(de.quest.quest.daily.FirstDailyChoiceService.accept(world,player,type));
                assertTrue(DailyQuestService.cancelToday(world,id));
                assertTrue(de.quest.quest.daily.FirstDailyChoiceService.canChoose(state.getPlayerData(id)));
                assertTrue(de.quest.quest.daily.FirstDailyChoiceService.accept(world,player,type));
                state.getPlayerData(id).setDailyInt("saved",7);
                state=QuestState.fromNbt(QuestState.toNbt(state));
                assertTrue(DailyQuestService.isDailyActive(world,id));assertEquals(7,DailyQuestService.getQuestInt(world,id,"saved"));
                DailyQuestService.markCompletedToday(world,id);
                assertTrue(de.quest.quest.daily.FirstDailyChoiceService.complete(state.getPlayerData(id)));
                assertFalse(DailyQuestService.isDailyActive(world,id));
                assertTrue(de.quest.quest.daily.DailyChoiceService.offers(world,id).isEmpty());
                assertEquals(day.get(),state.getPlayerData(id).getLastRewardDay());
                parties.verify(()->QuestPartyService.onDailyQuestAccepted(any(),any(),any(),any()),never());
            }
            assertEquals(20000,state.getPlayerData(id).getBonusRewardDay());assertEquals(19999,state.getPlayerData(id).getWeeklyRewardCycle());
        }
    }
    @Test void activeBonusOccupiesTheSameDailySlotAfterRollover() {
        daily(true); day.incrementAndGet();
        assertEquals(DailyQuestService.DailyQuestType.WOODCUTTING, DailyQuestService.activeQuestType(world, id));
        assertEquals(7, DailyQuestService.getQuestInt(world, id, "saved"));
        assertFalse(DailyQuestService.canShowDailyOffer(world, id));
        try (var rewards = mockStatic(VanillaRewardService.class)) {
            DailyQuestService.acceptQuest(world, player);
            assertEquals(7, state.getPlayerData(id).getDailyInt("saved"));
            assertEquals(20000, state.getPlayerData(id).getBonusAcceptedDay());
            assertFalse(DailyQuestService.rerollQuestForToday(world, player));
        }
    }
    @Test void staleBonusAcceptCannotClearAnActiveNormalDaily() {
        var data = daily(false);
        data.setBonusChoice(DailyQuestService.DailyQuestType.WOODCUTTING);
        data.setBonusChoiceDay(day.get());
        try (var rewards = mockStatic(VanillaRewardService.class);
             var definitions = mockStatic(de.quest.quest.daily.DailyQuestGenerator.class);
             var sounds = mockStatic(de.quest.quest.QuestSoundFeedback.class);
             var tracker = mockStatic(de.quest.quest.QuestTrackerService.class)) {
            rewards.when(() -> VanillaRewardService.dailyIdentity(anyString())).thenCallRealMethod();
            rewards.when(() -> VanillaRewardService.prepare(any(), any(), anyString(), anyString(), anyString(), any(), any())).thenReturn(true);
            DailyQuestService.acceptBonusQuest(world, player);
            assertEquals(PlayerQuestData.UNSET_DAY, data.getBonusAcceptedDay());
            assertEquals(7, data.getDailyInt("saved"));
        }
    }
    @Test void weeklyChoiceProfileAndProgressSurviveSeveralWeeksAndReload() {
        weekly(); state = QuestState.fromNbt(QuestState.toNbt(state)); week.addAndGet(21);
        assertTrue(WeeklyQuestService.isWeeklyActive(world, id));
        assertEquals(WeeklyQuestService.WeeklyQuestType.SMITH_WEEK, WeeklyQuestService.previewQuestChoice(world, id));
        assertEquals(11, WeeklyQuestService.getQuestInt(world, id, "saved"));
        assertEquals(RepeatableTargetProfile.LIGHT, state.getPlayerData(id).getWeeklyTargetProfile());
        assertFalse(WeeklyQuestService.acceptQuest(world, player));
    }
    @Test void completedDailyDoesNotReviveOnTheNextDay() {
        daily(false); DailyQuestService.markCompletedToday(world, id); day.incrementAndGet();
        assertFalse(DailyQuestService.isDailyActive(world, id));
        assertTrue(DailyQuestService.canShowDailyOffer(world, id));
    }
    @Test void lateDailyCompletionCountsOnDeliveryDayAndDoesNotReviveLater() {
        daily(false); day.addAndGet(3);
        assertTrue(DailyQuestService.isDailyActive(world, id));
        DailyQuestService.markCompletedToday(world, id);
        assertTrue(DailyQuestService.hasCompletedToday(world, id));
        assertFalse(DailyQuestService.isDailyActive(world, id));
        day.incrementAndGet();
        assertFalse(DailyQuestService.isDailyActive(world, id));
    }
    @Test void completedWeeklyDoesNotReviveOnTheNextWeek() {
        weekly(); WeeklyQuestService.markCompletedThisWeek(world, id); week.addAndGet(7);
        assertFalse(WeeklyQuestService.isWeeklyActive(world, id));
        assertNull(WeeklyQuestService.activeQuestType(world, id));
    }
    @Test void lateDailyAndWeeklyCanBeCancelledIndependently() {
        daily(false); weekly(); day.addAndGet(2); week.addAndGet(7);
        assertTrue(DailyQuestService.cancelToday(world, id));
        assertFalse(DailyQuestService.isDailyActive(world, id));
        assertTrue(WeeklyQuestService.isWeeklyActive(world, id));
        assertTrue(WeeklyQuestService.cancelThisWeek(world, id));
        assertFalse(WeeklyQuestService.isWeeklyActive(world, id));
        assertEquals(PlayerQuestData.UNSET_DAY, state.getPlayerData(id).getWeeklyAcceptedCycle());
    }
}
