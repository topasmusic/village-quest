package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomMarketCatalogTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var normal=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(normal);
        var sixteen=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,16).build();
        net.minecraft.world.item.Items.EGG.builtInRegistryHolder().bindComponents(sixteen);
        net.minecraft.world.item.Items.HONEY_BOTTLE.builtInRegistryHolder().bindComponents(sixteen);
        net.minecraft.world.item.Items.WRITABLE_BOOK.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1).build());
    }
    @Test void fiveIdentitiesCoverTheirRealNeedsAndOfferFourStablePersonalitySpecialties() {
        for(var type:VillageBondType.values()) {
            var specialties=new HashSet<Identifier>();
            for(int personality=0;personality<4;personality++) {
                var goods=KingdomMarketCatalog.offers(type,personality);
                assertEquals(5,goods.size()); assertEquals(5,goods.stream().map(KingdomMarketLedger.Goods::sku).distinct().count());
                // Upkeep is ordinary mined stone, separate from these identity-specific market needs.
                for(var request:VillageRequestType.values()) if(request.bondType()==type
                        && java.util.Arrays.stream(de.quest.village.VillageNeed.values()).anyMatch(need -> need.matches(request)))
                    assertTrue(goods.stream().anyMatch(g -> g.item().equals(BuiltInRegistries.ITEM.getKey(request.item()))));
                specialties.add(goods.getLast().item());
                for(var offer:goods) {
                    assertNotNull(BuiltInRegistries.ITEM.getValue(offer.item()));
                    assertEquals("minecraft",offer.item().getNamespace());
                    int maximum=BuiltInRegistries.ITEM.getValue(offer.item()).getDefaultInstance().getMaxStackSize();
                    assertTrue(offer.lot()*offer.stock()<=maximum*2,"At most two actual stacks");
                    assertTrue(offer.lot()>=8,"Useful lots instead of filler");
                    assertTrue(offer.demand()*offer.lot()<=128);
                }
            }
            assertEquals(4,specialties.size(),type.name());
        }
    }
    @Test void savedPersonIdentityKeepsItsGoodsRatherThanRerollingOnBodyOrGuiChanges() {
        var state=new LivingKingdomsState(); var home=UUID.randomUUID();
        state.registerCustom(home,UUID.randomUUID(),VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        var person=state.people().ensure(home,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow();
        var plan=KingdomMarketCatalog.plan(person,VillageBondType.ARCHIVE);
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(plan,KingdomMarketCatalog.plan(restored.people().person(person.id()).orElseThrow(),VillageBondType.ARCHIVE));
        assertEquals(person.id(),plan.person()); assertEquals(home,plan.home());
        var leader=state.people().ensure(home,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
        assertThrows(IllegalArgumentException.class,() -> KingdomMarketCatalog.plan(leader,VillageBondType.ARCHIVE));
    }
    @Test void everyPossibleProfileConstrainsGlobalBuybackAtItsCheapestPermanentSale() {
        var sources=KingdomMarketCatalog.acquisitions();
        var bounds=MarketAcquisitionBounds.solve(sources,java.util.List.of());
        assertTrue(bounds.complete());
        for(var type:VillageBondType.values()) for(int personality=0;personality<4;personality++) {
            for(var goods:KingdomMarketCatalog.offers(type,personality)) {
                long buy=MarketPricePolicy.buy(1_000_000_000,goods.lot(),15,1_000_000_000,java.util.List.of(bounds.source(goods.item())));
                assertTrue(buy<MarketPricePolicy.sell(goods.sell(),-15,15));
            }
        }
        assertThrows(IllegalArgumentException.class,() -> KingdomMarketCatalog.offers(VillageBondType.GRANARY,4));
        assertThrows(IllegalArgumentException.class,() -> KingdomMarketCatalog.offers(null,0));
    }
    @Test void partnerSpecialtiesPreserveCoreIdentityAndAllCombinationsStayInsideExistingAcquisitionBounds() {
        var bounds=MarketAcquisitionBounds.solve(KingdomMarketCatalog.acquisitions(),java.util.List.of());
        for(var type:VillageBondType.values()) for(int personality=0;personality<4;personality++) for(int mask=0;mask<32;mask++) {
            var partners=new java.util.ArrayList<VillageBondType>();
            for(int i=0;i<5;i++) if((mask&(1<<i))!=0) partners.add(VillageBondType.values()[i]);
            var p=new KingdomPersonRegistry.View(UUID.randomUUID(),UUID.randomUUID(),KingdomPersonRegistry.Role.MARKET_TRADER,0,
                    0,0,null,BlockPos.ZERO,personality*8L,"Mara",false,KingdomPersonRegistry.Presence.DORMANT,0,null);
            var goods=KingdomMarketCatalog.plan(p,type,partners).goods();
            assertEquals(KingdomMarketCatalog.offers(type,personality),goods.subList(0,5));
            assertTrue(goods.size()<=8); assertEquals(goods.size(),goods.stream().map(KingdomMarketLedger.Goods::sku).distinct().count());
            for(var g:goods.subList(5,goods.size())) {
                assertEquals(0,g.buy()); assertEquals(0,g.demand());
                assertTrue(bounds.source(g.item()).price()>0,g.sku());
                long buy=MarketPricePolicy.buy(1_000_000_000,g.lot(),15,1_000_000_000,java.util.List.of(bounds.source(g.item())));
                assertTrue(buy<MarketPricePolicy.sell(g.sell(),-15,15),g.sku());
            }
        }
    }
}
