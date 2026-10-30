package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

final class PilgrimMaterialSourceTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Items.BLAZE_ROD.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.ITEM_NAME,Component.translatable(Items.BLAZE_ROD.getDescriptionId())).build());
    }
    @Test void netherExpeditionCanChooseRealBlazeRodsForBrewingAndEndAccessWithinTheExistingChoiceLimit() {
        for(var profile:RepeatableTargetProfile.values()) {
            var options=VanillaRewardRegistry.options("pilgrim:ash_on_the_pass",profile);
            var source=options.stream().filter(option->option.goods().stream().anyMatch(g->g.item().equals("blaze_rod"))).findFirst();
            assertTrue(source.isPresent(),"An icon alone cannot supply the Peaceful material dependency");
            assertTrue(options.size()<=3);assertFalse(source.orElseThrow().diamondGate());
            var delivered=RewardItems.create((HolderLookup.Provider)null,source.orElseThrow().goods());
            assertEquals(4,delivered.stream().filter(s->s.is(Items.BLAZE_ROD)).mapToInt(ItemStack::getCount).sum());
            assertTrue(options.stream().anyMatch(o->o.id().equals("coins")&&o.currency()==16));
            assertTrue(options.stream().anyMatch(o->o.id().equals("supplies")&&o.goods().getFirst().potion().equals("long_fire_resistance")));
        }
        assertFalse(VanillaRewardRegistry.options("pilgrim:smoke_over_blackstone",RepeatableTargetProfile.NORMAL).stream()
                .flatMap(o->o.goods().stream()).anyMatch(g->g.item().equals("blaze_rod")));
    }
    @Test void onlyActuallyDeliveredRewardRodsAdvanceTheActiveRelicTrialAndRetryCannotDeliverAgain() {
        var world=mock(net.minecraft.server.level.ServerLevel.class);var server=mock(net.minecraft.server.MinecraftServer.class);
        var player=mock(net.minecraft.server.level.ServerPlayer.class);var inventory=mock(net.minecraft.world.entity.player.Inventory.class);
        var id=java.util.UUID.randomUUID();when(world.getServer()).thenReturn(server);when(player.getUUID()).thenReturn(id);
        when(player.isAlive()).thenReturn(true);when(player.getInventory()).thenReturn(inventory);
        var quests=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new net.minecraft.nbt.CompoundTag()).getOrThrow();
        var data=quests.getPlayerData(id);data.setShardRelicQuestStage(de.quest.quest.special.ShardRelicQuestStage.TRIAL_ACTIVE);
        when(world.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var pending=RewardItems.encode(world,java.util.List.of(new ItemStack(Items.BLAZE_ROD,4)));data.vanillaRewards().pending(pending);
        try(var states=mockStatic(de.quest.data.QuestState.class);
            var intro=mockStatic(de.quest.shrine.VillageIntroRequestService.class);
            var books=mockStatic(de.quest.quest.QuestBookHelper.class);var trackers=mockStatic(de.quest.quest.QuestTrackerService.class);
            var ui=mockStatic(de.quest.questmaster.QuestMasterUiService.class)) {
            states.when(()->de.quest.data.QuestState.get(server)).thenReturn(quests);
            assertEquals(0,VanillaRewardService.deliverItems(world,player));
            assertEquals(0,data.getShardRelicBlazeRodProgress());assertEquals(1,data.vanillaRewards().pending().size());
            var status=de.quest.quest.special.ShardRelicQuestService.openStatus(world,id);
            assertTrue(status.lines().stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("quest.village-quest.special.shards.blaze_source")));
            when(inventory.add(any(ItemStack.class))).thenAnswer(c->{((ItemStack)c.getArgument(0)).setCount(0);return true;});
            assertEquals(4,VanillaRewardService.deliverItems(world,player));
            assertEquals(1,data.getShardRelicBlazeRodProgress());assertTrue(data.vanillaRewards().pending().isEmpty());
            assertEquals(0,VanillaRewardService.deliverItems(world,player));
            verify(inventory,times(2)).add(any(ItemStack.class));
            // A feedback failure must not enter the item-decoder retry catch.
            data.vanillaRewards().pending(pending);
            try(var trial=mockStatic(de.quest.quest.special.ShardRelicQuestService.class)) {
                trial.when(()->de.quest.quest.special.ShardRelicQuestService.onTrackedItemPickup(eq(world),eq(player),any(),eq(4)))
                        .thenThrow(new IllegalStateException("feedback failed"));
                assertThrows(IllegalStateException.class,()->VanillaRewardService.deliverItems(world,player));
                assertTrue(data.vanillaRewards().pending().isEmpty());
                assertEquals(0,VanillaRewardService.deliverItems(world,player));
                verify(inventory,times(3)).add(any(ItemStack.class));
            }
        }
    }
}
