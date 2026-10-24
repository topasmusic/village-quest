package de.quest.village;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.content.block.GuildNoticePostBlock;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.QuestState;
import de.quest.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Mocked loaded block storage, real placement adapter/history; no game world or server starts. */
final class GuildNoticePostFallbackTest {
    private static final BlockPos VISITOR = new BlockPos(0, 64, 0);
    private static final GuildCornerLandmarkState.VillageKey KEY = GuildCornerLandmarkState.VillageKey.overworld(0, 0);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static boolean place(ServerLevel world, GuildCornerLandmarkState state) throws Exception {
        var method = GuildCornerPlacementService.class.getDeclaredMethod("placeCompact", ServerLevel.class,
                GuildCornerLandmarkState.class, GuildCornerLandmarkState.VillageKey.class,
                ShadowsTradeRoadEncounterService.VillageMarker.class, BlockPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, world, state, KEY,
                new ShadowsTradeRoadEncounterService.VillageMarker("test", 0, 0, -16, 16, -16, 16), VISITOR);
    }

    @Test void compactBoardSpawnsInClearingAndSurvivesReloadWithoutDuplicates() throws Exception {
        exercise(false, false);
    }
    @Test void compactBoardRecoversAnExhaustedReservationAndClearsTreeObstruction() throws Exception {
        exercise(true, true);
    }
    @Test void missingDecorativePresetStillAllowsRetrofitToPlaceACompactBoard() throws Exception {
        exercise(false, false, true);
    }

    @Test void replacementOrExplosionCannotTurnDroppedBoardsIntoAnAutomaticItemFarm() {
        var world = mock(ServerLevel.class); var server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server); when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        var state = new GuildCornerLandmarkState();
        state.reserve(KEY, VISITOR, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.GENERIC, GuildCornerLandmarkState.Footprint.singleBlock(VISITOR));
        state.markGenerated(KEY);
        var storage = mock(net.minecraft.world.level.storage.SavedDataStorage.class);
        when(server.overworld()).thenReturn(world); when(world.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(GuildCornerLandmarkState.TYPE)).thenReturn(state);
        Block previous = ModBlocks.GUILD_NOTICE_POST; ModBlocks.GUILD_NOTICE_POST = Blocks.LADDER;
        try {
            GuildCornerPlacementService.onBlockReplaced(world, VISITOR, Blocks.LADDER.defaultBlockState(), Blocks.AIR.defaultBlockState());
            assertEquals(GuildCornerLandmarkState.LandmarkStatus.REMOVED_BY_PLAYER, state.snapshot(KEY).orElseThrow().status());
            assertFalse(state.reopenMissingPost(KEY));
            assertFalse(state.relocatePending(KEY, VISITOR.east(), GuildCornerLandmarkState.CornerFacing.EAST,
                    GuildCornerLandmarkState.Footprint.singleBlock(VISITOR.east())));
        } finally { ModBlocks.GUILD_NOTICE_POST = previous; }
    }
    private static void exercise(boolean pending, boolean tree) throws Exception {
        exercise(pending, tree, false);
    }
    private static void exercise(boolean pending, boolean tree, boolean missingPreset) throws Exception {
        var world = mock(ServerLevel.class); var server = mock(MinecraftServer.class);
        var cache = mock(ServerChunkCache.class); var chunk = mock(LevelChunk.class);
        var border = mock(WorldBorder.class); var questState = mock(QuestState.class);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        if (tree) { blocks.put(VISITOR, Blocks.OAK_LOG.defaultBlockState()); blocks.put(VISITOR.above(), Blocks.OAK_LEAVES.defaultBlockState()); }
        when(world.getServer()).thenReturn(server); when(world.getChunkSource()).thenReturn(cache);
        when(cache.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(world.hasChunkAt(any())).thenReturn(true); when(world.getMinY()).thenReturn(-64); when(world.getMaxY()).thenReturn(320);
        when(world.getHeight(any(), anyInt(), anyInt())).thenReturn(tree ? 65 : 64);
        when(world.getWorldBorder()).thenReturn(border); when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(world.getBiome(any())).thenReturn(Holder.direct(mock(Biome.class)));
        when(world.getEntities(nullable(Entity.class), any(AABB.class), any())).thenReturn(List.of());
        when(world.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return blocks.getOrDefault(pos, pos.getY() <= 63 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
        });
        when(world.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
            blocks.put(((BlockPos) call.getArgument(0)).immutable(), call.getArgument(1)); return true;
        });
        var state = new GuildCornerLandmarkState();
        var storage = mock(net.minecraft.world.level.storage.SavedDataStorage.class);
        when(server.overworld()).thenReturn(world); when(world.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(GuildCornerLandmarkState.TYPE)).thenReturn(state);
        when(storage.computeIfAbsent(QuestState.TYPE)).thenReturn(questState);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        if (pending) {
            var abandoned = new BlockPos(10, 64, 10);
            state.reserve(KEY, abandoned, GuildCornerLandmarkState.CornerFacing.NORTH,
                    GuildCornerLandmarkState.CornerStyle.GENERIC, GuildCornerLandmarkState.Footprint.singleBlock(abandoned));
            state.requireManualPlacement(KEY);
        }
        Block previous = ModBlocks.GUILD_NOTICE_POST;
        // Bootstrap has frozen registries. A real registered horizontal block supplies identity/FACING;
        // the production adapter and the board's real 3x2 footprint remain under test.
        ModBlocks.GUILD_NOTICE_POST = Blocks.LADDER;
        try {
            if (missingPreset) {
                var templates = mock(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.class);
                when(world.getStructureManager()).thenReturn(templates);
                when(templates.get(any())).thenReturn(java.util.Optional.empty());
                var player = mock(net.minecraft.server.level.ServerPlayer.class);
                when(player.blockPosition()).thenReturn(VISITOR);
                assertEquals(GuildCornerPlacementService.ManualPlacementResult.PLACED,
                        GuildCornerPlacementService.requestManualPlacement(world, player,
                                new ShadowsTradeRoadEncounterService.VillageMarker("test", 0, 0, -16, 16, -16, 16)));
            } else assertTrue(place(world, state));
            var saved = state.snapshot(KEY).orElseThrow();
            assertEquals(GuildCornerLandmarkState.LandmarkStatus.GENERATED, saved.status());
            assertEquals(1, blocks.values().stream().filter(block -> block.is(ModBlocks.GUILD_NOTICE_POST)).count());
            var reloaded = GuildCornerLandmarkState.fromNbt(GuildCornerLandmarkState.toNbt(state));
            assertFalse(place(world, reloaded));
            assertEquals(1, blocks.values().stream().filter(block -> block.is(ModBlocks.GUILD_NOTICE_POST)).count());
            if (tree) assertTrue(blocks.get(VISITOR).is(ModBlocks.GUILD_NOTICE_POST));
            // A vanished physical board must not remain stuck on "already generated" after loading.
            blocks.put(saved.cornerPos(), Blocks.AIR.defaultBlockState());
            assertTrue(place(world, reloaded));
            assertEquals(1, blocks.values().stream().filter(block -> block.is(ModBlocks.GUILD_NOTICE_POST)).count());
            reloaded.markRemovedByPlayer(KEY);
            assertFalse(place(world, reloaded));
            verify(cache, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
        } finally { ModBlocks.GUILD_NOTICE_POST = previous; }
    }
}
