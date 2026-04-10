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
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WallPlaqueBlock extends HorizontalDirectionalBlock {
    private static final double MIN_X = 2.75d;
    private static final double MAX_X = 13.25d;
    private static final double MIN_Y = 1.0d;
    private static final double MAX_Y = 15.0d;
    private static final double MIN_Z = 2.75d;
    private static final double MAX_Z = 13.25d;
    private static final double DEPTH = 1.5d;
    private static final VoxelShape NORTH_SHAPE = Block.box(MIN_X, MIN_Y, 16.0d - DEPTH, MAX_X, MAX_Y, 16.0d);
    private static final VoxelShape SOUTH_SHAPE = Block.box(MIN_X, MIN_Y, 0.0d, MAX_X, MAX_Y, DEPTH);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0d, MIN_Y, MIN_Z, DEPTH, MAX_Y, MAX_Z);
    private static final VoxelShape WEST_SHAPE = Block.box(16.0d - DEPTH, MIN_Y, MIN_Z, 16.0d, MAX_Y, MAX_Z);

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
