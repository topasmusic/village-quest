package de.quest.shrine;

import de.quest.data.PersonalResetReceipts;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;

/** Native QuestState bridge. Callers serialize access on the server thread and validate board context. */
final class VillageIntroJournalPort implements VillageIntroDeliveryCoordinator.Port {
    interface PlayerProof { Tag get(); void set(CompoundTag tag); }
    private static final String EARNED = "prosperity.stat.earned";
    private final QuestState state;
    private final UUID actor;
    private final int index;
    private final Container inventory;
    private final HolderLookup.Provider registries;
    private final PlayerProof player;
    private final BooleanSupplier checkpoint;
    private final VillageIntroTransaction proposed;

    VillageIntroJournalPort(QuestState state, UUID actor, int index, Container inventory,
            HolderLookup.Provider registries, PlayerProof player, BooleanSupplier checkpoint,
            VillageIntroTransaction proposed) {
        this.state = state; this.actor = actor; this.index = index; this.inventory = inventory;
        this.registries = registries; this.player = player; this.checkpoint = checkpoint; this.proposed = proposed;
    }
    private PlayerQuestData data() { return state.getPlayerData(actor); }
    private VillageIntroTransaction q() { return VillageIntroTransaction.fromNbt(state.introTransaction(actor), registries).orElse(null); }
    private VillageIntroTransaction p() { return VillageIntroTransaction.fromNbt(player.get(), registries).orElse(null); }
    private boolean identity(VillageIntroTransaction tx) {
        if (tx == null || !actor.equals(tx.actor()) || index < 0 || index >= VillageBondService.historicalVillageCount(data())) return false;
        return data().validProgressGeneration() && tx.generation() == data().progressGeneration()
                && tx.village().toString().equals(data().getTradeRouteString(VillageBondService.villageKey(index, "canonical_id")))
                && tx.village().toString().equals(data().getTradeRouteString(VillageIntroRequestProgress.PREFIX + "village"))
                && tx.episode().toString().equals(data().getTradeRouteString(VillageIntroRequestProgress.PREFIX + "episode"));
    }
    public boolean valid() {
        var q = q(); var p = p();
        return q != null && q.actor().equals(actor) && q.sameTransaction(p) && (identity(q)
                || q.stage() == p.stage() && (q.stage() == VillageIntroDeliveryCoordinator.Stage.COMMITTED
                || q.stage() == VillageIntroDeliveryCoordinator.Stage.CANCELLED));
    }
    public boolean canBeginNext() {
        var q = q(); var p = p();
        return q != null && p != null && q.actor().equals(actor) && q.sameTransaction(p)
                && q.stage() == p.stage() && (q.stage() == VillageIntroDeliveryCoordinator.Stage.COMMITTED
                || q.stage() == VillageIntroDeliveryCoordinator.Stage.CANCELLED) && checkpoint.getAsBoolean();
    }
    public boolean validateNew() {
        return (state.introTransaction(actor) == null && player.get() == null || canBeginNext()) && identity(proposed)
                && (q() == null || !q().receipt().equals(proposed.receipt()))
                && proposed.stage() == VillageIntroDeliveryCoordinator.Stage.BEFORE && before(proposed);
    }
    public VillageIntroDeliveryCoordinator.Proof questProof() { var tx = q(); return tx == null ? null : new VillageIntroDeliveryCoordinator.Proof(tx.receipt(), tx.stage()); }
    public VillageIntroDeliveryCoordinator.Proof playerProof() { var tx = p(); return tx == null ? null : new VillageIntroDeliveryCoordinator.Proof(tx.receipt(), tx.stage()); }
    public void prepare(UUID receipt) {
        if (!validateNew() || !proposed.receipt().equals(receipt)
                || !state.compareIntroTransaction(actor, state.introTransaction(actor), proposed.toNbt())) throw new IllegalStateException("Intro claim changed");
        player.set(proposed.toNbt());
    }
    public boolean inventoryBefore() { return q() != null && q().plan(registries).matchesBefore(inventory); }
    public boolean inventoryAfter() { return q() != null && q().plan(registries).matchesAfter(inventory); }
    public boolean applyInventory() { return valid() && q().plan(registries).apply(inventory); }
    private int paidMask(PlayerQuestData data) {
        int mask = 0;
        for (var type : VillageIntroRequestType.values()) if (PersonalResetReceipts.has(data, "village_intro." + type.id())) mask |= type.bit();
        return mask;
    }
    private boolean before(VillageIntroTransaction tx) {
        var data = data();
        var progress = VillageIntroRequestProgress.open(data, tx.village(), tx.completionsBefore(), false);
        return progress.writable() && progress.active() == tx.type()
                && !data.hasTradeRouteFlag(VillageIntroRequestProgress.PREFIX + "finished")
                && VillageBondProgression.snapshot(data, index).level() == VillageBondLevel.KNOWN
                && (tx.type() != VillageIntroRequestType.HARVEST_HELP || progress.harvestCount() == 8)
                && data.getTradeRouteInt(VillageBondService.villageKey(index, "completions")) == tx.completionsBefore()
                && data.getTradeRouteInt(VillageIntroRequestProgress.PREFIX + "completed") == tx.completedBefore()
                && paidMask(data) == tx.paidBefore() && data.getCurrencyBalance() == tx.balanceBefore()
                && data.getPilgrimInt(EARNED) == tx.prosperityBefore();
    }
    public boolean effectsBefore() { return q() != null && before(q()); }
    public boolean effectsAfter() {
        var tx = q(); if (tx == null) return false;
        var data = data();
        var progress = VillageIntroRequestProgress.open(data, tx.village(), tx.completionsAfter(), false);
        return identity(tx) && progress.writable()
                && data.getTradeRouteInt(VillageBondService.villageKey(index, "completions")) == tx.completionsAfter()
                && data.getTradeRouteInt(VillageIntroRequestProgress.PREFIX + "completed") == tx.completedAfter()
                && data.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "active").isEmpty()
                && data.getTradeRouteInt(VillageIntroRequestProgress.PREFIX + "harvest_count") == 0
                && paidMask(data) == tx.paidAfter() && data.getCurrencyBalance() == tx.balanceAfter()
                && data.getPilgrimInt(EARNED) == tx.prosperityAfter()
                && data.getTradeRouteInt(VillageBondService.villageKey(index, "level")) == VillageBondProgression.snapshot(data, index).level().id() + 1;
    }
    public boolean applyEffects() {
        if (!valid() || !effectsBefore() || !inventoryAfter()) return false;
        var tx = q(); var original = data(); var journal = state.introTransaction(actor);
        // All mutation/validation happens on a detached native serialization copy. Publish once.
        var next = state.copyPersonalProgress(actor);
        var progress = VillageIntroRequestProgress.open(next, tx.village(), tx.completionsBefore(), false);
        if (!progress.complete(tx.type())) return false;
        next.setTradeRouteInt(VillageBondService.villageKey(index, "completions"), tx.completionsAfter());
        next.setTradeRouteInt(VillageBondService.villageKey(index, "level"), VillageBondProgression.snapshot(next, index).level().id() + 1);
        next.setCurrencyBalance(tx.balanceAfter()); next.setPilgrimInt(EARNED, tx.prosperityAfter());
        if (!effectsBefore()) return false;
        return state.commitIntroEffects(actor, original, journal, next,
                tx.withStage(VillageIntroDeliveryCoordinator.Stage.EFFECTS_COMMITTED).toNbt()) && effectsAfter();
    }
    public void questStage(VillageIntroDeliveryCoordinator.Stage stage) {
        var raw = state.introTransaction(actor); var tx = q();
        if (tx == null || !state.compareIntroTransaction(actor, raw, tx.withStage(stage).toNbt())) throw new IllegalStateException("Intro journal changed");
    }
    public void playerStage(VillageIntroDeliveryCoordinator.Stage stage) {
        var tx = p(); if (tx == null || !Objects.requireNonNull(q()).sameTransaction(tx)) throw new IllegalStateException("Intro player proof changed");
        player.set(tx.withStage(stage).toNbt());
    }
    public boolean checkpoint() { return valid() && checkpoint.getAsBoolean(); }
}
