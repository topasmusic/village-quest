package de.quest.shop;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ShopAcquisitionCatalogTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void allSixActualRewardPacksAndEightUtilityKitsHaveAcquisitionContents() {
        assertEquals(Set.of("proviantbeutel","hunters_satchel","nether_supplies","light_supplies","rail_supplies","leather_supplies",
                "beekeepers_kit","road_camp_kit","village_ledger_desk","apiary_supply_crate","smithing_supply_rack",
                "market_stall_kit","pasture_tack_bundle","watch_post_kit"),ShopAcquisitionCatalog.all().keySet());
        for(var entry:ShopAcquisitionCatalog.all().entrySet()) {
            assertEquals(ShopService.offer(entry.getKey()).price(),entry.getValue().price());
            assertFalse(entry.getValue().goods().isEmpty());
        }
        var hunter=ShopAcquisitionCatalog.all().get("hunters_satchel");
        assertEquals("golden_apple",hunter.goods().get(0).item()); assertEquals(1,hunter.goods().get(0).count());
        assertEquals("strong_healing",hunter.goods().get(1).potion()); assertEquals(2,hunter.goods().get(1).count());
    }
    @Test void realUtilityStacksMatchReviewedQuantitiesAndPrices() {
        assertGoods("beekeepers_kit",24,Map.of("beehive",1,"campfire",1,"glass_bottle",8));
        assertGoods("road_camp_kit",30,Map.of("white_bed",1,"campfire",2,"lantern",16,"chest",4,"torch",64,"bread",32,"cooked_beef",24));
        assertGoods("village_ledger_desk",30,Map.of("lectern",1,"chiseled_bookshelf",2,"bookshelf",4,"item_frame",8,"lantern",4,"writable_book",1));
        assertGoods("apiary_supply_crate",33,Map.of("beehive",2,"campfire",2,"glass_bottle",32,"candle",8,"oak_trapdoor",16));
        assertGoods("smithing_supply_rack",36,Map.of("smithing_table",2,"grindstone",2,"lantern",4,"iron_chain",24,"iron_bars",32));
        assertGoods("market_stall_kit",33,Map.of("barrel",4,"lantern",8,"red_wool",32,"white_wool",32,"spruce_fence",16,"spruce_sign",8));
        assertGoods("pasture_tack_bundle",39,Map.of("lead",8,"hay_block",16,"oak_fence",32,"oak_fence_gate",4,"lantern",4,"white_carpet",8));
        assertGoods("watch_post_kit",45,Map.of("bell",2,"spyglass",1,"lantern",8,"soul_lantern",4,"torch",64,"dark_oak_fence",32));
    }
    @Test void lowestPriceUsesPermanentDiscountRoundingAndReturnedStacksAreDefensive() {
        assertEquals(11,ShopAcquisitionCatalog.minimumPrice("proviantbeutel"));
        assertEquals(12,ShopAcquisitionCatalog.minimumPrice("hunters_satchel"));
        assertEquals(29,ShopAcquisitionCatalog.minimumPrice("market_stall_kit"));
        assertEquals(34,ShopAcquisitionCatalog.minimumPrice("pasture_tack_bundle"));
        var first=ShopAcquisitionCatalog.utilityStacks("market_stall_kit"); first[0].setCount(1);
        assertEquals(4,ShopAcquisitionCatalog.utilityStacks("market_stall_kit")[0].getCount());
        assertThrows(UnsupportedOperationException.class,() -> ShopAcquisitionCatalog.all().clear());
        assertThrows(IllegalArgumentException.class,() -> ShopAcquisitionCatalog.utilityStacks("hunters_satchel"));
    }
    @Test void actualUtilityPurchaseHandlersDeliverThoseGoodsIntoTheExistingInventoryPath() throws Exception {
        var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var inventory=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Inventory.class);
        org.mockito.Mockito.when(player.getInventory()).thenReturn(inventory);
        var menu=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        menu.setAccessible(true); menu.set(player,org.mockito.Mockito.mock(net.minecraft.world.inventory.InventoryMenu.class));
        var received=new java.util.HashMap<String,Integer>();
        org.mockito.Mockito.when(inventory.add(org.mockito.ArgumentMatchers.any(net.minecraft.world.item.ItemStack.class))).thenAnswer(call -> {
            var stack=call.getArgument(0,net.minecraft.world.item.ItemStack.class);
            received.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),stack.getCount(),Integer::sum);
            stack.setCount(0); return true;
        });
        var utilities=new java.util.TreeSet<>(ShopAcquisitionCatalog.all().keySet());
        utilities.removeAll(de.quest.reward.RewardShopCatalog.all().keySet());
        for(var id:utilities) {
            received.clear(); assertTrue(ShopService.offer(id).purchaseHandler().purchase(null,player).success());
            var expected=new java.util.HashMap<String,Integer>();
            for(var goods:ShopAcquisitionCatalog.utility(id).goods()) expected.merge(goods.item(),goods.count(),Integer::sum);
            assertEquals(expected,received,id);
            var prepared=new java.util.HashMap<String,Integer>();
            for(var stack:ShopService.offer(id).purchaseHandler().prepareGoods(null))
                prepared.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),stack.getCount(),Integer::sum);
            assertEquals(expected,prepared,id+" preparation must preserve paid goods");
        }
    }
    private static void assertGoods(String id,long price,Map<String,Integer> expected) {
        assertEquals(price,ShopService.offer(id).price());
        var actual=new java.util.HashMap<String,Integer>();
        for(var stack:ShopAcquisitionCatalog.utilityStacks(id)) {
            assertTrue(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"));
            actual.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),stack.getCount(),Integer::sum);
        }
        assertEquals(expected,actual,id);
    }
}
