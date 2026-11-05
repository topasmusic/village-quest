package de.quest.client.ui;

import de.quest.VillageQuest;
import de.quest.client.config.VillageQuestClientConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class InventoryJournalTutorialState {
    private static final String FILE_NAME = "village-quest-client.properties";
    private static final String JOURNAL_HINT_SEEN_KEY = "inventory_journal_hint_seen";
    private static final String JOURNAL_HINT_VERSION_KEY = "inventory_journal_hint_version";
    private static final String QUESTMASTER_BUTTON_HINT_SEEN_KEY = "journal_questmaster_button_hint_seen";
    private static final String ATLAS_DRAG_HINT_SEEN_KEY = "journal_atlas_drag_hint_seen";
    private static final String ATLAS_INTRO_SEEN_KEY = "journal_atlas_intro_seen";
    private static final int CURRENT_JOURNAL_HINT_VERSION = 2;
    private static boolean loaded;
    private static boolean journalHintSeen;
    private static int journalHintVersion;
    private static boolean questMasterButtonHintSeen;
    private static boolean atlasDragHintSeen;
    private static boolean atlasIntroSeen;

    private InventoryJournalTutorialState() {}

    public static void bootstrap() {
        loadIfNeeded();
    }

    public static boolean shouldShowInventoryHint() {
        loadIfNeeded();
        return VillageQuestClientConfig.get().tutorialHints()
                && (!journalHintSeen || journalHintVersion < CURRENT_JOURNAL_HINT_VERSION);
    }

    public static void markInventoryHintSeen() {
        loadIfNeeded();
        if (journalHintSeen && journalHintVersion >= CURRENT_JOURNAL_HINT_VERSION) {
            return;
        }
        journalHintSeen = true;
        journalHintVersion = CURRENT_JOURNAL_HINT_VERSION;
        save();
    }

    public static boolean shouldShowQuestMasterButtonHint() {
        loadIfNeeded();
        return VillageQuestClientConfig.get().tutorialHints() && !questMasterButtonHintSeen;
    }

    public static void markQuestMasterButtonHintSeen() {
        loadIfNeeded();
        if (questMasterButtonHintSeen) {
            return;
        }
        questMasterButtonHintSeen = true;
        save();
    }

    public static boolean shouldShowAtlasDragHint() {
        loadIfNeeded();
        return VillageQuestClientConfig.get().tutorialHints() && !atlasDragHintSeen;
    }

    public static void markAtlasDragHintSeen() {
        loadIfNeeded();
        if (atlasDragHintSeen) {
            return;
        }
        atlasDragHintSeen = true;
        save();
    }

    public static boolean shouldShowAtlasIntro() {
        loadIfNeeded();
        return VillageQuestClientConfig.get().tutorialHints() && !atlasIntroSeen;
    }

    public static void markAtlasIntroSeen() {
        loadIfNeeded();
        if (atlasIntroSeen) {
            return;
        }
        atlasIntroSeen = true;
        save();
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path configPath = configPath();
        if (!Files.isRegularFile(configPath)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
            journalHintSeen = Boolean.parseBoolean(properties.getProperty(JOURNAL_HINT_SEEN_KEY, "false"));
            journalHintVersion = parseHintVersion(
                    properties.getProperty(JOURNAL_HINT_VERSION_KEY),
                    journalHintSeen ? 1 : 0
            );
            questMasterButtonHintSeen = Boolean.parseBoolean(properties.getProperty(QUESTMASTER_BUTTON_HINT_SEEN_KEY, "false"));
            atlasDragHintSeen = Boolean.parseBoolean(properties.getProperty(ATLAS_DRAG_HINT_SEEN_KEY, "false"));
            atlasIntroSeen = Boolean.parseBoolean(properties.getProperty(ATLAS_INTRO_SEEN_KEY, "false"));
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Failed to load Village Quest client settings from {}", configPath, exception);
        }
    }

    private static void save() {
        Path configPath = configPath();
        Properties properties = new Properties();

        if (Files.isRegularFile(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                VillageQuest.LOGGER.warn("Failed to read existing Village Quest client settings from {}", configPath, exception);
            }
        }

        properties.setProperty(JOURNAL_HINT_SEEN_KEY, Boolean.toString(journalHintSeen));
        properties.setProperty(JOURNAL_HINT_VERSION_KEY, Integer.toString(journalHintVersion));
        properties.setProperty(QUESTMASTER_BUTTON_HINT_SEEN_KEY, Boolean.toString(questMasterButtonHintSeen));
        properties.setProperty(ATLAS_DRAG_HINT_SEEN_KEY, Boolean.toString(atlasDragHintSeen));
        properties.setProperty(ATLAS_INTRO_SEEN_KEY, Boolean.toString(atlasIntroSeen));

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "Village Quest client settings");
            }
        } catch (IOException exception) {
            VillageQuest.LOGGER.warn("Failed to save Village Quest client settings to {}", configPath, exception);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static int parseHintVersion(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
