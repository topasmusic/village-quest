package de.quest.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;

final class QuestHarvestTrackerTest {
    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void nativeAfterRemovesTheDelayedReportAndCanceledOrUnloadedHarvestsNeverCreditIntro() throws Exception {
        var world = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var player = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        var list = org.mockito.Mockito.mock(net.minecraft.server.players.PlayerList.class);
        var owner = java.util.UUID.randomUUID(); var pos = new net.minecraft.core.BlockPos(32,64,64);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(owner);
        org.mockito.Mockito.when(player.level()).thenReturn(world);
        org.mockito.Mockito.when(server.getPlayerList()).thenReturn(list);
        org.mockito.Mockito.when(list.getPlayer(owner)).thenReturn(player);
        org.mockito.Mockito.when(world.hasChunkAt(pos)).thenReturn(true);
        var crop = (net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
        var ripe = crop.getStateForAge(crop.getMaxAge());
        org.mockito.Mockito.when(world.getBlockState(pos)).thenReturn(crop.getStateForAge(0));
        var ticket = new de.quest.shrine.VillageIntroRequestService.HarvestTicket(owner,1,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),java.util.UUID.randomUUID());
        var nested = Class.forName(QuestHarvestTracker.class.getName() + "$PendingHarvest");
        var ctor = nested.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        var field = QuestHarvestTracker.class.getDeclaredField("PENDING_HARVESTS"); field.setAccessible(true);
        List pending = (List) field.get(null); pending.clear();
        try (var intro = org.mockito.Mockito.mockStatic(de.quest.shrine.VillageIntroRequestService.class);
             var daily = org.mockito.Mockito.mockStatic(de.quest.quest.daily.DailyQuestService.class);
             var story = org.mockito.Mockito.mockStatic(de.quest.quest.story.StoryQuestService.class);
             var special = org.mockito.Mockito.mockStatic(de.quest.quest.special.SpecialQuestService.class)) {
            pending.add(ctor.newInstance(world,owner,pos,ripe,crop.getMaxAge(),true,java.util.Map.of(),ticket,2L));
            QuestHarvestTracker.onBlockBreakFinished(world,player,pos);
            QuestHarvestTracker.onServerTick(server);
            intro.verify(() -> de.quest.shrine.VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket),org.mockito.Mockito.times(1));
            intro.clearInvocations();
            pending.add(ctor.newInstance(world,owner,pos,ripe,crop.getMaxAge(),true,java.util.Map.of(),ticket,2L));
            QuestHarvestTracker.onBlockBreakCanceled(world,player,pos);
            QuestHarvestTracker.onServerTick(server);
            intro.verifyNoInteractions();
            pending.add(ctor.newInstance(world,owner,pos,ripe,crop.getMaxAge(),true,java.util.Map.of(),ticket,2L));
            org.mockito.Mockito.when(world.hasChunkAt(pos)).thenReturn(false);
            QuestHarvestTracker.onServerTick(server); intro.verifyNoInteractions();
            org.mockito.Mockito.when(world.hasChunkAt(pos)).thenReturn(true);
            QuestHarvestTracker.onServerTick(server);
            QuestHarvestTracker.onBlockBreakFinished(world,player,pos);
            intro.verify(() -> de.quest.shrine.VillageIntroRequestService.onVerifiedHarvest(world,player,pos,ripe,ticket),org.mockito.Mockito.times(1));
            assertTrue(pending.isEmpty());
        } finally { pending.clear(); }
    }
    @Test void beetrootUsesItsOwnMaturityProperty() throws Exception {
        var method = QuestHarvestTracker.class.getDeclaredMethod("isMatureCrop", net.minecraft.world.level.block.state.BlockState.class);
        method.setAccessible(true);
        var beetroot = (net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.BEETROOTS;
        assertTrue((boolean) method.invoke(null, beetroot.getStateForAge(beetroot.getMaxAge())));
        assertFalse((boolean) method.invoke(null, beetroot.getStateForAge(beetroot.getMaxAge() - 1)));
    }
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Bootstrap.validate();
        for (var item : java.util.List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO,
                Items.BEETROOT, Items.BEETROOT_SEEDS, Items.STICK)) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @Test
    void everyGuildTownHarvestTargetIsTrackedForRightClickReplantFlows() {
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.WHEAT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.WHEAT_SEEDS)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.CARROT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.POTATO)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.BEETROOT)));
        assertTrue(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.BEETROOT_SEEDS)));
        assertFalse(QuestHarvestTracker.isTrackedCropDrop(new ItemStack(Items.STICK)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void clearRemovesPendingDelayedHarvestsBeforeTheirVerificationTick() throws Exception {
        Field field = QuestHarvestTracker.class.getDeclaredField("PENDING_HARVESTS");
        field.setAccessible(true);
        List pending = (List) field.get(null);
        pending.add(null);
        assertTrue(QuestHarvestTracker.pendingCount() > 0);
        QuestHarvestTracker.clear();
        assertTrue(QuestHarvestTracker.pendingCount() == 0);
    }
}
