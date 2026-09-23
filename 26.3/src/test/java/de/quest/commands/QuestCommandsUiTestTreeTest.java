package de.quest.commands;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class QuestCommandsUiTestTreeTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noticeJourneyPreviewsAreChildrenOfUiTest() {
        var uiTest = QuestCommands.buildUiTestCommand().build();
        var journey = uiTest.getChild("noticejourney");
        assertNotNull(journey);
        for (String state : new String[] {
                "welcome", "greeting", "available", "active", "choice", "ready", "remembered",
                "paused", "away", "forge", "pasture", "apiary", "archive", "connected"
        }) {
            assertNotNull(journey.getChild(state), state);
        }
        assertNotNull(uiTest.getChild("noticeboard").getChild("known"));
        assertNull(uiTest.getChild("noticeboard").getChild("noticejourney"));
    }
}
