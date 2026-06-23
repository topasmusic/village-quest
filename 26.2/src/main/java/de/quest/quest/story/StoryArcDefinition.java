package de.quest.quest.story;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public interface StoryArcDefinition {
    StoryArcType type();

    Component title();

    int chapterCount();

    StoryChapterDefinition chapter(int chapterIndex);

    default boolean isUnlocked(ServerLevel world, UUID playerId) {
        return true;
    }

    default boolean shouldShowLockedEntry(ServerLevel world, UUID playerId) {
        return false;
    }

    default Component lockedEntryBody(ServerLevel world, UUID playerId) {
        return Component.empty();
    }
}
