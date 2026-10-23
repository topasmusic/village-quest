package de.quest.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Presentation only; no client reply and no quest or dialogue decision is encoded here. */
public record NpcSpeechPayload(String dimension,UUID body,int entity,long sequence,int ticks,Scope scope,Component name,Component line)
        implements CustomPacketPayload {
    public enum Scope { PRIVATE,NEARBY }
    public NpcSpeechPayload {
        Objects.requireNonNull(body); Objects.requireNonNull(scope); Objects.requireNonNull(name); Objects.requireNonNull(line);
        if(dimension==null || dimension.length()>128 || Identifier.tryParse(dimension)==null || entity<0 || sequence<0
                || ticks<40 || ticks>240 || name.getString().length()>96 || line.getString().isBlank() || line.getString().length()>1024)
            throw new IllegalArgumentException("Invalid NPC speech");
        name=name.copy(); line=line.copy();
    }
    @Override public Component name() { return name.copy(); }
    @Override public Component line() { return line.copy(); }
    public static final Type<NpcSpeechPayload> ID=new Type<>(Identifier.fromNamespaceAndPath("village-quest","npc_speech"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcSpeechPayload> CODEC=StreamCodec.of((buf,p) -> {
        buf.writeUtf(p.dimension,128); buf.writeUUID(p.body); buf.writeVarInt(p.entity); buf.writeVarLong(p.sequence); buf.writeVarInt(p.ticks); buf.writeEnum(p.scope);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,p.name); ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,p.line);
    },buf -> new NpcSpeechPayload(buf.readUtf(128),buf.readUUID(),buf.readVarInt(),buf.readVarLong(),buf.readVarInt(),buf.readEnum(Scope.class),
            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
