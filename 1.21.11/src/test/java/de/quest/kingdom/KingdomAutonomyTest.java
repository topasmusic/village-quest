package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

/** Isolated SavedData/policy calls; no server, world, entities or ingame simulation. */
final class KingdomAutonomyTest {
    @Test void remoteSavedRouteObservationsEarnOneDayWithoutProximityOrDuplicateCredit() {
        delivery(1);var autonomy=state.autonomy();autonomy.observeRemote(a,b,0,true,0,0,false);int notices=0;
        for(int tick=200;tick<=24000;tick+=200) {
            if(autonomy.observeRemote(a,b,tick,true,0,0,false))notices++;
            assertFalse(autonomy.observeRemote(b,a,tick,true,0,0,false));
        }
        assertEquals(1,notices);assertEquals(1,autonomy.between(a,b).orElseThrow().habit());
        var saved=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));saved.autonomy().observeRemote(a,b,1000000,true,0,0,false);
        assertEquals(1,saved.autonomy().between(a,b).orElseThrow().habit());
    }
    @org.junit.jupiter.api.BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private final LivingKingdomsState state=new LivingKingdomsState();
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),actor=UUID.randomUUID(),other=UUID.randomUUID(),route=UUID.randomUUID();
    private long now;
    KingdomAutonomyTest() {
        state.registerCustom(a,actor,VillageKey.overworld(0,0),new BlockPos(0,64,0),"A").orElseThrow();
        state.registerCustom(b,other,VillageKey.overworld(80,0),new BlockPos(80,64,0),"B").orElseThrow();
        state.registerCustom(c,actor,VillageKey.overworld(160,0),new BlockPos(160,64,0),"C").orElseThrow();
    }
    private void delivery(int sequence) { assertTrue(state.relations().delivered(a,b,actor,route,sequence)); }
    @Test void lateDirectKnownPartnerIsObservedWithoutExposingUnknownOrIndirectVillages() {
        var data=new de.quest.data.PlayerQuestData();
        for(int i=0;i<40;i++) {
            var village=state.registerCustom(new UUID(0,100+i),actor,VillageKey.overworld(1000+i*80,0),
                    new BlockPos(1000+i*80,64,0),"Contact "+i).orElseThrow();
            de.quest.shrine.VillageContactService.establish(data,village,de.quest.shrine.VillageBondType.FORGE);
        }
        var late=new UUID(0,139);
        var routes=List.of(
                new de.quest.caravan.KingdomRouteConnections.Connection(actor,route,a,late,"active"),
                new de.quest.caravan.KingdomRouteConnections.Connection(other,UUID.randomUUID(),late,a,"active"),
                new de.quest.caravan.KingdomRouteConnections.Connection(actor,UUID.randomUUID(),a,b,"active"),
                new de.quest.caravan.KingdomRouteConnections.Connection(actor,UUID.randomUUID(),a,new UUID(0,100),"stopped"),
                new de.quest.caravan.KingdomRouteConnections.Connection(actor,UUID.randomUUID(),late,new UUID(0,101),"active"));
        assertEquals(List.of(late),KingdomAutonomyService.observationPartners(
                KingdomDiplomacyService.contacts(state,data,a),routes,a,0).stream().map(LivingKingdomsState.VillageView::id).toList());
    }
    @Test void crowdedDirectContactsReceiveBoundedRepeatedObservationWindows() {
        var known=new ArrayList<LivingKingdomsState.VillageView>();
        var routes=new ArrayList<de.quest.caravan.KingdomRouteConnections.Connection>();
        for(int i=0;i<40;i++) {
            var id=new UUID(0,200+i);
            known.add(state.registerCustom(id,actor,VillageKey.overworld(5000+i*80,0),new BlockPos(5000+i*80,64,0),"Route "+i).orElseThrow());
            routes.add(new de.quest.caravan.KingdomRouteConnections.Connection(actor,UUID.randomUUID(),a,id,"active"));
        }
        var first=KingdomAutonomyService.observationPartners(known,routes,a,0);
        assertEquals(32,first.size());
        assertEquals(first,KingdomAutonomyService.observationPartners(known,routes,a,20),"consecutive observations can earn real contact time");
        var later=KingdomAutonomyService.observationPartners(known,routes,a,200);
        assertEquals(32,later.size());
        var all=new HashSet<>(first); all.addAll(later);
        assertEquals(40,all.size(),"no permanently excluded tail of direct contacts");
    }
    private int day(LivingKingdomsState s,int personality,boolean shortage,boolean twoObservers) {
        int notices=0; s.autonomy().observe(a,b,now,true,personality,0,shortage);
        for(int i=0;i<1200;i++) {
            now+=20;
            if(s.autonomy().observe(a,b,now,true,personality,0,shortage)) notices++;
            if(twoObservers) assertFalse(s.autonomy().observe(b,a,now,true,0,personality,shortage));
        }
        return notices;
    }
    @Test void realRepeatedFreightAndActiveDaysAllowOnlySmallDirectTrade() {
        delivery(1); assertEquals(1,day(state,0,false,true));
        assertEquals(KingdomDiplomacy.Type.FRIENDSHIP,state.autonomy().between(a,b).orElseThrow().request());
        assertTrue(state.diplomacy().contracts(a).isEmpty());
        delivery(2); delivery(3); assertEquals(0,day(state,0,false,true));
        var trade=state.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow();
        assertEquals(KingdomDiplomacy.Status.SIGNED,trade.status()); assertTrue(state.diplomacy().autonomous(trade));
        assertNull(trade.proposer()); assertNull(trade.acceptor());
        assertTrue(KingdomDiplomacyService.explain(state,actor,a,b).causes().containsAll(List.of("autonomous","reliable","initiative_bond")));
        var contacts=new de.quest.data.PlayerQuestData();
        assertTrue(KingdomDiplomacyService.view(state,contacts,actor,a,0).partners().isEmpty());
        de.quest.shrine.VillageContactService.establish(contacts,state.village(b).orElseThrow(),de.quest.shrine.VillageBondType.FORGE);
        assertEquals(1,KingdomDiplomacyService.view(state,contacts,actor,a,0).partners().size());
        assertTrue(state.diplomacy().between(a,b,KingdomDiplomacy.Type.FRIENDSHIP).isEmpty());
        assertTrue(state.diplomacy().between(a,b,KingdomDiplomacy.Type.SUPPLY).isEmpty());
        assertTrue(state.autonomy().between(b,c).isEmpty()); assertTrue(state.diplomacy().contracts(c).isEmpty());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertFalse(restored.diplomacy().readOnly()); assertFalse(restored.autonomy().readOnly());
        assertTrue(restored.diplomacy().autonomous(restored.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow()));
    }
    @Test void cautiousLeaderRequiresThirdReliableDayAndOldFreightDoesNotRepeat() {
        delivery(1); day(state,2,false,false); delivery(2); delivery(3); day(state,2,false,false);
        assertTrue(state.diplomacy().contracts(a).isEmpty());
        assertFalse(state.relations().delivered(b,a,actor,route,3));
        day(state,2,false,false); assertEquals(1,state.autonomy().between(a,b).orElseThrow().habit());
        delivery(4); day(state,2,false,false); delivery(5); day(state,2,false,false);
        assertEquals(KingdomDiplomacy.Terms.CAUTIOUS,state.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow().terms());
    }
    @Test void noOfflineUnknownRestartOrClockReplayCredit() {
        delivery(1); state.autonomy().observe(a,b,0,true,0,0,false);
        state.autonomy().observe(a,b,240000,true,0,0,false);
        assertEquals(0,state.autonomy().between(a,b).orElseThrow().habit());
        state.autonomy().observe(a,b,240020,true,0,0,false);
        var snapshot=state.autonomy().toNbt();
        state.autonomy().observe(a,b,240000,true,0,0,false); state.autonomy().observe(a,b,240020,true,0,0,false);
        assertEquals(snapshot,state.autonomy().toNbt(),"replayed interval cannot add a second tick credit");
        state.autonomy().observe(a,b,240040,false,0,0,false); state.autonomy().observe(a,b,480000,true,0,0,false);
        assertEquals(snapshot,state.autonomy().toNbt());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        restored.autonomy().observe(a,b,480020,true,0,0,false); assertEquals(snapshot,restored.autonomy().toNbt());
    }
    @Test void activeQuietDaysSoftenHabitWithoutCancellingPermanentTrade() {
        delivery(1); day(state,0,false,false); delivery(2); delivery(3); day(state,0,false,false);
        var signed=state.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow();
        day(state,0,false,false); day(state,0,false,false);
        assertEquals(0,state.autonomy().between(a,b).orElseThrow().habit()); assertNull(state.autonomy().between(a,b).orElseThrow().request());
        assertEquals(signed,state.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow());
    }
    @Test void initiativeCreatesNoSupplyConsentAndResolvedShortageIsNotAdvertisedForever() {
        delivery(1); assertEquals(1,day(state,1,true,false));
        assertEquals(KingdomDiplomacy.Type.SUPPLY,state.autonomy().between(a,b).orElseThrow().request());
        assertTrue(state.diplomacy().contracts(a).isEmpty());
        now+=20; state.autonomy().observe(a,b,now,true,1,0,false);
        assertNull(state.autonomy().between(a,b).orElseThrow().request());
        delivery(2); assertEquals(0,day(state,1,true,false));
        assertTrue(state.diplomacy().between(a,b,KingdomDiplomacy.Type.SUPPLY).isEmpty());
    }
    @Test void pendingPlayerOfferAndUnresolvedLoyaltyAreNotOverridden() {
        var offer=state.diplomacy().offer(actor,a,b,0,0,KingdomDiplomacy.Type.TRADE,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).orElseThrow();
        delivery(1); day(state,0,false,false); delivery(2); delivery(3); day(state,0,false,false);
        assertEquals(offer,state.diplomacy().between(a,b,KingdomDiplomacy.Type.TRADE).orElseThrow());
        assertTrue(state.diplomacy().accept(actor,offer.id(),offer.revision()).isEmpty(),"habit never replaces the other mandate");
        assertTrue(state.relations().remember(a,b,UUID.randomUUID(),"loyalty"));
        assertNull(state.autonomy().between(a,b).orElseThrow().request());
    }
    @Test void futureAndMalformedAutonomyArePreservedWithoutSideEffects() {
        for(boolean future:List.of(true,false)) {
            var saved=LivingKingdomsState.toNbt(state); var payload=new CompoundTag(); payload.putInt("schema",future?99:1);
            var rows=new ListTag(); if(!future) rows.add(new CompoundTag()); payload.put("pairs",rows); payload.putString("extra","preserve"); saved.put("autonomy",payload);
            var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.autonomy().readOnly());
            assertFalse(restored.autonomy().observe(a,b,0,true,0,0,false)); assertEquals(payload,LivingKingdomsState.toNbt(restored).get("autonomy"));
        }
    }
    @Test void autonomousMarkerCannotForgeSupplyOrPoliticalMandates() {
        var deal=state.diplomacy().offer(actor,a,b,0,0,KingdomDiplomacy.Type.FRIENDSHIP,-1,KingdomDiplomacy.Terms.BALANCED).orElseThrow();
        state.diplomacy().accept(other,deal.id(),deal.revision()).orElseThrow();
        var saved=LivingKingdomsState.toNbt(state); var diplomacy=(CompoundTag)saved.get("diplomacy");
        ((CompoundTag)((ListTag)diplomacy.get("contracts")).get(0)).putBoolean("autonomous",true);
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.diplomacy().readOnly());
        assertEquals(diplomacy,LivingKingdomsState.toNbt(restored).get("diplomacy"));
    }
    @Test void autonomousTradeCannotLoadWithoutItsCommittedFreightEvidence() {
        delivery(1); day(state,0,false,false); delivery(2); delivery(3); day(state,0,false,false);
        var saved=LivingKingdomsState.toNbt(state); var diplomacy=saved.get("diplomacy").copy();
        saved.remove("relations");
        var restored=LivingKingdomsState.fromNbt(saved); assertTrue(restored.diplomacy().readOnly());
        assertEquals(diplomacy,LivingKingdomsState.toNbt(restored).get("diplomacy"));
    }
}
