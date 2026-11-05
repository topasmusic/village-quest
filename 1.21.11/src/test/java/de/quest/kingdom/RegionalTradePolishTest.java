package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageNeed;
import de.quest.village.VillageCondition;
import de.quest.village.LivingVillageNetworkState.VillageSnapshot;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Offline ledger/catalog tests; injected credits test request mechanics, not native source approval. */
final class RegionalTradePolishTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void everyNeedSelectsOnlyMatchingProvenRequestsAndNoColdOrWrongNeedCanPay() {
        for(var need:VillageNeed.values()) {
            var state=KingdomMarketPlacesTest.state(0); var home=KingdomMarketPlacesTest.TOWN;
            confirm(state,need);
            assertTrue(state.markets().buybackOffer(home).isEmpty());
            var credits=new HashMap<Identifier,Long>();
            for(var c:RegionalBuybackPolicy.CANDIDATES) credits.put(id(c.item()),1_000_000L);
            assertTrue(state.marketSources().rebuild(() -> MarketSourceClosure.reviewed(credits)));
            var selected=new HashSet<Identifier>();
            for(int seed=0;seed<20;seed++) {
                var request=state.markets().buybackOffer(home,seed).orElseThrow(); selected.add(request.item());
                assertTrue(need.matches(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(request.item()).orElseThrow().value()));
                assertEquals(0,request.stock()); assertTrue(request.demand()<=4);
            }
            assertEquals(need==VillageNeed.GRANARY_SEED_RESERVES || need==VillageNeed.GRANARY_PANTRY || need==VillageNeed.FORGE_FUEL_AND_ORE
                    || need==VillageNeed.PASTURE_HUSBANDRY || need==VillageNeed.APIARY_FORAGE
                    || need==VillageNeed.ARCHIVE_WRITING_SUPPLIES?2:1,selected.size());
            state.markets().invalidateHomeNeed(home);
            assertTrue(state.markets().buybackOffer(home).isEmpty());
        }
    }
    @Test void eachCandidateSharesTheCycleCapAcrossPlayersAndTradersAndSurvivesReloadWithoutReroll() {
        for(var candidate:RegionalBuybackPolicy.CANDIDATES) {
            var item=id(candidate.item());
            var need=Arrays.stream(VillageNeed.values()).filter(n -> n.matches(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item).orElseThrow().value())).findFirst().orElseThrow();
            var state=KingdomMarketPlacesTest.state(0); confirm(state,need);
            state.marketSources().rebuild(() -> MarketSourceClosure.reviewed(Map.of(item,1_000_000L)));
            var request=state.markets().buybackOffer(KingdomMarketPlacesTest.TOWN).orElseThrow();
            var plans=new ArrayList<KingdomMarketLedger.TraderPlan>();
            for(int slot=0;slot<4;slot++) {
                var person=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,slot).orElseThrow();
                plans.add(new KingdomMarketLedger.TraderPlan(person.id(),person.village(),List.of(request)));
            }
            var visit=state.markets().start(KingdomMarketPlacesTest.TOWN,plans,0).orElseThrow();
            int accepted=0;
            for(var plan:plans) for(int lot=0;lot<request.demand() && accepted+request.lot()<=128;lot++) {
                var actor=UUID.randomUUID();
                var q=state.markets().quote(actor,visit.id(),plan.person(),request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
                var stale=state.markets().quote(UUID.randomUUID(),visit.id(),plan.person(),request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
                assertTrue(state.markets().sourceAllows(q));
                var receipt=state.markets().prepare(q,actor,0,request.lot(),true,0).orElseThrow();
                assertTrue(state.markets().prepare(stale,stale.actor(),0,request.lot(),true,0).isEmpty());
                assertTrue(state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems())); accepted+=request.lot();
            }
            assertEquals(128,accepted,candidate.item());
            var savedVisit=state.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow();
            var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
            assertEquals(savedVisit,restored.markets().visit(KingdomMarketPlacesTest.TOWN).orElseThrow());
            restored.marketSources().rebuild(() -> MarketSourceClosure.reviewed(Map.of(item,1_000_000L))); confirm(restored,need);
            assertTrue(restored.markets().buybackOffer(KingdomMarketPlacesTest.TOWN).isEmpty());
        }
    }
    @Test void developedForgeImportsFavorRailsAndCartsWithoutGuaranteeingEither() {
        var person=KingdomMarketPlacesTest.state(0).people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        int core=0,other=0;
        for(int date=0;date<120;date++) {
            var first=KingdomMarketCatalog.regional(person,VillageBondType.GRANARY,1,date,List.of(new KingdomMarketCatalog.Partner(VillageBondType.FORGE,5))).goods().getFirst();
            if(first.sku().equals("rail") || first.sku().equals("minecart")) core++; else other++;
        }
        assertTrue(core>=30 && other>=30,"Useful imports recur but do not become a guaranteed shop: "+core+"/"+other);
    }
    @Test void lastStageShipsLargerDearerFiniteLotsAndAllDiscountedSalesHaveAcquisitionBounds() {
        var person=KingdomMarketPlacesTest.state(0).people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var bounds=MarketAcquisitionBounds.solve(KingdomMarketCatalog.acquisitions(),List.of(),List.of());
        for(var type:VillageBondType.values()) {
            boolean larger=false;
            for(int date=0;date<60;date++) {
                var iv=KingdomMarketCatalog.regional(person,type,4,date,List.of()).goods();
                var v=KingdomMarketCatalog.regional(person,type,5,date,List.of()).goods();
                for(var g:v) {
                    assertTrue(g.stock()*g.lot()<=128);
                    var old=iv.stream().filter(x -> x.item().equals(g.item())).findFirst();
                    if(old.isPresent() && g.lot()>old.get().lot()) { larger=true; assertTrue(g.sell()>=old.get().sell()*2); }
                    assertTrue(bounds.maximumPayout(g.item(),g.lot())<=MarketPricePolicy.sell(g.sell(),-15,15));
                }
            }
            assertTrue(larger,type.toString());
        }
    }
    private static Identifier id(String item) { return Identifier.withDefaultNamespace(item); }
    private static void confirm(LivingKingdomsState state,VillageNeed need) {
        assertTrue(state.markets().observeHomeNeed(KingdomMarketPlacesTest.TOWN,KingdomMarketPlacesTest.OWNER,
                new VillageSnapshot(0,0,0,need.villageType(),need,55,VillageCondition.fromSupport(55),0,0,0,0,0,0)));
    }
}
