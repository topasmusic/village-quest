package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.network.NpcSpeechPayload;
import java.util.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class NpcSpeechBufferTest {
    private static NpcSpeechPayload speech(UUID body,long sequence) {
        return new NpcSpeechPayload("minecraft:overworld",body,1,sequence,100,NpcSpeechPayload.Scope.PRIVATE,Component.literal("Mira"),Component.literal("Hello"));
    }
    @Test void privateAudienceNeverLeaksAndNearbyAudienceRequiresSameWorldAndRange() {
        var actor=UUID.randomUUID(); var other=UUID.randomUUID();
        assertTrue(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.PRIVATE,actor,actor,true,64));
        assertFalse(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.PRIVATE,actor,other,true,1));
        assertFalse(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.NEARBY,null,actor,false,1));
        assertFalse(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.NEARBY,null,actor,true,1025));
        assertFalse(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.NEARBY,null,actor,true,Double.NaN));
        assertTrue(NpcSpeechBuffer.recipient(NpcSpeechPayload.Scope.NEARBY,null,other,true,1024));
    }
    @Test void staleReplaysWrongDimensionsAndExpiryCannotRenewABubble() {
        var buffer=new NpcSpeechBuffer(); var body=UUID.randomUUID();
        assertFalse(buffer.offer(speech(body,1),"minecraft:the_nether",0));
        assertTrue(buffer.offer(speech(body,2),"minecraft:overworld",0));
        assertFalse(buffer.offer(speech(body,2),"minecraft:overworld",1));
        assertFalse(buffer.offer(speech(body,1),"minecraft:overworld",2));
        assertEquals(1,buffer.active("minecraft:overworld",99).size());
        assertTrue(buffer.active("minecraft:overworld",100).isEmpty());
        assertFalse(buffer.offer(speech(body,2),"minecraft:overworld",101));
        buffer.clear(); assertTrue(buffer.offer(speech(body,1),"minecraft:overworld",0));
        assertTrue(buffer.active("minecraft:the_nether",1).isEmpty());
    }
    @Test void cacheDurationAndComponentsRemainBoundedAndDefensive() {
        var buffer=new NpcSpeechBuffer(); for(int i=0;i<80;i++) buffer.offer(speech(UUID.randomUUID(),i),"minecraft:overworld",0);
        assertEquals(16,buffer.active("minecraft:overworld",0).size());
        assertEquals(80,NpcSpeechBuffer.duration(-1)); assertEquals(240,NpcSpeechBuffer.duration(Integer.MAX_VALUE));
        var source=Component.literal("Original"); var payload=new NpcSpeechPayload("minecraft:overworld",UUID.randomUUID(),1,0,100,
                NpcSpeechPayload.Scope.NEARBY,Component.literal("Mira"),source); source.append("changed");
        assertEquals("Original",payload.line().getString()); ((net.minecraft.network.chat.MutableComponent)payload.line()).append("changed");
        assertEquals("Original",payload.line().getString());
    }
}
