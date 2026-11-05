package de.quest.quest;

import net.minecraft.world.Difficulty;

public final class DifficultyObjectiveState {
    private DifficultyObjectiveState() {}

    public record Transition(
            DifficultyObjectiveMode mode,
            int persistedValue,
            boolean initialized,
            boolean switched
    ) {}

    public static Transition transition(int storedValue, Difficulty difficulty) {
        DifficultyObjectiveMode desired = DifficultyObjectiveMode.forDifficulty(difficulty);
        DifficultyObjectiveMode stored = DifficultyObjectiveMode.fromSerializedId(storedValue);
        if (stored == null) {
            return new Transition(desired, desired.serializedId(), true, false);
        }
        if (stored != desired) {
            return new Transition(desired, desired.serializedId(), false, true);
        }
        return new Transition(stored, stored.serializedId(), false, false);
    }
}
