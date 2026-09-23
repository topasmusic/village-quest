package de.quest.questmaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.QuestCompletionMode;
import org.junit.jupiter.api.Test;

final class QuestMasterTurnInPolicyTest {
    @Test
    void missingItemsKeepServerValidatedTurnInVisibleWithoutMarkingQuestReady() {
        boolean ready = false;
        QuestMasterTurnInPolicy.Decision decision = QuestMasterTurnInPolicy.decide(
                QuestCompletionMode.QUESTMASTER_TURN_IN,
                ready,
                true
        );

        assertFalse(ready);
        assertTrue(decision.visible());
        assertTrue(decision.claimAction());
        assertTrue(decision.enabled());
        assertEquals("screen.village-quest.questmaster.action.turn_in", decision.labelKey());
    }

    @Test
    void unfinishedObjectivesShowDisabledInProgressAction() {
        QuestMasterTurnInPolicy.Decision decision = QuestMasterTurnInPolicy.decide(
                QuestCompletionMode.QUESTMASTER_TURN_IN,
                false,
                false
        );

        assertTrue(decision.visible());
        assertFalse(decision.claimAction());
        assertFalse(decision.enabled());
        assertEquals("screen.village-quest.questmaster.action.in_progress", decision.labelKey());
    }

    @Test
    void completedTurnInAndAutomaticRewardsUseDifferentLabels() {
        QuestMasterTurnInPolicy.Decision turnIn = QuestMasterTurnInPolicy.decide(
                QuestCompletionMode.QUESTMASTER_TURN_IN,
                true,
                false
        );
        QuestMasterTurnInPolicy.Decision automatic = QuestMasterTurnInPolicy.decide(
                QuestCompletionMode.AUTOMATIC,
                true,
                false
        );

        assertEquals("screen.village-quest.questmaster.action.turn_in", turnIn.labelKey());
        assertEquals("screen.village-quest.questmaster.action.claim", automatic.labelKey());
    }
}
