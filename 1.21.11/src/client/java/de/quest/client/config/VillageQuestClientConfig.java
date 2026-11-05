package de.quest.client.config;

import de.quest.VillageQuest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Local presentation, notification, and recoverable map-cache preferences. */
public final class VillageQuestClientConfig {
    public enum HudPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public enum MapQuality {
        LOW(0.65f),
        BALANCED(1.0f),
        HIGH(1.35f);

        private final float sampleScale;

        MapQuality(float sampleScale) {
            this.sampleScale = sampleScale;
        }

        public float sampleScale() {
            return sampleScale;
        }
    }

    private static final String FILE_NAME = "client.properties";
    private static VillageQuestClientConfig instance = defaults();
    private boolean speechBubblesEnabled=true;
    public boolean speechBubblesEnabled() { return speechBubblesEnabled; }

    private final boolean questTrackerEnabledByDefault;
    private HudPosition questTrackerPosition;
    private final float questTrackerScale;
    private final float questTrackerBackgroundOpacity;
    private final boolean questAvailableChatNotifications;
    private final boolean caravanEventNotifications;
    private final boolean questProgressSounds;
    private final float questProgressSoundVolume;
    private final boolean minimapEnabledByDefault;
    private final HudPosition minimapPosition;
    private final float minimapScale;
    private final float minimapOpacity;
    private final boolean showPlayerMarker;
    private final boolean showVillageMarkers;
    private final boolean showCaravanMarkers;
    private final boolean showRouteLines;
    private final boolean persistentMapCache;
    private final MapQuality mapQuality;
    private final int mapCacheMaxSizeMb;
    private final int mapCacheRetentionDays;
    private final boolean tutorialHints;

    private VillageQuestClientConfig(boolean questTrackerEnabledByDefault,
                                     HudPosition questTrackerPosition,
                                     float questTrackerScale,
                                     float questTrackerBackgroundOpacity,
                                     boolean questAvailableChatNotifications,
                                     boolean caravanEventNotifications,
                                     boolean questProgressSounds,
                                     float questProgressSoundVolume,
                                     boolean minimapEnabledByDefault,
                                     HudPosition minimapPosition,
                                     float minimapScale,
                                     float minimapOpacity,
                                     boolean showPlayerMarker,
                                     boolean showVillageMarkers,
                                     boolean showCaravanMarkers,
                                     boolean showRouteLines,
                                     boolean persistentMapCache,
                                     MapQuality mapQuality,
                                     int mapCacheMaxSizeMb,
                                     int mapCacheRetentionDays,
                                     boolean tutorialHints) {
        this.questTrackerEnabledByDefault = questTrackerEnabledByDefault;
        this.questTrackerPosition = questTrackerPosition;
        this.questTrackerScale = questTrackerScale;
        this.questTrackerBackgroundOpacity = questTrackerBackgroundOpacity;
        this.questAvailableChatNotifications = questAvailableChatNotifications;
        this.caravanEventNotifications = caravanEventNotifications;
        this.questProgressSounds = questProgressSounds;
        this.questProgressSoundVolume = questProgressSoundVolume;
        this.minimapEnabledByDefault = minimapEnabledByDefault;
        this.minimapPosition = minimapPosition;
        this.minimapScale = minimapScale;
        this.minimapOpacity = minimapOpacity;
        this.showPlayerMarker = showPlayerMarker;
        this.showVillageMarkers = showVillageMarkers;
        this.showCaravanMarkers = showCaravanMarkers;
        this.showRouteLines = showRouteLines;
        this.persistentMapCache = persistentMapCache;
        this.mapQuality = mapQuality;
        this.mapCacheMaxSizeMb = mapCacheMaxSizeMb;
        this.mapCacheRetentionDays = mapCacheRetentionDays;
        this.tutorialHints = tutorialHints;
    }

    public static void bootstrap() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.isRegularFile(path)) {
                Files.writeString(path, defaultFile(), StandardCharsets.UTF_8);
            }
            instance = load(path);
            VillageQuest.LOGGER.info("Loaded Village Quest client config from {}", path);
        } catch (IOException exception) {
            instance = defaults();
            VillageQuest.LOGGER.warn("Failed to load Village Quest client config from {}; using safe defaults", path, exception);
        }
    }

    public record Presentation(boolean tracker, boolean minimap, boolean questNotifications,
                               boolean caravanNotifications, boolean sounds, boolean speechBubbles) {
        public static Presentation recommended() { return new Presentation(true, false, true, true, true, true); }
    }
    public Presentation presentation() {
        return new Presentation(questTrackerEnabledByDefault, minimapEnabledByDefault,
                questAvailableChatNotifications, caravanEventNotifications, questProgressSounds, speechBubblesEnabled);
    }
    public boolean savePresentation(Presentation value) {
        try {
            instance = savePresentation(configPath(), value);
            return true;
        } catch (IOException failure) {
            VillageQuest.LOGGER.warn("Could not save journal presentation settings", failure);
            return false;
        }
    }
    static VillageQuestClientConfig savePresentation(Path path, Presentation value) throws IOException {
        var properties = new Properties();
        if (Files.isRegularFile(path)) try (var input = Files.newInputStream(path)) { properties.load(input); }
        properties.setProperty("quest_tracker_enabled_by_default", Boolean.toString(value.tracker()));
        properties.setProperty("minimap_enabled_by_default", Boolean.toString(value.minimap()));
        properties.setProperty("quest_available_chat_notifications", Boolean.toString(value.questNotifications()));
        properties.setProperty("caravan_event_notifications", Boolean.toString(value.caravanNotifications()));
        properties.setProperty("quest_progress_sounds", Boolean.toString(value.sounds()));
        properties.setProperty("speech_bubbles_enabled", Boolean.toString(value.speechBubbles()));
        store(path, properties);
        return load(path);
    }
    public static VillageQuestClientConfig get() {
        return instance;
    }

    public boolean questTrackerEnabledByDefault() { return questTrackerEnabledByDefault; }
    public HudPosition questTrackerPosition() { return questTrackerPosition; }
    public float questTrackerScale() { return questTrackerScale; }
    public float questTrackerBackgroundOpacity() { return questTrackerBackgroundOpacity; }
    public boolean questAvailableChatNotifications() { return questAvailableChatNotifications; }
    public boolean caravanEventNotifications() { return caravanEventNotifications; }
    public boolean questProgressSounds() { return questProgressSounds; }
    public float questProgressSoundVolume() { return questProgressSoundVolume; }
    public boolean minimapEnabledByDefault() { return minimapEnabledByDefault; }
    public HudPosition minimapPosition() { return minimapPosition; }
    public float minimapScale() { return minimapScale; }
    public float minimapOpacity() { return minimapOpacity; }
    public boolean showPlayerMarker() { return showPlayerMarker; }
    public boolean showVillageMarkers() { return showVillageMarkers; }
    public boolean showCaravanMarkers() { return showCaravanMarkers; }
    public boolean showRouteLines() { return showRouteLines; }
    public boolean persistentMapCache() { return persistentMapCache; }
    public MapQuality mapQuality() { return mapQuality; }
    public int mapCacheMaxSizeMb() { return mapCacheMaxSizeMb; }
    public int mapCacheRetentionDays() { return mapCacheRetentionDays; }
    public boolean tutorialHints() { return tutorialHints; }

    static VillageQuestClientConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        // Upgrade only former stock defaults. Explicit custom scale/opacity stay intact.
        if (!"1".equals(properties.getProperty("quest_tracker_style_version"))) {
            if ("1.0".equals(properties.getProperty("quest_tracker_scale", "").trim()))
                properties.setProperty("quest_tracker_scale", "0.85");
            if ("0.55".equals(properties.getProperty("quest_tracker_background_opacity", "").trim()))
                properties.setProperty("quest_tracker_background_opacity", "0.5");
            properties.setProperty("quest_tracker_style_version", "1");
            store(path, properties);
        }
        VillageQuestClientConfig defaults = defaults();
        var config = new VillageQuestClientConfig(
                bool(properties, "quest_tracker_enabled_by_default", defaults.questTrackerEnabledByDefault),
                enumValue(properties, "quest_tracker_position", defaults.questTrackerPosition, HudPosition.class),
                decimal(properties, "quest_tracker_scale", defaults.questTrackerScale, 0.65f, 1.75f),
                decimal(properties, "quest_tracker_background_opacity", defaults.questTrackerBackgroundOpacity, 0.0f, 1.0f),
                bool(properties, "quest_available_chat_notifications", defaults.questAvailableChatNotifications),
                bool(properties, "caravan_event_notifications", defaults.caravanEventNotifications),
                bool(properties, "quest_progress_sounds", defaults.questProgressSounds),
                decimal(properties, "quest_progress_sound_volume", defaults.questProgressSoundVolume, 0.0f, 1.0f),
                bool(properties, "minimap_enabled_by_default", defaults.minimapEnabledByDefault),
                enumValue(properties, "minimap_position", defaults.minimapPosition, HudPosition.class),
                decimal(properties, "minimap_scale", defaults.minimapScale, 0.65f, 1.75f),
                decimal(properties, "minimap_opacity", defaults.minimapOpacity, 0.2f, 1.0f),
                bool(properties, "show_player_marker", defaults.showPlayerMarker),
                bool(properties, "show_village_markers", defaults.showVillageMarkers),
                bool(properties, "show_caravan_markers", defaults.showCaravanMarkers),
                bool(properties, "show_route_lines", defaults.showRouteLines),
                bool(properties, "persistent_map_cache", defaults.persistentMapCache),
                enumValue(properties, "map_quality", defaults.mapQuality, MapQuality.class),
                integer(properties, "map_cache_max_size_mb", defaults.mapCacheMaxSizeMb, 32, 2048),
                integer(properties, "map_cache_retention_days", defaults.mapCacheRetentionDays, 1, 3650),
                bool(properties, "tutorial_hints", defaults.tutorialHints)
        );
        config.speechBubblesEnabled=bool(properties,"speech_bubbles_enabled",true);
        return config;
    }

    public boolean setQuestTrackerSide(boolean right) {
        boolean bottom=questTrackerPosition==HudPosition.BOTTOM_LEFT || questTrackerPosition==HudPosition.BOTTOM_RIGHT;
        HudPosition position=right?(bottom?HudPosition.BOTTOM_RIGHT:HudPosition.TOP_RIGHT)
                :(bottom?HudPosition.BOTTOM_LEFT:HudPosition.TOP_LEFT);
        try {
            saveQuestTrackerPosition(configPath(),position);
            return true;
        } catch(IOException exception) {
            VillageQuest.LOGGER.warn("Could not save quest tracker position",exception);
            return false;
        }
    }

    void saveQuestTrackerPosition(Path path,HudPosition position) throws IOException {
        Properties properties=new Properties();
        if(Files.isRegularFile(path)) try(InputStream input=Files.newInputStream(path)) {properties.load(input);}
        properties.setProperty("quest_tracker_position",position.name());
        store(path,properties);
        questTrackerPosition=position;
    }

    private static void store(Path path,Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary=Files.createTempFile(path.getParent(),"client-",".tmp");
        try {
            try(var output=Files.newOutputStream(temporary)) {properties.store(output,"Village Quest local client settings");}
            try {Files.move(temporary,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ignored) {Files.move(temporary,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }

    private static VillageQuestClientConfig defaults() {
        return new VillageQuestClientConfig(
                true, HudPosition.TOP_LEFT, 0.85f, 0.5f,
                true, true, true, 1.0f,
                false, HudPosition.TOP_RIGHT, 1.0f, 1.0f,
                true, true, true, true,
                true, MapQuality.BALANCED, 256, 30, true);
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String raw = value(properties, key, Boolean.toString(fallback));
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        warn(key, raw, fallback);
        return fallback;
    }

    private static int integer(Properties properties, String key, int fallback, int minimum, int maximum) {
        String raw = value(properties, key, Integer.toString(fallback));
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed >= minimum && parsed <= maximum) return parsed;
        } catch (NumberFormatException ignored) {
        }
        warn(key, raw, fallback);
        return fallback;
    }

    private static float decimal(Properties properties, String key, float fallback, float minimum, float maximum) {
        String raw = value(properties, key, Float.toString(fallback));
        try {
            float parsed = Float.parseFloat(raw);
            if (Float.isFinite(parsed) && parsed >= minimum && parsed <= maximum) return parsed;
        } catch (NumberFormatException ignored) {
        }
        warn(key, raw, fallback);
        return fallback;
    }

    private static <E extends Enum<E>> E enumValue(Properties properties, String key, E fallback, Class<E> type) {
        String raw = value(properties, key, fallback.name());
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warn(key, raw, fallback);
            return fallback;
        }
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void warn(String key, Object raw, Object fallback) {
        VillageQuest.LOGGER.warn("Invalid client config {} '{}'; using {}", key, raw, fallback);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(VillageQuest.MOD_ID).resolve(FILE_NAME);
    }

    private static String defaultFile() {
        return """
                # Village Quest 2.1.1 local presentation and cache settings
                # Restart Minecraft after changing this file.
                quest_tracker_enabled_by_default=true
                quest_tracker_position=TOP_LEFT
                quest_tracker_style_version=1
                quest_tracker_scale=0.85
                quest_tracker_background_opacity=0.5

                quest_available_chat_notifications=true
                caravan_event_notifications=true
                speech_bubbles_enabled=true
                quest_progress_sounds=true
                quest_progress_sound_volume=1.0

                minimap_enabled_by_default=false
                minimap_position=TOP_RIGHT
                minimap_scale=1.0
                minimap_opacity=1.0
                show_player_marker=true
                show_village_markers=true
                show_caravan_markers=true
                show_route_lines=true

                persistent_map_cache=true
                map_quality=BALANCED
                map_cache_max_size_mb=256
                map_cache_retention_days=30

                tutorial_hints=true
                """;
    }
}
