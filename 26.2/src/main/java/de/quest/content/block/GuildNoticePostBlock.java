package de.quest.content.block;

import com.mojang.serialization.MapCodec;
import de.quest.shrine.VillageBondService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class GuildNoticePostBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH_SOUTH = Shapes.or(
            Block.box(-7, 0, 5, 1, 2, 11),
            Block.box(15, 0, 5, 23, 2, 11),
            Block.box(-5, 2, 6, -1, 29, 10),
            Block.box(17, 2, 6, 21, 29, 10),
            Block.box(-6, 6, 5, 22, 29, 11));
    private static final VoxelShape EAST_WEST = Shapes.or(
            Block.box(5, 0, -7, 11, 2, 1),
            Block.box(5, 0, 15, 11, 2, 23),
            Block.box(6, 2, -5, 10, 29, -1),
            Block.box(6, 2, 17, 10, 29, 21),
            Block.box(5, 6, -6, 11, 29, 22));

    public GuildNoticePostBlock(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel world
                && placer instanceof net.minecraft.server.level.ServerPlayer player) {
            VillageBondService.registerDecoration(world, player, pos, 0);
        }
    }
    @Override public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof net.minecraft.server.level.ServerLevel world) VillageBondService.removeDecoration(world, pos);
        super.destroy(level, pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel world && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) return VillageBondService.useNoticePost(world, serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
