package de.quest.ambient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;

/** Direct real placement/journal boundaries with mocks, without world ticks or Ingame simulation. */
final class EncounterQuestIntegrationTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void savedVillageIdentityNotOnlyNearbyCoordinatesIsRequired(){
        var state=new de.quest.kingdom.LivingKingdomsState();
        // A target that no longer exists must never be completed by an unrelated nearby villager.
        var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var p=new EncounterQuestProgress(null);
        p.accept(EncounterQuestProgress.Kind.GREETINGS,1);p.text("village",UUID.randomUUID().toString());
        p.number("target",BlockPos.ZERO.asLong());
        // The actual resolver must reject the missing identity (not silently accept a radius).
        assertFalse(EncounterQuestService.greetingVillage(state,p,"minecraft:overworld",BlockPos.ZERO));
    }
    @Test void nearbyDifferentVillageAndOtherDimensionDoNotReceiveTheGreeting(){
        var state=new de.quest.kingdom.LivingKingdomsState();
        var key=de.quest.village.VillageLifeState.VillageKey.overworld(0,0);
        var target=state.registerGenerated(key,new BlockPos(0,64,0),"Target").orElseThrow();
        state.registerGenerated(de.quest.village.VillageLifeState.VillageKey.overworld(40,0),new BlockPos(40,64,0),"Other").orElseThrow();
        var p=new EncounterQuestProgress(null);p.accept(EncounterQuestProgress.Kind.GREETINGS,1);p.text("village",target.id().toString());
        assertTrue(EncounterQuestService.greetingVillage(state,p,"minecraft:overworld",new BlockPos(1,64,0)));
        assertFalse(EncounterQuestService.greetingVillage(state,p,"minecraft:overworld",new BlockPos(39,64,0)));
        assertFalse(EncounterQuestService.greetingVillage(state,p,"minecraft:the_nether",new BlockPos(0,64,0)));
    }
    @Test void progressAndTargetNameRemainVisibleInTheActualJournal(){
        var player=mock(ServerPlayer.class);var p=new EncounterQuestProgress(null);
        p.accept(EncounterQuestProgress.Kind.LIGHT,1);p.advance(1);p.number("origin",BlockPos.ZERO.asLong());
        try(var service=mockStatic(EncounterQuestService.class,CALLS_REAL_METHODS)){
            service.when(()->EncounterQuestService.progress(player)).thenReturn(p);
            var details=EncounterQuestService.journal(player).getFirst().details();
            assertTrue(details.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("quest.village-quest.encounter.progress")),"Torch journal must show 1 / 2, not only a fixed objective");
        }
    }
    @Test void unknownTokenHasNoInventoryOrProgressSideEffects(){
        var player=mock(ServerPlayer.class);
        EncounterQuestService.handle(player,new de.quest.network.EncounterQuestPayloads.Action(UUID.randomUUID(),1));
        verify(player).getUUID();verifyNoMoreInteractions(player);
    }
    @Test void fullInventoryDefersBothRewardsAndRetryPaysExactlyOnce(){
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
        var player=mock(ServerPlayer.class);var inventory=mock(net.minecraft.world.entity.player.Inventory.class);var world=mock(ServerLevel.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);when(player.level()).thenReturn(world);
        when(inventory.getItem(anyInt())).thenReturn(new ItemStack(Items.DIRT,64));
        var p=new EncounterQuestProgress(null);p.accept(EncounterQuestProgress.Kind.IRON,1);p.advance(1);assertTrue(p.complete(1));
        try(var service=mockStatic(EncounterQuestService.class,CALLS_REAL_METHODS);var books=mockStatic(de.quest.quest.QuestBookHelper.class);var trackers=mockStatic(de.quest.quest.QuestTrackerService.class)){
            service.when(()->EncounterQuestService.store(player,p)).thenAnswer(i->null);
            EncounterQuestService.finish(player,p);assertTrue(p.pending());verify(player,never()).giveExperienceLevels(anyInt());verify(inventory,never()).add(any());
            when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
            try{
                var field=net.minecraft.world.entity.player.Player.class.getDeclaredField("inventoryMenu");field.setAccessible(true);
                field.set(player,mock(net.minecraft.world.inventory.InventoryMenu.class));
            }catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
            EncounterQuestService.finish(player,p);EncounterQuestService.finish(player,p);
            verify(player,times(1)).giveExperienceLevels(2);verify(inventory,times(1)).add(argThat(stack->stack.is(Items.EMERALD) && stack.getCount()==4));
            verify(world,times(1)).playSound(isNull(),nullable(BlockPos.class),eq(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP),eq(net.minecraft.sounds.SoundSource.PLAYERS),eq(.9f),eq(1f));
            assertFalse(p.active());assertEquals(1,p.paidDay());assertFalse(p.canOffer(1));
        }
    }
    @Test void outsideRadiusAndWrongDimensionNeverReadPlacementBlocks(){
        var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var p=new EncounterQuestProgress(null);
        p.accept(EncounterQuestProgress.Kind.LIGHT,1);p.text("dimension","minecraft:overworld");p.number("origin",0);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        try(var service=mockStatic(EncounterQuestService.class,CALLS_REAL_METHODS)){
            service.when(()->EncounterQuestService.progress(player)).thenReturn(p);
            EncounterQuestService.placed(world,player,new BlockPos(33,0,0));
            verify(world,never()).getBlockState(any());assertEquals(0,p.progress());
            p.text("dimension","minecraft:the_nether");EncounterQuestService.placed(world,player,BlockPos.ZERO);
            verify(world,never()).getBlockState(any());
        }
    }
    @Test void completionCardUsesQuestmasterTitleRewardsHoverAndExactTwoLevels(){
        Items.EMERALD.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable("item.minecraft.emerald"))
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        for(var kind:EncounterQuestProgress.Kind.values()){
            var card=EncounterQuestService.completionMessage(kind);var parts=new java.util.ArrayList<Component>();
            java.util.function.Consumer<Component> collect=new java.util.function.Consumer<>(){public void accept(Component c){parts.add(c);c.getSiblings().forEach(this);}};collect.accept(card);
            assertEquals(2,parts.stream().filter(c->c.getString().equals("------------------------------")).count());
            assertTrue(parts.stream().anyMatch(c->c.equals(de.quest.util.Texts.completedTitle(EncounterQuestService.tr(kind.key()+".title"),net.minecraft.ChatFormatting.GREEN))));
            assertTrue(parts.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("text.village-quest.daily.rewards")));
            assertTrue(parts.stream().anyMatch(c->c.getStyle().getHoverEvent()!=null),"Native item hover must survive");
            assertTrue(parts.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("screen.village-quest.questmaster.reward.experience") && java.util.Arrays.equals(new Object[]{"2"},t.getArgs())));
            assertTrue(card.getString().contains(" x4"));
        }
    }
}
