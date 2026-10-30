package de.quest.quest.story;

/** Two personal story slots; local village stories retain their own state. */
public enum StoryLane {
    CORE, KINGDOM;

    public static StoryLane forArc(StoryArcType arc) {
        return arc == StoryArcType.LIVING_KINGDOMS ? KINGDOM : CORE;
    }
}
