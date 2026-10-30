package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.guildtown.*;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.story.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;

final class ParallelTrackerSnapshotTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        TrackerObjectiveMatrixTest.bindDeliveryItems();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    @Test void nativeSnapshotKeepsBothStoryLanesLocalAndCommissionAcrossChapterChanges() {
        var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        var owner=UUID.randomUUID(); when(world.getServer()).thenReturn(server); when(player.getUUID()).thenReturn(owner);
        when(server.getPlayerList()).thenReturn(mock(net.minecraft.server.players.PlayerList.class));
        var data=state.getPlayerData(owner); data.setLastRewardDay(1);
        data.setActiveStoryArc(StoryArcType.FAILING_HARVEST); data.setStoryChapterProgress(StoryArcType.FAILING_HARVEST.id(),1);
        data.setKingdomStoryActive(true);
        assertTrue(GuildTownProgress.beginStory(data,GuildTownStory.SHARED_TABLE,2,1));
        assertTrue(GuildTownProgress.beginCommission(data,GuildTownCommission.WINTER_FEED,
                new GuildTownProgress.VillageIdentity(owner,10,20),new GuildTownProgress.VillageIdentity(owner,100,20)));
        var emptyGuilds=de.quest.guild.VillageGuildState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var emptyShared=GuildTownSharedState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        // No encounter exists in this fixture; Fabric attachment registration requires mixins.
        try(var encounters=mockStatic(de.quest.ambient.EncounterQuestService.class);
                var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
                var kingdoms=mockStatic(LivingKingdomsState.class,CALLS_REAL_METHODS);
                var guilds=mockStatic(de.quest.guild.VillageGuildState.class,CALLS_REAL_METHODS);
                var shared=mockStatic(GuildTownSharedState.class,CALLS_REAL_METHODS);
                var guidance=mockStatic(QuestGuidanceService.class)) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            kingdoms.when(()->LivingKingdomsState.get(server)).thenReturn(new LivingKingdomsState());
            guilds.when(()->de.quest.guild.VillageGuildState.get(server)).thenReturn(emptyGuilds);
            shared.when(()->GuildTownSharedState.get(server)).thenReturn(emptyShared);
            for(var arc:List.of(StoryArcType.FAILING_HARVEST,StoryArcType.LIVING_KINGDOMS))
                guidance.when(()->QuestGuidanceService.story(world,player,arc)).thenReturn(new QuestGuidanceService.QuestNextStep(
                        "story",QuestGuidanceService.Status.ACTIVE,Component.literal("Authoritative next step"),0,1,null,"",null));
            var snapshot=QuestTrackerService.journalEntries(world,player);
            assertEquals(4,snapshot.size());
            var ids=snapshot.stream().map(q -> q.entry().id()).toList();
            assertTrue(ids.contains("story:core:failing_harvest")); assertTrue(ids.contains("story:kingdom:living_kingdoms"));
            assertEquals(2,QuestShortObjective.rows(snapshot.stream().filter(q->q.entry().id().equals("story:core:failing_harvest"))
                    .findFirst().orElseThrow().entry().shortObjective()).size(),"The actual journal/HUD snapshot exposes both honey requirements");
            assertEquals(1,ids.stream().filter(id -> id.startsWith("story:local:")).count());
            assertEquals(1,ids.stream().filter(id -> id.startsWith("story:commission:")).count());
            data.setStoryChapterProgress(StoryArcType.FAILING_HARVEST.id(),2);
            assertEquals(ids,QuestTrackerService.journalEntries(world,player).stream().map(q -> q.entry().id()).toList());
            data.setActiveStoryArc(null);
            assertFalse(QuestTrackerService.journalEntries(world,player).stream().anyMatch(q -> q.entry().id().startsWith("story:core:")));
            assertTrue(QuestTrackerService.journalEntries(world,player).stream().anyMatch(q -> q.entry().id().equals("story:kingdom:living_kingdoms")));
        }
    }
}
