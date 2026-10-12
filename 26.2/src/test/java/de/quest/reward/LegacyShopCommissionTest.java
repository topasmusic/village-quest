package de.quest.reward;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.shop.ShopService;
import de.quest.economy.ProsperityService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.special.SpecialQuestService;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.junit.jupiter.api.*;

final class LegacyShopCommissionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void oldPaidHuntersAndProvisionsKeepTheirOldGoodsAndNewPaidHealingIsFrozen() throws Exception {
        var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item : List.of(Items.ARROW,Items.COOKED_BEEF,Items.GOLDEN_CARROT,Items.GOLD_INGOT,Items.GOLDEN_APPLE)) item.builtInRegistryHolder().bindComponents(components);
        Items.POTION.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build());
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);when(world.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var random=mock(net.minecraft.util.RandomSource.class);when(world.getRandom()).thenReturn(random);when(random.nextFloat()).thenReturn(0.9f);
        var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);when(player.isAlive()).thenReturn(true);var inventory=mock(Inventory.class);when(player.getInventory()).thenReturn(inventory);
        var menu=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");menu.setAccessible(true);menu.set(player,mock(InventoryMenu.class));
        var received=new ArrayList<ItemStack>();when(inventory.add(any(ItemStack.class))).thenAnswer(call -> { var stack=call.getArgument(0,ItemStack.class);received.add(stack.copy());stack.setCount(0);return true; });
        var load=QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class);load.setAccessible(true);var state=(QuestState)load.invoke(null,new CompoundTag());
        try(var lookup=mockStatic(QuestState.class);var shop=mockStatic(ShopService.class,CALLS_REAL_METHODS);var weekly=mockStatic(WeeklyQuestService.class);var special=mockStatic(SpecialQuestService.class);var prosperity=mockStatic(ProsperityService.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);shop.when(() -> ShopService.isOfferUnlocked(world,id,"hunters_satchel")).thenReturn(false);shop.when(() -> ShopService.isOfferUnlocked(world,id,"proviantbeutel")).thenReturn(false);
            assertTrue(ShopService.fulfillCommission(world,player,"hunters_satchel"));assertEquals(32,received.stream().filter(i->i.is(Items.ARROW)).mapToInt(ItemStack::getCount).sum());assertEquals(6,received.stream().filter(i->i.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum());
            received.clear();assertTrue(ShopService.fulfillCommission(world,player,"proviantbeutel"));assertEquals(24,received.stream().filter(i->i.is(Items.GOLDEN_CARROT)).mapToInt(ItemStack::getCount).sum());assertEquals(8,received.stream().filter(i->i.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum());
            received.clear();var data=state.getPlayerData(id);var snapshot=new CompoundTag();snapshot.putString("key","shop:hunters_satchel");snapshot.put("items",RewardItems.encode(world,RewardItems.create(world,RewardShopCatalog.get("hunters_satchel").goods())));assertTrue(data.vanillaRewards().accept("shop","paid:1",snapshot));data.setMilestoneFlag("reward.shop_snapshot",true);
            assertTrue(ShopService.fulfillCommission(world,player,"hunters_satchel"));assertEquals(2,received.stream().filter(i->i.is(Items.POTION)).mapToInt(ItemStack::getCount).sum());assertEquals(1,received.stream().filter(i->i.is(Items.GOLDEN_APPLE)).mapToInt(ItemStack::getCount).sum());assertFalse(received.stream().anyMatch(i->i.is(Items.ARROW)));assertFalse(data.hasMilestoneFlag("reward.shop_snapshot"));
        }
    }
}
