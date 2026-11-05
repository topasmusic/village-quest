package de.quest.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/** Shared non-chunk-loading target selection for distant treasure and ruin quests. */
public final class QuestSiteLocator {
    private static final int LAND_RECOVERY_RADIUS = 1024;
    private static final int LAND_HORIZONTAL_STEP = 16;
    private static final int LAND_VERTICAL_STEP = 64;

    private QuestSiteLocator() {}

    public static BlockPos findDistantLandTarget(ServerLevel world, BlockPos origin,
                                                 int minimumDistance, int maximumDistance,
                                                 int attempts) {
        if (world == null || origin == null || minimumDistance <= 0
                || maximumDistance < minimumDistance || attempts <= 0) {
            return null;
        }

        BlockPos lastCandidate = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(world.getRandom(), minimumDistance, maximumDistance);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos candidate = new BlockPos(x, world.getSeaLevel(), z);
            if (!world.getWorldBorder().isWithinBounds(candidate)) {
                continue;
            }
            lastCandidate = candidate;

            Holder<Biome> biome = world.getUncachedNoiseBiome(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(world.getSeaLevel()), QuartPos.fromBlock(z));
            if (isDryLandBiome(biome)) {
                return candidate;
            }
        }

        if (lastCandidate != null) {
            Pair<BlockPos, Holder<Biome>> nearestLand = world.findClosestBiome3d(
                    QuestSiteLocator::isDryLandBiome,
                    lastCandidate,
                    LAND_RECOVERY_RADIUS,
                    LAND_HORIZONTAL_STEP,
                    LAND_VERTICAL_STEP);
            if (nearestLand != null) {
                BlockPos recovered = new BlockPos(
                        nearestLand.getFirst().getX(), world.getSeaLevel(), nearestLand.getFirst().getZ());
                double distanceFromOrigin = Math.sqrt(origin.distSqr(recovered));
                if (world.getWorldBorder().isWithinBounds(recovered)
                        && distanceFromOrigin >= minimumDistance * 0.6
                        && distanceFromOrigin <= maximumDistance + LAND_RECOVERY_RADIUS) {
                    return recovered;
                }
            }
        }

        Pair<BlockPos, Holder<Biome>> nearbyLand = world.findClosestBiome3d(
                QuestSiteLocator::isDryLandBiome,
                origin,
                maximumDistance,
                LAND_HORIZONTAL_STEP,
                LAND_VERTICAL_STEP);
        if (nearbyLand == null) {
            return null;
        }
        BlockPos recovered = new BlockPos(
                nearbyLand.getFirst().getX(), world.getSeaLevel(), nearbyLand.getFirst().getZ());
        return world.getWorldBorder().isWithinBounds(recovered) ? recovered : null;
    }

    private static boolean isDryLandBiome(Holder<Biome> biome) {
        return biome != null && !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
    }
}
