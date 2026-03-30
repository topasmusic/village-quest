package de.quest.config;

import java.time.ZoneId;

public final class QuestConfig {
    public static final ZoneId DAILY_RESET_ZONE = ZoneId.of("Europe/Berlin");
    public static final int DAILY_RESET_HOUR = 6;
    public static final int WEEKLY_RESET_HOUR = 6;

    private QuestConfig() {}
}
