package de.quest.content.block;

import de.quest.kingdom.BuildingScanner;
import de.quest.kingdom.KingdomBuildingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Physical desk only; official roles/identity live in the shared registry, never in an item or duplicate container. */
public final class KingdomAnchorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final BuildingScanner.Role role;
    public KingdomAnchorBlock(BlockBehaviour.Properties properties, BuildingScanner.Role role) {
        super(properties); this.role = role; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() { return com.mojang.serialization.MapCodec.unit(this); }
    public BuildingScanner.Role role() { return role; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KingdomAnchorBlockEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel world && player instanceof ServerPlayer actor)
            KingdomBuildingService.open(world, actor, pos, role, state.getValue(FACING));
        return InteractionResult.CONSUME;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (role == BuildingScanner.Role.INN) return Block.box(2, 0, 2, 14, 15, 14);
        if (role == BuildingScanner.Role.STOREHOUSE) return Block.box(1, 0, 1, 15, 14, 15);
        if (role == BuildingScanner.Role.MARKET) return Shapes.or(Block.box(0, 10, 0, 16, 14, 16), Block.box(2, 0, 2, 14, 10, 14));
        return Shapes.or(Block.box(2, 0, 2, 14, 7, 14),
                state.getValue(FACING) == Direction.NORTH ? Block.box(2, 7, 12, 14, 16, 15)
                : state.getValue(FACING) == Direction.SOUTH ? Block.box(2, 7, 1, 14, 16, 4)
                : state.getValue(FACING) == Direction.WEST ? Block.box(12, 7, 2, 15, 16, 14) : Block.box(1, 7, 2, 4, 16, 14));
    }
}
