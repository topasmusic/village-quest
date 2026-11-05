package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static de.quest.kingdom.KingdomAdminTestProgress.PREFIX;
import de.quest.kingdom.KingdomAdminTestProgress;
import de.quest.kingdom.KingdomAdminTestProgress.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Real personal document persistence plus test policies; no Minecraft server/world. */
final class KingdomAdminTestProgressTest {
    private final UUID owner=UUID.randomUUID(),village=UUID.randomUUID();
    private PlayerQuestData started() {
        var d=new PlayerQuestData(); assertTrue(KingdomAdminTestProgress.start(d,owner,true)); return d;
    }
    private View view(PlayerQuestData d) { return KingdomAdminTestProgress.view(d,owner).orElseThrow(); }
    private void bind(PlayerQuestData d) { var v=view(d); assertTrue(KingdomAdminTestProgress.target(d,owner,v.session(),v.revision(),village,true)); }
    private void go(PlayerQuestData d,Step step) {
        while(view(d).cursor()!=step.ordinal()) { var v=view(d); assertTrue(KingdomAdminTestProgress.move(d,owner,v.session(),v.revision(),Integer.signum(step.ordinal()-v.cursor()),true)); }
    }
    @Test void unauthorizedLegacyLedgerMutationsRemainRejected() {
        assertFalse(KingdomAdminTestProgress.start(new PlayerQuestData(),owner,false));
    }
    @Test void staleAndForeignCommandsCannotChangeTheLedger() {
        var d=started(); var v=view(d);
        assertFalse(KingdomAdminTestProgress.move(d,UUID.randomUUID(),v.session(),v.revision(),1,true));
        assertFalse(KingdomAdminTestProgress.move(d,owner,UUID.randomUUID(),v.revision(),1,true));
        assertFalse(KingdomAdminTestProgress.move(d,owner,v.session(),v.revision()+1,1,true));
        assertFalse(KingdomAdminTestProgress.move(d,owner,v.session(),v.revision(),1,false));
        assertEquals(v,view(d)); assertTrue(KingdomAdminTestProgress.move(d,owner,v.session(),v.revision(),1,true));
        assertFalse(KingdomAdminTestProgress.move(d,owner,v.session(),v.revision(),1,true));
    }
    @Test void genuineOwnFoundingCanBindAnUnboundRunButOtherEventsCannot() {
        var d=started(); assertFalse(KingdomAdminTestProgress.record(d,owner,village,Event.COUNCIL,"old scan",true));
        assertTrue(KingdomAdminTestProgress.record(d,owner,village,Event.FOUND,"new founding",true));
        assertEquals(village,view(d).village());
        assertFalse(KingdomAdminTestProgress.record(d,owner,UUID.randomUUID(),Event.INN,"other village",true));
        assertFalse(KingdomAdminTestProgress.record(d,UUID.randomUUID(),village,Event.INN,"foreign actor",true));
    }
    @Test void observationIsNeverAutomaticHumanPassAndManualClaimsNeedRealProof() {
        var d=started(); bind(d); go(d,Step.BUY); var v=view(d);
        assertFalse(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"",null,true));
        assertTrue(KingdomAdminTestProgress.record(d,owner,village,Event.BUY,"commit:1",true));
        assertEquals(Result.OPEN,view(d).results().get(Step.BUY)); assertTrue(view(d).ready());
        assertFalse(KingdomAdminTestProgress.record(d,owner,village,Event.BUY,"commit:1",true));
        v=view(d); assertTrue(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"seen exact inventory and balance",null,true));
        assertEquals(Result.PASS,view(d).results().get(Step.BUY));
    }
    @Test void futureAndMalformedDataStayUntouchedAndCannotBeOverwrittenByStart() {
        for(boolean future:List.of(true,false)) {
            var d=started(); d.setTradeRouteInt(PREFIX+"schema",future?99:1); if(!future) d.setTradeRouteString(PREFIX+"session","broken");
            var before=new HashMap<>(d.getTradeRouteStringState()); var ints=new HashMap<>(d.getTradeRouteIntState());
            assertTrue(KingdomAdminTestProgress.view(d,owner).isEmpty()); assertFalse(KingdomAdminTestProgress.start(d,owner,true));
            assertFalse(KingdomAdminTestProgress.record(d,owner,village,Event.FOUND,"cannot repair",true));
            assertEquals(before,d.getTradeRouteStringState()); assertEquals(ints,d.getTradeRouteIntState());
        }
    }
    @Test void unknownKeysAndForgedPassWithoutProofQuarantineOnlyThisTestLedger() {
        var d=started(); bind(d); d.setTradeRouteInt(PREFIX+"result.BUY",Result.PASS.ordinal());
        assertTrue(KingdomAdminTestProgress.view(d,owner).isEmpty());
        d.setTradeRouteInt(PREFIX+"result.BUY",0); d.setTradeRouteString(PREFIX+"future","preserve");
        assertTrue(KingdomAdminTestProgress.view(d,owner).isEmpty()); assertEquals("preserve",d.getTradeRouteString(PREFIX+"future"));
    }
    @Test void preparedCurrentEpochAndActualAckAreRequiredForMarketAndRetargetDropsOldTicket() {
        var d=started(); bind(d); UUID receipt=UUID.randomUUID();
        assertFalse(KingdomAdminTestProgress.market(d,owner,receipt,village,true,true,true));
        assertTrue(KingdomAdminTestProgress.prepareMarket(d,owner,receipt,village,true));
        assertFalse(KingdomAdminTestProgress.market(d,owner,receipt,village,true,false,true));
        assertFalse(KingdomAdminTestProgress.market(d,owner,UUID.randomUUID(),village,true,true,true));
        assertTrue(KingdomAdminTestProgress.market(d,owner,receipt,village,true,true,true));
        var old=view(d); assertTrue(KingdomAdminTestProgress.target(d,owner,old.session(),old.revision(),village,true));
        assertNotEquals(old.session(),view(d).session()); assertFalse(KingdomAdminTestProgress.market(d,owner,receipt,village,false,true,true));
        assertTrue(view(d).observed().isEmpty());
    }
    @Test void pausePreventsEventsAndResumePreservesActualProgress() {
        var d=started(); bind(d); assertTrue(KingdomAdminTestProgress.record(d,owner,village,Event.LEADER,"speaker",true));
        var v=view(d); assertTrue(KingdomAdminTestProgress.mode(d,owner,v.session(),v.revision(),false,true));
        assertFalse(KingdomAdminTestProgress.record(d,owner,village,Event.INNKEEPER,"inn",true));
        assertFalse(KingdomAdminTestProgress.move(d,owner,v.session(),view(d).revision(),1,true));
        v=view(d); assertTrue(KingdomAdminTestProgress.mode(d,owner,v.session(),v.revision(),true,true));
        assertTrue(view(d).observed().contains(Event.LEADER)); assertTrue(KingdomAdminTestProgress.record(d,owner,village,Event.INNKEEPER,"inn",true));
    }
    @Test void wholePersonalSaveRoundTripKeepsLedgerSeparateFromWalletStoryAndClaims() {
        var quests=QuestState.fromNbt(new CompoundTag()); var d=quests.getPlayerData(owner);
        d.setCurrencyBalance(123); d.setTradeRouteString("unrelated","keep"); d.setMilestoneFlag("kingdoms.village-check.earned",true);
        assertTrue(KingdomAdminTestProgress.start(d,owner,true)); bind(d); assertTrue(KingdomAdminTestProgress.record(d,owner,village,Event.SELL,"receipt:2",true));
        var before=view(d); var restored=QuestState.fromNbt(QuestState.toNbt(quests)).getPlayerData(owner); assertEquals(before,view(restored));
        assertEquals(123,restored.getCurrencyBalance()); assertEquals("keep",restored.getTradeRouteString("unrelated"));
        assertTrue(restored.hasMilestoneFlag("kingdoms.village-check.earned")); assertNull(restored.getActiveStoryArc());
        assertTrue(KingdomAdminTestProgress.reset(restored,owner,before.session(),before.revision(),true));
        assertFalse(KingdomAdminTestProgress.exists(restored)); assertEquals(123,restored.getCurrencyBalance()); assertEquals("keep",restored.getTradeRouteString("unrelated"));
    }
    @Test void mpNeedsADifferentWitnessAndDeferredCasesStayNotRunInFinishedReport() {
        var d=started(); bind(d); go(d,Step.MULTIPLAYER); var v=view(d);
        assertFalse(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"",null,true));
        assertFalse(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"",owner,true));
        assertTrue(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"two real clients",UUID.randomUUID(),true));
        go(d,Step.RESULT); v=view(d); assertFalse(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"",null,true));
        for(var step:Step.values()) if(step!=Step.RESULT && step!=Step.MULTIPLAYER) {
            go(d,step); v=view(d); assertTrue(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.NOT_RUN,"scenario unavailable",null,true));
        }
        go(d,Step.RESULT); v=view(d); assertTrue(KingdomAdminTestProgress.result(d,owner,v.session(),v.revision(),Result.PASS,"report reviewed",null,true));
        assertEquals(3,view(d).mode()); assertEquals(Result.NOT_RUN,view(d).results().get(Step.CRASH));
        assertFalse(KingdomAdminTestProgress.record(d,owner,village,Event.BUY,"late event",true));
    }
}
