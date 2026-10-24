package de.quest.quest.special;
import de.quest.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
final class LutteCoastalQuestTest {
 @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
 private FriendsQuestProgress fixture(PlayerQuestData data) {
  var p=new FriendsQuestProgress(data);p.number("lutte.stage",1);p.number("lutte.active",1);p.number("lutte.signals",3);
  for(int i=0;i<3;i++) {p.number("lutte.base."+i+".set",1);p.number("lutte.base."+i+".x",i*1024);p.number("lutte.base."+i+".y",64);for(int bit=0;bit<4;bit++)p.number("lutte.built."+i+"."+bit,1);}
  return p;
 }
 @Test void unloadedUnconfirmedSignalsNeverBecomeCompleteButDurableCertifiedSignalsRemainCheckable() {
  var world=mock(ServerLevel.class);when(world.dimension()).thenReturn(Level.OVERWORLD);var p=fixture(new PlayerQuestData());
  assertFalse(LutteCoastalQuest.complete(world,p,0));assertFalse(LutteCoastalQuest.ready(world,UUID.randomUUID(),p));
  for(int i=0;i<3;i++)p.number("lutte.certified."+i,1);
  assertFalse(LutteCoastalQuest.ready(world,UUID.randomUUID(),p),"Restart must not trust disk certificates without fresh loaded evidence");verify(world,never()).getBlockState(any());
 }
 @Test void anyActorOrExplosionMutationRevokesOfflineOwnersPersonalProof() {
  var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.getServer()).thenReturn(server);
  var data=new PlayerQuestData();var p=fixture(data);p.number("lutte.certified.0",1);var state=mock(QuestState.class);when(state.getPlayersView()).thenReturn(Map.of(UUID.randomUUID(),data));
  try(var states=mockStatic(QuestState.class)) {states.when(()->QuestState.get(server)).thenReturn(state);LutteCoastalQuest.invalidate(world,new BlockPos(0,66,0));}
  assertEquals(0,p.number("lutte.built.0.2"));assertEquals(0,p.number("lutte.certified.0"));assertEquals(3,p.number("lutte.signals"));assertEquals(1,p.number("lutte.built.1.2"));
 }
 @Test void targetSeparationUsesHorizontalDistanceWithoutIntegerOverflow() {
  assertEquals(262144,LutteCoastalQuest.horizontal(new BlockPos(0,1,0),new BlockPos(512,200,0)));
  assertTrue(LutteCoastalQuest.horizontal(new BlockPos(-30000000,64,0),new BlockPos(30000000,64,0))>Integer.MAX_VALUE);
 }
 @Test void loadEventReadsOnlyDeliveredChunkAndRestoresProofWithoutRecursivelyRequestingWorldChunk() {
  for(boolean intact:new boolean[]{true,false}) {
   var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);
   // Vanilla reports the chunk present during CHUNK_LOAD although its full future has not completed.
   when(world.hasChunkAt(any())).thenReturn(true);
   when(world.getBlockState(any())).thenThrow(new AssertionError("CHUNK_LOAD must not request its own unfinished world chunk"));
   var chunk=mock(net.minecraft.world.level.chunk.LevelChunk.class);when(chunk.getPos()).thenReturn(new net.minecraft.world.level.ChunkPos(0,0));
   var log=mock(BlockState.class);when(log.is(net.minecraft.tags.BlockTags.LOGS)).thenReturn(true);
   var wall=mock(BlockState.class);when(wall.is(net.minecraft.tags.BlockTags.WALLS)).thenReturn(true);
   var lantern=mock(BlockState.class);when(lantern.is(net.minecraft.world.level.block.Blocks.LANTERN)).thenReturn(intact);when(lantern.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)).thenReturn(false);
   when(chunk.getBlockState(any())).thenAnswer(call -> switch(((BlockPos)call.getArgument(0)).getY()){case 64 -> log;case 65,66 -> wall;default -> lantern;});
   var data=new PlayerQuestData();var p=fixture(data);var id=UUID.randomUUID();for(int i=0;i<3;i++)p.number("lutte.certified."+i,1);
   var state=mock(QuestState.class);when(state.getPlayersView()).thenReturn(Map.of(id,data));
   try(var states=mockStatic(QuestState.class)) {
    states.when(()->QuestState.get(server)).thenReturn(state);LutteCoastalQuest.chunkLoaded(world,chunk);
    assertEquals(intact?1:0,p.number("lutte.certified.0"));assertEquals(1,p.number("lutte.certified.1"));assertEquals(1,p.number("lutte.certified.2"));
    verify(world,never()).getBlockState(any());verify(world,never()).hasChunkAt(any());verify(chunk,atMost(4)).getBlockState(any());
    // Only the delivered column may gain runtime proof; other saved signals still require their own load.
    when(world.hasChunkAt(any())).thenReturn(false);assertFalse(LutteCoastalQuest.ready(world,id,p));
    if(intact) {
     for(int i=1;i<3;i++){var next=mock(net.minecraft.world.level.chunk.LevelChunk.class);when(next.getPos()).thenReturn(new net.minecraft.world.level.ChunkPos(i*64,0));when(next.getBlockState(any())).thenAnswer(call -> switch(((BlockPos)call.getArgument(0)).getY()){case 64 -> log;case 65,66 -> wall;default -> lantern;});LutteCoastalQuest.chunkLoaded(world,next);}
     assertTrue(LutteCoastalQuest.ready(world,id,p),"Each directly loaded intact signal must regain fresh runtime proof");
    }
   }
  }
 }
 @Test void freshLoadedProofSurvivesUnloadButNotAnotherServerInstance() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.hasChunkAt(any())).thenReturn(true);
  var log=mock(BlockState.class);when(log.is(net.minecraft.tags.BlockTags.LOGS)).thenReturn(true);
  var wall=mock(BlockState.class);when(wall.is(net.minecraft.tags.BlockTags.WALLS)).thenReturn(true);
  var lantern=mock(BlockState.class);when(lantern.is(net.minecraft.world.level.block.Blocks.LANTERN)).thenReturn(true);when(lantern.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)).thenReturn(false);
  when(world.getBlockState(any())).thenAnswer(call -> switch(((BlockPos)call.getArgument(0)).getY()-64){case 0 -> log;case 1,2 -> wall;default -> lantern;});
  var p=fixture(new PlayerQuestData());var player=UUID.randomUUID();for(int i=0;i<3;i++)p.number("lutte.certified."+i,1);
  assertTrue(LutteCoastalQuest.ready(world,player,p));when(world.hasChunkAt(any())).thenReturn(false);assertTrue(LutteCoastalQuest.ready(world,player,p));
  var afterRestart=mock(MinecraftServer.class);when(world.getServer()).thenReturn(afterRestart);assertFalse(LutteCoastalQuest.ready(world,player,p));
 }

}
