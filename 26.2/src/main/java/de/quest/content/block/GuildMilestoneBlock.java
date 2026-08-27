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

public final class GuildMilestoneBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(4, 3, 4, 12, 16, 12));
    public GuildMilestoneBlock(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel world
                && placer instanceof net.minecraft.server.level.ServerPlayer player) {
            VillageBondService.registerDecoration(world, player, pos, 1);
        }
    }
    @Override public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof net.minecraft.server.level.ServerLevel world) VillageBondService.removeDecoration(world, pos);
        super.destroy(level, pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel world && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!player.getMainHandItem().is(de.quest.registry.ModItems.WAYFARERS_SIGIL)
                    && !player.getOffhandItem().is(de.quest.registry.ModItems.WAYFARERS_SIGIL)) {
                if (VillageBondService.isActiveRuinMilestone(world, serverPlayer, pos)) {
                    return InteractionResult.SUCCESS;
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.village-quest.wayshrine.inspect_sigil_required")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            return VillageBondService.inspectWithSigil(world, serverPlayer, pos, state);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
