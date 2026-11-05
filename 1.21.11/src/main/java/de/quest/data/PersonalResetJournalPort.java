package de.quest.data;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Owner-only native journal bridge. All callbacks execute synchronously on the server thread. */
final class PersonalResetJournalPort implements PersonalResetCoordinator.Port {
    interface PlayerProof { Tag get(); void set(CompoundTag tag); }
    interface Shared { boolean ready(); boolean detach(); boolean detached(); }
    private final QuestState state;
    private final ServerPlayer player;
    private final UUID actor;
    private final PlayerProof proof;
    private final Shared shared;
    private final BooleanSupplier checkpoint;
    private final PersonalResetTransaction proposed;
    PersonalResetJournalPort(QuestState state,ServerPlayer player,PlayerProof proof,Shared shared,
            BooleanSupplier checkpoint,PersonalResetTransaction proposed) {
        this.state=state;this.player=player;this.actor=player.getUUID();this.proof=proof;
        this.shared=shared;this.checkpoint=checkpoint;this.proposed=proposed;
    }
    private PersonalResetTransaction decode(Tag raw){return PersonalResetTransaction.fromNbt(raw,player.registryAccess()).orElse(null);}
    private PersonalResetTransaction q(){return decode(state.personalResetTransaction(actor));}
    private PersonalResetTransaction p(){return decode(proof.get());}
    public boolean valid(){var q=q();return q!=null && actor.equals(q.actor()) && q.sameTransaction(p());}
    public boolean canBeginNext(){
        var q=q();var p=p();
        return valid() && q.acknowledged() && p.acknowledged() && q.stage()==p.stage()
                && (q.stage()==PersonalResetCoordinator.Stage.COMMITTED || q.stage()==PersonalResetCoordinator.Stage.CANCELLED)
                && checkpoint.getAsBoolean();
    }
    public boolean validateNew(){
        return (state.personalResetTransaction(actor)==null && proof.get()==null || canBeginNext())
                && proposed!=null && proposed.actor().equals(actor) && proposed.stage()==PersonalResetCoordinator.Stage.BEFORE
                && (q()==null || !q().receipt().equals(proposed.receipt()))
                && PersonalResetItems.nativeStorageOnly(player) && proposed.items(player.registryAccess()).matchesBefore(player)
                && state.personalResetImage(actor).equals(proposed.before()) && shared.ready();
    }
    public PersonalResetCoordinator.Proof questProof(){var q=q();return q==null?null:new PersonalResetCoordinator.Proof(q.receipt(),q.stage());}
    public PersonalResetCoordinator.Proof playerProof(){var p=p();return p==null?null:new PersonalResetCoordinator.Proof(p.receipt(),p.stage());}
    public void prepare(UUID receipt){
        if(!validateNew() || !proposed.receipt().equals(receipt)
                || !state.comparePersonalResetTransaction(actor,state.personalResetTransaction(actor),proposed.toNbt()))
            throw new IllegalStateException("Reset intent changed");
        proof.set(proposed.toNbt());
    }
    public boolean inventoryBefore(){var q=q();return q!=null && q.items(player.registryAccess()).matchesBefore(player);}
    public boolean inventoryAfter(){var q=q();return q!=null && q.items(player.registryAccess()).matchesAfter(player);}
    public boolean personalBefore(){var q=q();return q!=null && state.personalResetImage(actor).equals(q.before());}
    public boolean personalAfter(){var q=q();return q!=null && state.personalResetImage(actor).equals(q.after());}
    public boolean applyInventory(){if(!valid() || !inventoryBefore())return false;q().items(player.registryAccess()).apply(player);return inventoryAfter();}
    public boolean applyPersonal(){
        var q=q();return valid() && inventoryAfter() && personalBefore()
                && state.commitPersonalReset(actor,q.before(),q.after(),q.toNbt(),q.withStage(PersonalResetCoordinator.Stage.PERSONAL).toNbt());
    }
    public boolean detachShared(){return valid() && personalAfter() && shared.detach();}
    public boolean sharedDetached(){return shared.detached();}
    public void questStage(PersonalResetCoordinator.Stage stage){
        var raw=state.personalResetTransaction(actor);var q=q();
        if(q==null || !state.comparePersonalResetTransaction(actor,raw,q.withStage(stage).toNbt()))
            throw new IllegalStateException("Reset journal changed");
    }
    public void playerStage(PersonalResetCoordinator.Stage stage){
        var p=p();if(p==null || !Objects.requireNonNull(q()).sameTransaction(p))throw new IllegalStateException("Reset player proof changed");
        proof.set(p.withStage(stage).toNbt());
    }
    public boolean checkpoint(){return valid() && PersonalResetItems.nativeStorageOnly(player) && checkpoint.getAsBoolean();}
    boolean acknowledge(){
        var q=q();var p=p();
        if(!valid() || q.stage()!=p.stage() || (q.stage()!=PersonalResetCoordinator.Stage.COMMITTED && q.stage()!=PersonalResetCoordinator.Stage.CANCELLED))return false;
        if(!state.comparePersonalResetTransaction(actor,q.toNbt(),q.acknowledgedImage()))return false;
        proof.set(p.acknowledgedImage());return checkpoint();
    }
}
