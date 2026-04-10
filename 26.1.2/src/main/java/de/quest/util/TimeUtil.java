package de.quest.util;

import de.quest.config.QuestConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.ZonedDateTime;

public final class TimeUtil {
    private TimeUtil() {}

    public static long currentDay() {
        return currentResetDateTime(QuestConfig.DAILY_RESET_HOUR).toLocalDate().toEpochDay();
    }

    public static long millisUntilNextDailyReset() {
        ZonedDateTime now = now();
        ZonedDateTime nextReset = nextDailyReset(now);
        return Math.max(0L, Duration.between(now, nextReset).toMillis());
    }

    public static long currentWeekCycle() {
        return currentWeeklyResetStart(now()).toLocalDate().toEpochDay();
    }

    public static long millisUntilNextWeeklyReset() {
        ZonedDateTime now = now();
        ZonedDateTime nextReset = nextWeeklyReset(now);
        return Math.max(0L, Duration.between(now, nextReset).toMillis());
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), QuestConfig.DAILY_RESET_ZONE);
    }

    private static ZonedDateTime currentResetDateTime(int hour) {
        ZonedDateTime now = now();
        ZonedDateTime todayReset = resetAt(now.toLocalDate(), hour);
        return now.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
    }

    private static ZonedDateTime nextDailyReset(ZonedDateTime now) {
        ZonedDateTime todayReset = resetAt(now.toLocalDate(), QuestConfig.DAILY_RESET_HOUR);
        return now.isBefore(todayReset) ? todayReset : todayReset.plusDays(1);
    }

    private static ZonedDateTime currentWeeklyResetStart(ZonedDateTime now) {
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        ZonedDateTime mondayReset = resetAt(monday, QuestConfig.WEEKLY_RESET_HOUR);
        if (now.isBefore(mondayReset)) {
            return mondayReset.minusWeeks(1);
        }
        return mondayReset;
    }

    private static ZonedDateTime nextWeeklyReset(ZonedDateTime now) {
        return currentWeeklyResetStart(now).plusWeeks(1);
    }

    private static ZonedDateTime resetAt(LocalDate date, int hour) {
        return ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(hour, 0)), QuestConfig.DAILY_RESET_ZONE);
    }
}
