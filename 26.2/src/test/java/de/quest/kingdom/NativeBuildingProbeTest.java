package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NativeBuildingProbeTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unloadedProbeNeverReadsBlocksOrRequestsAChunk() {
        ServerLevel world = mock(ServerLevel.class);
        var probe = new NativeBuildingProbe(world);
        assertFalse(probe.read(POS).loaded());
        verify(world, never()).getBlockState(any());
        verify(world, never()).getChunk(any(BlockPos.class));
    }

    @Test void onlyBottomStraightDryStairsCountAsSeatsAndTheirLowSideFacesTheTable() {
        var state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM);
        var seat = NativeBuildingProbe.classify(state, blocks(), POS);
        assertTrue(seat.features().contains(BuildingScanner.Feature.SEAT));
        assertEquals(Direction.SOUTH, seat.seatFacing());
        var inverted = NativeBuildingProbe.classify(state.setValue(StairBlock.HALF, Half.TOP), blocks(), POS);
        assertFalse(inverted.features().contains(BuildingScanner.Feature.SEAT));
    }

    @Test void closedWoodDoorIsTraversableForOwnDoorOpeningNpcButClosedIronDoorIsNot() {
        var wood = NativeBuildingProbe.classify(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, false), blocks(), POS);
        var iron = NativeBuildingProbe.classify(Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, false), blocks(), POS);
        assertTrue(wood.passable()); assertFalse(wood.roof()); assertFalse(wood.support());
        assertFalse(iron.passable());
        assertTrue(NativeBuildingProbe.classify(Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true), blocks(), POS).passable());
    }

    @Test void glassAndSlabAreRoofsWhileAirAndWaterAreNotFunctionalFloor() {
        assertTrue(NativeBuildingProbe.classify(Blocks.GLASS.defaultBlockState(), blocks(), POS).roof());
        assertTrue(NativeBuildingProbe.classify(Blocks.STONE_SLAB.defaultBlockState(), blocks(), POS).roof());
        assertFalse(NativeBuildingProbe.classify(Blocks.AIR.defaultBlockState(), blocks(), POS).support());
        var water = NativeBuildingProbe.classify(Blocks.WATER.defaultBlockState(), blocks(), POS);
        assertFalse(water.passable()); assertFalse(water.support());
    }

    @Test void bedFootCountsOneStationAndHeadDoesNotCreateAnotherBed() {
        var bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState();
        assertTrue(NativeBuildingProbe.classify(bed.setValue(BedBlock.PART, BedPart.FOOT), blocks(), POS)
                .features().contains(BuildingScanner.Feature.BED));
        assertFalse(NativeBuildingProbe.classify(bed.setValue(BedBlock.PART, BedPart.HEAD), blocks(), POS)
                .features().contains(BuildingScanner.Feature.BED));
    }

    @Test void barrelIsAContainerButAnchorPlaceholderCannotBeCountedAsTwoVanillaBarrels() {
        var barrel = NativeBuildingProbe.classify(Blocks.BARREL.defaultBlockState(), blocks(), POS);
        assertTrue(barrel.features().contains(BuildingScanner.Feature.BARREL));
        assertTrue(barrel.features().contains(BuildingScanner.Feature.CONTAINER));
        assertFalse(NativeBuildingProbe.classify(Blocks.OAK_PLANKS.defaultBlockState(), blocks(), POS)
                .features().contains(BuildingScanner.Feature.BARREL));
    }

    @Test void innBedNeedsMatchingLoadedHeadInTheBudgetedSnapshot() {
        var foot=Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT);
        var head=foot.setValue(BedBlock.PART,BedPart.HEAD);
        assertFalse(bedCheck(foot,Blocks.AIR.defaultBlockState()));
        assertFalse(bedCheck(foot,head.setValue(BedBlock.FACING,Direction.WEST)));
        assertFalse(bedCheck(foot,Blocks.BED.pick(DyeColor.BLUE).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD)));
        assertTrue(bedCheck(foot,head));
    }
    private static boolean bedCheck(net.minecraft.world.level.block.state.BlockState foot,net.minecraft.world.level.block.state.BlockState head) {
        var bedPos=POS.west(4);
        var scanner=new BuildingScanner(POS,Direction.NORTH,BuildingScanner.Role.INN,BuildingScanner.Range.BASIC,pos -> {
            if(pos.equals(bedPos)) return NativeBuildingProbe.classify(foot,blocks(),pos);
            if(pos.equals(bedPos.east())) return NativeBuildingProbe.classify(head,blocks(),pos);
            return new BuildingScanner.Cell(true,pos.getY()!=63 && pos.getY()!=67,pos.getY()==63 || pos.getY()==67,pos.getY()==67,java.util.Set.of(),null);
        });
        while(!scanner.done()) scanner.advance(256);
        return scanner.report().orElseThrow().checks().get(BuildingScanner.Check.BED);
    }

    @Test void eitherReachableHalfOfADoubleChestResolvesToOneSharedResourceAndContainer() {
        var left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos rightPos = POS.relative(ChestBlock.getConnectedDirection(left));
        var right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
        var a = NativeBuildingProbe.classify(left, blocks(), POS);
        var b = NativeBuildingProbe.classify(right, blocks(), rightPos);
        assertTrue(a.features().contains(BuildingScanner.Feature.CONTAINER));
        assertTrue(b.features().contains(BuildingScanner.Feature.CONTAINER));
        assertEquals(POS, a.resourceRoot()); assertEquals(POS, b.resourceRoot());
    }

    private static BlockGetter blocks() {
        BlockGetter blocks = mock(BlockGetter.class);
        when(blocks.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return blocks;
    }
}
