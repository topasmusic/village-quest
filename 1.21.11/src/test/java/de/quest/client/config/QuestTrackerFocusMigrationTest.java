package de.quest.client.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

final class QuestTrackerFocusMigrationTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void legacyPinMigratesOnlyToOneMatchingQuestAndKeepsFilterAcrossReload() {
        var path=directory.resolve("focus.properties"); var focus=QuestTrackerFocus.load(path);
        var title=Component.translatable("quest.village-quest.story.living_kingdoms.title");
        focus.toggle("story",title); focus.toggleOnlyPinned();
        focus=QuestTrackerFocus.load(path);
        focus.reconcile(Map.of("story:core:failing_harvest",Component.literal("Other"),"story:kingdom:living_kingdoms",title));
        assertEquals("story:kingdom:living_kingdoms",focus.quest()); assertTrue(focus.onlyPinned());
        assertTrue(QuestTrackerFocus.load(path).matches("story:kingdom:living_kingdoms",Component.literal("New title")));
    }
    @Test void ambiguousLegacyStoryNeverChoosesBetweenIndependentEntries() {
        var focus=new QuestTrackerFocus(null); var title=Component.literal("Same title");
        focus.toggle("story",title); focus.toggleOnlyPinned();
        focus.reconcile(Map.of("story:core:a",title,"story:local:2:b",title));
        assertEquals("",focus.quest()); assertTrue(focus.onlyPinned());
    }
    @Test void stablePinSurvivesChapterAndLanguageChangesButNotCompletionOrAnotherQuest() {
        var path=directory.resolve("focus.properties"); var focus=QuestTrackerFocus.load(path);
        focus.toggle("story:core:failing_harvest",Component.translatable("test.chapter_1"));
        for(String title:new String[]{"Chapter 2","Kapitel 3","Capítulo 4"}) {
            focus=QuestTrackerFocus.load(path); focus.reconcile(Map.of("story:core:failing_harvest",Component.literal(title)));
            assertTrue(focus.matches("story:core:failing_harvest",Component.literal(title)));
            assertFalse(focus.matches("story:kingdom:living_kingdoms",Component.literal(title)));
        }
        focus.reconcile(Map.of("story:core:silent_forge",Component.literal("Next")));
        assertEquals("",QuestTrackerFocus.load(path).quest());
    }
}
