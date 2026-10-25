package de.quest.client.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

final class QuestTrackerFocusTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void pinAndFilterSurviveReloadWithoutTouchingAnotherWorldOrPlayer() {
        var file=directory.resolve("world-a/player-a.properties");
        var first=QuestTrackerFocus.load(file);
        var title=Component.translatable("quest.village-quest.daily.honey.title");
        first.toggle("daily",title); first.toggleOnlyPinned();
        var restored=QuestTrackerFocus.load(file);
        assertTrue(restored.matches("daily",title.copy())); assertTrue(restored.onlyPinned());
        assertEquals("",QuestTrackerFocus.load(directory.resolve("world-b/player-a.properties")).quest());
        assertEquals("",QuestTrackerFocus.load(directory.resolve("world-a/player-b.properties")).quest());
        assertNotEquals(QuestTrackerFocus.digest("world-a:player-a"),QuestTrackerFocus.digest("world-b:player-a"));
        assertNotEquals(QuestTrackerFocus.digest("world-a:player-a"),QuestTrackerFocus.digest("world-a:player-b"));
        restored.toggle("daily",title); assertEquals("",QuestTrackerFocus.load(file).quest());
    }

    @Test void replacementCompletionAndCancellationClearAnObsoletePinButProgressDoesNot() {
        var focus=QuestTrackerFocus.load(directory.resolve("focus.properties"));
        var title=Component.translatable("test.title","A");
        focus.toggle("story",title);
        focus.reconcile(Map.of("story",title.copy(),"daily",Component.literal("Other")));
        assertEquals("story",focus.quest());
        focus.reconcile(Map.of("story",Component.translatable("test.title","B")));
        assertEquals("",focus.quest());
        focus.toggle("daily",title); focus.reconcile(Map.of()); assertEquals("",focus.quest());
        assertEquals("",QuestTrackerFocus.load(directory.resolve("focus.properties")).quest());
    }

    @Test void unknownQuestIdsCannotReplaceThePinAndTitleArgumentsRemainSignificant() {
        var focus=new QuestTrackerFocus(null);
        focus.toggle("weekly",Component.translatable("test.title",1));
        focus.toggle("daily_reset",Component.literal("Not an active task"));
        assertTrue(focus.matches("weekly",Component.translatable("test.title",1)));
        assertFalse(focus.matches("weekly",Component.translatable("test.title",2)));
        assertFalse(focus.matches("daily",Component.translatable("test.title",1)));
    }
}
