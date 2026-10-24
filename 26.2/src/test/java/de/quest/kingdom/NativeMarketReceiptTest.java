package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Actual NBT and inventory snapshots; no server, player entity, world or game simulation. */
final class NativeMarketReceiptTest {
    private static final UUID TOWN=UUID.randomUUID(),OWNER=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    private static final BlockPos POS=new BlockPos(0,64,0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void components() { Items.WHEAT.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build()); }
    private record Fixture(LivingKingdomsState state,SimpleContainer bag,KingdomMarketLedger.Quote quote,MarketInventoryPlan plan) {}
    private static RegistryAccess access() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static BuildingScanner.Report report() { return new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(POS.north())); }
    private static Fixture fixture() {
        var s=new LivingKingdomsState(); s.registerCustom(TOWN,OWNER,VillageKey.overworld(0,0),POS,"Market").orElseThrow();
        s.anchors().bind(OWNER,TOWN,0,UUID.randomUUID(),-1,POS,Direction.NORTH,BuildingScanner.Range.BASIC,report(),0,0);
        var person=s.people().ensure(TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow().id();
        var goods=new KingdomMarketLedger.Goods("grain",Identifier.withDefaultNamespace("wheat"),32,2,4,4,1);
        var v=s.markets().start(TOWN,List.of(new KingdomMarketLedger.TraderPlan(person,TOWN,List.of(goods))),0).orElseThrow();
        var q=s.markets().quote(ACTOR,v.id(),person,"grain",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).orElseThrow();
        var bag=new SimpleContainer(36); return new Fixture(s,bag,q,MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),q.side(),q.quantity()).orElseThrow());
    }
    private static void reserve(Fixture f) { assertTrue(f.state.markets().prepareNative(f.quote,ACTOR,100,f.bag,f.plan,access(),0).isPresent()); }
    @Test void exactPlanPersistsAndNumericCountersCannotCommitOrRefundNativeReservations() {
        var f=fixture(); reserve(f); var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        var l=restored.markets(); assertTrue(l.nativePlan(f.quote.id(),access()).orElseThrow().matchesBefore(f.bag));
        assertFalse(l.commit(f.quote.id(),96,32)); assertFalse(l.cancel(f.quote.id(),100,0));
        assertEquals(1,l.visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock());
        assertEquals(KingdomMarketLedger.Phase.RESERVED,l.receipt(f.quote.id()).orElseThrow().phase());
    }
    @Test void mixedOrMissingSaveMarkersHoldWithoutInventoryMutationOrStockRefund() {
        var f=fixture(); reserve(f); var l=f.state.markets();
        var before=l.marker(f.quote.id(),MarketTransferMarker.Stage.BEFORE).orElseThrow().toNbt();
        var after=l.marker(f.quote.id(),MarketTransferMarker.Stage.AFTER).orElseThrow().toNbt();
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),ACTOR,96,f.bag,after,before,access()));
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),ACTOR,100,f.bag,null,before,access()));
        assertTrue(f.bag.getItem(0).isEmpty()); assertEquals(1,l.visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock());
        assertEquals(1,l.pending(ACTOR).size());
    }
    @Test void matchingAfterMarkersStillRequireExactSlotsAndCommitOnlyOnceThenExplicitAck() {
        var f=fixture(); reserve(f); var l=f.state.markets();
        var after=l.marker(f.quote.id(),MarketTransferMarker.Stage.AFTER).orElseThrow().toNbt();
        assertTrue(f.plan.apply(f.bag)); f.bag.setItem(1,f.bag.removeItemNoUpdate(0));
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),ACTOR,96,f.bag,after,after,access()));
        f.bag.setItem(0,f.bag.removeItemNoUpdate(1));
        assertEquals(KingdomMarketLedger.Resolution.COMMITTED,l.resolveNative(f.quote.id(),ACTOR,96,f.bag,after,after,access()));
        assertEquals(1,l.pending(ACTOR).size());
        assertFalse(l.acknowledgeNative(f.quote.id(),ACTOR,after,after));
        var ack=l.marker(f.quote.id(),MarketTransferMarker.Stage.COMMITTED).orElseThrow().toNbt();
        assertTrue(l.acknowledgeNative(f.quote.id(),ACTOR,ack,ack)); assertTrue(l.acknowledgeNative(f.quote.id(),ACTOR,ack,ack));
        assertTrue(l.pending(ACTOR).isEmpty()); assertEquals(32,f.bag.getItem(0).getCount());
        assertEquals(1,l.visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock());
    }
    @Test void beforeMarkersCancelOnceButUnacknowledgedReceiptBlocksVisitReplacementAcrossReload() {
        var f=fixture(); reserve(f); var l=f.state.markets(); var before=l.marker(f.quote.id(),MarketTransferMarker.Stage.BEFORE).orElseThrow().toNbt();
        assertEquals(KingdomMarketLedger.Resolution.CANCELLED,l.resolveNative(f.quote.id(),ACTOR,100,f.bag,before,before,access()));
        l.close(TOWN); for(long t=0;t<=6000;t+=100) l.observe(TOWN,t,true);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state)); l=restored.markets();
        assertFalse(l.readOnly()); assertEquals(1,l.pending(ACTOR).size());
        var anchor=restored.anchors().official(TOWN,BuildingScanner.Role.MARKET).orElseThrow(); restored.anchors().observe(anchor.id(),anchor.revision(),report(),6000);
        var person=l.visit(TOWN).orElseThrow().traders().getFirst().person(); var goods=l.visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().offer();
        assertTrue(l.start(TOWN,List.of(new KingdomMarketLedger.TraderPlan(person,TOWN,List.of(goods))),6000).isEmpty());
        var ack=l.marker(f.quote.id(),MarketTransferMarker.Stage.CANCELLED).orElseThrow().toNbt();
        assertTrue(l.acknowledgeNative(f.quote.id(),ACTOR,ack,ack));
        assertTrue(l.start(TOWN,List.of(new KingdomMarketLedger.TraderPlan(person,TOWN,List.of(goods))),6000).isPresent());
    }
    @Test void changedPlanOrForeignProofCannotReserveOrResolveSomeoneElsesMoney() {
        var f=fixture(); var wrong=MarketInventoryPlan.plan(f.bag,new ItemStack(Items.WHEAT),f.quote.side(),1).orElseThrow();
        assertTrue(f.state.markets().prepareNative(f.quote,ACTOR,100,f.bag,wrong,access(),0).isEmpty()); reserve(f);
        var l=f.state.markets(); var before=l.marker(f.quote.id(),MarketTransferMarker.Stage.BEFORE).orElseThrow().toNbt();
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),UUID.randomUUID(),100,f.bag,before,before,access()));
        var tampered=before.copy(); tampered.getCompoundOrEmpty("transfer").putLong("beforeBalance",101);
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),ACTOR,100,f.bag,tampered,before,access()));
    }
    @Test void futureAndMalformedNativeEvidenceIsPreservedAndCannotUseNumericFallback() {
        var f=fixture(); reserve(f); var saved=LivingKingdomsState.toNbt(f.state);
        var row=saved.getCompoundOrEmpty("markets").getListOrEmpty("receipts").getCompoundOrEmpty(0);
        row.getCompoundOrEmpty("nativePlan").putInt("schema",99); row.putString("unknownReceipt","keep");
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.markets().readOnly());
        assertEquals(saved.get("markets"),LivingKingdomsState.toNbt(restored).get("markets"));
        assertFalse(restored.markets().cancel(f.quote.id(),100,0));
    }
    @Test void truncatedNativeSnapshotCannotBeAcknowledgedAndDiscardedAsAValidReceipt() {
        var f=fixture(); reserve(f); var saved=LivingKingdomsState.toNbt(f.state);
        saved.getCompoundOrEmpty("markets").getListOrEmpty("receipts").getCompoundOrEmpty(0)
                .getCompoundOrEmpty("nativePlan").getListOrEmpty("before").remove(35);
        var restored=LivingKingdomsState.fromNbt(saved);
        assertTrue(restored.markets().readOnly()); assertEquals(saved.get("markets"),LivingKingdomsState.toNbt(restored).get("markets"));
    }
    @Test void twoPersistedReservationsRequireBothSharedStockDeductionsNotJustEachOneInIsolation() {
        var f=fixture(); reserve(f); var l=f.state.markets(); var other=UUID.randomUUID();
        var q=l.quote(other,f.quote.visit(),f.quote.trader(),"grain",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).orElseThrow();
        var bag=new SimpleContainer(36); var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),q.side(),q.quantity()).orElseThrow();
        assertTrue(l.prepareNative(q,other,100,bag,plan,access(),0).isPresent());
        var saved=LivingKingdomsState.toNbt(f.state);
        saved.getCompoundOrEmpty("markets").getListOrEmpty("hosts").getCompoundOrEmpty(0).getCompoundOrEmpty("visit")
                .getListOrEmpty("traders").getCompoundOrEmpty(0).getListOrEmpty("goods").getCompoundOrEmpty(0).putInt("remaining",1);
        assertTrue(LivingKingdomsState.fromNbt(saved).markets().readOnly());
    }
    @Test void cancelledAndCommittedTerminalsCannotBeReversedByOlderMatchingProofs() {
        var f=fixture(); reserve(f); var l=f.state.markets();
        var before=l.marker(f.quote.id(),MarketTransferMarker.Stage.BEFORE).orElseThrow().toNbt();
        var after=l.marker(f.quote.id(),MarketTransferMarker.Stage.AFTER).orElseThrow().toNbt();
        assertEquals(KingdomMarketLedger.Resolution.CANCELLED,l.resolveNative(f.quote.id(),ACTOR,100,f.bag,before,before,access()));
        f.plan.apply(f.bag);
        assertEquals(KingdomMarketLedger.Resolution.HELD,l.resolveNative(f.quote.id(),ACTOR,96,f.bag,after,after,access()));
        assertEquals(KingdomMarketLedger.Phase.CANCELLED,l.receipt(f.quote.id()).orElseThrow().phase());
        assertEquals(2,l.visit(TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock());
    }
}
