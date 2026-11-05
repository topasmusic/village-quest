package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.guildtown.GuildTownService;
import de.quest.mixin.ItemEntityMixin;
import de.quest.mixin.ItemStackCraftMixin;
import de.quest.quest.daily.DailyQuestService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native event boundaries, no world/entity ticks or Mixin runtime. */
final class WritingSupplyEventsTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @AfterEach void clearSources() { QuestDropTracker.clear(); }

    @Test void naturalChickenAndSquidDropsReachDailyFanoutAfterFullInventoryMutation() {
        for (boolean chicken : new boolean[]{true, false}) {
            QuestDropTracker.clear();
            var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
            var owner=UUID.randomUUID(); when(player.getUUID()).thenReturn(owner); when(player.level()).thenReturn(world);
            var killed=mock(LivingEntity.class);
            doReturn(chicken ? EntityType.CHICKEN : EntityType.SQUID).when(killed).getType();
            Item item=chicken ? Items.FEATHER : Items.INK_SAC;
            var stack=new ItemStack(item,3); var drop=drop(stack);
            QuestDropTracker.onKilledOtherEntity(world,player,killed);
            QuestDropTracker.onEntityLoad(drop,world); // Real pre-inventory event path.
            assertEquals(owner,((TrackedQuestDrop)drop).villageQuest$getTrackedPlayerId());
            stack.setCount(0); // Vanilla Inventory.add has emptied the original stack.
            try (var daily=mockStatic(DailyQuestService.class)) {
                QuestDropTracker.onTrackedItemPickup(player,drop,new ItemStack(item,3),3);
                daily.verify(()->DailyQuestService.onTrackedItemPickup(eq(world),eq(player),
                        argThat(s->s.is(item)&&s.getCount()==3),eq(3)));
            }
        }
    }

    @Test void partialPickupKeepsOwnerAndOnlyCountsTakenUnits() {
        var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class); var stranger=mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID()); when(player.level()).thenReturn(world);
        when(stranger.getUUID()).thenReturn(UUID.randomUUID()); when(stranger.level()).thenReturn(world);
        var killed=mock(LivingEntity.class); doReturn(EntityType.CHICKEN).when(killed).getType();
        var stack=new ItemStack(Items.FEATHER,7); var drop=drop(stack);
        QuestDropTracker.onKilledOtherEntity(world,player,killed); QuestDropTracker.onEntityLoad(drop,world);
        stack.setCount(5);
        try(var daily=mockStatic(DailyQuestService.class)) {
            QuestDropTracker.onTrackedItemPickup(stranger,drop,new ItemStack(Items.FEATHER,2),2);
            QuestDropTracker.onTrackedItemPickup(player,drop,new ItemStack(Items.FEATHER,2),2);
            stack.setCount(0);
            QuestDropTracker.onTrackedItemPickup(player,drop,new ItemStack(Items.FEATHER,5),5);
            QuestDropTracker.onTrackedItemPickup(player,drop,ItemStack.EMPTY,0);
            daily.verify(()->DailyQuestService.onTrackedItemPickup(eq(world),eq(player),any(),eq(2)));
            daily.verify(()->DailyQuestService.onTrackedItemPickup(eq(world),eq(player),any(),eq(5)));
            daily.verifyNoMoreInteractions();
        }
    }

    @Test void playerRethrowsAndUnattributedPaperCannotAcquireFreshSourceCredit() {
        var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID()); when(player.level()).thenReturn(world);
        var killed=mock(LivingEntity.class); doReturn(EntityType.CHICKEN).when(killed).getType();
        QuestDropTracker.onKilledOtherEntity(world,player,killed);
        var rethrow=drop(new ItemStack(Items.FEATHER,3));
        when(((TrackedQuestDrop)rethrow).villageQuest$hasThrower()).thenReturn(true);
        QuestDropTracker.onEntityLoad(rethrow,world);
        var paper=drop(new ItemStack(Items.PAPER,12)); QuestDropTracker.onEntityLoad(paper,world);
        try(var daily=mockStatic(DailyQuestService.class)) {
            QuestDropTracker.onTrackedItemPickup(player,rethrow,new ItemStack(Items.FEATHER,3),3);
            QuestDropTracker.onTrackedItemPickup(player,paper,new ItemStack(Items.PAPER,12),12);
            daily.verifyNoInteractions();
        }
    }

    @Test void unassignedNaturalSupplyCannotMergeWithPlayerDiscards() throws Exception {
        var hook = mock(ItemEntityMixin.class, CALLS_REAL_METHODS);
        var other = drop(new ItemStack(Items.FEATHER, 1));
        var trackedOther = (TrackedQuestDrop) other;
        var merge = ItemEntityMixin.class.getDeclaredMethod(
                "villageQuest$preventTrackedDropMerges", ItemEntity.class, CallbackInfo.class);
        merge.setAccessible(true);

        for (boolean selfThrown : new boolean[]{false, true}) {
            doReturn(selfThrown).when(hook).villageQuest$hasThrower();
            when(trackedOther.villageQuest$hasThrower()).thenReturn(!selfThrown);
            var mixed = new CallbackInfo("tryToMerge", true);
            merge.invoke(hook, other, mixed);
            assertTrue(mixed.isCancelled(), "Discarded and natural feathers must remain separate before attribution");

            when(trackedOther.villageQuest$hasThrower()).thenReturn(selfThrown);
            var sameOrigin = new CallbackInfo("tryToMerge", true);
            merge.invoke(hook, other, sameOrigin);
            assertFalse(sameOrigin.isCancelled(), "Equal origins retain ordinary merging");
        }
    }

    @Test void paperCraftHookUsesActualQuantityAndIgnoresOtherItemsAndZero() throws Exception {
        var hook=mock(ItemStackCraftMixin.class,CALLS_REAL_METHODS);
        var player=mock(ServerPlayer.class); var world=mock(ServerLevel.class); when(player.level()).thenReturn(world);
        var method=ItemStackCraftMixin.class.getDeclaredMethod("villageQuest$paperCrafted",
                net.minecraft.world.entity.player.Player.class,int.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        method.setAccessible(true); when(hook.getItem()).thenReturn(Items.PAPER);
        try(var guild=mockStatic(GuildTownService.class)) {
            method.invoke(hook,player,12,null); method.invoke(hook,player,0,null);
            when(hook.getItem()).thenReturn(Items.BOOK); method.invoke(hook,player,12,null);
            guild.verify(()->GuildTownService.onTrackedItemPickup(eq(world),eq(player),
                    argThat(s->s.is(Items.PAPER)&&s.getCount()==12),eq(12)));
            guild.verifyNoMoreInteractions();
        }
    }

    private static ItemEntity drop(ItemStack stack) {
        var drop=mock(ItemEntity.class,withSettings().extraInterfaces(TrackedQuestDrop.class));
        when(drop.getItem()).thenReturn(stack); var tracked=(TrackedQuestDrop)drop;
        var owner=new AtomicReference<UUID>(); when(tracked.villageQuest$getTrackedPlayerId()).thenAnswer(i->owner.get());
        doAnswer(i->{owner.set(i.getArgument(0));return null;}).when(tracked).villageQuest$setTrackedPlayerId(any());
        return drop;
    }
}
