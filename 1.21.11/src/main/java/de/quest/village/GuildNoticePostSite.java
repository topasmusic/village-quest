package de.quest.village;

import de.quest.content.block.GuildNoticePostBlock;
import de.quest.util.NaturalSurfacePolicy;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Compact 3x2 board with supported feet and a clear approach. Null reads mean protected/unloaded. */
final class GuildNoticePostSite {
    private GuildNoticePostSite() {}

    static boolean isSafe(BlockPos root, Direction facing, Function<BlockPos, BlockState> read) {
        if (root == null || facing == null || !facing.getAxis().isHorizontal() || read == null) return false;
        for (BlockPos pos : GuildNoticePostBlock.occupiedPositions(root, facing)) {
            if (!GuildCornerPlacementService.isClearableCover(read.apply(pos))) return false;
            if (pos.getY() == root.getY() && !support(read.apply(pos.below()), pos.below())) return false;
        }
        // The authored front is opposite the stored facing. Keep two standing spaces free.
        for (int step = 1; step <= 2; step++) {
            BlockPos feet = root.relative(facing.getOpposite(), step);
            if (!support(read.apply(feet.below()), feet.below())
                    || !GuildCornerPlacementService.isClearableCover(read.apply(feet))
                    || !GuildCornerPlacementService.isClearableCover(read.apply(feet.above()))) return false;
        }
        return true;
    }

    private static boolean support(BlockState state, BlockPos pos) {
        if (!NaturalSurfacePolicy.isStructurallyEligible(state) || state.hasBlockEntity()
                || state.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock) return false;
        return BlockFootingPolicy.supportsFooting(state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
    }

    static GuildCornerLandmarkState.Footprint footprint(BlockPos root, Direction facing) {
        Direction width = facing.getClockWise();
        var first = root.relative(width); var last = root.relative(width.getOpposite());
        return new GuildCornerLandmarkState.Footprint(Math.min(first.getX(), last.getX()), root.getY(),
                Math.min(first.getZ(), last.getZ()), Math.max(first.getX(), last.getX()), root.getY() + 1,
                Math.max(first.getZ(), last.getZ()));
    }
}
