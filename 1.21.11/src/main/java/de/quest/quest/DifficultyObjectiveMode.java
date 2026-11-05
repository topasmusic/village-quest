package de.quest.quest;

import net.minecraft.world.Difficulty;

public enum DifficultyObjectiveMode {
    COMBAT(1),
    PEACEFUL(2);

    private final int serializedId;

    DifficultyObjectiveMode(int serializedId) {
        this.serializedId = serializedId;
    }

    public int serializedId() {
        return serializedId;
    }

    public static DifficultyObjectiveMode fromSerializedId(int value) {
        for (DifficultyObjectiveMode mode : values()) {
            if (mode.serializedId == value) {
                return mode;
            }
        }
        return null;
    }

    public static DifficultyObjectiveMode forDifficulty(Difficulty difficulty) {
        return difficulty == Difficulty.PEACEFUL ? PEACEFUL : COMBAT;
    }
}
