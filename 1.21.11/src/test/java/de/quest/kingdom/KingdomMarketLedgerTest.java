package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.village.VillageNeed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Saved market/receipt policies only. No inventory entities, worlds, AI or Ingame simulation. */
final class KingdomMarketLedgerTest {
    private static final UUID TOWN=UUID.randomUUID(),OWNER=UUID.randomUUID(),A=UUID.randomUUID(),B=UUID.randomUUID();
    private static final BlockPos POS=new BlockPos(0,64,0);
    private static final Identifier WHEAT=Identifier.withDefaultNamespace("wheat");
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void twoPlayersCannotPurchaseTheLastSharedLotUsingTheSameRevision() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,1)),0).orElseThrow();
        var first=quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.BUY);
        var second=quote(ledger,visit.id(),person,B,KingdomMarketLedger.Side.BUY);
        var hold=ledger.prepare(first,A,100,0,true,0).orElseThrow();
        assertTrue(ledger.prepare(second,B,100,0,true,0).isEmpty());
        assertTrue(ledger.commit(hold.id(),hold.afterBalance(),hold.afterItems()));
        assertEquals(0,ledger.visit(TOWN).orElseThrow().traders().get(0).goods().get(0).stock());
    }
    @Test void neitherGuiRefreshNorSaveReloadOrBodyChangesRenewTheVisitBudget() {
        var state=state(); var person=trader(state,0); var ledger=state.markets();
        assertTrue(ledger.observeNeed(TOWN,VillageNeed.GRANARY_SEED_RESERVES,0));
        var visit=ledger.start(TOWN,List.of(plan(person,4)),0).orElseThrow();
        var hold=ledger.prepare(quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.SELL),A,10,64,true,0).orElseThrow();
        assertTrue(ledger.commit(hold.id(),hold.afterBalance(),hold.afterItems()));
        assertEquals(visit.id(),ledger.start(TOWN,List.of(plan(person,4)),0).orElseThrow().id());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(15,restored.markets().visit(TOWN).orElseThrow().traders().get(0).budget());
        assertEquals(visit.id(),restored.markets().visit(TOWN).orElseThrow().id());
    }
    @Test void sharedHomeDemandIsNotMultipliedByFiveVisitorsOrANewVisit() {
        var state=state(); var ledger=state.markets(); ledger.observeNeed(TOWN,VillageNeed.GRANARY_SEED_RESERVES,0);
        var people=java.util.stream.IntStream.range(0,5).mapToObj(i -> trader(state,i)).toList();
        var visit=ledger.start(TOWN,people.stream().map(p -> plan(p,4)).toList(),0).orElseThrow();
        for(int i=0;i<4;i++) {
            var q=quote(ledger,visit.id(),people.get(i),A,KingdomMarketLedger.Side.SELL);
            var hold=ledger.prepare(q,A,10,64,true,0).orElseThrow(); ledger.commit(hold.id(),hold.afterBalance(),hold.afterItems());
        }
        assertTrue(ledger.quote(A,visit.id(),people.get(4),"grain",KingdomMarketLedger.Side.SELL,1,0,0,List.of(),0).isEmpty());
        assertFalse(ledger.observeNeed(TOWN,VillageNeed.GRANARY_PANTRY,0));
        assertTrue(ledger.observeNeed(TOWN,VillageNeed.GRANARY_PANTRY,1));
    }
    @Test void reservationsSurviveSaveAndPartialPlayerStateIsQuarantinedInsteadOfPayingAgain() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow();
        var q=quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.BUY);
        var hold=ledger.prepare(q,A,100,0,true,0).orElseThrow();
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(hold,restored.markets().receipt(hold.id()).orElseThrow());
        assertFalse(restored.markets().commit(hold.id(),hold.afterBalance(),hold.beforeItems()));
        assertEquals(KingdomMarketLedger.Phase.CONFLICT,restored.markets().receipt(hold.id()).orElseThrow().phase());
        assertTrue(restored.markets().commit(hold.id(),hold.afterBalance(),hold.afterItems()));
        assertTrue(restored.markets().commit(hold.id(),hold.afterBalance(),hold.afterItems()));
    }
    @Test void cancelOnlyRestoresReservedGoodsWhenPlayerStillMatchesTheExactBeforeCounters() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow();
        var q=quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.BUY);
        assertTrue(ledger.prepare(q,B,100,0,true,0).isEmpty());
        assertTrue(ledger.prepare(q,A,100,0,false,0).isEmpty());
        var hold=ledger.prepare(q,A,100,0,true,0).orElseThrow();
        assertFalse(ledger.cancel(hold.id(),99,0));
        assertTrue(ledger.cancel(hold.id(),100,0)); assertFalse(ledger.cancel(hold.id(),100,0));
        assertEquals(2,ledger.visit(TOWN).orElseThrow().traders().get(0).goods().get(0).stock());
    }
    @Test void aClosedVisitCannotRenewUntilLoadedObservedTimeAndPendingReceiptsAreResolved() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow(); ledger.close(TOWN);
        assertTrue(ledger.start(TOWN,List.of(plan(person,2)),6000).isEmpty());
        ledger.observe(TOWN,0,true); ledger.observe(TOWN,6000,true);
        assertTrue(ledger.start(TOWN,List.of(plan(person,2)),6000).isEmpty());
        for(long tick=6000;tick<=12000;tick+=100) ledger.observe(TOWN,tick,true);
        // The native adapter must supply a new fresh loaded building report, never a saved stale one.
        var anchor=state.anchors().official(TOWN,BuildingScanner.Role.MARKET).orElseThrow();
        state.anchors().observe(anchor.id(),anchor.revision(),report(),12000);
        var next=ledger.start(TOWN,List.of(plan(person,2)),12000).orElseThrow(); assertNotEquals(visit.id(),next.id());
    }
    @Test void malformedAndFutureMarketRootsRemainOpaqueAndCannotMintFreshVisits() {
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("keep","opaque");
        for(var payload:List.of(future,StringTag.valueOf("unknown future market"))) {
            var saved=LivingKingdomsState.toNbt(state()); saved.put("markets",payload.copy());
            var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.markets().readOnly());
            assertEquals(payload,LivingKingdomsState.toNbt(restored).get("markets"));
        }
    }
    @Test void missingSavedDemandCountersCannotRestoreAFullHomeQuota() {
        var state=state(); state.markets().observeNeed(TOWN,VillageNeed.GRANARY_SEED_RESERVES,0);
        var saved=LivingKingdomsState.toNbt(state);
        saved.getCompoundOrEmpty("markets").getListOrEmpty("needs").getCompoundOrEmpty(0).remove("accepted");
        var original=saved.getCompoundOrEmpty("markets").copy(); var restored=LivingKingdomsState.fromNbt(saved);
        assertTrue(restored.markets().readOnly()); assertEquals(original,LivingKingdomsState.toNbt(restored).get("markets"));
    }
    @Test void unknownAcceptedCounterFieldsSurviveSavingWithoutChangingTheFiniteQuota() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        ledger.observeNeed(TOWN,VillageNeed.GRANARY_SEED_RESERVES,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow();
        var hold=ledger.prepare(quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.SELL),A,10,64,true,0).orElseThrow();
        ledger.commit(hold.id(),hold.afterBalance(),hold.afterItems());
        var saved=LivingKingdomsState.toNbt(state);
        saved.getCompoundOrEmpty("markets").getListOrEmpty("needs").getCompoundOrEmpty(0).getListOrEmpty("accepted").getCompoundOrEmpty(0).putString("futureCounter","retain");
        var restored=LivingKingdomsState.fromNbt(saved);
        assertEquals("retain",LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("markets").getListOrEmpty("needs").getCompoundOrEmpty(0)
                .getListOrEmpty("accepted").getCompoundOrEmpty(0).getStringOr("futureCounter",""));
    }
    @Test void aReservationWithoutItsStockDeductionIsCorruptAndCannotBeCancelledIntoExtraGoods() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow();
        ledger.prepare(quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.BUY),A,100,0,true,0).orElseThrow();
        var saved=LivingKingdomsState.toNbt(state);
        saved.getCompoundOrEmpty("markets").getListOrEmpty("hosts").getCompoundOrEmpty(0).getCompoundOrEmpty("visit")
                .getListOrEmpty("traders").getCompoundOrEmpty(0).getListOrEmpty("goods").getCompoundOrEmpty(0).putInt("remaining",2);
        assertTrue(LivingKingdomsState.fromNbt(saved).markets().readOnly());
    }
    private static KingdomMarketLedger.Quote quote(KingdomMarketLedger ledger,UUID visit,UUID person,UUID actor,KingdomMarketLedger.Side side) {
        return ledger.quote(actor,visit,person,"grain",side,1,0,0,List.of(),0).orElseThrow();
    }
    @Test void sourceReloadInvalidatesUnpreparedQuotesWithoutResettingReceiptsOrSavedStock() {
        var state=state(); var ledger=state.markets(); var person=trader(state,0);
        var visit=ledger.start(TOWN,List.of(plan(person,2)),0).orElseThrow();
        var reserved=ledger.prepare(quote(ledger,visit.id(),person,A,KingdomMarketLedger.Side.BUY),A,100,0,true,0).orElseThrow();
        var stale=quote(ledger,visit.id(),person,B,KingdomMarketLedger.Side.BUY);
        var saved=LivingKingdomsState.toNbt(state);
        assertTrue(state.marketSources().rebuild(() -> MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(5,
                List.of(new MarketAcquisitionBounds.Goods(WHEAT,32)))),new NativeMarketRecipeRules.Reading(true,List.of()),
                item -> new NativeMarketRecipeRules.Reading(true,List.of()))));
        assertTrue(ledger.prepare(stale,B,100,0,true,0).isEmpty());
        assertEquals(reserved,ledger.receipt(reserved.id()).orElseThrow()); assertEquals(saved,LivingKingdomsState.toNbt(state));
        assertTrue(ledger.commit(reserved.id(),reserved.afterBalance(),reserved.afterItems()));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertFalse(restored.marketSources().ready()); assertEquals(0,restored.marketSources().source(WHEAT).price());
    }
    private static KingdomMarketLedger.TraderPlan plan(UUID person,int stock) {
        return new KingdomMarketLedger.TraderPlan(person,TOWN,List.of(new KingdomMarketLedger.Goods("grain",WHEAT,32,stock,4,4,1)));
    }
    private static UUID trader(LivingKingdomsState state,int slot) { return state.people().ensure(TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,slot).orElseThrow().id(); }
    private static BuildingScanner.Report report() { return new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(POS.north())); }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState(); state.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),POS,"Market town").orElseThrow();
        state.anchors().bind(OWNER,TOWN,0,UUID.randomUUID(),-1,POS,Direction.NORTH,BuildingScanner.Range.BASIC,report(),0,0); return state;
    }
}
