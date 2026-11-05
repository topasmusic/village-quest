package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;

/** Server-thread transaction ordering. Persistence must verify all three independent documents. */
public final class MarketTradeCoordinator {
    public enum Result { COMMITTED,CANCELLED,HELD,REJECTED }
    public interface PlayerProof {
        Tag get(); void set(CompoundTag next);
        /** Read only: integrated player data can retain another UUID's completed attachment. */
        default MarketWalletJournal originalWallet(UUID owner) { return null; }
    }
    private final KingdomMarketLedger ledger;
    private final UUID actor;
    private final PlayerQuestData data;
    private final Container bag;
    private final PlayerProof player;
    private final HolderLookup.Provider registries;
    private final BooleanSupplier persist;
    private final Predicate<MarketTransferMarker> mutateWallet;
    private final java.util.function.Consumer<KingdomMarketLedger.Quote> prepared;
    public MarketTradeCoordinator(KingdomMarketLedger ledger,UUID actor,PlayerQuestData data,Container bag,PlayerProof player,
                                  HolderLookup.Provider registries,BooleanSupplier persist,Predicate<MarketTransferMarker> mutateWallet) {
        this(ledger,actor,data,bag,player,registries,persist,mutateWallet,quote -> {});
    }
    public MarketTradeCoordinator(KingdomMarketLedger ledger,UUID actor,PlayerQuestData data,Container bag,PlayerProof player,
                                  HolderLookup.Provider registries,BooleanSupplier persist,Predicate<MarketTransferMarker> mutateWallet,
                                  java.util.function.Consumer<KingdomMarketLedger.Quote> prepared) {
        this.ledger=Objects.requireNonNull(ledger); this.actor=Objects.requireNonNull(actor); this.data=Objects.requireNonNull(data);
        this.bag=Objects.requireNonNull(bag); this.player=Objects.requireNonNull(player); this.registries=Objects.requireNonNull(registries);
        this.persist=Objects.requireNonNull(persist); this.mutateWallet=Objects.requireNonNull(mutateWallet);
        this.prepared=Objects.requireNonNull(prepared);
    }
    public Result execute(KingdomMarketLedger.Quote quote,MarketInventoryPlan plan,long now) {
        if(quote==null || !actor.equals(quote.actor()) || plan==null) return Result.REJECTED;
        if(ledger.receipt(quote.id()).isPresent()) return reconcile(quote.id());
        if(!ready()) return Result.HELD;
        if(ledger.prepareNative(quote,actor,data.getCurrencyBalance(),bag,plan,registries,now).isEmpty()) return Result.REJECTED;
        var before=ledger.marker(quote.id(),MarketTransferMarker.Stage.BEFORE).orElseThrow();
        if(!data.marketWallet().begin(actor,before,data.getCurrencyBalance())) return Result.HELD;
        player.set(before.toNbt());
        try { prepared.accept(quote); } catch(RuntimeException failure) { return Result.HELD; }
        // No item or money mutation until the reservation and both BEFORE proofs are saved and read back.
        if(!checkpoint()) return Result.HELD;
        if(!plan.apply(bag)) return reconcile(quote.id());
        var after=before.withStage(MarketTransferMarker.Stage.AFTER); player.set(after.toNbt());
        try { if(!mutateWallet.test(after)) return Result.HELD; }
        catch(RuntimeException failure) { return Result.HELD; }
        return reconcile(quote.id());
    }
    public Result reconcile(UUID id) {
        var receipt=ledger.receipt(id); var wallet=data.marketWallet().marker(); var inventory=MarketTransferMarker.fromNbt(player.get());
        if(ledger.readOnly() || data.marketWallet().readOnly() || receipt.isEmpty() || !actor.equals(receipt.get().quote().actor())
                || ledger.nativePlan(id,registries).isEmpty() || wallet.isEmpty() || inventory.isEmpty() || !wallet.get().sameTransfer(inventory.get())) return Result.HELD;
        var expected=ledger.marker(id,MarketTransferMarker.Stage.BEFORE).orElseThrow();
        if(!wallet.get().sameTransfer(expected)) return Result.HELD;
        var phase=receipt.get().phase();
        var terminal=phase==KingdomMarketLedger.Phase.COMMITTED?MarketTransferMarker.Stage.COMMITTED:
                phase==KingdomMarketLedger.Phase.CANCELLED?MarketTransferMarker.Stage.CANCELLED:null;
        if(terminal!=null && wallet.get().stage()==terminal && inventory.get().stage()==terminal) {
            if(!ledger.nativeAcknowledged(id) && (!checkpoint() || !ledger.acknowledgeNative(id,actor,wallet.get().toNbt(),inventory.get().toNbt()))) return Result.HELD;
            return terminal==MarketTransferMarker.Stage.COMMITTED?Result.COMMITTED:Result.CANCELLED;
        }
        // Persist the existing images before resolving. Missing/mixed effects are never inferred or repaired here.
        if(!checkpoint()) return Result.HELD;
        var resolved=ledger.resolveNative(id,actor,data.getCurrencyBalance(),bag,wallet.get().toNbt(),inventory.get().toNbt(),registries);
        if(resolved==KingdomMarketLedger.Resolution.HELD) return Result.HELD;
        var next=resolved==KingdomMarketLedger.Resolution.COMMITTED?MarketTransferMarker.Stage.COMMITTED:MarketTransferMarker.Stage.CANCELLED;
        if(!data.marketWallet().advance(wallet.get().withStage(next))) return Result.HELD;
        player.set(inventory.get().withStage(next).toNbt());
        // Terminal markers must be verified on disk before actor/visit reuse or any journal replacement.
        if(!checkpoint() || !ledger.acknowledgeNative(id,actor,data.marketWallet().marker().orElseThrow().toNbt(),player.get())) return Result.HELD;
        return resolved==KingdomMarketLedger.Resolution.COMMITTED?Result.COMMITTED:Result.CANCELLED;
    }
    private boolean ready() {
        if(ledger.readOnly() || data.marketWallet().readOnly() || !ledger.pending(actor).isEmpty()) return false;
        var wallet=data.marketWallet().marker(); var raw=player.get();
        if(wallet.isEmpty()) return raw==null || completedForeignProof(raw);
        var inventory=MarketTransferMarker.fromNbt(raw);
        return inventory.isPresent() && wallet.get().actor().equals(actor) && wallet.get().sameTransfer(inventory.get())
                && wallet.get().stage()==inventory.get().stage()
                && (wallet.get().stage()==MarketTransferMarker.Stage.COMMITTED || wallet.get().stage()==MarketTransferMarker.Stage.CANCELLED);
    }
    private boolean completedForeignProof(Tag raw) {
        var proof=MarketTransferMarker.fromNbt(raw);
        if(proof.isEmpty() || actor.equals(proof.get().actor())) return false;
        var marker=proof.get();
        if(marker.stage()!=MarketTransferMarker.Stage.COMMITTED && marker.stage()!=MarketTransferMarker.Stage.CANCELLED) return false;
        var original=player.originalWallet(marker.actor());
        if(original==null || original.readOnly() || !ledger.pending(marker.actor()).isEmpty()) return false;
        var saved=original.marker();
        if(saved.isEmpty() || saved.get().stage()!=marker.stage() || !saved.get().sameTransfer(marker)) return false;
        // Old visits prune only acknowledged receipts. A retained receipt must still carry its native ACK.
        var receipt=ledger.receipt(marker.receipt());
        return receipt.isEmpty() || ledger.nativeAcknowledged(marker.receipt())
                && receipt.get().quote().actor().equals(marker.actor())
                && (marker.stage()==MarketTransferMarker.Stage.COMMITTED?receipt.get().phase()==KingdomMarketLedger.Phase.COMMITTED:
                    receipt.get().phase()==KingdomMarketLedger.Phase.CANCELLED);
    }
    private boolean checkpoint() {
        try { return persist.getAsBoolean(); } catch(RuntimeException failure) { return false; }
    }
}
