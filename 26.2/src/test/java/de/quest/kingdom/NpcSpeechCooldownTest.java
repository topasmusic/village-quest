package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcSpeechCooldownTest {
    @Test void clickFloodDoesNotExtendWaitAndRepeatsWaitThirtySeconds() {
        var gate=new NpcSpeechCooldown(); var npc=UUID.randomUUID();var player=UUID.randomUUID();
        assertTrue(gate.allow(npc,player,"Welcome",0,false));
        for(int tick=1;tick<160;tick++) assertFalse(gate.allow(npc,player,"Different",tick,false));
        assertTrue(gate.allow(npc,player,"Different",160,false));
        assertFalse(gate.allow(npc,player,"Different",759,false));
        assertTrue(gate.allow(npc,player,"Different",760,false));
    }
    @Test void listenersRemainIndependentAndAmbientSpeakersTakeTurns() {
        var gate=new NpcSpeechCooldown();var a=UUID.randomUUID();var b=UUID.randomUUID();var p=UUID.randomUUID();
        assertTrue(gate.allow(a,p,"Hello",100,true));
        assertFalse(gate.allow(b,p,"News",499,true));
        assertTrue(gate.allow(b,p,"News",500,true));
        assertTrue(gate.allow(a,UUID.randomUUID(),"Hello",101,true));
        assertTrue(gate.allow(a,p,"Trade",101,false));
        assertTrue(gate.allow(a,p,"After clock reset",0,false));
    }
}
