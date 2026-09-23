package de.quest.util;

import de.quest.config.QuestConfig;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.ZonedDateTime;

public final class TimeUtil {
    private TimeUtil() {}

    public static long currentDay() {
        return currentResetDateTime(now(), QuestConfig.dailyResetHour()).toLocalDate().toEpochDay();
    }

    public static long millisUntilNextDailyReset() {
        ZonedDateTime now = now();
        ZonedDateTime nextReset = nextDailyReset(now, QuestConfig.dailyResetHour());
        return Math.max(0L, Duration.between(now, nextReset).toMillis());
    }

    public static long currentWeekCycle() {
        return currentWeeklyResetStart(now(), QuestConfig.weeklyResetDay(), QuestConfig.weeklyResetHour())
                .toLocalDate().toEpochDay();
    }

    public static long millisUntilNextWeeklyReset() {
        ZonedDateTime now = now();
        ZonedDateTime nextReset = nextWeeklyReset(
                now, QuestConfig.weeklyResetDay(), QuestConfig.weeklyResetHour());
        return Math.max(0L, Duration.between(now, nextReset).toMillis());
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), QuestConfig.resetZone());
    }

    static ZonedDateTime currentResetDateTime(ZonedDateTime now, int hour) {
        ZonedDateTime todayReset = resetAt(now.toLocalDate(), hour, now.getZone());
        return now.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
    }

    static ZonedDateTime nextDailyReset(ZonedDateTime now, int hour) {
        ZonedDateTime todayReset = resetAt(now.toLocalDate(), hour, now.getZone());
        return now.isBefore(todayReset) ? todayReset : todayReset.plusDays(1);
    }

    static ZonedDateTime currentWeeklyResetStart(ZonedDateTime now, DayOfWeek resetWeekday, int hour) {
        LocalDate resetDay = now.toLocalDate().with(TemporalAdjusters.previousOrSame(resetWeekday));
        ZonedDateTime weeklyReset = resetAt(resetDay, hour, now.getZone());
        if (now.isBefore(weeklyReset)) {
            return weeklyReset.minusWeeks(1);
        }
        return weeklyReset;
    }

    static ZonedDateTime nextWeeklyReset(ZonedDateTime now, DayOfWeek resetWeekday, int hour) {
        return currentWeeklyResetStart(now, resetWeekday, hour).plusWeeks(1);
    }

    private static ZonedDateTime resetAt(LocalDate date, int hour, java.time.ZoneId zone) {
        return ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(hour, 0)), zone);
    }
}
