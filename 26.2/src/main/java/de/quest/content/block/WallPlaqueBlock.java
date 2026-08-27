package de.quest.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WallPlaqueBlock extends HorizontalDirectionalBlock {
    private static final double CORE_MIN = 2.0d;
    private static final double CORE_MAX = 14.0d;
    private static final double CAP_MIN = 0.75d;
    private static final double CAP_MAX = 15.25d;
    private static final double CAP_BAND_MIN = 5.0d;
    private static final double CAP_BAND_MAX = 11.0d;
    private static final double DEPTH = 1.5d;
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(CORE_MIN, CORE_MIN, 16.0d - DEPTH, CORE_MAX, CORE_MAX, 16.0d),
            Block.box(CAP_BAND_MIN, CAP_MIN, 16.0d - DEPTH, CAP_BAND_MAX, CAP_MAX, 16.0d),
            Block.box(CAP_MIN, CAP_BAND_MIN, 16.0d - DEPTH, CAP_MAX, CAP_BAND_MAX, 16.0d));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(CORE_MIN, CORE_MIN, 0.0d, CORE_MAX, CORE_MAX, DEPTH),
            Block.box(CAP_BAND_MIN, CAP_MIN, 0.0d, CAP_BAND_MAX, CAP_MAX, DEPTH),
            Block.box(CAP_MIN, CAP_BAND_MIN, 0.0d, CAP_MAX, CAP_BAND_MAX, DEPTH));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0d, CORE_MIN, CORE_MIN, DEPTH, CORE_MAX, CORE_MAX),
            Block.box(0.0d, CAP_MIN, CAP_BAND_MIN, DEPTH, CAP_MAX, CAP_BAND_MAX),
            Block.box(0.0d, CAP_BAND_MIN, CAP_MIN, DEPTH, CAP_BAND_MAX, CAP_MAX));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(16.0d - DEPTH, CORE_MIN, CORE_MIN, 16.0d, CORE_MAX, CORE_MAX),
            Block.box(16.0d - DEPTH, CAP_MIN, CAP_BAND_MIN, 16.0d, CAP_MAX, CAP_BAND_MAX),
            Block.box(16.0d - DEPTH, CAP_BAND_MIN, CAP_MIN, 16.0d, CAP_BAND_MAX, CAP_MAX));

    public WallPlaqueBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return world.getBlockState(supportPos).isFaceSturdy(world, supportPos, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isVertical()) {
            return null;
        }

        BlockPos placementPos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(FACING, clickedFace);
        return state.canSurvive(context.getLevel(), placementPos) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     LevelReader world,
                                     ScheduledTickAccess scheduledTickAccess,
                                     BlockPos pos,
                                     Direction direction,
                                     BlockPos neighborPos,
                                     BlockState neighborState,
                                     RandomSource random) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(world, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, world, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
