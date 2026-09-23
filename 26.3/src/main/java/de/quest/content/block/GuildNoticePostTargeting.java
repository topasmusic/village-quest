package de.quest.content.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Extends client picking to the virtual cells occupied by the single-block notice post. */
public final class GuildNoticePostTargeting {
    private static final double DISTANCE_EPSILON = 1.0E-7;

    private GuildNoticePostTargeting() {}

    public record Target(BlockPos root, Direction facing) {
        public Target {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(facing, "facing");
            if (!facing.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("Guild Notice Post facing must be horizontal");
            }
            root = root.immutable();
        }
    }

    public static HitResult correctHit(BlockGetter world, Vec3 start, Vec3 end, HitResult vanillaHit) {
        if (world == null) return vanillaHit;
        return correctHit(start, end, vanillaHit, nearbyTargets(world, start, end));
    }

    public static HitResult correctHit(Vec3 start, Vec3 end, HitResult vanillaHit,
                                       Iterable<Target> targets) {
        if (start == null || end == null || targets == null) return vanillaHit;
        HitResult best = vanillaHit;
        double bestDistance = vanillaHit == null
                ? start.distanceToSqr(end)
                : start.distanceToSqr(vanillaHit.getLocation());

        for (Target target : targets) {
            if (target == null) continue;
            BlockHitResult candidate = GuildNoticePostBlock.selectionShape(target.facing())
                    .clip(start, end, target.root());
            if (candidate == null) continue;
            double candidateDistance = start.distanceToSqr(candidate.getLocation());
            if (candidateDistance + DISTANCE_EPSILON < bestDistance) {
                best = serverSafeHit(candidate, target.root());
                bestDistance = candidateDistance;
            }
        }
        return best;
    }

    private static BlockHitResult serverSafeHit(BlockHitResult visualHit, BlockPos root) {
        Vec3 location = visualHit.getLocation();
        Vec3 rootLocalLocation = new Vec3(
                Math.clamp(location.x, root.getX(), root.getX() + 1.0),
                Math.clamp(location.y, root.getY(), root.getY() + 1.0),
                Math.clamp(location.z, root.getZ(), root.getZ() + 1.0));
        return new BlockHitResult(rootLocalLocation, visualHit.getDirection(), root, visualHit.isInside());
    }

    private static List<Target> nearbyTargets(BlockGetter world, Vec3 start, Vec3 end) {
        int minX = (int) Math.floor(Math.min(start.x, end.x)) - 1;
        int minY = (int) Math.floor(Math.min(start.y, end.y)) - 1;
        int minZ = (int) Math.floor(Math.min(start.z, end.z)) - 1;
        int maxX = (int) Math.floor(Math.max(start.x, end.x)) + 1;
        int maxY = (int) Math.floor(Math.max(start.y, end.y)) + 1;
        int maxZ = (int) Math.floor(Math.max(start.z, end.z)) + 1;
        List<Target> targets = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof GuildNoticePostBlock) {
                targets.add(new Target(pos, state.getValue(GuildNoticePostBlock.FACING)));
            }
        }
        return targets;
    }
}
