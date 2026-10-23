package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Native stack/container data only; no player, world, ticks or Ingame simulation. */
final class MarketInventoryPlanTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void itemComponents() {
        var stackable=net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:java.util.List.of(Items.WHEAT,Items.STONE,Items.BOOK)) item.builtInRegistryHolder().bindComponents(stackable);
    }
    @Test void purchaseFillsPartialStacksThenFreeSlotsWithoutTouchingEquipmentOrNamedGoods() {
        var bag=new SimpleContainer(41);
        bag.setItem(0,new ItemStack(Items.WHEAT,60));
        var named=new ItemStack(Items.WHEAT,63); named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep"));
        bag.setItem(1,named); bag.setItem(36,new ItemStack(Items.WHEAT,64));
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,70).orElseThrow();
        assertEquals(60,plan.beforeCount()); assertEquals(130,plan.afterCount());
        assertEquals(60,bag.getItem(0).getCount());
        assertTrue(plan.apply(bag)); assertEquals(64,bag.getItem(0).getCount());
        assertEquals(64,bag.getItem(2).getCount()); assertEquals(2,bag.getItem(3).getCount());
        assertTrue(ItemStack.matches(named,bag.getItem(1))); assertEquals(64,bag.getItem(36).getCount());
        assertFalse(plan.apply(bag));
    }
    @Test void fullInventoryAndInsufficientPlainGoodsRejectWithoutPartialMutation() {
        var bag=new SimpleContainer(36); for(int i=0;i<36;i++) bag.setItem(i,new ItemStack(Items.STONE,64));
        assertTrue(MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,1).isEmpty());
        var named=new ItemStack(Items.WHEAT,64); named.set(DataComponents.CUSTOM_NAME,Component.literal("Reserve")); bag.setItem(0,named);
        bag.setItem(1,new ItemStack(Items.WHEAT,4));
        assertTrue(MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.SELL,5).isEmpty());
        assertEquals(4,bag.getItem(1).getCount()); assertTrue(ItemStack.matches(named,bag.getItem(0)));
    }
    @Test void exactSlotAndComponentEvidencePreventsStaleApplicationEvenWithSameItemCounts() {
        var bag=new SimpleContainer(36); bag.setItem(0,new ItemStack(Items.WHEAT,32));
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.SELL,16).orElseThrow();
        bag.setItem(2,bag.removeItemNoUpdate(0));
        assertFalse(plan.matchesBefore(bag)); assertFalse(plan.apply(bag));
        bag.setItem(0,bag.removeItemNoUpdate(2)); bag.getItem(0).set(DataComponents.CUSTOM_NAME,Component.literal("Changed"));
        assertFalse(plan.apply(bag));
    }
    @Test void snapshotsRoundTripUnrelatedComponentsAndRemainDefensiveCopies() {
        var bag=new SimpleContainer(36); var book=new ItemStack(Items.BOOK);
        book.set(DataComponents.CUSTOM_NAME,Component.literal("My book")); bag.setItem(0,book); bag.setItem(1,new ItemStack(Items.WHEAT,12));
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.SELL,12).orElseThrow();
        var access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved=plan.toNbt(access);
        var restored=MarketInventoryPlan.fromNbt(saved,access).orElseThrow();
        saved.putInt("quantity",1);
        assertTrue(restored.matchesBefore(bag)); assertTrue(restored.apply(bag));
        assertTrue(bag.getItem(1).isEmpty()); assertTrue(ItemStack.matches(book,bag.getItem(0)));
        assertTrue(restored.matchesAfter(bag));
        assertTrue(MarketInventoryPlan.fromNbt(saved,access).isEmpty());
    }
    @Test void oversizedInvalidOrDecoratedTradeInputsAndTamperedSaveFailClosed() {
        var bag=new SimpleContainer(36); var wheat=new ItemStack(Items.WHEAT);
        assertTrue(MarketInventoryPlan.plan(bag,wheat,KingdomMarketLedger.Side.BUY,129).isEmpty());
        assertTrue(MarketInventoryPlan.plan(new SimpleContainer(35),wheat,KingdomMarketLedger.Side.BUY,1).isEmpty());
        wheat.set(DataComponents.CUSTOM_NAME,Component.literal("Not a commodity"));
        assertTrue(MarketInventoryPlan.plan(bag,wheat,KingdomMarketLedger.Side.BUY,1).isEmpty());
        var access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,1).orElseThrow().toNbt(access);
        saved.getListOrEmpty("after").getCompoundOrEmpty(0).putInt("count",64);
        assertTrue(MarketInventoryPlan.fromNbt(saved,access).isEmpty());
    }
    @Test void changedSlotRulesRejectBeforeAnyWriteAndSavedCapacityKeepsRestrictedStacks() {
        boolean[] blocked={false};
        var bag=new SimpleContainer(36) {
            @Override public int getMaxStackSize() { return 16; }
            @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot!=1 || !blocked[0]; }
        };
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,20).orElseThrow();
        var access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored=MarketInventoryPlan.fromNbt(plan.toNbt(access),access).orElseThrow();
        blocked[0]=true;assertFalse(restored.apply(bag));assertTrue(bag.isEmpty());
        blocked[0]=false;assertTrue(restored.apply(bag));
        assertEquals(16,bag.getItem(0).getCount());assertEquals(4,bag.getItem(1).getCount());
    }
    @Test void knownUnknownMetadataIsPreservedAndFutureOrTruncatedSnapshotsStayInert() {
        var bag=new SimpleContainer(36);var access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),KingdomMarketLedger.Side.BUY,12).orElseThrow();
        var saved=plan.toNbt(access);saved.putString("futureNote","retain without inventing slots");
        var restored=MarketInventoryPlan.fromNbt(saved,access).orElseThrow();
        assertEquals(saved,restored.toNbt(access));
        restored.commodity().set(DataComponents.CUSTOM_NAME,Component.literal("Copy only"));
        assertTrue(restored.apply(bag));assertNull(bag.getItem(0).get(DataComponents.CUSTOM_NAME));
        saved.putInt("schema",99);assertTrue(MarketInventoryPlan.fromNbt(saved,access).isEmpty());
        saved.putInt("schema",1);saved.getListOrEmpty("before").remove(0);
        assertTrue(MarketInventoryPlan.fromNbt(saved,access).isEmpty());
    }
}
