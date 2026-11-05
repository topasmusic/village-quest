package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.*;
import de.quest.kingdom.KingdomGuidedProgress;
import de.quest.kingdom.KingdomAdminTestProgress;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomGuidedProgressTest {
    final UUID owner=UUID.randomUUID(),town=UUID.randomUUID();
    PlayerQuestData data() {
        var d=new PlayerQuestData(); d.setCurrencyBalance(137); d.setTradeRouteString("unrelated","keep");
        assertTrue(KingdomGuidedProgress.start(d,owner,new BlockPos(10,120,20),"minecraft:overworld",new BlockPos(0,70,0),true)); return d;
    }
    KingdomGuidedProgress.View view(PlayerQuestData d) { return KingdomGuidedProgress.view(d,owner).orElseThrow(); }
    @Test void explicitTraderShortcutCompletesEarlierCardsWithoutInventingEventsOrRegrantingAnything() {
        var d=data();var v=view(d);
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,owner,v.session(),v.revision(),true));
        assertTrue(KingdomGuidedProgress.record(d,owner,town,KingdomAdminTestProgress.Event.FOUND,true));
        v=view(d);assertTrue(KingdomGuidedProgress.advance(d,owner,v.session(),v.revision(),true,true));v=view(d);
        d.setTradeRouteInt(KingdomGuidedProgress.PREFIX+"kits",7);
        var events=v.observed();
        assertTrue(KingdomGuidedProgress.jumpToTrader(d,owner,v.session(),v.revision(),true));
        var next=view(d);assertEquals(KingdomGuidedProgress.Stage.BUY,next.stage());
        assertEquals((1<<KingdomGuidedProgress.Stage.BUY.ordinal())-1,next.done());assertEquals(0,next.skipped());
        assertEquals(events,next.observed());assertFalse(next.ready());assertEquals(town,next.village());
        assertEquals(7,d.getTradeRouteInt(KingdomGuidedProgress.PREFIX+"kits"));assertEquals(137,d.getCurrencyBalance());
        assertEquals("keep",d.getTradeRouteString("unrelated"));
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,owner,v.session(),v.revision(),true));
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,owner,next.session(),next.revision(),true));
    }
    @Test void traderShortcutRejectsWrongOwnerStaleRevisionAndMissingPermission() {
        var d=data();assertTrue(KingdomGuidedProgress.record(d,owner,town,KingdomAdminTestProgress.Event.FOUND,true));var v=view(d);
        var before=new HashMap<>(d.getTradeRouteIntState());
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,UUID.randomUUID(),v.session(),v.revision(),true));
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,owner,v.session(),v.revision()+1,true));
        assertFalse(KingdomGuidedProgress.jumpToTrader(d,owner,v.session(),v.revision(),false));
        assertEquals(before,d.getTradeRouteIntState());
    }
    @Test void restartClearsOnlyWalkthroughEvidenceAndInvalidatesOldClicksWithoutRegrantingMoneyOrKits() {
        var d=data();var v=view(d);String prefix=KingdomGuidedProgress.PREFIX;
        int grants=(1<<KingdomGuidedProgress.Stage.COUNCIL.ordinal())|(1<<KingdomGuidedProgress.Stage.BUY.ordinal())|(1<<KingdomGuidedProgress.Stage.TOOLS.ordinal());
        d.setTradeRouteInt(prefix+"kits",grants);d.setTradeRouteInt(prefix+"waiting",5);d.setTradeRouteInt(prefix+"tries",9);
        assertTrue(KingdomGuidedProgress.record(d,owner,town,KingdomAdminTestProgress.Event.FOUND,true));v=view(d);
        assertTrue(KingdomGuidedProgress.advance(d,owner,v.session(),v.revision(),true,true));v=view(d);
        var origin=v.origin();var next=UUID.randomUUID();var camp=v.camp().above(32);
        assertTrue(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision(),next,camp,true));
        var fresh=view(d);assertEquals(next,fresh.session());assertEquals(KingdomGuidedProgress.Stage.WELCOME,fresh.stage());
        assertTrue(fresh.active());assertNull(fresh.village());assertEquals(0,fresh.done());assertEquals(0,fresh.skipped());assertTrue(fresh.observed().isEmpty());
        assertEquals(camp,fresh.camp());assertEquals(origin,fresh.origin());assertEquals(grants,d.getTradeRouteInt(prefix+"kits"));
        assertEquals(0,d.getTradeRouteInt(prefix+"waiting"));assertEquals(0,d.getTradeRouteInt(prefix+"tries"));
        assertEquals(137,d.getCurrencyBalance());assertEquals("keep",d.getTradeRouteString("unrelated"));
        assertFalse(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision(),true));
        assertTrue(KingdomGuidedProgress.visit(d,owner,next,fresh.revision(),true));fresh=view(d);
        assertTrue(KingdomGuidedProgress.advance(d,owner,next,fresh.revision(),false,true));fresh=view(d);
        assertTrue(fresh.equipped());assertFalse(KingdomGuidedProgress.kit(d,owner,next,fresh.revision(),true));
    }
    @Test void restartAllowsPausedRunsButRejectsForeignStaleSameCampAndFutureSchemaWithoutWrites() {
        var d=data();var v=view(d);assertTrue(KingdomGuidedProgress.stop(d,owner,v.session(),v.revision(),true));v=view(d);
        var before=new HashMap<>(d.getTradeRouteStringState());var ints=new HashMap<>(d.getTradeRouteIntState());
        assertFalse(KingdomGuidedProgress.restart(d,UUID.randomUUID(),v.session(),v.revision(),UUID.randomUUID(),v.camp().above(32),true));
        assertFalse(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision()+1,UUID.randomUUID(),v.camp().above(32),true));
        assertFalse(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision(),UUID.randomUUID(),v.camp(),true));
        assertFalse(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision(),UUID.randomUUID(),v.camp().above(32),false));
        assertEquals(before,d.getTradeRouteStringState());assertEquals(ints,d.getTradeRouteIntState());
        assertTrue(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision(),UUID.randomUUID(),v.camp().above(32),true));
        v=view(d);d.setTradeRouteInt(KingdomGuidedProgress.PREFIX+"schema",99);
        assertFalse(KingdomGuidedProgress.restart(d,owner,v.session(),v.revision(),UUID.randomUUID(),v.camp().above(32),true));
    }
    @Test void freshStartIsAWelcomeNotAnAcceptanceFormAndKeepsPlayerState() {
        var d=data(); assertEquals(KingdomGuidedProgress.Stage.WELCOME,view(d).stage()); assertFalse(view(d).ready());
        assertEquals(137,d.getCurrencyBalance()); assertNull(d.getActiveStoryArc()); assertEquals("keep",d.getTradeRouteString("unrelated"));
        assertFalse(KingdomGuidedProgress.start(d,owner,BlockPos.ZERO,"minecraft:overworld",BlockPos.ZERO,true));
    }
    @Test void staleForeignAndNoPermissionActionsNeverGrantEquipmentOrAdvance() {
        var d=data(); var v=view(d);
        assertFalse(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision()+1,true));
        assertFalse(KingdomGuidedProgress.kit(d,UUID.randomUUID(),v.session(),v.revision(),true));
        assertFalse(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision(),false)); assertEquals(v,view(d));
        assertTrue(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision(),true)); v=view(d);
        assertFalse(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision(),true));
    }
    @Test void realFoundingIsRequiredAndUnrelatedVillagesCannotCompleteTheQuest() {
        var d=data(); var v=view(d); assertTrue(KingdomGuidedProgress.visit(d,owner,v.session(),v.revision(),true));
        v=view(d); assertTrue(KingdomGuidedProgress.advance(d,owner,v.session(),v.revision(),false,true));
        v=view(d); assertFalse(KingdomGuidedProgress.advance(d,owner,v.session(),v.revision(),false,true));
        assertTrue(KingdomGuidedProgress.record(d,owner,town,KingdomAdminTestProgress.Event.FOUND,true));
        assertTrue(view(d).ready()); assertFalse(KingdomGuidedProgress.record(d,owner,UUID.randomUUID(),KingdomAdminTestProgress.Event.INN,true));
        assertEquals(0,view(d).done()&2); // An observed action never becomes a human PASS in the legacy ledger.
    }
    @Test void savedResumeKeepsOneTimeKitsAndSkippedTasksStaySeparate() {
        var quests=QuestState.fromNbt(new net.minecraft.nbt.CompoundTag()); var d=quests.getPlayerData(owner);
        assertTrue(KingdomGuidedProgress.start(d,owner,new BlockPos(1,100,2),"minecraft:overworld",BlockPos.ZERO,true));
        var v=view(d); assertTrue(KingdomGuidedProgress.kit(d,owner,v.session(),v.revision(),true));
        v=view(d); assertTrue(KingdomGuidedProgress.advance(d,owner,v.session(),v.revision(),true,true));
        var restored=QuestState.fromNbt(QuestState.toNbt(quests)).getPlayerData(owner);
        assertEquals(view(d),view(restored)); assertEquals(1,view(restored).skipped()); assertEquals(0,view(restored).done());
        v=view(restored); assertTrue(KingdomGuidedProgress.stop(restored,owner,v.session(),v.revision(),true));
        assertFalse(KingdomGuidedProgress.record(restored,owner,town,KingdomAdminTestProgress.Event.FOUND,true));
        assertTrue(KingdomGuidedProgress.resume(restored,owner,true)); assertEquals(1,view(restored).skipped());
        assertEquals(KingdomGuidedProgress.Stage.COUNCIL,view(restored).stage());
    }
    @Test void anotherPlayersEventsAndFutureSavedRunsCannotBeOverwritten() {
        var d=data(); var other=UUID.randomUUID();
        assertFalse(KingdomGuidedProgress.record(d,other,town,KingdomAdminTestProgress.Event.FOUND,true));
        d.setTradeRouteInt(KingdomGuidedProgress.PREFIX+"schema",99);
        var before=new HashMap<>(d.getTradeRouteStringState());
        assertTrue(KingdomGuidedProgress.view(d,owner).isEmpty());
        assertFalse(KingdomGuidedProgress.start(d,owner,BlockPos.ZERO,"minecraft:overworld",BlockPos.ZERO,true));
        assertEquals(before,d.getTradeRouteStringState()); assertEquals(99,d.getTradeRouteInt(KingdomGuidedProgress.PREFIX+"schema"));
    }
}
