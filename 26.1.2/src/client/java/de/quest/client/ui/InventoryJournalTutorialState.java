package de.quest.client.ui;

import de.quest.VillageQuest;
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
    private static final String QUESTMASTER_BUTTON_HINT_SEEN_KEY = "journal_questmaster_button_hint_seen";
    private static boolean loaded;
    private static boolean journalHintSeen;
    private static boolean questMasterButtonHintSeen;

    private InventoryJournalTutorialState() {}

    public static void bootstrap() {
        loadIfNeeded();
    }

    public static boolean shouldShowInventoryHint() {
        loadIfNeeded();
        return !journalHintSeen;
    }

    public static void markInventoryHintSeen() {
        loadIfNeeded();
        if (journalHintSeen) {
            return;
        }
        journalHintSeen = true;
        save();
    }

    public static boolean shouldShowQuestMasterButtonHint() {
        loadIfNeeded();
        return !questMasterButtonHintSeen;
    }

    public static void markQuestMasterButtonHintSeen() {
        loadIfNeeded();
        if (questMasterButtonHintSeen) {
            return;
        }
        questMasterButtonHintSeen = true;
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
            questMasterButtonHintSeen = Boolean.parseBoolean(properties.getProperty(QUESTMASTER_BUTTON_HINT_SEEN_KEY, "false"));
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
        properties.setProperty(QUESTMASTER_BUTTON_HINT_SEEN_KEY, Boolean.toString(questMasterButtonHintSeen));

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
}
