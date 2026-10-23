package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.economy.CurrencyService;
import de.quest.data.QuestState;
import de.quest.shop.ShopService;
import de.quest.shop.ShopOffer;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.*;

final class ShopPaymentDeliveryTest {
    @BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void allEmptyDeliveriesFailButMixedGoodsStillDeliver() throws Exception {
        var player=mock(ServerPlayer.class);var inventory=mock(net.minecraft.world.entity.player.Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        var field=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");field.setAccessible(true);field.set(player,mock(net.minecraft.world.inventory.InventoryMenu.class));
        when(inventory.add(any(ItemStack.class))).thenAnswer(a->{((ItemStack)a.getArgument(0)).setCount(0);return true;});
        var method=ShopService.class.getDeclaredMethod("deliver",ServerPlayer.class,ItemStack[].class);method.setAccessible(true);
        for(var goods:new ItemStack[][]{{ItemStack.EMPTY},{null},{null,ItemStack.EMPTY}})
            assertFalse(((ShopOffer.PurchaseResult)method.invoke(null,player,goods)).success());
        assertTrue(((ShopOffer.PurchaseResult)method.invoke(null,player,new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.BREAD)})).success());
        verify(inventory,times(1)).add(any(ItemStack.class));
    }
    private boolean purchase(ServerLevel world,ServerPlayer player,List<ItemStack> goods,long price) throws Exception {
        return (boolean)VanillaRewardService.class.getMethod("enqueuePurchase",ServerLevel.class,ServerPlayer.class,List.class,long.class)
                .invoke(null,world,player,goods,price);
    }
    @Test void failedDebitNeverAddsClaimOrDeliversAndSuccessCommitsClaimBeforeDelivery() throws Exception {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);
        var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
        var state=new PlayerRewardState();var quest=mock(QuestState.class);var goods=List.of(new ItemStack(Items.BREAD));
        var raw=new CompoundTag();raw.putString("test","bread");var encoded=new ListTag();encoded.add(raw);
        try(var reward=mockStatic(VanillaRewardService.class,CALLS_REAL_METHODS);var items=mockStatic(RewardItems.class);var money=mockStatic(CurrencyService.class);var quests=mockStatic(QuestState.class)) {
            reward.when(()->VanillaRewardService.state(world,id)).thenReturn(state);
            items.when(()->RewardItems.encode(world,goods)).thenReturn(encoded);quests.when(()->QuestState.get(server)).thenReturn(quest);
            money.when(()->CurrencyService.removeBalance(world,id,10)).thenReturn(false);
            assertFalse(purchase(world,player,goods,10));assertTrue(state.pending().isEmpty());
            verify(player,never()).getInventory();
            money.when(()->CurrencyService.removeBalance(world,id,10)).thenReturn(true);
            when(player.isAlive()).thenReturn(true);
            var inventory=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(inventory);
            items.when(()->RewardItems.decode(eq(world),any(net.minecraft.nbt.Tag.class))).thenAnswer(a->{assertEquals(1,state.pending().size());return new ItemStack(Items.BREAD);});
            items.when(()->RewardItems.encode(eq(world),anyList())).thenReturn(encoded);
            assertTrue(purchase(world,player,goods,10));assertEquals(1,state.pending().size(),"Full inventory must retain claim");
            verify(inventory,times(1)).add(any(ItemStack.class));
            money.verify(()->CurrencyService.addBalance(world,id,10),never());
        }
    }
    @Test void unavailableOrEmptyPreparationNeverDebits() throws Exception {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);when(server.isSameThread()).thenReturn(true);
        var player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(UUID.randomUUID());
        try(var money=mockStatic(CurrencyService.class);var rewards=mockStatic(VanillaRewardService.class,CALLS_REAL_METHODS);var items=mockStatic(RewardItems.class)) {
            rewards.when(()->VanillaRewardService.state(eq(world),any())).thenReturn(new PlayerRewardState());
            assertFalse(purchase(world,player,List.of(ItemStack.EMPTY),10));
            var full=new PlayerRewardState();var pending=new ListTag();for(int i=0;i<240;i++)pending.add(new CompoundTag());full.pending(pending);
            rewards.when(()->VanillaRewardService.state(eq(world),any())).thenReturn(full);
            assertFalse(purchase(world,player,List.of(new ItemStack(Items.BREAD)),10));
            rewards.when(()->VanillaRewardService.state(eq(world),any())).thenReturn(new PlayerRewardState());
            items.when(()->RewardItems.encode(eq(world),anyList())).thenThrow(new IllegalArgumentException("bad components"));
            assertFalse(purchase(world,player,List.of(new ItemStack(Items.BREAD)),10));
            money.verifyNoInteractions();
        }
    }
}
