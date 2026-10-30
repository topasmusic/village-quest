package de.quest.ambient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.ambient.EncounterQuestProgress.Kind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

/** Calls the actual inventory boundary, without a running server or world ticks. */
final class EncounterDeliveryCollectionTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
            .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());}
    private ServerPlayer player(ItemStack... stacks)throws Exception{
        var p=mock(ServerPlayer.class);var inv=mock(net.minecraft.world.entity.player.Inventory.class);
        when(p.getInventory()).thenReturn(inv);when(inv.getContainerSize()).thenReturn(stacks.length);
        when(inv.getItem(anyInt())).thenAnswer(a->stacks[(int)a.getArgument(0)]);
        var field=net.minecraft.world.entity.player.Player.class.getDeclaredField("inventoryMenu");field.setAccessible(true);field.set(p,mock(net.minecraft.world.inventory.InventoryMenu.class));return p;
    }
    @Test void acceptanceWithNoSuppliesWaitsAndNeverConsumesPartialFood()throws Exception{
        var bread=new ItemStack(Items.BREAD,1);var potato=new ItemStack(Items.BAKED_POTATO,2);var player=player(bread,potato);
        var p=new EncounterQuestProgress(null);assertTrue(p.accept(Kind.PROVISIONS,1));
        assertFalse(EncounterQuestService.collectDelivery(player,p));assertEquals(0,p.progress());
        assertEquals(1,bread.getCount());assertEquals(2,potato.getCount());verify(player.getInventory(),never()).setChanged();
    }
    @Test void collectionConsumesOnlyOneCompleteAlternativeAndNeverAgainAfterSave()throws Exception{
        var bread=new ItemStack(Items.BREAD,5);var potato=new ItemStack(Items.BAKED_POTATO,5);var player=player(bread,potato);
        var p=new EncounterQuestProgress(null);p.accept(Kind.PROVISIONS,1);
        try(var service=mockStatic(EncounterQuestService.class,CALLS_REAL_METHODS)){
            service.when(()->EncounterQuestService.store(player,p)).thenAnswer(a->null);
            assertTrue(EncounterQuestService.collectDelivery(player,p));assertEquals(3,bread.getCount());assertEquals(5,potato.getCount());
            assertFalse(EncounterQuestService.collectDelivery(player,p));assertFalse(EncounterQuestService.collectDelivery(player,new EncounterQuestProgress(p.snapshot())));
            assertEquals(3,bread.getCount());verify(player.inventoryMenu,times(1)).broadcastChanges();
        }
    }
    @Test void potatoesAreCollectedWhenBreadIsInsufficientAndIronUsesExactlyOne()throws Exception{
        for(var kind:new Kind[]{Kind.PROVISIONS,Kind.IRON}){
            var bread=new ItemStack(Items.BREAD,1);var cost=new ItemStack(kind==Kind.IRON?Items.IRON_INGOT:Items.BAKED_POTATO,5);
            var player=player(bread,cost);var p=new EncounterQuestProgress(null);p.accept(kind,1);
            try(var service=mockStatic(EncounterQuestService.class,CALLS_REAL_METHODS)){
                service.when(()->EncounterQuestService.store(player,p)).thenAnswer(a->null);
                assertTrue(EncounterQuestService.collectDelivery(player,p));assertEquals(kind==Kind.IRON?4:2,cost.getCount());assertEquals(1,bread.getCount());
            }
        }
    }
    @Test void noActiveQuestOrPendingRewardCannotTakeMaterials()throws Exception{
        var player=player(new ItemStack(Items.IRON_INGOT,8));var p=new EncounterQuestProgress(null);
        assertFalse(EncounterQuestService.collectDelivery(player,p));p.accept(Kind.IRON,1);p.advance(1);p.complete(1);
        assertFalse(EncounterQuestService.collectDelivery(player,p));verify(player,never()).getInventory();
    }
}
