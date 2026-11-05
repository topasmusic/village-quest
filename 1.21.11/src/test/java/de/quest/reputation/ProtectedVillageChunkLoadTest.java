package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.QuestState;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerChunkCache;
import org.junit.jupiter.api.*;

/** Chunk loading must never re-enter a synchronous request for its own unfinished chunk. */
final class ProtectedVillageChunkLoadTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void chunkLoadMustNotReenterWorldHeightLookup() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var state=mock(QuestState.class);
        var index=new ProtectedVillageIndex();var key=VillageKey.overworld(-17,34);index.upsert(key);
        when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(state.protectedVillages()).thenReturn(index);when(world.hasChunkAt(any())).thenReturn(true);
        when(world.getHeight(any(),anyInt(),anyInt())).thenThrow(new AssertionError("Re-entrant chunk request during CHUNK_LOAD"));
        when(world.getChunkSource()).thenThrow(new AssertionError("CHUNK_LOAD must use the supplied chunk, not its unpublished cache entry"));
        var chunk=mock(LevelChunk.class);when(chunk.getPos()).thenReturn(new ChunkPos(-2,2));
        when(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,15,2)).thenReturn(64);
        try(var states=mockStatic(QuestState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            assertDoesNotThrow(() -> ProtectedVillageIndex.onChunkLoad(world,chunk));
            assertEquals(key,index.resolve("minecraft:overworld",new BlockPos(-17,97,34)).orElseThrow());
            assertTrue(index.resolve("minecraft:overworld",new BlockPos(-17,98,34)).isEmpty());
            verify(state,times(1)).setDirty();
            ProtectedVillageIndex.onChunkLoad(world,chunk);verify(state,times(1)).setDirty();
            verify(chunk,times(1)).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,15,2);
        }
    }
    @Test void differentChunkOrDimensionDoesNotRepairPendingAnchors() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var state=mock(QuestState.class);
        var index=new ProtectedVillageIndex();var key=VillageKey.overworld(1,1);index.upsert(key);
        when(world.getServer()).thenReturn(server);when(state.protectedVillages()).thenReturn(index);
        var chunk=mock(LevelChunk.class);when(chunk.getPos()).thenReturn(new ChunkPos(1,0));
        try(var states=mockStatic(QuestState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            when(world.dimension()).thenReturn(Level.OVERWORLD);ProtectedVillageIndex.onChunkLoad(world,chunk);
            when(world.dimension()).thenReturn(Level.NETHER);when(chunk.getPos()).thenReturn(new ChunkPos(0,0));ProtectedVillageIndex.onChunkLoad(world,chunk);
            verify(world,never()).getChunkSource();verify(world,never()).getHeight(any(),anyInt(),anyInt());verify(chunk,never()).getHeight(any(),anyInt(),anyInt());verify(state,never()).setDirty();
            assertTrue(index.resolve("minecraft:overworld",new BlockPos(1,65,1)).isEmpty());
        }
    }
    @Test void missingCachedChunkStaysPendingUntilRealLoadEventThenSurvivesSaveReload() {
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var state=mock(QuestState.class);var cache=mock(ServerChunkCache.class);
        var index=new ProtectedVillageIndex();var key=VillageKey.overworld(18,35);
        when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.getChunkSource()).thenReturn(cache);when(state.protectedVillages()).thenReturn(index);
        try(var states=mockStatic(QuestState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            ProtectedVillageIndex.register(world,key);
            assertTrue(index.resolve(world,new BlockPos(18,65,35)).isEmpty());
            var chunk=mock(LevelChunk.class);when(chunk.getPos()).thenReturn(new ChunkPos(1,2));when(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,3)).thenReturn(64);
            ProtectedVillageIndex.onChunkLoad(world,chunk);
            assertEquals(key,ProtectedVillageIndex.fromNbt(index.toNbt()).resolve("minecraft:overworld",new BlockPos(18,65,35)).orElseThrow());
            verify(world,never()).getHeight(any(),anyInt(),anyInt());verify(cache,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
        }
    }
    @Test void registerAndResolveUseOnlyAlreadyPublishedCachedChunks() {
        for(boolean register:new boolean[]{true,false}) {
            var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);var state=mock(QuestState.class);var cache=mock(ServerChunkCache.class);
            var index=new ProtectedVillageIndex();var key=VillageKey.overworld(-1,-18);var chunk=mock(LevelChunk.class);
            when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.getChunkSource()).thenReturn(cache);when(state.protectedVillages()).thenReturn(index);
            when(cache.getChunkNow(-1,-2)).thenReturn(chunk);when(chunk.getPos()).thenReturn(new ChunkPos(-1,-2));when(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,15,14)).thenReturn(64);
            try(var states=mockStatic(QuestState.class)) {
                states.when(() -> QuestState.get(server)).thenReturn(state);
                if(register) ProtectedVillageIndex.register(world,key);else index.upsert(key);
                assertEquals(key,index.resolve(world,new BlockPos(-1,65,-18)).orElseThrow());
                verify(world,never()).getHeight(any(),anyInt(),anyInt());verify(cache,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
                verify(state,times(1)).setDirty();
            }
        }
    }
}
