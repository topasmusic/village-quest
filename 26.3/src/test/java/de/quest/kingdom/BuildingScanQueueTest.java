package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class BuildingScanQueueTest {
    @Test void twentyVillagesShareGlobalBudgetAndEveryBuildingEventuallyReceivesEvidence() {
        var queue = new BuildingScanQueue();
        Map<UUID, AtomicInteger> reads = new HashMap<>();
        Map<UUID, BuildingScanner.Report> reports = new HashMap<>();
        for (int i = 0; i < 80; i++) {
            UUID id = UUID.randomUUID(); var count = new AtomicInteger(); reads.put(id, count);
            assertTrue(queue.request(id, 0, scanner(count), reports::put));
        }
        for (int tick = 0; tick < 900; tick++) {
            int before = total(reads);
            int used = queue.tick(tick);
            assertEquals(used, total(reads) - before); assertTrue(used <= 256);
        }
        assertEquals(80, reports.size()); assertEquals(80 * 2601, total(reads));
    }

    @Test void repeatedRequestsCannotRestartWorkOrBypassTwoSecondCooldown() {
        var queue = new BuildingScanQueue(); var id = UUID.randomUUID(); var reads = new AtomicInteger();
        assertTrue(queue.request(id, 0, scanner(reads), (key, report) -> {}));
        assertFalse(queue.request(id, 10, scanner(new AtomicInteger()), (key, report) -> fail()));
        for (int tick = 0; tick < 12; tick++) queue.tick(tick);
        assertEquals(2601, reads.get());
        assertFalse(queue.request(id, 39, scanner(new AtomicInteger()), (key, report) -> fail()));
        assertTrue(queue.request(id, 40, scanner(new AtomicInteger()), (key, report) -> {}));
    }

    @Test void cancellationStopsStaleScanAndBoundedCapacityRejectsExcessWork() {
        var queue = new BuildingScanQueue(); var id = UUID.randomUUID(); var reads = new AtomicInteger();
        queue.request(id, 0, scanner(reads), (key, report) -> fail("cancelled callback"));
        queue.tick(0); queue.cancel(id); queue.tick(1);
        assertEquals(256, reads.get());
        for (int i = 0; i < 128; i++) assertTrue(queue.request(UUID.randomUUID(), 50,
                scanner(new AtomicInteger()), (key, report) -> {}));
        assertFalse(queue.request(UUID.randomUUID(), 50, scanner(new AtomicInteger()), (key, report) -> {}));
    }

    private static int total(Map<UUID, AtomicInteger> reads) { return reads.values().stream().mapToInt(AtomicInteger::get).sum(); }
    private static BuildingScanner scanner(AtomicInteger reads) {
        return new BuildingScanner(new BlockPos(0, 64, 0), Direction.SOUTH, BuildingScanner.Role.COUNCIL,
                BuildingScanner.Range.BASIC, pos -> { reads.incrementAndGet();
                    return new BuildingScanner.Cell(true, true, false, false, Set.of(), null); });
    }
}
