package de.quest.client.screen;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class QuestCalendarDisplayTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void footerDisplaysTheServerSuppliedResetLineWithoutConsultingClientTime() {
        var supplied = Component.literal("Next weekly quest in 6.25 Minecraft days (Overworld).");
        var entry = new QuestMasterScreen.EntryView("weekly_main", "weekly", Component.empty(), Component.empty(),
                Component.empty(), false, Component.empty(), List.of(), List.of(supplied), List.of(),
                0, Component.empty(), false, 0, Component.empty(), false, false);
        assertEquals(supplied.getString(), QuestMasterScreen.serverResetLine(entry));
        assertNull(QuestMasterScreen.serverResetLine(null));
    }
}
