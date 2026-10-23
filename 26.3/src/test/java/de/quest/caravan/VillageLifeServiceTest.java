package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Isolated service/codec dependencies only: no server, world, entity ticking or GameTest. */
final class VillageLifeServiceTest {
    private static final VillageMarker MARKER = new VillageMarker("test", 0, 0, -4, 4, -4, 4);

    @BeforeAll
    static void registries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void threeAdultsAreObservableInsteadOfBeingTruncatedAtTwo() {
        ServerLevel world = world(List.of(resident(null), resident(null), resident(null)));
        assertEquals(3, VillageLifeService.resettlementProgress(world, MARKER).adults());
    }

    @Test
    void nearbyBedBlocksWithoutVillagerHomeAssignmentsDoNotCount() {
        ServerLevel world = world(List.of(resident(null), resident(null)));
        BlockState unassignedBed = bedState(BedPart.FOOT);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(unassignedBed);
        assertEquals(0, VillageLifeService.resettlementProgress(world, MARKER).beds());
    }

    @Test
    void threeDistinctAssignedReachableBedsCountForActivation() {
        ServerLevel world = world(List.of(resident(new BlockPos(1, 64, 1)),
                resident(new BlockPos(3, 64, 1)), resident(new BlockPos(5, 64, 1))));
        beds(world);
        assertEquals(3, VillageLifeService.resettlementProgress(world, MARKER).beds());
    }

    @Test
    void sharedHomeMemoryCannotCountTheSameBedTwice() {
        BlockPos home = new BlockPos(1, 64, 1);
        ServerLevel world = world(List.of(resident(home), resident(home), resident(home)));
        beds(world);
        assertEquals(1, VillageLifeService.resettlementProgress(world, MARKER).beds());
    }

    @Test
    void unreachableAssignedBedDoesNotCount() {
        BlockPos home = new BlockPos(1, 64, 1);
        Villager resident = resident(home);
        when(resident.getNavigation().createPath(home, 0)).thenReturn(
                new Path(List.of(new Node(0, 64, 0)), home, false));
        ServerLevel world = world(List.of(resident));
        beds(world);
        assertEquals(0, VillageLifeService.resettlementProgress(world, MARKER).beds());
    }

    @Test
    void differentDimensionHomeIsNotLocalBedEvidence() {
        Villager resident = resident(new BlockPos(1, 64, 1));
        when(resident.getBrain().getMemory(MemoryModuleType.HOME)).thenReturn(
                Optional.of(GlobalPos.of(Level.NETHER, new BlockPos(1, 64, 1))));
        ServerLevel world = world(List.of(resident));
        beds(world);
        assertEquals(0, VillageLifeService.resettlementProgress(world, MARKER).beds());
    }

    @Test
    void incompleteChunkEvidenceCannotProduceAPopulationOrBedConfirmation() {
        ServerLevel world = world(List.of(resident(new BlockPos(1, 64, 1))));
        when(world.hasChunk(anyInt(), anyInt())).thenReturn(false);
        var progress = VillageLifeService.resettlementProgress(world, MARKER);
        assertFalse(progress.fullyLoaded());
        assertEquals(0, progress.adults());
        assertEquals(0, progress.beds());
    }

    @Test void aRemainingAssignedBedFootWithoutItsHeadIsNotACompleteBed() {
        BlockPos home=new BlockPos(0,64,0); ServerLevel world=world(List.of(resident(home))); beds(world);
        when(world.getBlockState(home.south())).thenReturn(Blocks.AIR.defaultBlockState());
        assertEquals(0,VillageLifeService.resettlementProgress(world,MARKER).beds());
    }
    @Test void twoBedHalvesWithDifferentFacingCannotSupportRecovery() {
        BlockPos home=new BlockPos(0,64,0); ServerLevel world=world(List.of(resident(home))); beds(world);
        BlockState wrong=bedState(BedPart.HEAD).setValue(BedBlock.FACING,Direction.WEST);
        when(world.getBlockState(home.south())).thenReturn(wrong);
        assertEquals(0,VillageLifeService.resettlementProgress(world,MARKER).beds());
    }

    private static ServerLevel world(List<Villager> residents) {
        ServerLevel world = mock(ServerLevel.class);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(world.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(320);
        when(world.getEntitiesOfClass(eq(Villager.class), any(AABB.class), any()))
                .thenReturn(residents);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        return world;
    }

    private static void beds(ServerLevel world) {
        BlockState head = bedState(BedPart.HEAD);
        BlockState foot = bedState(BedPart.FOOT);
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation ->
                ((BlockPos) invocation.getArgument(0)).getZ() == 1 ? head : foot);
    }

    private static BlockState bedState(BedPart part) {
        // Bootstrap initializes native blocks but does not load datapack tags.
        // Keep the native state/bed properties; isolate only that external tag binding.
        BlockState state = spy(Blocks.BED.pick(DyeColor.RED).defaultBlockState()
                .setValue(BedBlock.PART, part).setValue(BedBlock.FACING, Direction.SOUTH));
        when(state.is(BlockTags.BEDS)).thenReturn(true);
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Villager resident(BlockPos home) {
        Villager resident = mock(Villager.class);
        Brain<Villager> brain = mock(Brain.class);
        PathNavigation navigation = mock(PathNavigation.class);
        when(resident.getBrain()).thenReturn(brain);
        when(resident.getNavigation()).thenReturn(navigation);
        when(resident.isAlive()).thenReturn(true);
        when(resident.blockPosition()).thenReturn(new BlockPos(0, 64, 0));
        when(brain.getMemory(MemoryModuleType.HOME)).thenReturn(home == null
                ? Optional.empty() : Optional.of(GlobalPos.of(Level.OVERWORLD, home)));
        if (home != null) when(navigation.createPath(home, 0)).thenReturn(
                new Path(List.of(new Node(home.getX(), home.getY(), home.getZ())), home, true));
        return resident;
    }
}
