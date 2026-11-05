package de.quest.client.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalPreferencesPersistenceTest {
    @TempDir Path directory;
    @Test void savingPersonalOptionsPreservesCustomScaleCacheAndUnknownKeys() throws Exception {
        Path path = directory.resolve("client.properties");
        Files.writeString(path, "quest_tracker_style_version=1\nquest_tracker_scale=1.4\nmap_cache_max_size_mb=512\nfuture_option=keep\nspeech_bubbles_enabled=true\n");
        var choices = new VillageQuestClientConfig.Presentation(false, true, false, false, false, false);
        var saved = VillageQuestClientConfig.savePresentation(path, choices);
        assertEquals(choices, saved.presentation());
        var reloaded = VillageQuestClientConfig.load(path);
        assertEquals(choices, reloaded.presentation());
        assertEquals(1.4f, reloaded.questTrackerScale());
        assertEquals(512, reloaded.mapCacheMaxSizeMb());
        assertTrue(Files.readString(path).contains("future_option=keep"));
    }
}
