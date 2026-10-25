package de.quest.quest.special;

import de.quest.data.*;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure native component/persistence tests with mocked boundaries. No game execution. */
final class WayfarersTokenTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    @Test void legacyTokenUpgradesWithoutGrantingOrdinaryOrAnvilNamedFeathersTheEffect(){
        var ordinary=new ItemStack(Items.FEATHER);assertFalse(WayfarersTokenService.isToken(ordinary));
        ordinary.set(DataComponents.CUSTOM_NAME,Component.literal("Wayfarerâ€™s Token"));assertFalse(WayfarersTokenService.isToken(ordinary));
        var legacy=new ItemStack(Items.FEATHER,2);legacy.set(DataComponents.ITEM_NAME,FriendsQuestService.tr("simon.keepsake"));
        assertTrue(WayfarersTokenService.isToken(legacy));WayfarersTokenService.refresh(legacy);
        assertEquals(2,legacy.getCount());assertEquals(Component.empty().withStyle(ChatFormatting.GREEN).getStyle().getColor(),legacy.get(DataComponents.ITEM_NAME).getStyle().getColor());
        assertEquals(2,legacy.get(DataComponents.LORE).lines().size());
        assertFalse(legacy.get(DataComponents.CUSTOM_DATA).copyTag().contains("owner"));
        var snapshot=legacy.copy();WayfarersTokenService.refresh(legacy);assertTrue(ItemStack.matches(snapshot,legacy));
    }
    @Test void useAppliesSpeedTwoOnceAndSharedCooldownSurvivesLedgerRoundtripWhileGiftWorksForRecipient(){
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var state=mock(QuestState.class);
        var alice=mock(ServerPlayer.class);var bob=mock(ServerPlayer.class);var a=UUID.randomUUID();var b=UUID.randomUUID();
        var aliceData=new PlayerQuestData();var bobData=new PlayerQuestData();
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(world.getGameTime()).thenReturn(100L);
        when(alice.getUUID()).thenReturn(a);when(bob.getUUID()).thenReturn(b);when(alice.isAlive()).thenReturn(true);when(bob.isAlive()).thenReturn(true);
        when(state.getPlayerData(a)).thenReturn(aliceData);when(state.getPlayerData(b)).thenReturn(bobData);
        try(var boundary=mockStatic(QuestState.class)) {
            boundary.when(()->QuestState.get(server)).thenReturn(state);
            var token=WayfarersTokenService.create();assertTrue(WayfarersTokenService.use(world,alice,token));assertEquals(1,token.getCount());
            var effect=org.mockito.ArgumentCaptor.forClass(MobEffectInstance.class);verify(alice).addEffect(effect.capture());
            assertEquals(MobEffects.SPEED,effect.getValue().getEffect());assertEquals(1,effect.getValue().getAmplifier());assertEquals(600,effect.getValue().getDuration());
            assertFalse(WayfarersTokenService.use(world,alice,WayfarersTokenService.create()));verify(alice,times(1)).addEffect(any());
            verify(alice).sendSystemMessage(FriendsQuestService.tr("simon.token.recharging","5:00"),false);
            var restored=aliceData;
            assertEquals(6000,WayfarersTokenService.remaining(new FriendsQuestProgress(restored),100));when(state.getPlayerData(a)).thenReturn(restored);
            assertTrue(WayfarersTokenService.use(world,bob,token));verify(bob).addEffect(any());
            when(world.getGameTime()).thenReturn(6099L);assertFalse(WayfarersTokenService.use(world,alice,token));
            when(world.getGameTime()).thenReturn(6100L);assertTrue(WayfarersTokenService.use(world,alice,token));
        }
    }
    @Test void malformedCooldownFailsClosedAndForgeCostsMatchNewDelivery(){
        var progress=new FriendsQuestProgress(new PlayerQuestData());progress.text("simon.token.readyAt","broken");
        assertEquals(-1,WayfarersTokenService.remaining(progress,100));
        assertEquals(2,FriendsQuestService.forgeCosts().get(Items.ANCIENT_DEBRIS));assertFalse(FriendsQuestService.forgeCosts().containsKey(Items.NETHERITE_INGOT));
        assertEquals(16,FriendsQuestService.forgeCosts().get(Items.REDSTONE));assertEquals(8,FriendsQuestService.forgeCosts().get(Items.AMETHYST_SHARD));
    }
}
