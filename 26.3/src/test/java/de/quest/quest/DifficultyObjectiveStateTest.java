package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

final class DifficultyObjectiveStateTest {
    @Test
    void legacyObjectiveInitializesFromCurrentDifficultyWithoutCallingItASwitch() {
        DifficultyObjectiveState.Transition transition = DifficultyObjectiveState.transition(0, Difficulty.PEACEFUL);

        assertEquals(DifficultyObjectiveMode.PEACEFUL, transition.mode());
        assertEquals(DifficultyObjectiveMode.PEACEFUL.serializedId(), transition.persistedValue());
        assertTrue(transition.initialized());
        assertFalse(transition.switched());
    }

    @Test
    void bothDifficultySwitchDirectionsResetOnlyWhenModeActuallyChanges() {
        DifficultyObjectiveState.Transition toPeaceful = DifficultyObjectiveState.transition(
                DifficultyObjectiveMode.COMBAT.serializedId(), Difficulty.PEACEFUL);
        DifficultyObjectiveState.Transition toCombat = DifficultyObjectiveState.transition(
                DifficultyObjectiveMode.PEACEFUL.serializedId(), Difficulty.HARD);
        DifficultyObjectiveState.Transition unchanged = DifficultyObjectiveState.transition(
                DifficultyObjectiveMode.COMBAT.serializedId(), Difficulty.EASY);

        assertTrue(toPeaceful.switched());
        assertEquals(DifficultyObjectiveMode.PEACEFUL, toPeaceful.mode());
        assertTrue(toCombat.switched());
        assertEquals(DifficultyObjectiveMode.COMBAT, toCombat.mode());
        assertFalse(unchanged.switched());
        assertFalse(unchanged.initialized());
    }

    @Test
    void malformedStoredModeIsConservativelyReinitialized() {
        DifficultyObjectiveState.Transition transition = DifficultyObjectiveState.transition(99, Difficulty.NORMAL);

        assertEquals(DifficultyObjectiveMode.COMBAT, transition.mode());
        assertTrue(transition.initialized());
        assertFalse(transition.switched());
    }
}
