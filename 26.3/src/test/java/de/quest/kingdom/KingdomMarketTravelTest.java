package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.caravan.KingdomRouteConnections.Connection;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

/** Saved services only, no server/world/game loop. */
final class KingdomMarketTravelTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private record Fixture(LivingKingdomsState state,UUID home,UUID person,Connection route,KingdomMarketLedger.TraderPlan plan) {}
    private static Fixture fixture() {
        var state=KingdomMarketPlacesTest.state(0); KingdomMarketPlacesTest.assign(state,0,0);
        var home=UUID.randomUUID(); var host=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER;
        state.registerCustom(home,owner,VillageKey.overworld(300,0),new BlockPos(300,64,0),"Forge home").orElseThrow();
        var offer=state.diplomacy().offer(owner,home,host,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        state.diplomacy().accept(owner,offer.id(),0).orElseThrow();
        var person=state.people().ensure(home,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        return new Fixture(state,home,person.id(),new Connection(owner,UUID.randomUUID(),home,host,"active"),KingdomMarketCatalog.plan(person,VillageBondType.FORGE));
    }
    private static KingdomMarketLedger.VisitView start(Fixture f) {
        return f.state.marketDays().observe(KingdomMarketPlacesTest.TOWN,2000,0,1,List.of(f.plan),true,List.of(f.route)).orElseThrow();
    }
    private static void observe(Fixture f,long now,long clock,boolean loaded,List<Connection> routes) {
        f.state.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),now);
        f.state.marketDays().observe(KingdomMarketPlacesTest.TOWN,clock,now,1,List.of(f.plan),loaded,routes);
    }
    private static void arrive(Fixture f) {
        for(int n=0;n<=1100;n+=20) observe(f,n,2000+n,true,List.of(f.route));
        assertEquals(KingdomMarketTravel.Phase.STAY,f.state.marketTravel().journey(f.person).orElseThrow().phase());
    }
    @Test void confirmedPermanentRouteLossReleasesTheVisitorWithoutRefillingSavedTrade() {
        var f=fixture(); var visit=start(f); arrive(f);
        var actor=UUID.randomUUID();
        var quote=f.state.markets().quote(actor,visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).orElseThrow();
        f.state.marketTravel().recoverLostRoutes(KingdomMarketPlacesTest.TOWN,(owner,id) -> false);
        assertEquals(KingdomMarketTravel.Phase.STAY,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        f.state.marketTravel().recoverLostRoutes(KingdomMarketPlacesTest.TOWN,(owner,id) -> owner.equals(f.route.owner()) && id.equals(f.route.id()));
        assertEquals(KingdomMarketTravel.Phase.HOME,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        assertNull(f.state.people().person(f.person).orElseThrow().visit());
        assertEquals(visit.traders(),f.state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().traders());
        assertTrue(f.state.markets().prepare(quote,actor,100,0,true,1120).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        assertFalse(restored.marketTravel().readOnly());
        assertEquals(KingdomMarketTravel.Phase.HOME,restored.marketTravel().journey(f.person).orElseThrow().phase());
        assertFalse(restored.marketTravel().canPlan(KingdomMarketPlacesTest.TOWN,f.person,1),"No cooldown bypass");
    }
    @Test void realHomeAndPinnedDirectRouteAreRequiredBeforeAnyStandOrQuote() {
        var f=fixture(); var s=f.state; var host=KingdomMarketPlacesTest.TOWN;
        assertTrue(s.marketDays().observe(host,2000,0,1,List.of(f.plan),true,List.of()).isEmpty());
        var visit=start(f); var person=s.people().person(f.person).orElseThrow();
        assertEquals(f.home,person.village()); assertEquals(host,person.visit()); assertEquals(f.home,visit.traders().getFirst().home());
        assertTrue(s.marketDays().place(host,f.person,0).isEmpty());
        assertTrue(s.markets().quote(UUID.randomUUID(),visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),0).isEmpty());
        assertTrue(s.people().reserve(person.id(),person.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),0).isEmpty());
        arrive(f);
        assertTrue(s.marketDays().place(host,f.person,1100).isPresent());
        assertTrue(s.markets().quote(UUID.randomUUID(),visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).isPresent());
        person=s.people().person(f.person).orElseThrow();
        assertTrue(s.people().reserve(person.id(),person.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),1100).isPresent());
    }
    @Test void unknownBlockedRollbackAndRestartNeverBecomeTravelOrFreshStock() {
        var f=fixture(); var visit=start(f); observe(f,20,2020,true,List.of(f.route)); observe(f,40,2040,true,List.of(f.route));
        long remaining=f.state.marketTravel().journey(f.person).orElseThrow().remaining();
        observe(f,60,2060,false,List.of(f.route)); observe(f,80,2080,false,List.of(f.route));
        assertEquals(remaining,f.state.marketTravel().journey(f.person).orElseThrow().remaining());
        var saved=LivingKingdomsState.toNbt(f.state); var restored=LivingKingdomsState.fromNbt(saved);
        assertFalse(restored.marketTravel().readOnly());
        var r=new Fixture(restored,f.home,f.person,f.route,f.plan);
        observe(r,100000,2100,true,List.of(f.route));
        assertEquals(remaining,restored.marketTravel().journey(f.person).orElseThrow().remaining());
        observe(r,99990,2090,true,List.of(f.route));
        assertEquals(remaining,restored.marketTravel().journey(f.person).orElseThrow().remaining());
        observe(r,100010,2110,true,List.of(new Connection(f.route.owner(),f.route.id(),f.home,KingdomMarketPlacesTest.TOWN,"incident")));
        assertTrue(restored.marketTravel().journey(f.person).orElseThrow().paused());
        assertEquals(remaining,restored.marketTravel().journey(f.person).orElseThrow().remaining());
        assertEquals(visit.id(),restored.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().id());
        assertEquals(16,restored.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().traders().getFirst().budget());
    }
    @Test void duplicateMarketsAndFastNewDatesCannotCreateAnotherPersonBudget() {
        var f=fixture(); start(f);
        assertFalse(f.state.marketTravel().canPlan(f.home,f.person,0));
        assertFalse(f.state.marketTravel().canPlan(f.home,f.person,1));
        assertTrue(f.state.marketDays().observe(f.home,2000,0,1,List.of(f.plan),true).isEmpty());
        arrive(f); observe(f,1120,13000,true,List.of(f.route));
        assertEquals(KingdomMarketTravel.Phase.RETURNING,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        assertTrue(f.state.marketDays().place(KingdomMarketPlacesTest.TOWN,f.person,1120).isEmpty());
        for(int n=1140;n<=2240;n+=20) observe(f,n,13000,true,List.of(f.route));
        assertEquals(KingdomMarketTravel.Phase.HOME,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        assertNull(f.state.people().person(f.person).orElseThrow().visit());
        assertFalse(f.state.marketTravel().canPlan(f.home,f.person,1),"New date does not bypass the observed personal visit cooldown");
        for(int n=2260;n<=6200;n+=20) observe(f,n,13000,true,List.of(f.route));
        assertTrue(f.state.marketTravel().canPlan(f.home,f.person,1));
    }
    @Test void returnRetiresTheDestinationBodyAndOldGenerationsCannotReactivate() {
        var f=fixture(); start(f); arrive(f);
        var p=f.state.people().person(f.person).orElseThrow();
        var ticket=f.state.people().reserve(p.id(),p.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),1100).orElseThrow();
        assertTrue(f.state.people().commit(ticket));
        observe(f,1120,13000,true,List.of(f.route));
        assertFalse(f.state.people().markLoaded(ticket.person(),ticket.generation(),ticket.body()));
        assertEquals(ticket.body(),f.state.marketTravel().takeRetired().getFirst().body());
        assertNull(f.state.people().person(f.person).orElseThrow().body());
    }
    @Test void closingAndReopeningKeepsSpentStockButRejectsAPreDepartureQuote() {
        var f=fixture(); var visit=start(f); arrive(f); var actor=UUID.randomUUID();
        int before=visit.traders().getFirst().goods().getFirst().stock();
        var q=f.state.markets().quote(actor,visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).orElseThrow();
        var receipt=f.state.markets().prepare(q,actor,100,0,true,1100).orElseThrow();
        assertTrue(f.state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems()));
        var next=f.state.markets().quote(actor,visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).orElseThrow();
        observe(f,1120,3120,false,List.of(f.route)); observe(f,1140,3140,true,List.of(f.route));
        assertEquals(visit.id(),f.state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().id());
        assertEquals(before-1,f.state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().traders().getFirst().goods().getFirst().stock());
        observe(f,1160,13000,true,List.of(f.route));
        assertTrue(f.state.markets().prepare(next,actor,100,0,true,1160).isEmpty());
    }
    @Test void futureAndMalformedJourneyDataArePreservedAndBlockNewTrading() {
        var f=fixture(); start(f); var saved=LivingKingdomsState.toNbt(f.state);
        var future=new CompoundTag(); future.putInt("schema",99); future.putString("unknown","keep"); saved.put("marketTravel",future);
        var s=LivingKingdomsState.fromNbt(saved); assertTrue(s.marketTravel().readOnly()); assertTrue(s.markets().readOnly());
        assertEquals(future,LivingKingdomsState.toNbt(s).get("marketTravel"));
        saved=LivingKingdomsState.toNbt(f.state); var bad=saved.getCompoundOrEmpty("marketTravel");
        bad.getListOrEmpty("journeys").getCompoundOrEmpty(0).putLong("duration",-1);
        s=LivingKingdomsState.fromNbt(saved); assertTrue(s.marketTravel().readOnly());
        assertEquals(bad,LivingKingdomsState.toNbt(s).get("marketTravel"));
    }
    @Test void occasionalEveningConversationKeepsTheSameBodyWithoutReopeningTrade() {
        var f=fixture(); var p=f.state.people().person(f.person).orElseThrow();
        long date=Math.floorMod(-p.appearanceSeed(),8),day=date*24000;
        var visit=f.state.marketDays().observe(KingdomMarketPlacesTest.TOWN,day+2000,0,1,List.of(f.plan),true,List.of(f.route)).orElseThrow();
        for(int n=0;n<=1100;n+=20) observe(f,n,day+2000+n,true,List.of(f.route));
        p=f.state.people().person(f.person).orElseThrow();
        var ticket=f.state.people().reserve(p.id(),p.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),1100).orElseThrow();
        assertTrue(f.state.people().commit(ticket));
        observe(f,1120,day+12000,true,List.of(f.route));
        assertEquals(KingdomMarketTravel.Phase.EVENING,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        assertTrue(f.state.marketDays().appearancePlace(KingdomMarketPlacesTest.TOWN,f.person,1120).isPresent());
        assertTrue(f.state.people().available(ticket.person(),ticket.generation(),ticket.body()));
        assertFalse(f.state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().open());
        assertTrue(f.state.marketDays().place(KingdomMarketPlacesTest.TOWN,f.person,1120).isEmpty());
        assertTrue(f.state.markets().quote(UUID.randomUUID(),visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1120).isEmpty());
        observe(f,1140,day+13000,true,List.of(f.route));
        assertEquals(KingdomMarketTravel.Phase.RETURNING,f.state.marketTravel().journey(f.person).orElseThrow().phase());
        assertFalse(f.state.people().markLoaded(ticket.person(),ticket.generation(),ticket.body()));
    }
    @Test void nativePlannerUsesTheKnownRealOriginAndPreservesTheLocalFallback() {
        var f=fixture(); var quests=de.quest.data.QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var owner=KingdomMarketPlacesTest.OWNER; var host=KingdomMarketPlacesTest.TOWN;
        de.quest.shrine.VillageContactService.establish(quests.getPlayerData(owner),f.state.village(f.home).orElseThrow(),VillageBondType.FORGE);
        de.quest.shrine.VillageContactService.establish(quests.getPlayerData(owner),f.state.village(host).orElseThrow(),VillageBondType.GRANARY);
        long date=Math.floorMod(host.getLeastSignificantBits(),3)==0?1:0;
        var visit=KingdomMarketVisitors.observe(f.state,quests,host,owner,date*24000+2000,0,1,true,true,List.of(f.route)).orElseThrow();
        assertEquals(f.home,visit.traders().getFirst().home()); assertEquals(f.person,visit.traders().getFirst().person());
        assertTrue(visit.traders().getFirst().goods().stream().allMatch(g -> g.offer().buy()==0));
        assertTrue(KingdomMarketVisitors.observe(f.state,quests,f.home,owner,date*24000+2000,0,1,true,true,List.of(f.route)).isEmpty());
        assertTrue(f.state.people().ensure(f.home,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow().visit().equals(host));
        var local=KingdomMarketPlacesTest.state(0); KingdomMarketPlacesTest.assign(local,0,0);
        var fallback=KingdomMarketVisitors.observe(local,quests,host,owner,2000,0,1,true,true,List.of()).orElseThrow();
        assertEquals(host,fallback.traders().getFirst().home());
    }
    @Test void actualRouteLengthAndLastConfirmedQualityShapeTravelWithoutMintingAnArrival() {
        var f=fixture(); var saved=LivingKingdomsState.toNbt(f.state); var copy=LivingKingdomsState.fromNbt(saved);
        copy.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),0);
        var low=new Connection(f.route.owner(),f.route.id(),f.home,KingdomMarketPlacesTest.TOWN,"active",900,20);
        var high=new Connection(f.route.owner(),f.route.id(),f.home,KingdomMarketPlacesTest.TOWN,"active",900,100);
        start(new Fixture(f.state,f.home,f.person,low,f.plan)); start(new Fixture(copy,f.home,f.person,high,f.plan));
        var slow=f.state.marketTravel().journey(f.person).orElseThrow(); var fast=copy.marketTravel().journey(f.person).orElseThrow();
        assertEquals(2340+(slow.late()?400:0),slow.remaining());
        assertEquals(900+(fast.late()?400:0),fast.remaining());
        assertEquals(0,f.state.relations().between(f.home,KingdomMarketPlacesTest.TOWN).map(KingdomRelations.View::deliveries).orElse(0));
    }
    @Test void departedLoadedOrUnloadedHomeBodyIsRetiredBeforeAnyNewDestinationGeneration() {
        for(boolean loaded:List.of(false,true)) {
            var f=fixture(); var saved=LivingKingdomsState.toNbt(f.state); var body=UUID.randomUUID();
            var row=saved.getCompoundOrEmpty("people").getListOrEmpty("records").getCompoundOrEmpty(0);
            row.putString("body",body.toString()); row.putLong("generation",1); row.putString("presence",loaded?"PRESENT":"DORMANT");
            var restored=LivingKingdomsState.fromNbt(saved); restored.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),0);
            if(loaded) assertTrue(restored.people().markLoaded(f.person,1,body));
            var r=new Fixture(restored,f.home,f.person,f.route,f.plan); start(r);
            assertFalse(restored.people().markLoaded(f.person,1,body));
            assertEquals(body,restored.marketTravel().takeRetired().getFirst().body());
            arrive(r); var p=restored.people().person(f.person).orElseThrow();
            var next=restored.people().reserve(p.id(),p.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(0),1100).orElseThrow();
            assertEquals(2,next.generation());
        }
    }
    @Test void removedDestinationStandRejectsBothFreshAndPreviouslyOfferedQuotes() {
        var f=fixture(); var visit=start(f); arrive(f); var actor=UUID.randomUUID();
        var quote=f.state.markets().quote(actor,visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).orElseThrow();
        assertEquals(KingdomMarketPlaces.Result.APPLIED,f.state.marketPlaces().remove(KingdomMarketPlacesTest.OWNER,KingdomMarketPlacesTest.TOWN,0,KingdomMarketPlacesTest.ANCHOR,0,1,0));
        assertTrue(f.state.markets().prepare(quote,actor,100,0,true,1100).isEmpty());
        assertTrue(f.state.markets().quote(actor,visit.id(),f.person,"coal",KingdomMarketLedger.Side.BUY,1,0,0,List.of(),1100).isEmpty());
    }
}
