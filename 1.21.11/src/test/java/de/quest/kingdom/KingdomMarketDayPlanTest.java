package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import java.util.*;
import org.junit.jupiter.api.Test;

final class KingdomMarketDayPlanTest {
    private static List<KingdomMarketLedger.TraderPlan> plans(LivingKingdomsState state) {
        var result=new ArrayList<KingdomMarketLedger.TraderPlan>();
        for(int i=0;i<5;i++) result.add(KingdomMarketCatalog.plan(state.people().ensure(KingdomMarketPlacesTest.TOWN,
                KingdomPersonRegistry.Role.MARKET_TRADER,i).orElseThrow(),VillageBondType.GRANARY));
        return result;
    }
    private static void refresh(LivingKingdomsState state,long now) {
        state.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),now);
    }
    @Test void unknownBuildingEvidenceCannotDismissADaytimeTraderButNightAndRemovalCan() {
        var state=KingdomMarketPlacesTest.state(0); var plans=plans(state); KingdomMarketPlacesTest.assign(state,0,0);
        var visit=state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,plans,true).orElseThrow();
        var person=visit.traders().getFirst().person();
        state.anchors().invalidateEvidence(KingdomMarketPlacesTest.ANCHOR);
        assertFalse(state.marketDays().mayDismiss(KingdomMarketPlacesTest.TOWN,person,2000,1));
        assertTrue(state.marketDays().mayDismiss(KingdomMarketPlacesTest.TOWN,person,12000,1));
        state.anchors().removedPhysical(KingdomMarketPlacesTest.ANCHOR);
        assertTrue(state.marketDays().mayDismiss(KingdomMarketPlacesTest.TOWN,person,2000,2));
    }
    @Test void visitorsAreLimitedByEarnedCapacityExplicitPlacesAndTheActualRoster() {
        var state=KingdomMarketPlacesTest.state(0); var plans=plans(state);
        for(int i=0;i<5;i++) KingdomMarketPlacesTest.assign(state,i,0);
        var visit=state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,2,plans,true).orElseThrow();
        assertEquals(2,visit.traders().size());
        assertEquals(visit.id(),state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2001,1,5,plans,true).orElseThrow().id());
        assertEquals(2,state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().traders().size());
    }
    @Test void noImplicitStandNoNightOpeningAndNoFutureClockBackfill() {
        var state=KingdomMarketPlacesTest.state(0); var plans=plans(state);
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,plans,true).isEmpty());
        KingdomMarketPlacesTest.assign(state,0,0);
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,14000,0,1,plans,true).isEmpty());
        assertTrue(state.markets().visit(KingdomMarketPlacesTest.TOWN).isEmpty());
        var visit=state.marketDays().observe(KingdomMarketPlacesTest.TOWN,26000,0,1,plans,true).orElseThrow();
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,1,1,plans,true).isEmpty());
        assertFalse(state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().open());
        assertEquals(visit.id(),state.marketDays().observe(KingdomMarketPlacesTest.TOWN,26001,2,1,plans,true).orElseThrow().id());
    }
    @Test void pauseUnloadRestartAndNightPreserveTheVisitAndSpentStock() {
        var state=KingdomMarketPlacesTest.state(0); var plans=plans(state); KingdomMarketPlacesTest.assign(state,0,0);
        var visit=state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,plans,true).orElseThrow();
        var trader=visit.traders().getFirst(); var goods=trader.goods().getFirst(); var actor=UUID.randomUUID();
        var quote=state.markets().quote(actor,visit.id(),trader.person(),goods.offer().sku(),KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).orElseThrow();
        var receipt=state.markets().prepare(quote,actor,100,0,true,0).orElseThrow();
        state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems());
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2001,1,1,plans,false).isEmpty());
        var saved=LivingKingdomsState.toNbt(state); var restored=LivingKingdomsState.fromNbt(saved); refresh(restored,10);
        var resumed=restored.marketDays().observe(KingdomMarketPlacesTest.TOWN,2010,10,1,plans,true).orElseThrow();
        assertEquals(visit.id(),resumed.id()); assertEquals(goods.stock()-1,resumed.traders().getFirst().goods().getFirst().stock());
        assertTrue(restored.marketDays().observe(KingdomMarketPlacesTest.TOWN,14000,20,1,plans,true).isEmpty());
        assertFalse(restored.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().open());
    }
    @Test void nextDayNeedsObservedCooldownAndPendingReceiptStillPreventsReplacement() {
        var state=KingdomMarketPlacesTest.state(0); var plans=plans(state); KingdomMarketPlacesTest.assign(state,0,0);
        var first=state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,plans,true).orElseThrow();
        refresh(state,10);
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,26000,10,1,plans,true).isEmpty());
        for(int tick=110;tick<=6010;tick+=100) { refresh(state,tick); state.marketDays().observe(KingdomMarketPlacesTest.TOWN,26000,tick,1,plans,true); }
        var second=state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow(); assertNotEquals(first.id(),second.id());
        assertEquals(16,second.traders().getFirst().budget());
        var trader=second.traders().getFirst(); var quote=state.markets().quote(UUID.randomUUID(),second.id(),trader.person(),trader.goods().getFirst().offer().sku(),KingdomMarketLedger.Side.BUY,1,0,0,List.of(),6010).orElseThrow();
        state.markets().prepare(quote,quote.actor(),100,0,true,6010).orElseThrow();
        for(int tick=6110;tick<=12010;tick+=100) { refresh(state,tick); state.marketDays().observe(KingdomMarketPlacesTest.TOWN,27000,tick,1,plans,true); }
        refresh(state,12020);
        assertTrue(state.marketDays().observe(KingdomMarketPlacesTest.TOWN,50000,12020,1,plans,true).isEmpty());
        assertEquals(second.id(),state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().id());
    }
}
