package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class NpcSpeechPayloadTest {
    @Test void translatedSpeechRoundTripsBodyDimensionScopeAndSequence() {
        var payload=new NpcSpeechPayload("minecraft:overworld",UUID.randomUUID(),7,42,160,NpcSpeechPayload.Scope.PRIVATE,
                Component.literal("Greta"),Component.translatable("message.village-quest.kingdom.arrival","Greta","Talheim"));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { NpcSpeechPayload.CODEC.encode(buf,payload); assertEquals(payload,NpcSpeechPayload.CODEC.decode(buf)); } finally { buf.release(); }
    }
    @Test void invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new NpcSpeechPayload("bad dimension",UUID.randomUUID(),1,0,100,NpcSpeechPayload.Scope.PRIVATE,Component.literal("Mira"),Component.literal("Hi")));
        assertThrows(IllegalArgumentException.class,() -> new NpcSpeechPayload("minecraft:overworld",UUID.randomUUID(),1,0,241,NpcSpeechPayload.Scope.PRIVATE,Component.literal("Mira"),Component.literal("Hi")));
        assertThrows(IllegalArgumentException.class,() -> new NpcSpeechPayload("minecraft:overworld",UUID.randomUUID(),1,-1,100,NpcSpeechPayload.Scope.PRIVATE,Component.literal("Mira"),Component.literal("Hi")));
    }
}
