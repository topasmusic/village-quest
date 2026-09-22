package de.quest.content.block;

import com.mojang.serialization.MapCodec;
import de.quest.guildtown.GuildTownService;
import de.quest.shrine.VillageBondService;
import de.quest.village.GuildCornerLandmarkState;
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
import java.util.ArrayList;
import java.util.List;

public final class GuildNoticePostBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH_SOUTH = Shapes.or(
            Block.box(-16, 0, 5, -6, 2, 11),
            Block.box(22, 0, 5, 32, 2, 11),
            Block.box(-13, 2, 6, -9, 30, 10),
            Block.box(25, 2, 6, 29, 30, 10),
            Block.box(-16, 5, 5, 32, 32, 11));
    private static final VoxelShape EAST_WEST = Shapes.or(
            Block.box(5, 0, -16, 11, 2, -6),
            Block.box(5, 0, 22, 11, 2, 32),
            Block.box(6, 2, -13, 10, 30, -9),
            Block.box(6, 2, 25, 10, 30, 29),
            Block.box(5, 5, -16, 11, 32, 32));

    public GuildNoticePostBlock(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return selectionShape(state.getValue(FACING)); }

    public static VoxelShape selectionShape(Direction facing) {
        return facing != null && facing.getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos root = context.getClickedPos();
        for (BlockPos occupied : occupiedPositions(root, facing)) {
            if (occupied.equals(root)) continue;
            if (!context.getLevel().getBlockState(occupied).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Six virtual cells occupied by the single saved block: three wide and two high. */
    public static List<BlockPos> occupiedPositions(BlockPos root, Direction facing) {
        if (root == null || facing == null || !facing.getAxis().isHorizontal()) return List.of();
        Direction width = facing.getClockWise();
        List<BlockPos> result = new ArrayList<>(6);
        for (int y = 0; y <= 1; y++) {
            for (int horizontal = -1; horizontal <= 1; horizontal++) {
                result.add(root.relative(width, horizontal).above(y).immutable());
            }
        }
        return List.copyOf(result);
    }

    public static boolean occupies(BlockPos root, Direction facing, BlockPos target) {
        return target != null && occupiedPositions(root, facing).contains(target);
    }

    /** Rejects BlockItem placement into any overhanging part of a nearby 3x2 board. */
    public static boolean blocksPlacementAt(BlockGetter world, BlockPos target) {
        if (world == null || target == null) return false;
        for (int y = target.getY() - 1; y <= target.getY(); y++) {
            for (int x = target.getX() - 1; x <= target.getX() + 1; x++) {
                for (int z = target.getZ() - 1; z <= target.getZ() + 1; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(candidate);
                    if (!(state.getBlock() instanceof GuildNoticePostBlock)) continue;
                    if (occupies(candidate, state.getValue(FACING), target)) return true;
                }
            }
        }
        return false;
    }
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
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof net.minecraft.server.level.ServerLevel world) {
            GuildCornerLandmarkState.get(world.getServer()).markRemovedByPlayer(
                    GuildCornerLandmarkState.dimensionKey(world), pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel world && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) return VillageBondService.useNoticePost(world, serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }

    static GuildTownService.NoticePostStoryResolution useWithoutItem(
            GuildTownService.NoticePostStoryContext interaction) {
        return VillageBondService.useNoticePost(interaction);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
