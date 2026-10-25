package de.quest.quest.special;

import de.quest.network.FriendsQuestPayloads.*;
import de.quest.reward.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native registry/component checks only: never starts a server, world or simulation. */
final class FriendsQuestRewardsTest {
    static HolderLookup.Provider registries;
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        registries=VanillaRegistries.createLookup();
        for(var item:BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).set(DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).set(DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    @Test void nativeBookAndBothExclusiveToolsKeepActualNamesAndEnchantments(){
        var book=FriendsQuestRewards.create(registries,"lutte",2,false,new UUID(0,1));
        assertEquals(20,book.currency());assertEquals(3,book.experience());assertFalse(book.choice());
        var stack=book.items().getFirst();assertTrue(stack.is(Items.ENCHANTED_BOOK));
        assertEquals(3,stack.get(DataComponents.STORED_ENCHANTMENTS).getLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING)));
        var choice=FriendsQuestRewards.create(registries,"lutte",3,false,new UUID(0,1));
        assertTrue(choice.choice());assertEquals(2,choice.items().size());assertEquals(0,choice.currency());
        var pick=choice.items().get(0);var sword=choice.items().get(1);
        assertTrue(pick.is(Items.NETHERITE_PICKAXE));assertTrue(sword.is(Items.NETHERITE_SWORD));
        assertEquals(FriendsQuestService.tr("lutte.tool.precision"),pick.get(DataComponents.ITEM_NAME));
        var enchantments=registries.lookupOrThrow(Registries.ENCHANTMENT);
        assertEquals(4,pick.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.EFFICIENCY)));
        assertEquals(3,pick.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.UNBREAKING)));
        assertEquals(4,sword.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.SHARPNESS)));
        assertEquals(3,sword.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.UNBREAKING)));
    }
    @Test void potionQuantitiesTierDurationAndNamedKeepsakeMatchDelivery(){
        var reward=FriendsQuestRewards.create(registries,"simon",1,false,new UUID(0,1));
        assertEquals(10,reward.currency());assertEquals(1.5,reward.experience());assertEquals(3,reward.items().size());
        for(var stack:reward.items()){
            assertTrue(stack.is(Items.POTION));assertEquals(1,stack.getCount());
            var potion=stack.get(DataComponents.POTION_CONTENTS).potion().orElseThrow();assertEquals(Potions.STRONG_SWIFTNESS,potion);
            assertEquals(1,potion.value().getEffects().getFirst().getAmplifier());assertEquals(1800,potion.value().getEffects().getFirst().getDuration());
        }
        var token=FriendsQuestRewards.create(registries,"simon",2,false,new UUID(0,1)).items().getFirst();
        assertTrue(token.is(Items.FEATHER));assertEquals(FriendsQuestService.tr("simon.keepsake").withStyle(net.minecraft.ChatFormatting.GREEN),token.get(DataComponents.ITEM_NAME));
    }
    @Test void registryAwareViewRoundtripPreservesProgressAndRewardComponentsAndValueEquality(){
        var enchantments=new MappedRegistry<net.minecraft.world.item.enchantment.Enchantment>(Registries.ENCHANTMENT,com.mojang.serialization.Lifecycle.stable());
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h->enchantments.register(h.key(),h.value(),RegistrationInfo.BUILT_IN));enchantments.freeze();
        var access=new RegistryAccess.ImmutableRegistryAccess(java.util.stream.Stream.concat(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries(),java.util.stream.Stream.of(new RegistryAccess.RegistryEntry<>(Registries.ENCHANTMENT,enchantments))));
        var rewards=FriendsQuestRewards.create(access,"lutte",2,false,new UUID(0,1));
        var stack=rewards.items().getFirst();var text=Component.literal("book");
        var view=new View(new UUID(0,2),4,false,true,"lutte",2,true,text,text,text,List.of(text),text,List.of(),List.of(new Progress(stack,1,1)),List.of(new Option("claim",text,true)),rewards);
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),access);
        try {View.CODEC.encode(buffer,view);var decoded=View.CODEC.decode(buffer);assertEquals(view,decoded);assertEquals(0,buffer.readableBytes());assertTrue(ItemStack.matches(stack,decoded.progress().getFirst().stack()));}
        finally {buffer.release();}
    }
    @Test void rewardCollectionAndAmountsAreBounded(){
        assertThrows(IllegalArgumentException.class,()->new Rewards(0,Double.NaN,List.of(),false));
        assertThrows(IllegalArgumentException.class,()->new Rewards(-1,0,List.of(),false));
        assertThrows(IllegalArgumentException.class,()->new Rewards(0,0,Collections.nCopies(9,new ItemStack(Items.POTION)),false));
    }
}
