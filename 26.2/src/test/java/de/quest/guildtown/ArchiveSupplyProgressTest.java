package de.quest.guildtown;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.shrine.VillageBondType;
import de.quest.village.VillageLifeState;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;

/** Actual service fanout, saved progress and location boundary without a world start. */
final class ArchiveSupplyProgressTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }
    @Test void activeNearbyOwnerProgressesToReadyAndSurvivesNativeSaveReload() {
        var state=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,new net.minecraft.nbt.CompoundTag()).getOrThrow();
        var owner=UUID.randomUUID();var data=state.getPlayerData(owner);var story=GuildTownStory.INK_BETWEEN_VILLAGES;
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);
        when(world.dimension()).thenReturn(Level.OVERWORLD);when(player.getUUID()).thenReturn(owner);
        var life=mock(VillageLifeState.class);when(life.status(any())).thenReturn(VillageLifeState.Status.ACTIVE);
        var village=new GuildTownStoryVillageResolver.StoryVillage(4,0,0,VillageBondType.ARCHIVE,null,false);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
            var resolver=mockStatic(GuildTownStoryVillageResolver.class);
            var lives=mockStatic(VillageLifeState.class);
            var books=mockStatic(QuestBookHelper.class);var trackers=mockStatic(QuestTrackerService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            lives.when(()->VillageLifeState.get(server)).thenReturn(life);
            resolver.when(()->GuildTownStoryVillageResolver.byIndex(world,owner,data,4)).thenReturn(village);
            GuildTownService.onTrackedItemPickup(world,player,new ItemStack(Items.PAPER,12),12);
            assertEquals(0,GuildTownProgress.storyProgress(data,story,2)); // No accepted story.
            assertTrue(GuildTownProgress.beginStory(data,story,4,GuildTownProgress.PREVENTIVE));
            when(player.getBlockX()).thenReturn(129);
            GuildTownService.onTrackedItemPickup(world,player,new ItemStack(Items.PAPER,12),12);
            assertEquals(0,GuildTownProgress.storyProgress(data,story,2));
            when(player.getBlockX()).thenReturn(128);
            for(Item item:new Item[]{Items.PAPER,Items.INK_SAC,Items.FEATHER})
                GuildTownService.onTrackedItemPickup(world,player,new ItemStack(item,4),4);
            assertEquals(12,GuildTownProgress.storyProgress(data,story,2));
            for(int i=0;i<3;i++)GuildTownService.onVillagerTrade(world,player);
            assertEquals(GuildTownProgress.READY,GuildTownProgress.storyState(data,story));
            assertEquals(0,GuildTownProgress.storyProgress(state.getPlayerData(UUID.randomUUID()),story,2));
        }
        var nbt=QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow();
        var loaded=QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,nbt).getOrThrow().getPlayerData(owner);
        assertEquals(12,GuildTownProgress.storyProgress(loaded,story,2));
        assertEquals(3,GuildTownProgress.storyProgress(loaded,story,1));
        assertEquals(GuildTownProgress.READY,GuildTownProgress.storyState(loaded,story));
    }
}
