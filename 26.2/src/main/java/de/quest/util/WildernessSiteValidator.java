package de.quest.util;

import de.quest.caravan.TradeRouteService;
import de.quest.data.QuestState;
import de.quest.shrine.VillageBondService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Conservative shared protection for generated quest sites in existing worlds. */
public final class WildernessSiteValidator {
    private static final long LEGACY_INHABITED_TIME_LIMIT = 72_000L;

    private WildernessSiteValidator() {}

    public static BlockPos findNaturalFlatSite(ServerLevel world, int centerX, int centerZ,
                                                int innerRadius, int outerRadius,
                                                int maxInnerDelta, int maxOuterDelta,
                                                int networkSafetyRadius, boolean bypassPlayerHistory) {
        if (world == null || innerRadius < 0 || outerRadius < innerRadius) return null;
        BlockPos seaLevel = new BlockPos(centerX, world.getSeaLevel(), centerZ);
        if (!world.getWorldBorder().isWithinBounds(seaLevel)
                || world.getUncachedNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(world.getSeaLevel()),
                QuartPos.fromBlock(centerZ)).is(BiomeTags.IS_OCEAN)
                || world.getUncachedNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(world.getSeaLevel()),
                QuartPos.fromBlock(centerZ)).is(BiomeTags.IS_RIVER)) {
            return null;
        }

        int minInner = Integer.MAX_VALUE;
        int maxInner = Integer.MIN_VALUE;
        int minOuter = Integer.MAX_VALUE;
        int maxOuter = Integer.MIN_VALUE;
        for (int xOffset = -outerRadius; xOffset <= outerRadius; xOffset++) {
            for (int zOffset = -outerRadius; zOffset <= outerRadius; zOffset++) {
                int x = centerX + xOffset;
                int z = centerZ + zOffset;
                if (!world.hasChunk(x >> 4, z >> 4)) return null;
                int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (topY <= world.getMinY()) return null;
                BlockPos surface = new BlockPos(x, topY - 1, z);
                if (!isNaturalSurface(world.getBlockState(surface))) return null;
                minOuter = Math.min(minOuter, topY);
                maxOuter = Math.max(maxOuter, topY);
                if (Math.abs(xOffset) <= innerRadius && Math.abs(zOffset) <= innerRadius) {
                    minInner = Math.min(minInner, topY);
                    maxInner = Math.max(maxInner, topY);
                }
            }
        }
        if (maxInner - minInner > maxInnerDelta || maxOuter - minOuter > maxOuterDelta) return null;

        BlockPos center = new BlockPos(centerX,
                world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ), centerZ);
        if (world.isCloseToVillage(center, 4)) return null;
        if (!bypassPlayerHistory) {
            QuestState questState = QuestState.get(world.getServer());
            if (questState.isTerrainModified(center, 1)
                    || TradeRouteService.isNearAnyNetworkAnchor(world, center, networkSafetyRadius)
                    || VillageBondService.isNearAnyBondAnchor(world, center, networkSafetyRadius)
                    || hasInhabitedChunk(world, centerX, centerZ, outerRadius)) {
                return null;
            }
        }
        return hasHumanTrace(world, centerX, centerZ, outerRadius) ? null : center;
    }

    private static boolean hasInhabitedChunk(ServerLevel world, int centerX, int centerZ, int radius) {
        int minChunkX = (centerX - radius) >> 4;
        int maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4;
        int maxChunkZ = (centerZ + radius) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (world.hasChunk(x, z) && world.getChunk(x, z).getInhabitedTime() >= LEGACY_INHABITED_TIME_LIMIT) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasHumanTrace(ServerLevel world, int centerX, int centerZ, int radius) {
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                int x = centerX + xOffset;
                int z = centerZ + zOffset;
                int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = topY - 2; y <= topY + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (world.getBlockEntity(pos) != null || isHumanTrace(state)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isNaturalSurface(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TERRACOTTA);
    }

    private static boolean isHumanTrace(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS)
                && state.getValue(RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("planks") || path.contains("fence") || path.contains("door")
                || path.contains("trapdoor") || path.contains("bed") || path.contains("chest")
                || path.contains("barrel") || path.contains("crafting_table") || path.contains("furnace")
                || path.contains("farmland") || path.contains("dirt_path") || path.contains("rail")
                || path.contains("redstone") || path.contains("torch") || path.contains("lantern")
                || path.contains("glass") || path.contains("stone_brick") || path.contains("deepslate_brick")
                || path.contains("deepslate_tile") || path.contains("copper") || path.contains("concrete")
                || path.contains("terracotta") && !state.is(Blocks.TERRACOTTA)
                || path.endsWith("_wall") || path.endsWith("_stairs") || path.endsWith("_slab")
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }
}
