package de.quest.questmaster;

import de.quest.quest.QuestCompletionMode;

/** Keeps an accepted hand-in visible without weakening server-side validation. */
final class QuestMasterTurnInPolicy {
    record Decision(boolean visible, boolean claimAction, boolean enabled, String labelKey) {}

    private QuestMasterTurnInPolicy() {}

    static Decision decide(QuestCompletionMode completionMode, boolean ready, boolean blockedByItems) {
        if (ready) {
            boolean turnIn = completionMode == QuestCompletionMode.QUESTMASTER_TURN_IN;
            return new Decision(true, true, true, turnIn
                    ? "screen.village-quest.questmaster.action.turn_in"
                    : "screen.village-quest.questmaster.action.claim");
        }
        if (completionMode == QuestCompletionMode.QUESTMASTER_TURN_IN) {
            return new Decision(true, blockedByItems, blockedByItems, blockedByItems
                    ? "screen.village-quest.questmaster.action.turn_in"
                    : "screen.village-quest.questmaster.action.in_progress");
        }
        return new Decision(false, false, false, "");
    }
}
