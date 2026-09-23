package de.quest.quest;

/**
 * Defines whether meeting a quest's objectives finishes it immediately or
 * merely makes it ready for an explicit Questmaster hand-in.
 */
public enum QuestCompletionMode {
    AUTOMATIC,
    QUESTMASTER_TURN_IN
}
