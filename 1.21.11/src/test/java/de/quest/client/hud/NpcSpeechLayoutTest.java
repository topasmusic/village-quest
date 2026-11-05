package de.quest.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class NpcSpeechLayoutTest {
    @Test void distantBubblesShrinkInTheSameProportionAsTheirSpeaker() {
        assertEquals(.75f,NpcSpeechLayout.scale(1));
        assertEquals(NpcSpeechLayout.scale(3)/2,NpcSpeechLayout.scale(6),.0001);
        assertEquals(NpcSpeechLayout.scale(6)/2,NpcSpeechLayout.scale(12),.0001);
        assertEquals(0,NpcSpeechLayout.scale(17));
        assertEquals(0,NpcSpeechLayout.scale(Double.NaN));
    }
    @Test void everyLastLineAndPageIndicatorStayInsideThePaperAboveTheBorderAndTail() {
        for(int lines=1;lines<=4;lines++) for(boolean paged:new boolean[]{false,true}) {
            int height=NpcSpeechLayout.height(lines,paged);
            int lastBottom=NpcSpeechLayout.BODY_Y+(lines-1)*NpcSpeechLayout.LINE_STEP+9;
            int paperBottom=height-NpcSpeechLayout.TAIL-NpcSpeechLayout.BORDER;
            assertTrue(lastBottom+6<=paperBottom);
            if(paged) { assertTrue(lastBottom<height-30); assertTrue(height-30+9<=paperBottom); }
        }
    }
    @Test void allWrappedLinesRemainReachableIncludingTheLastPageAtExpiry() {
        for(int lines=1;lines<=80;lines++) {
            var reached=new java.util.HashSet<Integer>();
            for(int tick=0;tick<240;tick++) {
                int first=NpcSpeechLayout.page(lines,tick,240)*NpcSpeechLayout.LINES;
                for(int i=first;i<Math.min(lines,first+NpcSpeechLayout.LINES);i++) reached.add(i);
            }
            assertEquals(lines,reached.size());
            assertEquals(0,NpcSpeechLayout.page(lines,-10,240));
        }
    }
    @Test void speakerIsShownOnceWithoutStrippingAnotherSpeakersNameOrDialogue() {
        assertEquals("Welcome!",NpcSpeechLayout.body("Alina","Alina: Welcome!"));
        assertEquals("Bert: Hello",NpcSpeechLayout.body("Alina","Bert: Hello"));
        assertEquals("Hello Alina: welcome",NpcSpeechLayout.body("Alina","Hello Alina: welcome"));
    }
}
