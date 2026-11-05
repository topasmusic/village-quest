package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class QuestSoundFeedbackLifecycleTest {
    private static final UUID PLAYER = UUID.fromString("8d639b9d-4027-4d49-a208-14094e9f0eb7");

    @AfterEach
    void cleanup() { QuestSoundFeedback.resetRuntimeState(); }

    @Test
    void disconnectAndRuntimeResetCannotLeaveFeedbackEntriesBehind() throws Exception {
        feedbackMap().put(PLAYER, null);
        assertEquals(1, QuestSoundFeedback.trackedPlayerCount());
        QuestSoundFeedback.handleDisconnect(PLAYER);
        assertEquals(0, QuestSoundFeedback.trackedPlayerCount());

        feedbackMap().put(PLAYER, null);
        QuestSoundFeedback.resetRuntimeState();
        assertEquals(0, QuestSoundFeedback.trackedPlayerCount());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> feedbackMap() throws Exception {
        Field field = QuestSoundFeedback.class.getDeclaredField("LAST_FEEDBACK");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }
}
