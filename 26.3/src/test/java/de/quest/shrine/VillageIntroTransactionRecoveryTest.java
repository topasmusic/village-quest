package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Independent document/failure model, no Minecraft server or gameplay simulation. */
final class VillageIntroTransactionRecoveryTest {
    @Test void everySaveInterruptionRetainsTheSameClaimAndNeverPaysTwice() {
        for (int fail = 1; fail <= 4; fail++) {
            var port = new Memory(); port.failAt = fail;
            var receipt = UUID.randomUUID();
            assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, new VillageIntroDeliveryCoordinator(port).execute(receipt));
            port.failAt = 0;
            var result = new VillageIntroDeliveryCoordinator(port).recover();
            assertTrue(result == VillageIntroDeliveryCoordinator.Result.COMMITTED || result == VillageIntroDeliveryCoordinator.Result.CANCELLED);
            assertEquals(receipt, port.q.receipt());
            int writes = port.payouts;
            assertEquals(result, new VillageIntroDeliveryCoordinator(port).recover());
            assertEquals(writes, port.payouts);
            if (result == VillageIntroDeliveryCoordinator.Result.COMMITTED) { assertEquals(1, port.payouts); assertTrue(port.inventoryAfter); }
            else { assertEquals(0, port.payouts); assertFalse(port.inventoryAfter); }
        }
    }
    @Test void savedAfterInventoryCompletesTheOriginalPreparedClaim() {
        var port = new Memory(); port.q = new VillageIntroDeliveryCoordinator.Proof(UUID.randomUUID(), VillageIntroDeliveryCoordinator.Stage.BEFORE);
        port.p = port.q.withStage(VillageIntroDeliveryCoordinator.Stage.AFTER); port.inventoryAfter = true;
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, new VillageIntroDeliveryCoordinator(port).recover());
        assertEquals(1, port.payouts);
    }
    @Test void committedQuestWithPlayerBeforeFinishesConsumptionWithoutAnotherPayout() {
        var port = new Memory(); port.q = new VillageIntroDeliveryCoordinator.Proof(UUID.randomUUID(), VillageIntroDeliveryCoordinator.Stage.EFFECTS_COMMITTED);
        port.p = port.q.withStage(VillageIntroDeliveryCoordinator.Stage.BEFORE); port.effectsAfter = true;
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, new VillageIntroDeliveryCoordinator(port).recover());
        assertTrue(port.inventoryAfter); assertEquals(0, port.payouts);
    }
    @Test void foreignMissingOrContradictoryProofsStayHeldWithoutErasingAnything() {
        for (int variant = 0; variant < 3; variant++) {
            var port = new Memory(); port.q = new VillageIntroDeliveryCoordinator.Proof(UUID.randomUUID(), VillageIntroDeliveryCoordinator.Stage.BEFORE);
            port.p = variant == 0 ? null : new VillageIntroDeliveryCoordinator.Proof(variant == 1 ? UUID.randomUUID() : port.q.receipt(), VillageIntroDeliveryCoordinator.Stage.AFTER);
            var original = port.q;
            assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, new VillageIntroDeliveryCoordinator(port).recover());
            assertEquals(original, port.q); assertEquals(0, port.payouts); assertFalse(port.inventoryAfter);
        }
    }
    @Test void effectsExceptionKeepsConsumedInventoryAndPreparedReceiptForRetry() {
        var port = new Memory(); port.throwEffects = true;
        assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, new VillageIntroDeliveryCoordinator(port).execute(UUID.randomUUID()));
        assertTrue(port.inventoryAfter); assertEquals(0, port.payouts);
        port.throwEffects = false;
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, new VillageIntroDeliveryCoordinator(port).recover());
        assertEquals(1, port.payouts);
    }
    private static final class Memory implements VillageIntroDeliveryCoordinator.Port {
        VillageIntroDeliveryCoordinator.Proof q, p;
        boolean inventoryAfter, effectsAfter, throwEffects;
        int checkpoints, failAt, payouts;
        public VillageIntroDeliveryCoordinator.Proof questProof() { return q; }
        public VillageIntroDeliveryCoordinator.Proof playerProof() { return p; }
        public boolean valid() { return true; }
        public boolean validateNew() { return q == null && p == null; }
        public void prepare(UUID receipt) { q = new VillageIntroDeliveryCoordinator.Proof(receipt, VillageIntroDeliveryCoordinator.Stage.BEFORE); p = q; }
        public boolean inventoryBefore() { return !inventoryAfter; }
        public boolean inventoryAfter() { return inventoryAfter; }
        public boolean effectsBefore() { return !effectsAfter; }
        public boolean effectsAfter() { return effectsAfter; }
        public boolean applyInventory() { if (inventoryAfter) return false; inventoryAfter = true; return true; }
        public boolean applyEffects() { if (throwEffects) throw new IllegalStateException("injected before atomic effects"); if (effectsAfter) return false; effectsAfter = true; payouts++; return true; }
        public void questStage(VillageIntroDeliveryCoordinator.Stage stage) { q = q.withStage(stage); }
        public void playerStage(VillageIntroDeliveryCoordinator.Stage stage) { p = p.withStage(stage); }
        public boolean checkpoint() { return ++checkpoints != failAt; }
    }
}
