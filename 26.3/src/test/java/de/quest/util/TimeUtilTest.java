package de.quest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

final class TimeUtilTest {
    @Test
    void dailyBoundaryUsesTheConfiguredLocalHour() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        ZonedDateTime before = ZonedDateTime.of(2026, 8, 2, 5, 59, 0, 0, zone);
        ZonedDateTime after = before.plusMinutes(2);

        assertEquals("2026-08-01T06:00-07:00[America/Los_Angeles]",
                TimeUtil.currentResetDateTime(before, 6).toString());
        assertEquals("2026-08-02T06:00-07:00[America/Los_Angeles]",
                TimeUtil.currentResetDateTime(after, 6).toString());
    }

    @Test
    void weeklyBoundaryHonorsConfiguredDayAndHour() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime mondayBefore = ZonedDateTime.of(2026, 8, 3, 5, 59, 0, 0, zone);
        ZonedDateTime mondayAfter = mondayBefore.plusMinutes(2);

        assertEquals("2026-07-27T06:00+02:00[Europe/Berlin]",
                TimeUtil.currentWeeklyResetStart(mondayBefore, DayOfWeek.MONDAY, 6).toString());
        assertEquals("2026-08-03T06:00+02:00[Europe/Berlin]",
                TimeUtil.currentWeeklyResetStart(mondayAfter, DayOfWeek.MONDAY, 6).toString());
    }

    @Test
    void nextDailyResetSurvivesDaylightSavingChanges() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime beforeSpringChange = ZonedDateTime.of(2026, 3, 7, 23, 30, 0, 0, zone);
        ZonedDateTime next = TimeUtil.nextDailyReset(beforeSpringChange, 6);

        assertEquals(6, next.getHour());
        assertEquals("2026-03-08", next.toLocalDate().toString());
        assertEquals("-04:00", next.getOffset().toString());
    }

    @Test
    void nextWeeklyResetRemainsLocalAcrossDst() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime beforeAutumnChange = ZonedDateTime.of(2026, 10, 24, 23, 0, 0, 0, zone);
        ZonedDateTime next = TimeUtil.nextWeeklyReset(beforeAutumnChange, DayOfWeek.MONDAY, 6);

        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
        assertEquals(6, next.getHour());
        assertEquals("+01:00", next.getOffset().toString());
    }
}
