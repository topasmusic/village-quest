package de.quest.data;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

/** One bounded, frozen owner-only reset intent. Retained unknown images are never guessed into a reset. */
final class PersonalResetTransaction {
    private final CompoundTag image;
    private PersonalResetTransaction(CompoundTag image){this.image=image.copy();}
    static Optional<PersonalResetTransaction> prepare(UUID receipt,UUID actor,QuestState state,
            PersonalResetItems.Plan items,HolderLookup.Provider registries) {
        if(receipt==null || actor==null || state==null || items==null)return Optional.empty();
        try {
            var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("receipt",receipt.toString());tag.putString("actor",actor.toString());
            tag.putInt("generation",state.getPlayerData(actor).progressGeneration());tag.putString("stage",PersonalResetCoordinator.Stage.BEFORE.name());
            tag.put("before",state.personalResetImage(actor));
            tag.put("after",QuestState.personalResetImage(actor,state.getPlayerData(actor).personalRestart()));
            tag.put("items",items.toNbt(registries));return fromNbt(tag,registries);
        } catch(RuntimeException invalid){return Optional.empty();}
    }
    static Optional<PersonalResetTransaction> fromNbt(Tag raw,HolderLookup.Provider registries) {
        if(!(raw instanceof CompoundTag tag) || registries==null || tag.sizeInBytes()>2_097_152L
                || !(tag.get("schema") instanceof IntTag) || tag.getIntOr("schema",0)!=1
                || !(tag.get("generation") instanceof IntTag) || tag.getIntOr("generation",0)<1
                || !(tag.get("before") instanceof CompoundTag before) || !(tag.get("after") instanceof CompoundTag after))return Optional.empty();
        try {
            var actor=UUID.fromString(tag.getStringOr("actor",""));var receipt=UUID.fromString(tag.getStringOr("receipt",""));
            if(actor.equals(new UUID(0,0)) || receipt.equals(new UUID(0,0)))return Optional.empty();
            var stage=PersonalResetCoordinator.Stage.valueOf(tag.getStringOr("stage",""));
            if(tag.contains("acknowledged") && (!(tag.get("acknowledged") instanceof ByteTag ack) || ack.byteValue()!=1
                    || stage!=PersonalResetCoordinator.Stage.COMMITTED && stage!=PersonalResetCoordinator.Stage.CANCELLED))return Optional.empty();
            var inventory=PersonalResetItems.fromNbt(tag.get("items"),registries);
            if(inventory.isEmpty() || !actor.toString().equals(inventory.get().toNbt(registries).getStringOr("owner","")))return Optional.empty();
            var isolated=QuestState.fromNbt(before);
            if(!isolated.getPlayersView().keySet().equals(java.util.Set.of(actor)) || !before.equals(QuestState.toNbt(isolated)))return Optional.empty();
            var old=isolated.getPlayerData(actor);
            if(!old.validProgressGeneration() || old.progressGeneration()!=tag.getIntOr("generation",0)
                    || !after.equals(QuestState.personalResetImage(actor,old.personalRestart())))return Optional.empty();
            return Optional.of(new PersonalResetTransaction(tag));
        } catch(RuntimeException invalid){return Optional.empty();}
    }
    UUID actor(){return UUID.fromString(image.getStringOr("actor",""));}
    UUID receipt(){return UUID.fromString(image.getStringOr("receipt",""));}
    int generation(){return image.getIntOr("generation",0);}
    PersonalResetCoordinator.Stage stage(){return PersonalResetCoordinator.Stage.valueOf(image.getStringOr("stage",""));}
    CompoundTag before(){return image.getCompoundOrEmpty("before").copy();}
    CompoundTag after(){return image.getCompoundOrEmpty("after").copy();}
    PersonalResetItems.Plan items(HolderLookup.Provider registries){return PersonalResetItems.fromNbt(image.get("items"),registries).orElseThrow();}
    CompoundTag toNbt(){return image.copy();}
    boolean acknowledged(){return image.getBooleanOr("acknowledged",false);}
    CompoundTag acknowledgedImage(){
        if(stage()!=PersonalResetCoordinator.Stage.COMMITTED && stage()!=PersonalResetCoordinator.Stage.CANCELLED)throw new IllegalStateException("Nonterminal reset intent");
        var tag=image.copy();tag.putBoolean("acknowledged",true);return tag;
    }
    PersonalResetTransaction withStage(PersonalResetCoordinator.Stage stage){
        var tag=image.copy();tag.remove("acknowledged");tag.putString("stage",stage.name());return new PersonalResetTransaction(tag);
    }
    boolean sameTransaction(PersonalResetTransaction other){
        if(other==null)return false;var a=image.copy();var b=other.image.copy();
        for(String key:java.util.List.of("stage","acknowledged")){a.remove(key);b.remove(key);}return a.equals(b);
    }
}
