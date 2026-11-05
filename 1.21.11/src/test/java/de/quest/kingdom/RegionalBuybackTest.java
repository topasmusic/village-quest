package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageNeed;
import de.quest.village.VillageCondition;
import de.quest.village.LivingVillageNetworkState.VillageSnapshot;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class RegionalBuybackTest {
    private static final Identifier COAL=Identifier.withDefaultNamespace("coal");
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static void proof(LivingKingdomsState state) {
        assertTrue(state.marketSources().rebuild(() -> NativeMarketSourceBuilder.regional(
                List.of(new MarketAcquisitionBounds.Acquisition(5,List.of(new MarketAcquisitionBounds.Goods(COAL,32)))),
                new NativeMarketRecipeRules.Reading(true,List.of()),item -> new NativeMarketRecipeRules.Reading(true,List.of()))));
    }
    private static void need(LivingKingdomsState state) {
        assertTrue(state.markets().observeHomeNeed(KingdomMarketPlacesTest.TOWN,KingdomMarketPlacesTest.OWNER,
                new VillageSnapshot(0,0,0,VillageBondType.FORGE,VillageNeed.FORGE_FUEL_AND_ORE,55,VillageCondition.fromSupport(55),0,0,0,0,0,0)));
    }
    @Test void positiveNeedOnlyCoalOfferPersistsAndSharesItsQuotaAcrossTradersAndPlayers() {
        var state=KingdomMarketPlacesTest.state(0); proof(state);
        assertTrue(state.markets().buybackOffer(KingdomMarketPlacesTest.TOWN).isEmpty()); need(state);
        var request=state.markets().buybackOffer(KingdomMarketPlacesTest.TOWN).orElseThrow();
        assertEquals(0,request.stock());
        var nativePerson=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var nativePlan=KingdomMarketVisitors.plan(state,nativePerson,VillageBondType.FORGE,List.of());
        assertEquals(request,nativePlan.goods().getLast()); assertTrue(nativePlan.goods().size()<=8);
        var plans=java.util.stream.IntStream.range(0,2).mapToObj(i -> {
            var person=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,i).orElseThrow();
            return new KingdomMarketLedger.TraderPlan(person.id(),person.village(),List.of(request));
        }).toList();
        var visit=state.markets().start(KingdomMarketPlacesTest.TOWN,plans,0).orElseThrow();
        var ledger=state.markets();
        assertTrue(ledger.quote(UUID.randomUUID(),visit.id(),plans.getFirst().person(),request.sku(),KingdomMarketLedger.Side.BUY,1,0,0,0).isEmpty());
        for(int n=0;n<4;n++) {
            var person=plans.get(n%2).person(); var actor=UUID.randomUUID();
            var q=ledger.quote(actor,visit.id(),person,request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
            assertTrue(ledger.sourceAllows(q)); assertTrue(q.price()<5);
            var stale=ledger.quote(UUID.randomUUID(),visit.id(),plans.get((n+1)%2).person(),request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
            var r=ledger.prepare(q,actor,0,32,true,0).orElseThrow();
            assertTrue(ledger.prepare(stale,stale.actor(),0,32,true,0).isEmpty());
            assertTrue(ledger.commit(r.id(),r.afterBalance(),r.afterItems()));
        }
        assertTrue(ledger.buybackOffer(KingdomMarketPlacesTest.TOWN).isEmpty());
        var saved=LivingKingdomsState.toNbt(state); var restored=LivingKingdomsState.fromNbt(saved);
        assertFalse(restored.markets().readOnly()); assertEquals(saved,LivingKingdomsState.toNbt(restored));
        proof(restored); need(restored); restored.anchors().observe(KingdomMarketPlacesTest.ANCHOR,0,KingdomMarketPlacesTest.report(),0);
        assertTrue(restored.markets().buybackOffer(KingdomMarketPlacesTest.TOWN).isEmpty());
        assertTrue(restored.markets().quote(UUID.randomUUID(),visit.id(),plans.getFirst().person(),request.sku(),KingdomMarketLedger.Side.SELL,1,0,0,0).isEmpty());
    }
    @Test void cheapCoalBlocksOrForeignCoalRecipesCannotCreatePositiveArbitrage() {
        var block=Identifier.withDefaultNamespace("coal_block");
        var inverse=new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(
                List.of(new MarketAcquisitionBounds.Goods(block,1)))),List.of(new MarketAcquisitionBounds.Goods(COAL,9)));
        var proof=NativeMarketSourceBuilder.regional(List.of(new MarketAcquisitionBounds.Acquisition(1,List.of(new MarketAcquisitionBounds.Goods(block,64)))),
                new NativeMarketRecipeRules.Reading(true,List.of(inverse)),item -> new NativeMarketRecipeRules.Reading(true,List.of()));
        assertTrue(proof.complete());
        assertEquals(0,MarketPricePolicy.buy(1,32,15,6,List.of(proof.bounds().source(COAL))));
        var foreign=new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(
                List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("wheat"),1)))),List.of(new MarketAcquisitionBounds.Goods(COAL,1)));
        assertFalse(NativeMarketSourceBuilder.regional(List.of(new MarketAcquisitionBounds.Acquisition(5,List.of(new MarketAcquisitionBounds.Goods(COAL,32)))),
                new NativeMarketRecipeRules.Reading(true,List.of(foreign)),item -> new NativeMarketRecipeRules.Reading(true,List.of())).complete());
    }
}
