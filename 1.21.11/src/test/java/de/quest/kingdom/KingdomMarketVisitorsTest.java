package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import net.minecraft.nbt.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class KingdomMarketVisitorsTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private static QuestState quests() { return QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow(); }
    @Test void realFounderProfileProducesOneSavedLocalDayVisitorWithoutCreatingObserverData() {
        var state=KingdomMarketPlacesTest.state(0); KingdomMarketPlacesTest.assign(state,0,0); var quests=quests(); var observer=UUID.randomUUID();
        assertTrue(KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,observer,2000,0,1,true,true).isEmpty());
        assertTrue(quests.getPlayersView().isEmpty());
        VillageContactService.establish(quests.getPlayerData(KingdomMarketPlacesTest.OWNER),state.village(KingdomMarketPlacesTest.TOWN).orElseThrow(),VillageBondType.FORGE);
        var visit=KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,observer,2000,0,1,true,true).orElseThrow();
        assertEquals(1,visit.traders().size()); assertForgeLoad(visit);
        assertFalse(quests.getPlayersView().containsKey(observer));
        var person=state.people().person(visit.traders().getFirst().person()).orElseThrow();
        var place=state.marketDays().place(KingdomMarketPlacesTest.TOWN,person.id(),0).orElseThrow();
        assertTrue(state.people().reserve(person.id(),person.revision(),UUID.randomUUID(),KingdomMarketPlacesTest.stand(1),0).isEmpty());
        var ticket=state.people().reserve(person.id(),person.revision(),UUID.randomUUID(),place.position(),0).orElseThrow(); state.people().commit(ticket);
        state.people().unloaded(person.id(),ticket.generation(),ticket.body(),place.position());
        var resumed=KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,observer,2001,1,1,true,true).orElseThrow();
        assertEquals(visit.id(),resumed.id()); assertEquals(person.id(),resumed.traders().getFirst().person());
        assertTrue(state.people().reserve(person.id(),state.people().person(person.id()).orElseThrow().revision(),UUID.randomUUID(),place.position(),1).isEmpty());
    }
    @Test void absenceAndAbandonmentCloseTradingWithoutReplacingTheRosterAndFiveSlotsRemainBounded() {
        var state=KingdomMarketPlacesTest.state(0); var quests=quests();
        for(int i=0;i<5;i++) KingdomMarketPlacesTest.assign(state,i,0);
        VillageContactService.establish(quests.getPlayerData(KingdomMarketPlacesTest.OWNER),state.village(KingdomMarketPlacesTest.TOWN).orElseThrow(),VillageBondType.APIARY);
        var visit=KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,UUID.randomUUID(),2000,0,5,true,true).orElseThrow();
        assertEquals(5,visit.traders().size());
        assertTrue(KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,UUID.randomUUID(),2001,1,5,true,false).isEmpty());
        assertFalse(state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().open());
        assertEquals(visit.id(),KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,UUID.randomUUID(),2002,2,5,true,true).orElseThrow().id());
    }
    @Test void anotherObserversPersonalProfileCannotRerollTheSameSharedPersonOnTheNextDay() {
        var state=new LivingKingdomsState(); var quests=quests(); var first=UUID.randomUUID(); var second=UUID.randomUUID();
        var town=state.registerGenerated(de.quest.village.VillageLifeState.VillageKey.overworld(0,0),KingdomMarketPlacesTest.POS,"Market town").orElseThrow();
        state.recognizeMediator(town.id(),first,true); long revision=state.village(town.id()).orElseThrow().revision();
        state.anchors().bind(first,town.id(),revision,KingdomMarketPlacesTest.ANCHOR,-1,KingdomMarketPlacesTest.POS,net.minecraft.core.Direction.NORTH,
                BuildingScanner.Range.BASIC,KingdomMarketPlacesTest.report(),0,0);
        state.marketPlaces().assign(first,town.id(),revision,KingdomMarketPlacesTest.ANCHOR,0,0,0,KingdomMarketPlacesTest.stand(0),KingdomMarketPlacesTest.counter(0),KingdomMarketPlacesTest.container(0),0);
        VillageContactService.establish(quests.getPlayerData(first),town,VillageBondType.FORGE);
        VillageContactService.establish(quests.getPlayerData(second),town,VillageBondType.PASTURE);
        var visit=KingdomMarketVisitors.observe(state,quests,town.id(),first,2000,0,1,true,true).orElseThrow();
        assertForgeLoad(visit);
        for(int tick=100;tick<=6100;tick+=100) {
            state.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),tick);
            KingdomMarketVisitors.observe(state,quests,town.id(),second,26000,tick,1,true,true);
        }
        var next=state.markets().visit(town.id()).orElseThrow(); assertNotEquals(visit.id(),next.id());
        assertForgeLoad(next);
        assertEquals(visit.traders().getFirst().person(),next.traders().getFirst().person());
        var network=de.quest.village.LivingVillageNetworkState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        network.ensureVillage(first,0,0,0,VillageBondType.FORGE);
        assertTrue(KingdomMarketDemandService.observe(state,quests,network,second,town.id()));
        assertEquals(first,state.markets().homeNeedSource(town.id()).orElseThrow().owner());
        var saved=LivingKingdomsState.toNbt(state); saved.getCompoundOrEmpty("marketHomes").getListOrEmpty("profiles").getCompoundOrEmpty(0).putString("futureNote","keep");
        var restored=LivingKingdomsState.fromNbt(saved);
        assertEquals(first,restored.marketHomes().profile(town.id()).orElseThrow().owner());
        assertEquals(VillageBondType.FORGE,restored.marketHomes().profile(town.id()).orElseThrow().type());
        assertEquals("keep",LivingKingdomsState.toNbt(restored).getCompoundOrEmpty("marketHomes").getListOrEmpty("profiles").getCompoundOrEmpty(0).getStringOr("futureNote",""));
    }
    private static void assertForgeLoad(KingdomMarketLedger.VisitView visit) {
        var goods=visit.traders().getFirst().goods(); assertTrue(goods.size()>=3 && goods.size()<=4);
        assertTrue(goods.stream().allMatch(g -> Set.of("coal","iron_ingot","copper_ingot","iron_chain","iron_bars","stone_bricks").contains(g.offer().sku())),
                "Personal observer differences must not replace the shared forge origin with pasture goods");
    }
    @Test void futureHomeAuthorityCannotBeCoercedIntoFreshVisitorsOrLostDuringSave() {
        var state=KingdomMarketPlacesTest.state(0); var quests=quests(); KingdomMarketPlacesTest.assign(state,0,0);
        VillageContactService.establish(quests.getPlayerData(KingdomMarketPlacesTest.OWNER),state.village(KingdomMarketPlacesTest.TOWN).orElseThrow(),VillageBondType.FORGE);
        var visit=KingdomMarketVisitors.observe(state,quests,KingdomMarketPlacesTest.TOWN,UUID.randomUUID(),2000,0,1,true,true).orElseThrow();
        var saved=LivingKingdomsState.toNbt(state); var future=new CompoundTag(); future.putInt("schema",99); future.putString("opaque","keep"); saved.put("marketHomes",future);
        var restored=LivingKingdomsState.fromNbt(saved); restored.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),10);
        assertTrue(restored.marketHomes().readOnly()); assertTrue(restored.marketDays().place(KingdomMarketPlacesTest.TOWN,visit.traders().getFirst().person(),10).isEmpty());
        assertTrue(KingdomMarketVisitors.observe(restored,quests,KingdomMarketPlacesTest.TOWN,UUID.randomUUID(),2010,10,1,true,true).isEmpty());
        assertEquals(future,LivingKingdomsState.toNbt(restored).get("marketHomes"));
        assertEquals(visit.id(),restored.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow().id());
    }
    @Test void earnedCapacityAffectsOnlyTheNextRegularVisitAndNeverRefillsTheCurrentDay() {
        var state=KingdomMarketPlacesTest.state(0); var town=KingdomMarketPlacesTest.TOWN; var owner=KingdomMarketPlacesTest.OWNER; var quests=quests();
        for(int i=0;i<5;i++) KingdomMarketPlacesTest.assign(state,i,0);
        VillageContactService.establish(quests.getPlayerData(owner),state.village(town).orElseThrow(),VillageBondType.FORGE);
        var first=KingdomMarketVisitors.observe(state,quests,town,owner,2000,0,true,true).orElseThrow();
        assertEquals(1,first.traders().size());
        for(int i=0;i<2;i++) assertTrue(state.recognition().complete(owner,town,0,i,i,EnumSet.allOf(KingdomRecognition.Evidence.class)));
        var same=KingdomMarketVisitors.observe(state,quests,town,owner,2001,1,true,true).orElseThrow();
        assertEquals(first.id(),same.id()); assertEquals(first.traders(),same.traders());
        for(int tick=100;tick<=6100;tick+=100) {
            state.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),tick);
            KingdomMarketVisitors.observe(state,quests,town,owner,26000,tick,true,true);
        }
        var next=state.markets().visit(town).orElseThrow(); assertNotEquals(first.id(),next.id());
        assertEquals(2,next.traders().size());
    }
}
