package de.quest.quest;

import java.util.UUID;

public interface TrackedQuestDrop {
    UUID villageQuest$getTrackedPlayerId();

    void villageQuest$setTrackedPlayerId(UUID playerId);
}
