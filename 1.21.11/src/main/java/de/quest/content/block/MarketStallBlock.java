package de.quest.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** A physical trader counter. No inventory or saved village identity is carried by the item. */
public final class MarketStallBlock extends Block {
    /** Zero keeps old saves and the inventory model green; placement chooses one of five cloth palettes. */
    public static final IntegerProperty CLOTH = IntegerProperty.create("cloth",0,4);
    public MarketStallBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(CLOTH,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CLOTH); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(CLOTH,context.getLevel().getRandom().nextInt(5));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(Block.box(0,8,0,16,11,16),Block.box(2,0,2,5,8,5),Block.box(11,0,11,14,8,14),
                Block.box(1,11,13,3,15,15),Block.box(13,11,13,15,15,15),Block.box(0,15,0,16,16,16));
    }
}
