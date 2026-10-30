package de.quest.party;

import de.quest.config.VillageQuestServerConfig;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reward.VanillaRewardService;
import de.quest.util.QuestCalendar;
import de.quest.util.QuestCalendarService;
import de.quest.util.TimeUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class QuestCalendarIntegrationTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void release() { QuestCalendarService.stop(); }
    private VillageQuestServerConfig config(String text) throws Exception {
        var path = temporary.resolve("server.properties"); Files.writeString(path, text);
        var method = VillageQuestServerConfig.class.getDeclaredMethod("load", Path.class);
        method.setAccessible(true); return (VillageQuestServerConfig) method.invoke(null, path);
    }

    @Test void configRequiresExplicitOptInAndInvalidValuesKeepRealtime() throws Exception {
        assertFalse(config("").minecraftQuestCalendar());
        assertFalse(config("quest_calendar_minecraft_days=invalid").minecraftQuestCalendar());
        assertFalse(config("quest_calendar_minecraft_days=false").minecraftQuestCalendar());
        assertTrue(config("quest_calendar_minecraft_days=true").minecraftQuestCalendar());
    }

    @Test void personalAndOfflinePartyMembersUseTheSameSavedCyclesAndRewardIds() throws Exception {
        var realtime = config(""); var minecraft = config("quest_calendar_minecraft_days=true");
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(world); when(world.getServer()).thenReturn(server);
        var state = mock(QuestState.class); var calendar = new QuestCalendar();
        when(state.questCalendar()).thenReturn(calendar);
        UUID owner = UUID.randomUUID(), offline = UUID.randomUUID();
        var ownerData = new PlayerQuestData(); var offlineData = new PlayerQuestData();
        when(state.getPlayerData(owner)).thenReturn(ownerData); when(state.getPlayerData(offline)).thenReturn(offlineData);
        var party = new PartyRuntime(UUID.randomUUID(), owner); party.members().add(offline);
        var daily = DailyQuestService.DailyQuestType.WHEAT_HARVEST;
        var weekly = WeeklyQuestService.WeeklyQuestType.SMITH_WEEK;
        try (var configs = mockStatic(VillageQuestServerConfig.class); var states = mockStatic(QuestState.class)) {
            configs.when(VillageQuestServerConfig::get).thenReturn(realtime);
            states.when(() -> QuestState.get(server)).thenReturn(state);
            QuestCalendarService.start(server);
            long day = QuestCalendarService.currentDay(), week = QuestCalendarService.currentWeekCycle();
            String dailyReward = VanillaRewardService.dailyIdentity(daily.name());
            String weeklyReward = VanillaRewardService.weeklyIdentity(weekly.name());
            var sharedDay = QuestPartySessions.ensureDailySession(party, daily);
            var sharedWeek = QuestPartySessions.ensureWeeklySession(party, weekly);
            sharedDay.markSynced(owner); sharedDay.markSynced(offline);
            sharedWeek.markSynced(owner); sharedWeek.markSynced(offline);
            sharedDay.setInt("progress", 4);
            for (var data : java.util.List.of(ownerData, offlineData)) {
                data.setAcceptedDay(day); data.setLastRewardDay(day);
                data.setWeeklyAcceptedCycle(week); data.setWeeklyRewardCycle(week);
            }
            configs.when(VillageQuestServerConfig::get).thenReturn(minecraft);
            when(world.getOverworldClockTime()).thenReturn(999999L);
            assertEquals(dailyReward, VanillaRewardService.dailyIdentity(daily.name()));
            assertEquals(weeklyReward, VanillaRewardService.weeklyIdentity(weekly.name()));
            assertSame(sharedDay, QuestPartySessions.ensureDailySession(party, daily));
            assertEquals(4, sharedDay.getInt("progress"));
            for (var id : java.util.List.of(owner, offline)) {
                assertTrue(DailyQuestService.hasCompletedToday(world, id));
                assertTrue(WeeklyQuestService.hasCompletedThisWeek(world, id));
            }
            // Reload plus backwards world time cannot reopen a rewarded cycle.
            QuestCalendarService.stop();
            when(state.questCalendar()).thenReturn(QuestCalendar.fromNbt(calendar.toNbt()));
            when(world.getOverworldClockTime()).thenReturn(0L);
            QuestCalendarService.start(server);
            assertEquals(dailyReward, VanillaRewardService.dailyIdentity(daily.name()));
            assertEquals(daily, QuestPartySessions.activeDailyType(party));
            assertEquals(weekly, QuestPartySessions.activeWeeklyType(party));
            when(world.getOverworldClockTime()).thenReturn(24000L);
            assertFalse(DailyQuestService.hasCompletedToday(world, offline));
            assertEquals(daily, QuestPartySessions.activeDailyType(party));
            assertEquals(weekly, QuestPartySessions.activeWeeklyType(party));
            when(world.getOverworldClockTime()).thenReturn(168000L);
            assertFalse(WeeklyQuestService.hasCompletedThisWeek(world, offline));
            assertEquals(weekly, QuestPartySessions.activeWeeklyType(party));
            assertNotEquals(weeklyReward, VanillaRewardService.weeklyIdentity(weekly.name()));
            // Every daily consumer, including economic and notice-board limits, follows the same calendar.
            assertEquals(QuestCalendarService.currentDay(), TimeUtil.currentDay());
            assertTrue(TimeUtil.currentDay() > day);
            var noticeDay = de.quest.shrine.VillageBondService.class.getDeclaredMethod("currentRequestDay");
            noticeDay.setAccessible(true);
            assertEquals(Math.toIntExact(QuestCalendarService.currentDay()), noticeDay.invoke(null));
            String before = VanillaRewardService.dailyIdentity(daily.name());
            configs.when(VillageQuestServerConfig::get).thenReturn(realtime);
            assertEquals(before, VanillaRewardService.dailyIdentity(daily.name()));
        }
    }
}
