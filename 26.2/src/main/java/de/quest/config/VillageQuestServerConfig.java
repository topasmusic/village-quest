package de.quest.config;

import de.quest.VillageQuest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Server-authoritative Village Quest settings loaded once during mod bootstrap. */
public final class VillageQuestServerConfig {
    public enum CaravanVisualMode {
        FULL,
        REDUCED,
        MAP_ONLY
    }

    private static final String FILE_NAME = "server.properties";
    private static VillageQuestServerConfig instance = defaults();

    private final String configuredResetTimezone;
    private final ZoneId resetZone;
    private final int dailyResetHour;
    private final DayOfWeek weeklyResetDay;
    private final int weeklyResetHour;
    private final boolean allowPlayerCaravanYards;
    private final CaravanVisualMode caravanVisualMode;

    private VillageQuestServerConfig(String configuredResetTimezone,
                                     ZoneId resetZone,
                                     int dailyResetHour,
                                     DayOfWeek weeklyResetDay,
                                     int weeklyResetHour,
                                     boolean allowPlayerCaravanYards,
                                     CaravanVisualMode caravanVisualMode) {
        this.configuredResetTimezone = configuredResetTimezone;
        this.resetZone = resetZone;
        this.dailyResetHour = dailyResetHour;
        this.weeklyResetDay = weeklyResetDay;
        this.weeklyResetHour = weeklyResetHour;
        this.allowPlayerCaravanYards = allowPlayerCaravanYards;
        this.caravanVisualMode = caravanVisualMode;
    }

    public static void bootstrap() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.isRegularFile(path)) {
                Files.writeString(path, defaultFile(), StandardCharsets.UTF_8);
            }
            instance = load(path);
            VillageQuest.LOGGER.info(
                    "Loaded Village Quest server config: reset zone {}, daily {}:00, weekly {} {}:00, caravans {}",
                    instance.resetZone, String.format("%02d", instance.dailyResetHour), instance.weeklyResetDay,
                    String.format("%02d", instance.weeklyResetHour), instance.caravanVisualMode);
        } catch (IOException exception) {
            instance = defaults();
            VillageQuest.LOGGER.warn("Failed to load Village Quest server config from {}; using safe defaults", path, exception);
        }
    }

    public static VillageQuestServerConfig get() {
        return instance;
    }

    public String configuredResetTimezone() {
        return configuredResetTimezone;
    }

    public ZoneId resetZone() {
        return resetZone;
    }

    public int dailyResetHour() {
        return dailyResetHour;
    }

    public DayOfWeek weeklyResetDay() {
        return weeklyResetDay;
    }

    public int weeklyResetHour() {
        return weeklyResetHour;
    }

    public boolean allowPlayerCaravanYards() {
        return allowPlayerCaravanYards;
    }

    public CaravanVisualMode caravanVisualMode() {
        return caravanVisualMode;
    }

    private static VillageQuestServerConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }

        String configuredZone = value(properties, "reset_timezone", "AUTO");
        ZoneId zone = parseZone(configuredZone);
        int dailyHour = boundedInt(properties, "daily_reset_hour", 6, 0, 23);
        DayOfWeek weeklyDay = enumValue(properties, "weekly_reset_day", DayOfWeek.MONDAY, DayOfWeek.class);
        int weeklyHour = boundedInt(properties, "weekly_reset_hour", 6, 0, 23);
        boolean yards = booleanValue(properties, "allow_player_caravan_yards", true);
        CaravanVisualMode caravanMode = enumValue(
                properties, "physical_caravans", CaravanVisualMode.FULL, CaravanVisualMode.class);
        return new VillageQuestServerConfig(
                configuredZone, zone, dailyHour, weeklyDay, weeklyHour, yards, caravanMode);
    }

    private static VillageQuestServerConfig defaults() {
        return new VillageQuestServerConfig(
                "AUTO", ZoneId.systemDefault(), 6, DayOfWeek.MONDAY, 6, true, CaravanVisualMode.FULL);
    }

    private static ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("AUTO") || raw.equalsIgnoreCase("SYSTEM")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException exception) {
            VillageQuest.LOGGER.warn("Invalid reset_timezone '{}'; using server system zone {}", raw, ZoneId.systemDefault());
            return ZoneId.systemDefault();
        }
    }

    private static int boundedInt(Properties properties, String key, int fallback, int minimum, int maximum) {
        String raw = value(properties, key, Integer.toString(fallback));
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed >= minimum && parsed <= maximum) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the logged default below.
        }
        VillageQuest.LOGGER.warn("Invalid {} '{}'; using {}", key, raw, fallback);
        return fallback;
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String raw = value(properties, key, Boolean.toString(fallback));
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        VillageQuest.LOGGER.warn("Invalid {} '{}'; using {}", key, raw, fallback);
        return fallback;
    }

    private static <E extends Enum<E>> E enumValue(Properties properties,
                                                     String key,
                                                     E fallback,
                                                     Class<E> type) {
        String raw = value(properties, key, fallback.name());
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            VillageQuest.LOGGER.warn("Invalid {} '{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(VillageQuest.MOD_ID).resolve(FILE_NAME);
    }

    private static String defaultFile() {
        return """
                # Village Quest 2.1.1 server/world-owner settings
                # Restart the game or dedicated server after changing this file.
                # AUTO uses the timezone of the running integrated or dedicated server.
                reset_timezone=AUTO
                daily_reset_hour=6
                weekly_reset_day=MONDAY
                weekly_reset_hour=6

                # Allows one deliberately registered player base to act as the route network's home node after Market Charter access.
                allow_player_caravan_yards=true

                # FULL = three nearby merchants, REDUCED = one nearby merchant, MAP_ONLY = simulation and maps only.
                physical_caravans=FULL
                """;
    }
}
