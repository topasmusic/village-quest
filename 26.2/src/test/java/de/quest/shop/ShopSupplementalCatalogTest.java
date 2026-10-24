package de.quest.shop;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ShopSupplementalCatalogTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void everySupplementalOfferUsesItsActualPriceAndDiscountedAcquisition() {
        assertEquals(32,ShopSupplementalCatalog.all().size());
        var covered=new java.util.HashSet<>(ShopAcquisitionCatalog.all().keySet());
        covered.addAll(ShopSupplementalCatalog.all().keySet());
        assertEquals(covered,ShopService.offerIds());
        assertTrue(ShopSupplementalCatalog.coversCurrentOffers());
        assertThrows(UnsupportedOperationException.class,() -> ShopService.offerIds().clear());
        var sources=de.quest.kingdom.MarketSourceClosure.catalogAcquisitions();
        ShopSupplementalCatalog.all().forEach((id,source) -> {
            assertEquals(source.price(),ShopService.offer(id).price(),id);
            assertTrue(sources.contains(source.acquisition()),id);
            assertEquals(1,source.acquisition().outputs().size());
            assertEquals(1,source.acquisition().outputs().getFirst().quantity());
        });
        assertEquals(43,ShopSupplementalCatalog.offer("honigfass").acquisition().price());
        assertEquals(13,ShopSupplementalCatalog.offer("gemaelde_good_doge").acquisition().price());
        assertEquals(30,ShopSupplementalCatalog.offer("apiary_charter_plaque").acquisition().price());
        assertThrows(UnsupportedOperationException.class,() -> ShopSupplementalCatalog.all().clear());
        assertThrows(IllegalArgumentException.class,() -> ShopSupplementalCatalog.offer("missing"));
    }
    @Test void actualHeadAndTrophyHandlersDeliverTheCatalogGoodsRatherThanTheDepictedMaterial() throws Exception {
        var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var inventory=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Inventory.class);
        org.mockito.Mockito.when(player.getInventory()).thenReturn(inventory);
        var menu=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menu.setAccessible(true); menu.set(player,org.mockito.Mockito.mock(net.minecraft.world.inventory.InventoryMenu.class));
        var received=new java.util.ArrayList<de.quest.kingdom.MarketAcquisitionBounds.Goods>();
        org.mockito.Mockito.when(inventory.add(org.mockito.ArgumentMatchers.any(net.minecraft.world.item.ItemStack.class))).thenAnswer(call -> {
            var stack=call.getArgument(0,net.minecraft.world.item.ItemStack.class);
            received.add(new de.quest.kingdom.MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(stack.getItem()),stack.getCount()));
            stack.setCount(0); return true;
        });
        for(var entry:ShopSupplementalCatalog.all().entrySet()) {
            var item=entry.getValue().item();
            if(item.equals(Identifier.withDefaultNamespace("painting")) || !item.getNamespace().equals("minecraft")) continue;
            received.clear(); assertTrue(ShopService.offer(entry.getKey()).purchaseHandler().purchase(null,player).success());
            assertEquals(entry.getValue().acquisition().outputs(),received,entry.getKey());
            var prepared=new java.util.ArrayList<de.quest.kingdom.MarketAcquisitionBounds.Goods>();
            for(var stack:ShopService.offer(entry.getKey()).purchaseHandler().prepareGoods(null))
                prepared.add(new de.quest.kingdom.MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(stack.getItem()),stack.getCount()));
            assertEquals(entry.getValue().acquisition().outputs(),prepared,entry.getKey()+" prepared stock");
        }
        assertEquals(Identifier.withDefaultNamespace("player_head"),ShopSupplementalCatalog.offer("decor_water_bucket").item());
        assertEquals(Identifier.parse("village-quest:watch_bell_reliquary"),ShopSupplementalCatalog.offer("watch_bell_reliquary").item());
    }
}
