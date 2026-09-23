package de.quest.config;

import java.time.DayOfWeek;
import java.time.ZoneId;

public final class QuestConfig {
    private QuestConfig() {}

    public static ZoneId resetZone() {
        return VillageQuestServerConfig.get().resetZone();
    }

    public static int dailyResetHour() {
        return VillageQuestServerConfig.get().dailyResetHour();
    }

    public static DayOfWeek weeklyResetDay() {
        return VillageQuestServerConfig.get().weeklyResetDay();
    }

    public static int weeklyResetHour() {
        return VillageQuestServerConfig.get().weeklyResetHour();
    }
}
