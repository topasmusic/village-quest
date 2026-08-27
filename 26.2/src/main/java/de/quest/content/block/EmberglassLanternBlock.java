package de.quest.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EmberglassLanternBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 0, 6, 10, 12, 10), Block.box(4, 1, 4, 12, 9, 12), Block.box(5, 12, 7, 11, 16, 9));
    public EmberglassLanternBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5, 0.0, 0.01, 0.0);
    }
}
