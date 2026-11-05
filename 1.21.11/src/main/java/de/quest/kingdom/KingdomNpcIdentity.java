package de.quest.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Entity save header is a reference to canonical authority, never independent role/appearance authority. */
public final class KingdomNpcIdentity {
    public static final Codec<Tag> PAYLOAD_CODEC=Codec.PASSTHROUGH.xmap(
            value -> value.convert(NbtOps.INSTANCE).getValue(),value -> new Dynamic<>(NbtOps.INSTANCE,value));
    private final Tag retained;
    private final UUID person;
    private final long generation;
    private KingdomNpcIdentity(Tag retained,UUID person,long generation) {
        this.retained=retained.copy(); this.person=person; this.generation=generation;
    }
    public static KingdomNpcIdentity fromNbt(Tag payload) {
        if (payload==null) payload=new CompoundTag();
        if (payload instanceof CompoundTag tag && tag.get("schema") instanceof net.minecraft.nbt.IntTag
                && tag.getIntOr("schema",0)==1 && tag.get("generation") instanceof net.minecraft.nbt.LongTag) {
            try {
                UUID person=UUID.fromString(tag.getStringOr("person","")); long generation=tag.getLongOr("generation",0);
                if (!person.equals(BuildingSessionPolicy.NONE) && generation>0) return new KingdomNpcIdentity(payload,person,generation);
            } catch (IllegalArgumentException ignored) { }
        }
        return new KingdomNpcIdentity(payload,null,0);
    }
    public static KingdomNpcIdentity bound(KingdomPersonRegistry.Ticket ticket) {
        if (ticket==null || ticket.person()==null || ticket.person().equals(BuildingSessionPolicy.NONE)
                || ticket.body()==null || ticket.body().equals(BuildingSessionPolicy.NONE) || ticket.generation()<=0)
            throw new IllegalArgumentException("Invalid person ticket");
        var payload=new CompoundTag(); payload.putInt("schema",1); payload.putString("person",ticket.person().toString()); payload.putLong("generation",ticket.generation());
        return fromNbt(payload);
    }
    public Optional<KingdomPersonRegistry.Ticket> ticket(UUID actualBody) {
        return person==null || actualBody==null || actualBody.equals(BuildingSessionPolicy.NONE)?Optional.empty()
                :Optional.of(new KingdomPersonRegistry.Ticket(person,generation,actualBody));
    }
    public Tag payload() { return retained.copy(); }
    public CompoundTag toNbt() {
        if (retained instanceof CompoundTag compound) return compound.copy();
        throw new IllegalStateException("Opaque entity header must be stored through payload()");
    }
}
