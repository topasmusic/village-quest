package de.quest.map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MapWorkBudgetTest {
    @Test void screenAndHudShareFiniteCountersAndUploadsAreThrottled() {
        var budget = new MapWorkBudget();
        budget.begin(10);
        for (int i = 0; i < MapWorkBudget.SAMPLES; i++) assertTrue(budget.sample());
        assertFalse(budget.sample());
        budget.begin(10);
        assertFalse(budget.sample());
        assertTrue(budget.diskRead());
        assertFalse(budget.diskRead());
        for (int i = 0; i < MapWorkBudget.TILES; i++) assertTrue(budget.tile());
        assertFalse(budget.tile());
        assertTrue(budget.upload());
        assertFalse(budget.upload());
        budget.begin(11);
        assertTrue(budget.sample());
        assertFalse(budget.upload());
        budget.begin(10 + MapWorkBudget.UPLOAD_INTERVAL);
        assertTrue(budget.upload());
        budget.begin(0); // world/time reset must recover
        assertTrue(budget.upload());
    }
}
