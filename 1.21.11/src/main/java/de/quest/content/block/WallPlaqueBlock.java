package de.quest.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.block.ShapeContext;

public final class WallPlaqueBlock extends HorizontalFacingBlock {
    public static final MapCodec<WallPlaqueBlock> CODEC = createCodec(WallPlaqueBlock::new);

    private static final double MIN_X = 2.75d;
    private static final double MAX_X = 13.25d;
    private static final double MIN_Y = 1.0d;
    private static final double MAX_Y = 15.0d;
    private static final double MIN_Z = 2.75d;
    private static final double MAX_Z = 13.25d;
    private static final double DEPTH = 1.5d;
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(MIN_X, MIN_Y, 16.0d - DEPTH, MAX_X, MAX_Y, 16.0d);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(MIN_X, MIN_Y, 0.0d, MAX_X, MAX_Y, DEPTH);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0d, MIN_Y, MIN_Z, DEPTH, MAX_Y, MAX_Z);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(16.0d - DEPTH, MIN_Y, MIN_Z, 16.0d, MAX_Y, MAX_Z);

    public WallPlaqueBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return false;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        return world.getBlockState(supportPos).isSideSolidFullSquare(world, supportPos, facing);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        Direction clickedFace = context.getSide();
        if (clickedFace.getAxis().isVertical()) {
            return null;
        }

        BlockPos placementPos = context.getBlockPos();
        BlockState state = this.getDefaultState().with(FACING, clickedFace);
        return state.canPlaceAt(context.getWorld(), placementPos) ? state : null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state,
                                                   WorldView world,
                                                   ScheduledTickView tickView,
                                                   BlockPos pos,
                                                   Direction direction,
                                                   BlockPos neighborPos,
                                                   BlockState neighborState,
                                                   Random random) {
        return direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)
                ? Blocks.AIR.getDefaultState()
                : super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
