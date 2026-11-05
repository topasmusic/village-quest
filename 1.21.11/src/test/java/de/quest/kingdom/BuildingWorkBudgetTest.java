package de.quest.kingdom;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
final class BuildingWorkBudgetTest {
    @Test void buildingScansActionsAndProofsShareOneTickBudgetInsteadOfResettingItOnAnotherRequest() {
        var budget = new BuildingWorkBudget(); budget.begin(100);
        assertTrue(budget.takeBlock()); assertTrue(budget.takeBlocks(250)); assertEquals(5, budget.blocks());
        budget.begin(100); assertEquals(5, budget.blocks()); assertFalse(budget.takeBlocks(6));
        assertTrue(budget.takePaths(12)); assertEquals(0, budget.paths()); assertFalse(budget.takePaths(1));
        budget.begin(99); assertEquals(5, budget.blocks()); assertEquals(0, budget.paths());
        budget.begin(101); assertEquals(256, budget.blocks()); assertEquals(12, budget.paths());
    }
    @Test void invalidReservationsCannotMintBudget() {
        var budget = new BuildingWorkBudget(); budget.begin(0);
        assertFalse(budget.takeBlocks(-1)); assertFalse(budget.takePaths(-1));
        assertEquals(256, budget.blocks()); assertEquals(12, budget.paths());
    }
}
