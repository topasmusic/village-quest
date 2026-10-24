package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Saved-state unit tests only; no Minecraft world or game loop. */
final class RegionalMarketTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void aYoungVillageHasASmallSaleOnlyRegionalLoad() {
        var state=KingdomMarketPlacesTest.state(0);
        var person=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        for(var type:VillageBondType.values()) {
            var goods=KingdomMarketVisitors.plan(state,person,type,List.of()).goods();
            assertTrue(goods.size()>=3 && goods.size()<=4,"Young village: 3–4 rows");
            assertTrue(goods.stream().allMatch(g -> g.buy()==0 && g.demand()==0),"A sale is not automatically a buyback");
            assertEquals(goods,KingdomMarketVisitors.plan(state,person,type,List.of()).goods());
        }
    }
    @Test void earnedStagesUnlockInfrastructureAndAllCatalogEntriesExist() {
        var state=KingdomMarketPlacesTest.state(0);
        var person=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        for(var type:VillageBondType.values()) for(int level=1;level<=5;level++) {
            var seen=new java.util.HashSet<String>();
            for(long date=0;date<60;date++) {
                var goods=KingdomMarketCatalog.regional(person,type,level,date,List.of()).goods();
                assertTrue(goods.size()<=8 && goods.size()>=(level==1?3:level<=3?4:5));
                assertEquals(goods.size(),goods.stream().map(KingdomMarketLedger.Goods::sku).distinct().count());
                for(var g:goods) {
                    assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(g.item()),g.sku());
                    assertFalse(java.util.Set.of("diamond","netherite_ingot","elytra","enchanted_book","wither_skeleton_skull").contains(g.sku()));
                    assertEquals(0,g.buy()); seen.add(g.sku());
                }
            }
            if(type==VillageBondType.FORGE) assertEquals(level>=2,seen.contains("rail"));
            if(type==VillageBondType.APIARY) assertEquals(level>=4,seen.contains("sticky_piston"));
        }
    }
    @Test void importsUseTheOriginsEarnedStageAndDoNotRequireTheHostsStage() {
        var state=KingdomMarketPlacesTest.state(0);
        var person=state.people().ensure(KingdomMarketPlacesTest.TOWN,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        boolean rails=false;
        for(long date=0;date<30;date++) {
            var goods=KingdomMarketCatalog.regional(person,VillageBondType.GRANARY,1,date,
                    List.of(new KingdomMarketCatalog.Partner(VillageBondType.FORGE,2))).goods();
            rails|=goods.stream().anyMatch(g -> g.sku().equals("rail"));
            assertTrue(goods.size()<=4);
        }
        assertTrue(rails,"A real forge connection supplies useful rails regularly");
    }
    @Test void nativePlanningUsesEarnedRecognitionAndCannotUpgradeAnAlreadySavedVisit() {
        var state=KingdomMarketPlacesTest.state(0); var home=KingdomMarketPlacesTest.TOWN;
        var person=state.people().ensure(home,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var young=KingdomMarketVisitors.plan(state,person,VillageBondType.FORGE,List.of());
        var visit=state.markets().start(home,List.of(young),0).orElseThrow();
        for(int project=0;project<5;project++) assertTrue(state.recognition().complete(KingdomMarketPlacesTest.OWNER,home,0,project,project,KingdomRecognition.requirements(project)));
        var developed=KingdomMarketVisitors.plan(state,person,VillageBondType.FORGE,List.of());
        assertTrue(developed.goods().size()>=5);
        assertEquals(visit,state.markets().start(home,List.of(developed),0).orElseThrow());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(visit.traders(),restored.markets().visit(home).orElseThrow().traders());
    }
}
