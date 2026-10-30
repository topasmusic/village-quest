package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.quest.DifficultyObjectiveMode;
import de.quest.quest.story.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;

final class NightBellsPeacefulGuidanceTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    private static String key(Component c) {return ((TranslatableContents)c.getContents()).getKey();}
    @Test void twoOfThreeOffersAConcreteLoadedUnvisitedSquareWithoutBlockSearches() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();
        var players=mock(net.minecraft.server.players.PlayerList.class);when(world.getServer()).thenReturn(server);when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(id)).thenReturn(player);when(player.blockPosition()).thenReturn(new BlockPos(100,70,100));when(world.hasChunkAt(any())).thenReturn(true);
        var state=new HashMap<String,Integer>();state.put(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND,1);
        state.put(StoryQuestKeys.NIGHT_BELLS_VILLAGE_X,100);state.put(StoryQuestKeys.NIGHT_BELLS_VILLAGE_Z,100);
        state.put(StoryQuestKeys.NIGHT_BELLS_MODE_PREFIX+1,2);
        state.put(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"1_0",384);
        state.put(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"1_1",385);
        var village=new ShadowsTradeRoadEncounterService.VillageMarker("100_100",100,100,0,200,0,200);
        try(var service=mockStatic(StoryQuestService.class);var villages=mockStatic(ShadowsTradeRoadEncounterService.class)) {
            service.when(()->StoryQuestService.getQuestInt(eq(world),eq(id),anyString())).thenAnswer(c->state.getOrDefault(c.getArgument(2),0));
            villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(eq(world),any())).thenReturn(village);
            var before=Map.copyOf(state);var lines=new NightBellsStoryArc().chapter(1).progressLines(world,id);
            var target=(TranslatableContents)lines.stream().filter(c->key(c).endsWith(".next_sector")).findFirst().orElseThrow().getContents();
            int x=(Integer)target.getArgs()[0],z=(Integer)target.getArgs()[2];
            assertTrue(NightBellsMarkerProgress.available(k->state.getOrDefault(k,0),1,Math.floorDiv(x,8),Math.floorDiv(z,8)));
            assertEquals(x+7,target.getArgs()[1]);assertEquals(z+7,target.getArgs()[3]);assertEquals(before,state);
            verify(world,never()).getBlockState(any());
        }
    }
    @Test void allFourChaptersDescribeTheSavedModeAndExposeExactIndividualSuppliesWithoutMutation() {
        var world=mock(ServerLevel.class);when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(mock(net.minecraft.server.players.PlayerList.class));
        var id=UUID.randomUUID();var state=new HashMap<String,Integer>();
        state.put(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND,1);
        var supplies=List.of(Map.of(Items.TORCH,16),Map.of(Items.OAK_FENCE,16),Map.of(Items.BREAD,16),Map.of(Items.LANTERN,8,Items.SHIELD,1));
        try(var service=mockStatic(StoryQuestService.class)) {
            service.when(()->StoryQuestService.getQuestInt(eq(world),eq(id),anyString())).thenAnswer(c->state.getOrDefault(c.getArgument(2),0));
            for(int index=0;index<4;index++) {
                var chapter=new NightBellsStoryArc().chapter(index);String mode=StoryQuestKeys.NIGHT_BELLS_MODE_PREFIX+index;
                state.put(mode,DifficultyObjectiveMode.COMBAT.serializedId());
                assertEquals(chapter.descriptionLines(),chapter.descriptionLines(world,id));
                state.put(mode,DifficultyObjectiveMode.PEACEFUL.serializedId());
                var before=Map.copyOf(state);
                var descriptions=chapter.descriptionLines(world,id);
                assertEquals("quest.village-quest.story.night_bells.peaceful.description."+index,key(descriptions.getFirst()));
                var lines=chapter.progressLines(world,id);
                var materials=lines.stream().filter(c->key(c).equals("screen.village-quest.ui_review.objective_row")).map(c->(TranslatableContents)c.getContents()).toList();
                assertEquals(supplies.get(index).size(),materials.size());
                for(var material:supplies.get(index).entrySet()) {
                    var row=materials.stream().filter(c->key((Component)c.getArgs()[0]).equals(material.getKey().getDescriptionId())).findFirst().orElseThrow();
                    assertEquals(0,row.getArgs()[1]);assertEquals(material.getValue(),row.getArgs()[2]);
                }
                if(index==3) {
                    assertTrue(lines.stream().anyMatch(c->key(c).endsWith(".progress.golem")));
                    assertTrue(lines.stream().anyMatch(c->key(c).endsWith(".progress.confirm")));
                }
                var hudRows=de.quest.quest.QuestShortObjective.rows(chapter.shortObjective(world,id,lines));
                assertTrue(hudRows.stream().anyMatch(c->key(c).contains(".peaceful.progress.")));
                assertEquals(supplies.get(index).size(),hudRows.stream().filter(c->key(c).equals("screen.village-quest.ui_review.objective_row")).count());
                assertEquals(before,state,"Reading must not initialize mode, village or marker evidence");
                if(index==3) {
                    state.put(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_BELL,1);
                    for(int slot=0;slot<4;slot++)state.put(StoryQuestKeys.NIGHT_BELLS_PEACEFUL_MARKER_PREFIX+"3_"+slot,slot+1);
                    service.when(()->StoryQuestService.countCompletionItem(world,id,Items.LANTERN)).thenReturn(8);
                    var finalRows=de.quest.quest.QuestShortObjective.rows(chapter.shortObjective(world,id,chapter.progressLines(world,id)));
                    assertTrue(finalRows.stream().anyMatch(de.quest.quest.QuestObjectiveProgress::complete));
                    var next=(TranslatableContents)finalRows.stream().filter(c->key(c).equals("screen.village-quest.ui_review.objective_row")
                            && !de.quest.quest.QuestObjectiveProgress.complete(c)).findFirst().orElseThrow().getContents();
                    assertEquals("screen.village-quest.ui_review.objective_row",next.getKey());
                    assertEquals("item.minecraft.shield",key((Component)next.getArgs()[0]));
                    assertEquals(0,next.getArgs()[1]);assertEquals(1,next.getArgs()[2]);
                }
            }
        }
    }
    @Test void acceptanceOutsideVillageBindsAtFirstValidInteractionAndOnlyNewProtectionSquaresCount() {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();
        when(player.getUUID()).thenReturn(id);when(player.blockPosition()).thenReturn(new BlockPos(1000,70,1000));
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        var state=new HashMap<String,Integer>();var chapter=new NightBellsStoryArc().chapter(1);
        var village=new ShadowsTradeRoadEncounterService.VillageMarker("100_100",100,100,0,200,0,200);
        try(var service=mockStatic(StoryQuestService.class);var villages=mockStatic(ShadowsTradeRoadEncounterService.class)) {
            service.when(()->StoryQuestService.getQuestInt(eq(world),eq(id),anyString())).thenAnswer(c->state.getOrDefault(c.getArgument(2),0));
            service.when(()->StoryQuestService.setQuestIntQuietly(eq(world),eq(id),anyString(),anyInt())).thenAnswer(c->{state.put(c.getArgument(2),c.getArgument(3));return null;});
            chapter.onAccepted(world,player);assertEquals(0,state.getOrDefault(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND,0));
            var first=new BlockPos(40,70,100);villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,first)).thenReturn(village);
            chapter.onUseBlock(world,player,first,Blocks.OAK_FENCE_GATE.defaultBlockState(),ItemStack.EMPTY);
            assertEquals(1,state.get(StoryQuestKeys.NIGHT_BELLS_VILLAGE_BOUND));
            assertEquals(1,NightBellsMarkerProgress.count(k->state.getOrDefault(k,0),1));
            chapter.onUseBlock(world,player,first,Blocks.OAK_FENCE_GATE.defaultBlockState(),ItemStack.EMPTY);
            assertEquals(1,NightBellsMarkerProgress.count(k->state.getOrDefault(k,0),1));
            var before=Map.copyOf(state);
            chapter.onUseBlock(world,player,new BlockPos(48,70,100),Blocks.STONE.defaultBlockState(),ItemStack.EMPTY);
            chapter.onServerTick(world,player); // Mere time/walking provides no protection-marker credit.
            assertEquals(before,state);
            for(int x:List.of(48,56)) {
                var at=new BlockPos(x,70,100);villages.when(()->ShadowsTradeRoadEncounterService.currentVillage(world,at)).thenReturn(village);
                chapter.onUseBlock(world,player,at,Blocks.LANTERN.defaultBlockState(),ItemStack.EMPTY);
            }
            assertEquals(3,NightBellsMarkerProgress.count(k->state.getOrDefault(k,0),1));
            before=Map.copyOf(state);
            chapter.onUseBlock(world,player,new BlockPos(350,70,100),Blocks.OAK_FENCE_GATE.defaultBlockState(),ItemStack.EMPTY);
            assertEquals(before,state,"Other village cannot supply another sector");
            for(var key:NightBellsMarkerProgress.keys(1))assertTrue(NightBellsStoryArc.modeSpecificIntKeys(1).contains(key));
        }
    }
}
