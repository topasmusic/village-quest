package de.quest.quest.story;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.UUID;

public interface StoryArcDefinition {
    StoryArcType type();

    Text title();

    int chapterCount();

    StoryChapterDefinition chapter(int chapterIndex);

    default boolean isUnlocked(ServerWorld world, UUID playerId) {
        return true;
    }
}
