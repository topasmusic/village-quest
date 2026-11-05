package de.quest.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** No client-supplied quest, reward, location, or inventory counts are accepted. */
public final class EncounterQuestPayloads {
    private EncounterQuestPayloads(){}
    public record View(UUID session,boolean valid,Component name,Component title,Component dialogue,
                       Component objective,Component rules,Component reward,int choices,int emeralds,int levels) implements CustomPacketPayload {
        public View {if(session==null || choices<0 || choices>7 || emeralds<0 || emeralds>64 || levels<0 || levels>10)throw new IllegalArgumentException("Encounter view");}
        public static final Type<View> ID=new Type<>(Identifier.parse("village-quest:encounter_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,v)->{
            b.writeUUID(v.session);b.writeBoolean(v.valid);
            for(var c:java.util.List.of(v.name,v.title,v.dialogue,v.objective,v.rules,v.reward))ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b,c);
            b.writeVarInt(v.choices);b.writeVarInt(v.emeralds);b.writeVarInt(v.levels);
        },b->new View(b.readUUID(),b.readBoolean(),read(b),read(b),read(b),read(b),read(b),read(b),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return ID;}
    }
    public record Action(UUID session,int action) implements CustomPacketPayload {
        public Action {if(session==null || action<0 || action>4)throw new IllegalArgumentException("Encounter action");}
        public static final Type<Action> ID=new Type<>(Identifier.parse("village-quest:encounter_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.session);b.writeVarInt(v.action);},b->new Action(b.readUUID(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return ID;}
    }
    private static Component read(RegistryFriendlyByteBuf b){return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);}
}
