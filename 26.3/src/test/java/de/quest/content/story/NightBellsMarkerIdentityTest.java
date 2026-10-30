package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.quest.story.StoryQuestKeys;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class NightBellsMarkerIdentityTest {
    @Test void distinctSectorsWithTheSameLegacyHashAndZeroNormalizationCountSeparately() {
        var state=new HashMap<String,Integer>();
        // (0,31) and (1,0) collide; (0,0) and (0,1) previously normalize to 1.
        for(var pos:List.of(new BlockPos(0,70,248),new BlockPos(8,70,0),new BlockPos(0,70,0),new BlockPos(0,70,8)))
            assertTrue(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,3,pos,4));
        assertEquals(4,NightBellsMarkerProgress.count(k->state.getOrDefault(k,0),3));
        assertFalse(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,3,new BlockPos(40,70,40),4));
        assertEquals(4,NightBellsMarkerProgress.exactSectors(k->state.getOrDefault(k,0),3).size());
    }
    @Test void legacyCountAndTokensRemainOpaqueAndNewExactSectorsAreIdempotent() {
        var state=new HashMap<String,Integer>();String legacy=StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"0_0";
        state.put(legacy,31);state.put(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"0_1",1);
        assertFalse(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,0,new BlockPos(8,70,0),3));
        assertFalse(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,0,new BlockPos(0,70,0),3));
        assertTrue(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,0,new BlockPos(-8,70,-8),3));
        var saved=Map.copyOf(state);var reloaded=new HashMap<>(saved);
        assertEquals(3,NightBellsMarkerProgress.count(k->reloaded.getOrDefault(k,0),0));
        assertEquals(31,state.get(legacy));
        assertFalse(NightBellsMarkerProgress.record(k->reloaded.getOrDefault(k,0),reloaded::put,0,new BlockPos(-1,70,-1),3));
        assertEquals(saved,reloaded);
    }
    @Test void unknownFormatIsPreservedAndCannotAcquireAnotherCredit() {
        var state=new HashMap<String,Integer>();state.put("night_bells_peaceful_exact_1_format",2);
        var before=Map.copyOf(state);
        assertFalse(NightBellsMarkerProgress.record(k->state.getOrDefault(k,0),state::put,1,BlockPos.ZERO,3));
        assertEquals(before,state);
    }
}
