package de.quest.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deterministic, bounded ordering for safe-site checks inside and around a village footprint. */
public final class GuildCornerCandidateSearch {
    static final int DEFAULT_GAP = 1;
    static final int DEFAULT_STEP = 4;
    static final int MAX_CANDIDATES_PER_SIDE = 7;
    static final int MAX_INTERIOR_SAMPLES_PER_AXIS = 5;

    private GuildCornerCandidateSearch() {}

    /** Prefers a bounded center-out interior grid, then falls back to the nearest outer ring. */
    public static List<Candidate> insideThenAround(int villageMinX, int villageMaxX,
                                                   int villageMinZ, int villageMaxZ,
                                                   int templateWidth, int templateDepth) {
        List<Candidate> result = new ArrayList<>(inside(villageMinX, villageMaxX, villageMinZ,
                villageMaxZ, templateWidth, templateDepth));
        result.addAll(around(villageMinX, villageMaxX, villageMinZ, villageMaxZ,
                templateWidth, templateDepth));
        return List.copyOf(result);
    }

    /** Returns only the bounded center-out interior tier. */
    public static List<Candidate> inside(int villageMinX, int villageMaxX,
                                         int villageMinZ, int villageMaxZ,
                                         int templateWidth, int templateDepth) {
        if (!valid(villageMinX, villageMaxX, villageMinZ, villageMaxZ,
                templateWidth, templateDepth)) return List.of();
        int centerX = midpoint(villageMinX, villageMaxX);
        int centerZ = midpoint(villageMinZ, villageMaxZ);
        int span = Math.max(templateWidth, templateDepth);
        int lowerRadius = span / 2;
        int upperRadius = span - 1 - lowerRadius;
        List<Integer> xSamples = centeredSamples(villageMinX + lowerRadius,
                villageMaxX - upperRadius, centerX);
        List<Integer> zSamples = centeredSamples(villageMinZ + lowerRadius,
                villageMaxZ - upperRadius, centerZ);

        List<Candidate> result = new ArrayList<>(xSamples.size() * zSamples.size());
        record Interior(int x, int z, long distanceSqr) {}
        List<Interior> interior = new ArrayList<>(xSamples.size() * zSamples.size());
        for (int x : xSamples) {
            for (int z : zSamples) {
                long dx = (long) x - centerX;
                long dz = (long) z - centerZ;
                interior.add(new Interior(x, z, dx * dx + dz * dz));
            }
        }
        interior.sort(Comparator.comparingLong(Interior::distanceSqr)
                .thenComparingInt(Interior::x).thenComparingInt(Interior::z));
        for (Interior site : interior) {
            result.add(new Candidate(site.x(), site.z(), outwardFacing(
                    site.x() - centerX, site.z() - centerZ)));
        }
        return List.copyOf(result);
    }

    public static List<Candidate> around(int villageMinX, int villageMaxX,
                                         int villageMinZ, int villageMaxZ,
                                         int templateWidth, int templateDepth) {
        if (!valid(villageMinX, villageMaxX, villageMinZ, villageMaxZ,
                templateWidth, templateDepth)) return List.of();
        int centerX = midpoint(villageMinX, villageMaxX);
        int centerZ = midpoint(villageMinZ, villageMaxZ);
        HorizontalFootprint northSouth = rotatedFootprint(templateWidth, templateDepth,
                GuildCornerLandmarkState.CornerFacing.NORTH);
        HorizontalFootprint eastWest = rotatedFootprint(templateWidth, templateDepth,
                GuildCornerLandmarkState.CornerFacing.EAST);
        List<Candidate> result = new ArrayList<>(MAX_CANDIDATES_PER_SIDE * 4);
        for (int offset : centeredOffsets(DEFAULT_STEP, MAX_CANDIDATES_PER_SIDE)) {
            result.add(new Candidate(centerX + offset,
                    centerOutsideNegative(villageMinZ, northSouth.depth()),
                    GuildCornerLandmarkState.CornerFacing.NORTH));
            result.add(new Candidate(centerOutsidePositive(villageMaxX, eastWest.width()),
                    centerZ + offset, GuildCornerLandmarkState.CornerFacing.EAST));
            result.add(new Candidate(centerX - offset,
                    centerOutsidePositive(villageMaxZ, northSouth.depth()),
                    GuildCornerLandmarkState.CornerFacing.SOUTH));
            result.add(new Candidate(centerOutsideNegative(villageMinX, eastWest.width()),
                    centerZ - offset, GuildCornerLandmarkState.CornerFacing.WEST));
        }
        return List.copyOf(result);
    }

    private static HorizontalFootprint rotatedFootprint(int width, int depth,
                                                         GuildCornerLandmarkState.CornerFacing facing) {
        return facing == GuildCornerLandmarkState.CornerFacing.EAST
                || facing == GuildCornerLandmarkState.CornerFacing.WEST
                ? new HorizontalFootprint(depth, width)
                : new HorizontalFootprint(width, depth);
    }

    /** Places the rotated footprint so its maximum lies exactly one gap outside the minimum edge. */
    private static int centerOutsideNegative(int boundaryMin, int rotatedSpan) {
        int lowerExtent = rotatedSpan / 2;
        int upperExtent = rotatedSpan - 1 - lowerExtent;
        return boundaryMin - DEFAULT_GAP - 1 - upperExtent;
    }

    /** Places the rotated footprint so its minimum lies exactly one gap outside the maximum edge. */
    private static int centerOutsidePositive(int boundaryMax, int rotatedSpan) {
        int lowerExtent = rotatedSpan / 2;
        return boundaryMax + DEFAULT_GAP + 1 + lowerExtent;
    }

    private static boolean valid(int villageMinX, int villageMaxX, int villageMinZ, int villageMaxZ,
                                 int templateWidth, int templateDepth) {
        return villageMinX <= villageMaxX && villageMinZ <= villageMaxZ
                && templateWidth >= 1 && templateDepth >= 1
                && templateWidth <= 16 && templateDepth <= 16;
    }

    private static List<Integer> centeredSamples(int min, int max, int center) {
        if (min > max) return List.of();
        int count = Math.min(MAX_INTERIOR_SAMPLES_PER_AXIS, max - min + 1);
        Set<Integer> unique = new LinkedHashSet<>(count);
        if (count == 1) {
            unique.add(Math.clamp(center, min, max));
        } else {
            for (int index = 0; index < count; index++) {
                unique.add(min + (int) Math.round((double) (max - min) * index / (count - 1)));
            }
        }
        List<Integer> samples = new ArrayList<>(unique);
        samples.sort(Comparator.comparingInt((Integer value) -> Math.abs(value - center))
                .thenComparingInt(Integer::intValue));
        return samples;
    }

    /** Stored/authored facing points away because the visible preset front is its opposite side. */
    private static GuildCornerLandmarkState.CornerFacing outwardFacing(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? GuildCornerLandmarkState.CornerFacing.EAST
                    : GuildCornerLandmarkState.CornerFacing.WEST;
        }
        if (dz != 0) {
            return dz > 0 ? GuildCornerLandmarkState.CornerFacing.SOUTH
                    : GuildCornerLandmarkState.CornerFacing.NORTH;
        }
        return GuildCornerLandmarkState.CornerFacing.NORTH;
    }

    private static List<Integer> centeredOffsets(int step, int count) {
        List<Integer> result = new ArrayList<>(count);
        result.add(0);
        for (int distance = step; result.size() < count; distance += step) {
            result.add(distance);
            if (result.size() < count) result.add(-distance);
        }
        return result;
    }

    private static int midpoint(int min, int max) {
        return (int) (((long) min + max) / 2L);
    }

    public record Candidate(int centerX, int centerZ,
                            GuildCornerLandmarkState.CornerFacing facing) {}

    private record HorizontalFootprint(int width, int depth) {}
}
