package de.quest.caravan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Chunk-safe surface selection for route surveys, ferries, and physical caravans. */
final class TradeRouteSurfaceResolver {
    private TradeRouteSurfaceResolver() {}

    static BlockPos findNearbyRoadSurface(ServerLevel world, BlockPos center, int radius) {
        if (world == null || center == null || !world.hasChunkAt(center)) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                BlockPos surface = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (surface == null
                        || Math.abs(surface.getY() - center.getY()) > 4
                        || !isStableCaravanSurface(world, surface)
                        || !isRoadBlock(world.getBlockState(surface.below()))) {
                    continue;
                }
                double distance = surface.distSqr(center);
                if (distance < bestDistance) {
                    best = surface;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    static BlockPos safeSurface(ServerLevel world, int x, int z) {
        BlockPos probe = new BlockPos(x, 64, z);
        if (!world.hasChunkAt(probe)) {
            return null;
        }
        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, topY, z);
        if (!world.getBlockState(feet).isAir()) {
            feet = feet.above();
        }
        BlockPos below = feet.below();
        BlockState belowState = world.getBlockState(below);
        if (!world.getBlockState(feet).isAir()
                || !world.getBlockState(feet.above()).isAir()
                || belowState.isAir()
                || !belowState.getFluidState().isEmpty()
                || belowState.is(BlockTags.LEAVES)
                || isDangerousSupport(belowState)
                || !belowState.isFaceSturdy(world, below, Direction.UP)) {
            return null;
        }
        return feet;
    }

    static BlockPos findCaravanSurface(ServerLevel world, BlockPos center, int radius) {
        if (world == null || center == null) {
            return null;
        }
        BlockPos fallback = null;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                    if (candidate == null || !isStableCaravanSurface(world, candidate)) {
                        continue;
                    }
                    if (isRoadBlock(world.getBlockState(candidate.below()))) {
                        return candidate;
                    }
                    if (fallback == null) {
                        fallback = candidate;
                    }
                }
            }
            if (fallback != null && ring >= 2) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean isStableCaravanSurface(ServerLevel world, BlockPos center) {
        if (center == null) {
            return false;
        }
        int stableNeighbors = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos neighbor = safeSurface(world, center.getX() + dx, center.getZ() + dz);
                if (neighbor != null && Math.abs(neighbor.getY() - center.getY()) <= 1) {
                    stableNeighbors++;
                }
            }
        }
        return stableNeighbors >= 5;
    }

    static BlockPos findNearbySafeSurface(ServerLevel world, int x, int z, int radius) {
        BlockPos center = safeSurface(world, x, z);
        if (center != null) {
            return center;
        }
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos candidate = safeSurface(world, x + dx, z + dz);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    static boolean isRoadBlock(BlockState state) {
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.SPRUCE_PLANKS)
                || state.is(Blocks.STONE_SLAB)
                || state.is(Blocks.COBBLESTONE_SLAB)
                || state.is(Blocks.OAK_SLAB)
                || state.is(Blocks.SPRUCE_SLAB);
    }

    private static boolean isDangerousSupport(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW);
    }
}
