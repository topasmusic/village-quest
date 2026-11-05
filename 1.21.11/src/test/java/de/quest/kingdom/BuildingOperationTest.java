package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class BuildingOperationTest {
    @Test void firstInvalidBuildingHasNoGraceOrEarnedCredit() {
        var operation = new BuildingOperation();
        operation.observe(BuildingScanner.Status.INVALID, 100);
        assertFalse(operation.earned()); assertFalse(operation.operational(100));
        assertEquals(BuildingOperation.Phase.UNCONFIRMED, operation.phase());
    }

    @Test void damagePausesAfterThirtyObservedSecondsAndRepairRetainsEarnedCredit() {
        var operation = new BuildingOperation();
        operation.observe(BuildingScanner.Status.VALID, 0);
        operation.observe(BuildingScanner.Status.INVALID, 40);
        for (long tick = 80; tick <= 600; tick += 40) operation.observe(BuildingScanner.Status.INVALID, tick);
        assertTrue(operation.operational(600)); assertTrue(operation.earned());
        operation.observe(BuildingScanner.Status.INVALID, 640);
        assertEquals(BuildingOperation.Phase.PAUSED, operation.phase()); assertFalse(operation.operational(640));
        operation.observe(BuildingScanner.Status.VALID, 680);
        assertTrue(operation.operational(680)); assertTrue(operation.earned());
    }

    @Test void unknownAndOfflineGapDoNotSpendGraceOrPermitNewSpawnsFromStaleEvidence() {
        var operation = new BuildingOperation();
        operation.observe(BuildingScanner.Status.VALID, 0);
        assertTrue(operation.maySpawn(0)); assertFalse(operation.maySpawn(41));
        operation.observe(BuildingScanner.Status.INVALID, 40);
        operation.observe(BuildingScanner.Status.INVALID, 80);
        assertEquals(40, operation.invalidTicks());
        operation.observe(BuildingScanner.Status.UNKNOWN, 120);
        assertFalse(operation.operational(120)); assertFalse(operation.maySpawn(120));
        operation.observe(BuildingScanner.Status.INVALID, 10000);
        assertEquals(40, operation.invalidTicks());
        operation.observe(BuildingScanner.Status.INVALID, 10040);
        assertEquals(80, operation.invalidTicks());
    }

    @Test void reloadKeepsProgressButDemandsFreshLoadedReportAndDoesNotCreditOfflineTime() {
        var operation = new BuildingOperation();
        operation.observe(BuildingScanner.Status.VALID, 0);
        operation.observe(BuildingScanner.Status.INVALID, 40);
        operation.observe(BuildingScanner.Status.INVALID, 80);
        var tag = operation.toNbt(); tag.putString("extension", "keep");
        var restored = BuildingOperation.fromNbt(tag);
        assertTrue(restored.earned()); assertEquals(40, restored.invalidTicks());
        assertFalse(restored.operational(90000)); assertFalse(restored.maySpawn(90000));
        restored.observe(BuildingScanner.Status.INVALID, 90000);
        assertEquals(40, restored.invalidTicks());
        assertEquals("keep", restored.toNbt().getStringOr("extension", ""));
    }

    @Test void futureOperationDataIsPreservedAndCannotBeOperated() {
        var tag = new CompoundTag(); tag.putInt("schema", 20); tag.putString("phase", "FUTURE");
        var operation = BuildingOperation.fromNbt(tag);
        operation.observe(BuildingScanner.Status.VALID, 40);
        assertFalse(operation.operational(40)); assertFalse(operation.maySpawn(40));
        assertEquals(tag, operation.toNbt());
    }
}
