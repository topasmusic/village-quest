package de.quest.kingdom;

import de.quest.village.BlockFootingPolicy;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/** Reads one already-loaded block per budget sample; collision evaluation cannot load neighbours. */
public final class NativeBuildingProbe implements BuildingScanner.Probe {
    private final ServerLevel world;
    private final java.util.function.Predicate<BlockPos> availableBed;
    private final BedAccess bedPermission;
    public record BedUse(KingdomBedRegistry.Permission permission,boolean ownHeld) {}
    @FunctionalInterface public interface BedAccess { BedUse inspect(BlockPos foot,BlockPos head,BlockState state); }
    public NativeBuildingProbe(ServerLevel world) { this(world, pos -> true); }
    public NativeBuildingProbe(ServerLevel world, java.util.function.Predicate<BlockPos> availableBed) {
        this(world,availableBed,(foot,head,state) -> new BedUse(KingdomBedRegistry.Permission.AVAILABLE,false));
    }
    public NativeBuildingProbe(ServerLevel world, java.util.function.Predicate<BlockPos> availableBed,
                               BedAccess bedPermission) {
        this.world = java.util.Objects.requireNonNull(world); this.availableBed = java.util.Objects.requireNonNull(availableBed);
        this.bedPermission=java.util.Objects.requireNonNull(bedPermission);
    }

    @Override public BuildingScanner.Cell read(BlockPos pos) {
        if (!world.hasChunkAt(pos)) return BuildingScanner.Cell.unknown();
        BlockState state = world.getBlockState(pos);
        BuildingScanner.Cell cell = classify(state, EmptyBlockGetter.INSTANCE, pos);
        if (cell.features().contains(BuildingScanner.Feature.BED)) {
            var head=pos.relative(state.getValue(BedBlock.FACING)); var use=bedPermission.inspect(pos,head,state);
            if(use.permission()==KingdomBedRegistry.Permission.UNKNOWN) return BuildingScanner.Cell.unknown();
            if(use.permission()==KingdomBedRegistry.Permission.RESERVED || !availableBed.test(pos) || !availableBed.test(head)) {
                var features = EnumSet.copyOf(cell.features()); features.remove(BuildingScanner.Feature.BED);
                return new BuildingScanner.Cell(cell.loaded(), cell.passable(), cell.support(), cell.roof(), features, cell.seatFacing(), cell.resourceRoot(), cell.bed());
            }
            if(use.ownHeld()) cell=new BuildingScanner.Cell(cell.loaded(),cell.passable(),cell.support(),cell.roof(),cell.features(),cell.seatFacing(),cell.resourceRoot(),
                    new BuildingScanner.BedGeometry(cell.bed().block(),cell.bed().facing(),cell.bed().part(),cell.bed().occupied(),true));
        }
        return cell;
    }

    static BuildingScanner.Cell classify(BlockState state, BlockGetter blocks, BlockPos pos) {
        var features = EnumSet.noneOf(BuildingScanner.Feature.class);
        boolean dry = state.getFluidState().isEmpty();
        boolean hazard = state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.WITHER_ROSE);
        var shape = state.getCollisionShape(blocks, pos);
        boolean door = state.getBlock() instanceof DoorBlock;
        boolean passable = dry && !hazard && (shape.isEmpty()
                || door && (state.getValue(DoorBlock.OPEN) || ((DoorBlock) state.getBlock()).type().canOpenByHand()));
        boolean support = dry && !hazard && !door && !state.is(BlockTags.LEAVES)
                && BlockFootingPolicy.supportsFooting(shape);
        boolean roof = dry && !hazard && !door && !state.is(BlockTags.LEAVES) && !shape.isEmpty()
                && shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X) >= 0.6
                && shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z) >= 0.6;
        Direction seatFacing = null;
        BlockPos resourceRoot = null;
        BuildingScanner.BedGeometry bed=null;
        if (dry && !hazard) {
            if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM
                    && state.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT) {
                features.add(BuildingScanner.Feature.SEAT);
                seatFacing = state.getValue(StairBlock.FACING).getOpposite();
            }
            if (state.getBlock() instanceof BedBlock) {
                bed=new BuildingScanner.BedGeometry(state.getBlock(),state.getValue(BedBlock.FACING),state.getValue(BedBlock.PART),state.getValue(BedBlock.OCCUPIED),false);
                if (bed.part()==BedPart.FOOT) features.add(BuildingScanner.Feature.BED);
            }
            boolean barrel = state.is(Blocks.BARREL);
            if (barrel) features.add(BuildingScanner.Feature.BARREL);
            if (barrel || state.getBlock() instanceof ChestBlock) {
                features.add(BuildingScanner.Feature.CONTAINER);
                resourceRoot = state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT
                        ? pos.relative(ChestBlock.getConnectedDirection(state)) : pos;
            }
            boolean table = state.getBlock() instanceof SlabBlock || state.is(Blocks.CRAFTING_TABLE)
                    || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.LECTERN);
            if (table) features.add(BuildingScanner.Feature.TABLE);
            if (state.getBlock() instanceof de.quest.content.block.MarketStallBlock) {
                features.add(BuildingScanner.Feature.STALL); features.add(BuildingScanner.Feature.COUNTER);
            }
            if (table || barrel || state.is(BlockTags.PLANKS)) features.add(BuildingScanner.Feature.COUNTER);
            if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF) || state.is(Blocks.LECTERN))
                features.add(BuildingScanner.Feature.BOOKS);
            boolean kitchen = state.is(Blocks.SMOKER) || state.is(Blocks.CAULDRON)
                    || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.BREWING_STAND);
            if (kitchen) features.add(BuildingScanner.Feature.KITCHEN);
            if (kitchen || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CARTOGRAPHY_TABLE)
                    || state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.LOOM) || state.is(Blocks.FLETCHING_TABLE)
                    || state.is(Blocks.STONECUTTER) || state.is(Blocks.COMPOSTER) || state.is(Blocks.GRINDSTONE))
                features.add(BuildingScanner.Feature.WORKSTATION);
            if (state.getLightEmission() > 0) features.add(BuildingScanner.Feature.LIGHT);
        }
        return new BuildingScanner.Cell(true, passable, support, roof, features, seatFacing, resourceRoot, bed);
    }
}
