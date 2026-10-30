package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

final class VillageIntroInventoryPlanTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void components() {
        var map = net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : VillageIntroInventoryPlan.TIMBER) item.builtInRegistryHolder().bindComponents(map);
        for (var item : java.util.List.of(Items.COBBLESTONE, Items.CRIMSON_STEM, Items.BOOK)) item.builtInRegistryHolder().bindComponents(map);
    }
    @Test void mixedLogsArePlannedBeforeAnyWriteAndReplayCannotConsumeTwice() {
        var bag = new SimpleContainer(41);
        bag.setItem(0, new ItemStack(Items.OAK_LOG, 7)); bag.setItem(1, new ItemStack(Items.BIRCH_LOG, 12));
        var plan = VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.REPAIR_TIMBER).orElseThrow();
        assertEquals(7, bag.getItem(0).getCount()); assertTrue(plan.apply(bag));
        assertTrue(bag.getItem(0).isEmpty()); assertEquals(3, bag.getItem(1).getCount());
        assertFalse(plan.apply(bag)); assertTrue(plan.matchesAfter(bag));
        assertEquals(2 + de.quest.economy.DeliveryEconomy.materialValue(Items.OAK_LOG, 7)
                + de.quest.economy.DeliveryEconomy.materialValue(Items.BIRCH_LOG, 9), plan.paidReward());
    }
    @Test void insufficientPlainLogsDoNotConsumeNamedGoodsNetherWoodOrEquipment() {
        var bag = new SimpleContainer(41); bag.setItem(0, new ItemStack(Items.OAK_LOG, 15));
        var named = new ItemStack(Items.OAK_LOG, 64); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        bag.setItem(1, named); bag.setItem(2, new ItemStack(Items.CRIMSON_STEM, 64)); bag.setItem(36, new ItemStack(Items.OAK_LOG, 64));
        assertTrue(VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.REPAIR_TIMBER).isEmpty());
        assertEquals(15, bag.getItem(0).getCount()); assertTrue(ItemStack.matches(named, bag.getItem(1)));
    }
    @Test void nativeReloadPreservesComponentsAndRejectsForgedPostimagesOrStaleSlots() {
        var bag = new SimpleContainer(36); bag.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        var book = new ItemStack(Items.BOOK); book.set(DataComponents.CUSTOM_NAME, Component.literal("My book")); bag.setItem(3, book);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var encoded = VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.PATH_STONE).orElseThrow().toNbt(registries);
        var restored = VillageIntroInventoryPlan.fromNbt(encoded, registries).orElseThrow();
        bag.setItem(1, bag.removeItemNoUpdate(0)); assertFalse(restored.apply(bag));
        bag.setItem(0, bag.removeItemNoUpdate(1)); assertTrue(restored.apply(bag));
        assertEquals(4, bag.getItem(0).getCount()); assertTrue(ItemStack.matches(book, bag.getItem(3)));
        encoded.getListOrEmpty("after").getCompoundOrEmpty(0).putInt("count", 20);
        assertTrue(VillageIntroInventoryPlan.fromNbt(encoded, registries).isEmpty());
    }
    @Test void harvestHasAnEmptyDeltaButStillRetainsAnExactInventoryProof() {
        var bag = new SimpleContainer(36);
        var plan = VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.HARVEST_HELP).orElseThrow();
        assertTrue(plan.matchesBefore(bag)); assertTrue(plan.matchesAfter(bag)); assertEquals(2, plan.paidReward());
        bag.setItem(0, new ItemStack(Items.BOOK)); assertFalse(plan.apply(bag));
    }
}
