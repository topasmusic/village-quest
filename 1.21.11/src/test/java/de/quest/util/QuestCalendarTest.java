package de.quest.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class QuestCalendarTest {
    @Test void realtimeKeepsLegacyIdsAndCountsOfflineDaysAndWeeks() {
        var clock = new QuestCalendar();
        clock.update(false, 0, 20000, 19999);
        assertEquals(20000, clock.day()); assertEquals(19999, clock.week());
        assertFalse(clock.update(false, 80000, 20000, 19999));
        clock.update(false, 90000, 20009, 20006);
        assertEquals(20009, clock.day()); assertEquals(20006, clock.week());
        assertFalse(clock.update(false, 0, 19900, 19894));
        assertEquals(20009, clock.day()); assertEquals(20006, clock.week());
    }

    @Test void minecraftBoundariesAreExactlyOneAndSevenDaysAndIgnoreRealTime() {
        var clock = new QuestCalendar();
        clock.update(true, 6000, 20000, 19999);
        clock.update(true, 29999, 20100, 20097);
        assertEquals(20000, clock.day()); assertEquals(1, clock.remainingTicks(false));
        clock.update(true, 30000, 20100, 20097);
        assertEquals(20001, clock.day()); assertEquals(19999, clock.week());
        clock.update(true, 173999, 20100, 20097);
        assertEquals(1, clock.remainingTicks(true));
        clock.update(true, 174000, 20100, 20097);
        assertEquals(20007, clock.day()); assertEquals(20006, clock.week());
    }

    @Test void switchingInEitherDirectionRetainsCycleIdsAndRestartsTheMinecraftWait() {
        var clock = new QuestCalendar();
        clock.update(false, 0, 20000, 19999);
        clock.update(true, 900000, 20010, 20006);
        assertEquals(20000, clock.day()); assertEquals(19999, clock.week());
        clock.update(true, 912000, 20010, 20006);
        clock.update(false, 912000, 20010, 20006);
        assertEquals(20000, clock.day()); assertEquals(19999, clock.week());
        clock.update(false, 912000, 20011, 20006);
        assertEquals(20001, clock.day());
        clock.update(true, 912000, 20011, 20006);
        assertEquals(24000, clock.remainingTicks(false));
        clock.update(false, 912000, 20011, 20006);
        clock.update(true, 912000, 20011, 20006);
        assertEquals(20001, clock.day()); assertEquals(19999, clock.week());
    }

    @Test void rewindRebasesWithoutReplayingRewardsAndReloadRetainsPartialDays() {
        var clock = new QuestCalendar();
        clock.update(true, 0, 20000, 19999);
        clock.update(true, 30000, 20000, 19999);
        clock = QuestCalendar.fromNbt(clock.toNbt());
        assertFalse(clock.update(true, 30000, 20000, 19999));
        assertEquals(20001, clock.day()); assertEquals(18000, clock.remainingTicks(false));
        clock.update(true, 0, 20000, 19999);
        assertEquals(20001, clock.day()); assertEquals(18000, clock.remainingTicks(false));
        clock.update(true, 18000, 20000, 19999);
        assertEquals(20002, clock.day()); assertEquals(120000, clock.remainingTicks(true));
    }

    @Test void sleepingAndForwardTimeJumpsCountButStoppedWorldTimeDoesNot() {
        var clock = new QuestCalendar();
        clock.update(true, 0, 20000, 19999);
        clock.update(true, 12000, 20000, 19999);
        assertFalse(clock.update(true, 12000, 21000, 20999));
        clock.update(true, 24000, 21000, 20999);
        assertEquals(20001, clock.day());
        clock.update(true, 480000, 21000, 20999);
        assertEquals(20020, clock.day()); assertEquals(20013, clock.week());
    }
}
