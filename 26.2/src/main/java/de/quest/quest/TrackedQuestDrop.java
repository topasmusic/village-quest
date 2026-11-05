package de.quest.quest;

import java.util.UUID;

public interface TrackedQuestDrop {
    /** A persisted thrower reference excludes player-discarded items even while the thrower is offline. */
    boolean villageQuest$hasThrower();

    UUID villageQuest$getTrackedPlayerId();

    void villageQuest$setTrackedPlayerId(UUID playerId);
}
