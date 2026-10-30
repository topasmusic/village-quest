package de.quest.ambient;

import de.quest.ambient.EncounterQuestProgress.Kind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Document transitions only; no server, world or simulated Ingame run. */
final class EncounterQuestProgressTest {
    @Test void onlyOneQuestCanBeAcceptedAndLaterDaysDoNotEraseIt(){
        var p=new EncounterQuestProgress(null);assertTrue(p.accept(Kind.COMPANY,7));
        assertFalse(p.accept(Kind.IRON,7));assertFalse(p.canOffer(8));assertEquals(Kind.COMPANY,p.kind());
    }
    @Test void completionNeedsTheFullTargetAndCanBeRecordedOnlyOnce(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.ZOMBIES,4);
        assertFalse(p.complete(4));p.advance(1);assertFalse(p.complete(4));p.advance(1);
        assertTrue(p.complete(4));assertFalse(p.complete(4));assertFalse(p.advance(1));assertTrue(p.pending());
    }
    @Test void pendingRewardAndDayReceiptSurviveRoundTrip(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.IRON,5);p.advance(1);p.complete(5);
        var loaded=new EncounterQuestProgress(p.snapshot());assertTrue(loaded.pending());assertEquals(5,loaded.paidDay());
        assertFalse(loaded.canOffer(6));loaded.clear();assertFalse(loaded.canOffer(5));assertTrue(loaded.canOffer(6));
    }
    @Test void backwardsCalendarNeverReopensAnAlreadyRewardedDay(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.PROVISIONS,10);p.advance(1);p.complete(10);p.clear();
        assertFalse(p.canOffer(9));assertFalse(p.canOffer(10));assertTrue(p.canOffer(11));
    }
    @Test void cancellationPreservesDayReceiptButAllowsUnrewardedFutureEncounter(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.COMPANY,1);p.advance(12);p.clear();
        assertTrue(p.canOffer(1));assertEquals(0,p.progress());assertEquals("",p.text("companion"));
    }
    @Test void savedWalkingProgressResumesWithoutResetAndCannotOverflow(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.COMPANY,1);p.advance(18);p.text("companion","old");
        p=new EncounterQuestProgress(p.snapshot());p.text("companion","next");assertEquals(18,p.progress());
        p.advance(Integer.MAX_VALUE);assertEquals(45,p.progress());assertTrue(p.complete(2));
    }
    @Test void unknownSchemaAndQuestAreReadOnlyAndPreserved(){
        var tag=new CompoundTag();tag.putInt("schema",9);tag.putString("kind","FUTURE");tag.putString("extra","keep");
        var p=new EncounterQuestProgress(tag);assertFalse(p.writable());assertFalse(p.accept(Kind.LIGHT,2));assertFalse(p.complete(2));assertEquals(tag,p.snapshot());
    }
    @Test void snapshotsNeverShareMutableStateWithTheirInput(){
        var p=new EncounterQuestProgress(null);p.accept(Kind.ZOMBIES,1);var snapshot=p.snapshot();
        var loaded=new EncounterQuestProgress(snapshot);loaded.advance(1);assertEquals(0,p.progress());assertEquals(0,snapshot.getIntOr("progress",0));
    }
    @Test void agreedRewardsAndTargetsUseOneDefinition(){
        for(var kind:Kind.values())assertEquals(2,kind.levels);
        assertEquals(2,Kind.ZOMBIES.target);assertEquals(2,Kind.LIGHT.target);assertEquals(45,Kind.COMPANY.target);
    }
}
