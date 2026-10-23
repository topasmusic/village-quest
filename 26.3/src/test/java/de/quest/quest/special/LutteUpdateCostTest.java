package de.quest.quest.special;
import de.quest.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
final class LutteUpdateCostTest {
 @Test void validSignalBuildAndRepairStillRefreshAndAdvanceExactlyOnce() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();var quest=mock(QuestState.class);var data=new PlayerQuestData();var p=new FriendsQuestProgress(data);
  p.number("lutte.active",1);p.number("lutte.stage",1);p.number("lutte.targets",1);p.number("lutte.target.0.y",64);
  when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);when(player.getUUID()).thenReturn(id);when(quest.getPlayersView()).thenReturn(Map.of(id,data));when(world.hasChunkAt(any())).thenReturn(true);
  var log=mock(net.minecraft.world.level.block.state.BlockState.class);when(log.is(net.minecraft.tags.BlockTags.LOGS)).thenReturn(true);
  var wall=mock(net.minecraft.world.level.block.state.BlockState.class);when(wall.is(net.minecraft.tags.BlockTags.WALLS)).thenReturn(true);
  var lantern=mock(net.minecraft.world.level.block.state.BlockState.class);when(lantern.is(net.minecraft.world.level.block.Blocks.LANTERN)).thenReturn(true);when(lantern.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)).thenReturn(false);
  var ground=mock(net.minecraft.world.level.block.state.BlockState.class);when(ground.isFaceSturdy(eq(world),any(),eq(net.minecraft.core.Direction.UP))).thenReturn(true);
  when(world.getBlockState(any())).thenAnswer(call->switch(((BlockPos)call.getArgument(0)).getY()){case 63->ground;case 64->log;case 65,66->wall;default->lantern;});
  try(var states=mockStatic(QuestState.class);var friends=mockStatic(FriendsQuestService.class)) {
   states.when(()->QuestState.get(server)).thenReturn(quest);friends.when(()->FriendsQuestService.progress(world,id)).thenReturn(p);
   for(int y=64;y<68;y++)LutteCoastalQuest.placed(world,player,new BlockPos(0,y,0));assertEquals(1,p.number("lutte.signals"));assertEquals(1,p.number("lutte.certified.0"));
   LutteCoastalQuest.invalidate(world,new BlockPos(0,65,0));assertEquals(0,p.number("lutte.certified.0"));LutteCoastalQuest.placed(world,player,new BlockPos(0,65,0));assertEquals(1,p.number("lutte.certified.0"));assertEquals(1,p.number("lutte.signals"));
   friends.verify(()->FriendsQuestService.changed(world,player),times(5));
  }
 }
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void thousandUnrelatedPlacementsDoNotRefreshQuestOrRescanHistoricalPlayers() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();var quest=mock(QuestState.class);var data=new PlayerQuestData();var p=new FriendsQuestProgress(data);
  p.number("lutte.active",1);p.number("lutte.stage",1);p.number("lutte.targets",1);p.number("lutte.target.0.x",1024);
  p.number("lutte.base.0.set",1);p.number("lutte.base.0.y",64);p.number("lutte.certified.0",1);p.number("lutte.built.0.2",1);
  var profiles=spy(new HashMap<UUID,PlayerQuestData>());for(int i=0;i<10000;i++)profiles.put(UUID.randomUUID(),new PlayerQuestData());profiles.put(id,data);
  when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);when(player.getUUID()).thenReturn(id);when(quest.getPlayersView()).thenReturn(profiles);
  when(world.getBlockState(any())).thenReturn(mock(net.minecraft.world.level.block.state.BlockState.class));
  try(var states=mockStatic(QuestState.class);var friends=mockStatic(FriendsQuestService.class)) {
   states.when(()->QuestState.get(server)).thenReturn(quest);friends.when(()->FriendsQuestService.progress(world,id)).thenReturn(p);
   for(int i=0;i<1000;i++)LutteCoastalQuest.placed(world,player,new BlockPos(5000+i,64,5000));
   var chunk=mock(net.minecraft.world.level.chunk.LevelChunk.class);when(chunk.getPos()).thenReturn(new net.minecraft.world.level.ChunkPos(123,456));
   for(int i=0;i<1000;i++){LutteCoastalQuest.invalidate(world,new BlockPos(-5000-i,64,-5000));LutteCoastalQuest.chunkLoaded(world,chunk);}
   friends.verify(()->FriendsQuestService.changed(world,player),never());verify(profiles,atMost(1)).entrySet();verify(quest,never()).setDirty();
   LutteCoastalQuest.invalidate(world,new BlockPos(0,66,0));assertEquals(0,p.number("lutte.built.0.2"));assertEquals(0,p.number("lutte.certified.0"));verify(quest).setDirty();verify(profiles,atMost(1)).entrySet();
  }
 }
}
