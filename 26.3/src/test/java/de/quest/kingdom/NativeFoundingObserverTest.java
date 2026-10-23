package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native block/path API with mocked dependencies only: no entities/world/server runtime. */
final class NativeFoundingObserverTest {
    private static final BlockPos COUNCIL = new BlockPos(0, 64, 0), JOB = new BlockPos(2, 64, 0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void assignedBedsAndWorkingAssignedPoiSupplyTheRealThreeThreeProof() {
        var villagers = List.of(resident(new BlockPos(1, 64, 1), JOB),
                resident(new BlockPos(3, 64, 1), null), resident(new BlockPos(5, 64, 1), null));
        var evidence = NativeFoundingObserver.observe(world(villagers), COUNCIL, BuildingScanner.Range.BASIC, 12);
        assertTrue(evidence.fullyLoaded()); assertEquals(3, evidence.adults()); assertEquals(3, evidence.residents().size());
        assertTrue(evidence.residents().stream().anyMatch(r -> JOB.equals(r.job()))); assertEquals(4, evidence.pathChecks());
    }

    @Test void solidJobBlockUsesNativeInteractionRangeInsteadOfDemandingAPathIntoItsInterior() {
        var worker=resident(new BlockPos(1,64,1),JOB);
        when(worker.getNavigation().createPath(JOB,0)).thenReturn(new Path(List.of(new Node(2,64,0)),JOB,false));
        var proof=NativeFoundingObserver.observe(world(List.of(worker,resident(new BlockPos(3,64,1),null),resident(new BlockPos(5,64,1),null))),COUNCIL,BuildingScanner.Range.BASIC,12);
        assertTrue(proof.hasJob()); verify(worker.getNavigation()).createPath(JOB,1);
        verify(worker.getNavigation(),never()).createPath(JOB,0);
    }

    @Test void unloadedAreaNeverQueriesEntitiesBlocksOrNavigation() {
        ServerLevel world = mock(ServerLevel.class);
        var evidence = NativeFoundingObserver.observe(world, COUNCIL, BuildingScanner.Range.BASIC, 12);
        assertFalse(evidence.fullyLoaded()); verify(world, never()).getBlockState(any());
        verify(world, never()).getEntitiesOfClass(eq(Villager.class), any(AABB.class), any());
    }

    @Test void pathBudgetExhaustionReturnsUnknownInsteadOfInventingARejectedOrCompleteVillage() {
        var villagers = List.of(resident(new BlockPos(1, 64, 1), JOB),
                resident(new BlockPos(3, 64, 1), null), resident(new BlockPos(5, 64, 1), null));
        var evidence = NativeFoundingObserver.observe(world(villagers), COUNCIL, BuildingScanner.Range.BASIC, 2);
        assertFalse(evidence.fullyLoaded()); assertEquals(2, evidence.pathChecks()); assertTrue(evidence.residents().size() < 3);
    }

    @Test void inactiveOrUnreachableJobDoesNotSatisfyTheFoundingRequirement() {
        var worker = resident(new BlockPos(1, 64, 1), JOB);
        var villagers = List.of(worker, resident(new BlockPos(3, 64, 1), null), resident(new BlockPos(5, 64, 1), null));
        var world = world(villagers); when(world.getBlockState(JOB)).thenReturn(Blocks.AIR.defaultBlockState());
        var evidence = NativeFoundingObserver.observe(world, COUNCIL, BuildingScanner.Range.BASIC, 12);
        assertTrue(evidence.fullyLoaded()); assertEquals(3, evidence.residents().size());
        assertTrue(evidence.residents().stream().noneMatch(r -> r.job() != null));
        world = world(villagers); when(worker.getNavigation().createPath(JOB, 1)).thenReturn(new Path(List.of(new Node(2, 64, 0)), JOB, false));
        evidence = NativeFoundingObserver.observe(world, COUNCIL, BuildingScanner.Range.BASIC, 12);
        assertTrue(evidence.residents().stream().noneMatch(r -> r.job() != null));
    }

    @Test void deadChildrenAndResidentsOutsideTheConfirmedYRoomCannotBeBorrowed() {
        var child = resident(new BlockPos(1, 64, 1), JOB); when(child.isBaby()).thenReturn(true);
        var dead = resident(new BlockPos(3, 64, 1), null); when(dead.isAlive()).thenReturn(false);
        var upstairs = resident(new BlockPos(5, 64, 1), null); when(upstairs.blockPosition()).thenReturn(COUNCIL.above(20));
        var evidence = NativeFoundingObserver.observe(world(List.of(child, dead, upstairs)), COUNCIL, BuildingScanner.Range.BASIC, 12);
        assertEquals(0, evidence.adults()); assertTrue(evidence.residents().isEmpty()); assertEquals(0, evidence.pathChecks());
    }

    @Test void sharedNativeBlockBudgetIsRespectedWithoutReadingAnUnbudgetedBedHalf() {
        var villagers = List.of(resident(new BlockPos(1, 64, 1), JOB), resident(new BlockPos(3, 64, 1), null), resident(new BlockPos(5, 64, 1), null));
        var world = world(villagers); var budget = new BuildingWorkBudget(); budget.begin(0); budget.takeBlocks(254);
        var evidence = NativeFoundingObserver.observe(world, COUNCIL, BuildingScanner.Range.BASIC, 12, budget::takeBlock, id -> true);
        assertFalse(evidence.fullyLoaded()); assertEquals(0, evidence.pathChecks());
        verify(world, times(2)).getBlockState(any()); assertEquals(0, budget.blocks());
    }

    private static ServerLevel world(List<Villager> villagers) {
        ServerLevel world = mock(ServerLevel.class);
        when(world.hasChunk(anyInt(), anyInt())).thenReturn(true); when(world.hasChunkAt(any())).thenReturn(true);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.getEntitiesOfClass(eq(Villager.class), any(AABB.class), any())).thenReturn(villagers);
        when(world.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.equals(JOB)) return Blocks.SMOKER.defaultBlockState();
            if (pos.getY() == 64 && pos.getZ() == 1 && pos.getX() > 0 && pos.getX() <= 5)
                return Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT)
                        .setValue(BedBlock.FACING, Direction.SOUTH);
            if (pos.getY() == 64 && pos.getZ() == 2 && pos.getX() > 0 && pos.getX() <= 5)
                return Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD)
                        .setValue(BedBlock.FACING, Direction.SOUTH);
            return Blocks.AIR.defaultBlockState();
        });
        return world;
    }
    @SuppressWarnings("unchecked") private static Villager resident(BlockPos bed, BlockPos job) {
        Villager resident = mock(Villager.class); Brain<Villager> brain = mock(Brain.class);
        PathNavigation navigation = mock(PathNavigation.class); VillagerData data = mock(VillagerData.class);
        VillagerProfession profession = mock(VillagerProfession.class);
        Holder<VillagerProfession> holder = Holder.direct(profession);
        when(profession.heldJobSite()).thenReturn(ignored -> true);
        when(data.profession()).thenReturn(holder); when(resident.getVillagerData()).thenReturn(data);
        when(resident.getUUID()).thenReturn(UUID.randomUUID()); when(resident.isAlive()).thenReturn(true);
        when(resident.blockPosition()).thenReturn(COUNCIL); when(resident.getBrain()).thenReturn(brain);
        when(resident.getNavigation()).thenReturn(navigation);
        when(brain.getMemory(MemoryModuleType.HOME)).thenReturn(Optional.of(GlobalPos.of(Level.OVERWORLD, bed)));
        when(brain.getMemory(MemoryModuleType.JOB_SITE)).thenReturn(job == null ? Optional.empty() : Optional.of(GlobalPos.of(Level.OVERWORLD, job)));
        when(navigation.createPath(any(BlockPos.class), anyInt())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0); return new Path(List.of(new Node(pos.getX(), pos.getY(), pos.getZ())), pos, true);
        });
        return resident;
    }
}
