package de.quest.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Generic support policy shared by world-safe quest-site placement. */
public final class NaturalSurfacePolicy {
    private NaturalSurfacePolicy() {}

    public static boolean isStructurallyEligible(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof LeavesBlock)
                && !state.is(BlockTags.LEAVES)
                && !state.is(Blocks.MAGMA_BLOCK)
                && !state.is(Blocks.CACTUS)
                && !state.is(Blocks.CAMPFIRE)
                && !state.is(Blocks.SOUL_CAMPFIRE)
                && !state.is(Blocks.POWDER_SNOW);
    }

    public static boolean isSafeNaturalSupport(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null || world.getBlockEntity(pos) != null) return false;
        BlockState state = world.getBlockState(pos);
        return isStructurallyEligible(state) && state.isFaceSturdy(world, pos, Direction.UP);
    }
}
