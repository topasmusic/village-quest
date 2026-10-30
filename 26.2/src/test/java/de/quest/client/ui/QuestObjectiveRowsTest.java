package de.quest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestObjectiveRowsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void playerFeedbackHarvestAndAutumnCountersRemainIndependent() {
        var harvest=QuestObjectiveRows.rows(Component.translatable(
                "quest.village-quest.weekly.harvest.stage.1a",66,97,20,20));
        assertEquals(2,harvest.size());
        assertTrue(harvest.get(0).current()<harvest.get(0).target());
        assertEquals(harvest.get(1).target(),harvest.get(1).current());
        var autumn=QuestObjectiveRows.rows(Component.translatable(
                "quest.village-quest.daily.autumn.progress",13,13,4,13));
        assertEquals(2,autumn.size());
        assertEquals(autumn.get(0).target(),autumn.get(0).current());
        assertTrue(autumn.get(1).current()<autumn.get(1).target());
    }
    @Test void splitsOnlyKnownStructuredProgressWithoutParsingLocalizedText() {
        var rows = QuestObjectiveRows.separate(Component.translatable(
                "quest.village-quest.daily.wheat.progress", 3, 25, 2, 7));
        assertEquals(2, rows.size());
        var wheat = (TranslatableContents) rows.getFirst().getContents();
        var bread = (TranslatableContents) rows.getLast().getContents();
        assertEquals(3, wheat.getArgs()[1]); assertEquals(25, wheat.getArgs()[2]);
        assertEquals(2, bread.getArgs()[1]); assertEquals(7, bread.getArgs()[2]);
        Component unknown = Component.literal("Custom objective | preserve its meaning");
        assertSame(unknown, QuestObjectiveRows.separate(unknown).getFirst());
        Component malformed = Component.translatable("quest.village-quest.daily.wheat.progress", 3);
        assertSame(malformed, QuestObjectiveRows.separate(malformed).getFirst());
    }
}
