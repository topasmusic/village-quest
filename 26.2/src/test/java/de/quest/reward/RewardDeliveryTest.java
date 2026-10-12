package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import de.quest.data.QuestState;
import net.minecraft.SharedConstants;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.*;

final class RewardDeliveryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void bindTestComponents() {
        var stackable = net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        Items.GOLDEN_CARROT.builtInRegistryHolder().bindComponents(stackable);
        Items.POTION.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build());
        Items.DIAMOND_PICKAXE.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).set(DataComponents.MAX_DAMAGE,1561).set(DataComponents.DAMAGE,0).build());
    }
    @Test void realPotionAndEnchantmentComponentsRoundtrip() {
        var registries = VanillaRegistries.createLookup();
        var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.LONG_FIRE_RESISTANCE));
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),4);
        tool.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);
        tool.set(DataComponents.CUSTOM_NAME,Component.literal("Frozen reward"));
        for (var stack : List.of(potion,tool)) {
            var tag = RewardItems.encode(registries,List.of(stack)).getFirst(); var loaded = RewardItems.decode(registries,tag);
            assertTrue(ItemStack.isSameItemSameComponents(stack,loaded)); assertEquals(stack.getCount(),loaded.getCount());
        }
    }
    @Test void fullThenPartialInventoryRetainsExactItemsAndDoesNotRedeemTwice() throws Exception {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); var player = mock(ServerPlayer.class); var inventory = mock(Inventory.class);
        when(world.getServer()).thenReturn(server); when(world.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var id = UUID.randomUUID(); when(player.getUUID()).thenReturn(id); when(player.isAlive()).thenReturn(true); when(player.getInventory()).thenReturn(inventory);
        var method = QuestState.class.getDeclaredMethod("fromNbt",CompoundTag.class); method.setAccessible(true); var state = (QuestState)method.invoke(null,new CompoundTag());
        var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.STRONG_HEALING));
        var carrots = new ItemStack(Items.GOLDEN_CARROT,24); carrots.set(DataComponents.CUSTOM_NAME,Component.literal("Saved supplies"));
        var snapshot = new CompoundTag(); snapshot.put("items",RewardItems.encode(world,List.of(potion,carrots)));
        assertTrue(state.getPlayerData(id).vanillaRewards().accept("weekly","1",snapshot));
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            VanillaRewardService.complete(world,player,"weekly","1"); var rewards = state.getPlayerData(id).vanillaRewards();
            assertEquals(2,rewards.pending().size());
            VanillaRewardService.complete(world,player,"weekly","1"); assertEquals(2,rewards.pending().size());
            when(inventory.add(any(ItemStack.class))).thenAnswer(call -> { var stack=call.getArgument(0,ItemStack.class); if (stack.is(Items.GOLDEN_CARROT)) stack.shrink(5); return false; });
            assertEquals(5,VanillaRewardService.claim(world,player)); assertEquals(2,rewards.pending().size());
            var remaining = RewardItems.decode(world,rewards.pending().get(1)); assertEquals(19,remaining.getCount()); assertEquals(carrots.get(DataComponents.CUSTOM_NAME),remaining.get(DataComponents.CUSTOM_NAME));
            assertEquals(potion.get(DataComponents.POTION_CONTENTS),RewardItems.decode(world,rewards.pending().getFirst()).get(DataComponents.POTION_CONTENTS));
            when(inventory.add(any(ItemStack.class))).thenAnswer(call -> { call.getArgument(0,ItemStack.class).setCount(0); return true; });
            assertEquals(20,VanillaRewardService.claim(world,player)); assertTrue(rewards.pending().isEmpty()); assertEquals(0,VanillaRewardService.claim(world,player));
        }
    }
}
