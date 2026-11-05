package de.quest.shrine;

import java.util.Objects;
import java.util.UUID;

/** Serialized delivery protocol. A native Port must validate the entire frozen transaction identity. */
final class VillageIntroDeliveryCoordinator {
    enum Stage { BEFORE, AFTER, EFFECTS_COMMITTED, COMMITTED, CANCELLED }
    enum Result { REJECTED, HELD, COMMITTED, CANCELLED }
    record Proof(UUID receipt, Stage stage) {
        Proof { Objects.requireNonNull(receipt); Objects.requireNonNull(stage); }
        Proof withStage(Stage stage) { return new Proof(receipt, stage); }
    }
    interface Port {
        /** Reject unknown schema, different actor/generation/episode/plan or any unprovable document. */
        boolean valid();
        boolean validateNew();
        default boolean canBeginNext() { return false; }
        Proof questProof();
        Proof playerProof();
        /** Reserve only; install matching BEFORE documents, without inventory or effects mutation. */
        void prepare(UUID receipt);
        boolean inventoryBefore();
        boolean inventoryAfter();
        boolean effectsBefore();
        boolean effectsAfter();
        boolean applyInventory();
        /** Apply exact prevalidated Q postimages together, without externally visible callbacks or partial writes. */
        boolean applyEffects();
        void questStage(Stage stage);
        void playerStage(Stage stage);
        /** Write and read back BOTH actual documents. A void save or setDirty is insufficient. */
        boolean checkpoint();
    }
    private final Port port;
    VillageIntroDeliveryCoordinator(Port port) { this.port = Objects.requireNonNull(port); }
    Result execute(UUID receipt) {
        if (receipt == null) return Result.REJECTED;
        try {
            if (port.questProof() != null) {
                if (port.questProof().receipt().equals(receipt)) return recover();
                if (!port.canBeginNext()) return Result.HELD;
            }
            if (!port.validateNew()) return Result.REJECTED;
            port.prepare(receipt);
            if (!matching() || !port.inventoryBefore() || !port.effectsBefore() || !port.checkpoint()) return Result.HELD;
            if (!port.applyInventory()) return Result.HELD;
            port.playerStage(Stage.AFTER);
            // Inventory-AFTER must be verified before Q effects are touched.
            if (!port.inventoryAfter() || !port.checkpoint()) return Result.HELD;
            return finishEffects();
        } catch (RuntimeException uncertain) { return Result.HELD; }
    }
    Result recover() {
        try {
            if (!matching()) return Result.HELD;
            var q = port.questProof().stage(); var p = port.playerProof().stage();
            // Two matching terminal documents already acknowledge the result. Later legitimate
            // inventory/balance changes must not replay the old delta or turn it into a new claim.
            if (q == p && (q == Stage.COMMITTED || q == Stage.CANCELLED))
                return port.checkpoint() ? (q == Stage.COMMITTED ? Result.COMMITTED : Result.CANCELLED) : Result.HELD;
            if (q == Stage.CANCELLED || p == Stage.CANCELLED) {
                if ((q != Stage.BEFORE && q != Stage.CANCELLED) || (p != Stage.BEFORE && p != Stage.CANCELLED)
                        || !port.inventoryBefore() || !port.effectsBefore()) return Result.HELD;
                return terminal(Stage.CANCELLED);
            }
            if (q == Stage.BEFORE) {
                if (!port.effectsBefore()) return Result.HELD;
                if (p == Stage.BEFORE && port.inventoryBefore()) return terminal(Stage.CANCELLED);
                if ((p == Stage.AFTER || p == Stage.COMMITTED) && port.inventoryAfter()) {
                    if (!port.checkpoint()) return Result.HELD;
                    return finishEffects();
                }
                return Result.HELD;
            }
            if (q != Stage.EFFECTS_COMMITTED && q != Stage.COMMITTED || !port.effectsAfter()) return Result.HELD;
            if (p == Stage.BEFORE) {
                if (!port.inventoryBefore() || !port.checkpoint() || !port.applyInventory()) return Result.HELD;
                port.playerStage(Stage.AFTER);
                if (!port.inventoryAfter() || !port.checkpoint()) return Result.HELD;
            } else if ((p != Stage.AFTER && p != Stage.COMMITTED) || !port.inventoryAfter()) return Result.HELD;
            return terminal(Stage.COMMITTED);
        } catch (RuntimeException uncertain) { return Result.HELD; }
    }
    private boolean matching() {
        var q = port.questProof(); var p = port.playerProof();
        return port.valid() && q != null && p != null && q.receipt().equals(p.receipt());
    }
    private Result finishEffects() {
        if (!matching() || !port.inventoryAfter() || !port.effectsBefore() || !port.applyEffects()) return Result.HELD;
        port.questStage(Stage.EFFECTS_COMMITTED);
        if (!port.effectsAfter() || !port.checkpoint()) return Result.HELD;
        return terminal(Stage.COMMITTED);
    }
    private Result terminal(Stage stage) {
        port.questStage(stage); port.playerStage(stage);
        if (!port.checkpoint()) return Result.HELD;
        return stage == Stage.COMMITTED ? Result.COMMITTED : Result.CANCELLED;
    }
}
