package de.quest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestObjectiveRowsTest {
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
