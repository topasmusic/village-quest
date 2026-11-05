package de.quest.data;

import java.util.Objects;
import java.util.UUID;

/** Reset intent and each document boundary must be durable before the next destructive phase. */
final class PersonalResetCoordinator {
    enum Stage { BEFORE, AFTER, PERSONAL, SHARED, COMMITTED, CANCELLED }
    enum Result { REJECTED, HELD, COMMITTED, CANCELLED }
    record Proof(UUID receipt,Stage stage) {
        Proof { Objects.requireNonNull(receipt); Objects.requireNonNull(stage); }
    }
    interface Port {
        /** Includes actor, generation, schema, complete immutable images and financial guards. */
        boolean valid();
        boolean validateNew();
        default boolean canBeginNext() { return false; }
        Proof questProof();
        Proof playerProof();
        void prepare(UUID receipt);
        boolean inventoryBefore();
        boolean inventoryAfter();
        boolean personalBefore();
        boolean personalAfter();
        boolean applyInventory();
        /** Atomic publication of the frozen personal image and PERSONAL journal stage. */
        boolean applyPersonal();
        /** Owner-scoped, idempotent steps. Never restore complete old shared documents. */
        boolean detachShared();
        boolean sharedDetached();
        void questStage(Stage stage);
        void playerStage(Stage stage);
        /** Save and independently read back player, journal, quest, guild and network documents. */
        boolean checkpoint();
    }
    private final Port port;
    PersonalResetCoordinator(Port port) { this.port=Objects.requireNonNull(port); }
    Result execute(UUID receipt) {
        if(receipt==null)return Result.REJECTED;
        try {
            if(port.questProof()!=null) {
                if(receipt.equals(port.questProof().receipt()))return recover();
                if(!port.canBeginNext())return Result.HELD;
            }
            if(!port.validateNew())return Result.REJECTED;
            port.prepare(receipt);
            if(!matching() || !port.inventoryBefore() || !port.personalBefore() || !port.checkpoint())return Result.HELD;
            if(!port.applyInventory())return Result.HELD;
            port.playerStage(Stage.AFTER);
            if(!port.inventoryAfter() || !port.checkpoint())return Result.HELD;
            return finishPersonal();
        } catch(RuntimeException uncertain) { return Result.HELD; }
    }
    Result recover() {
        try {
            if(!matching())return Result.HELD;
            var q=port.questProof().stage();var p=port.playerProof().stage();
            if(q==p && (q==Stage.COMMITTED || q==Stage.CANCELLED))
                return port.checkpoint() ? (q==Stage.COMMITTED?Result.COMMITTED:Result.CANCELLED):Result.HELD;
            if(q==Stage.CANCELLED || p==Stage.CANCELLED) {
                if((q!=Stage.BEFORE && q!=Stage.CANCELLED) || (p!=Stage.BEFORE && p!=Stage.CANCELLED)
                        || !port.inventoryBefore() || !port.personalBefore())return Result.HELD;
                return terminal(Stage.CANCELLED);
            }
            if(q==Stage.BEFORE) {
                if(!port.personalBefore())return Result.HELD;
                if(p==Stage.BEFORE && port.inventoryBefore())return terminal(Stage.CANCELLED);
                if(p!=Stage.AFTER || !port.inventoryAfter() || !port.checkpoint())return Result.HELD;
                return finishPersonal();
            }
            if((q!=Stage.PERSONAL && q!=Stage.SHARED && q!=Stage.COMMITTED) || !port.personalAfter())return Result.HELD;
            if(p==Stage.BEFORE) {
                if(!port.inventoryBefore() || !port.checkpoint() || !port.applyInventory())return Result.HELD;
                port.playerStage(Stage.AFTER);
                if(!port.inventoryAfter() || !port.checkpoint())return Result.HELD;
            } else if((p!=Stage.AFTER && p!=Stage.COMMITTED) || !port.inventoryAfter())return Result.HELD;
            return finishShared();
        } catch(RuntimeException uncertain) { return Result.HELD; }
    }
    private boolean matching() {
        return port.valid() && port.questProof()!=null && port.playerProof()!=null
                && port.questProof().receipt().equals(port.playerProof().receipt());
    }
    private Result finishPersonal() {
        if(!matching() || !port.inventoryAfter() || !port.personalBefore() || !port.applyPersonal())return Result.HELD;
        port.questStage(Stage.PERSONAL);
        if(!port.personalAfter() || !port.checkpoint())return Result.HELD;
        return finishShared();
    }
    private Result finishShared() {
        if(!matching() || !port.inventoryAfter() || !port.personalAfter())return Result.HELD;
        if(port.questProof().stage()==Stage.PERSONAL || !port.sharedDetached()) {
            if(!port.detachShared() || !port.sharedDetached())return Result.HELD;
            port.questStage(Stage.SHARED);
            if(!port.checkpoint())return Result.HELD;
        }
        return terminal(Stage.COMMITTED);
    }
    private Result terminal(Stage stage) {
        port.questStage(stage);port.playerStage(stage);
        if(!port.checkpoint())return Result.HELD;
        return stage==Stage.COMMITTED?Result.COMMITTED:Result.CANCELLED;
    }
}
