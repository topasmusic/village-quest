package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.network.RewardPayloads;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import org.junit.jupiter.api.*;

final class RewardPreviewTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Items.RAIL.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build());
        Items.POTION.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build());
    }
    @Test void currencyUsesExactNonzeroDenominations() {
        var choice = new RewardPayloads.Choice("coins",List.of(Component.literal("ignored translated text")),31,List.of());
        var rows = RewardPreview.rows(choice);
        assertEquals(2,rows.size()); assertEquals("crown",rows.get(0).currency()); assertEquals(3,rows.get(0).count());
        assertEquals("silvermark",rows.get(1).currency()); assertEquals(1,rows.get(1).count());
        assertEquals(1,RewardPreview.rows(new RewardPayloads.Choice("coins",List.of(),20,List.of())).size());
    }
    @Test void splitStacksShowTotalWithoutMutatingPreviewOrCombiningDifferentComponents() {
        var rails = new ItemStack(Items.RAIL,64); var extra = new ItemStack(Items.RAIL,12);
        var healing = new ItemStack(Items.POTION); healing.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.STRONG_HEALING));
        var fire = new ItemStack(Items.POTION); fire.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.LONG_FIRE_RESISTANCE));
        var choice = new RewardPayloads.Choice("work",List.of(),0,List.of(rails,extra,healing,healing.copy(),fire));
        var rows = RewardPreview.rows(choice); assertEquals(3,rows.size()); assertEquals(76,rows.get(0).count());
        assertEquals(2,rows.get(1).count()); assertEquals(1,rows.get(2).count());
        assertEquals(64,rails.getCount()); assertEquals(12,extra.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(healing,rows.get(1).stack()));
    }
    @Test void networkPreviewRoundTripPreservesPotionComponentsCurrencyAndFollowingFields() {
        var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.STRONG_HEALING));
        var original = new RewardPayloads.View(UUID.randomUUID(),"saved",List.of(new RewardPayloads.Choice("supplies",List.of(Component.literal("Healing II")),31,List.of(potion))),"supplies");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {
            RewardPayloads.View.CODEC.encode(buffer,original); var decoded = RewardPayloads.View.CODEC.decode(buffer);
            assertEquals(original.session(),decoded.session()); assertEquals("supplies",decoded.preferred());
            assertEquals(31,decoded.choices().getFirst().currency());
            assertTrue(ItemStack.isSameItemSameComponents(potion,decoded.choices().getFirst().items().getFirst()));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }

}
