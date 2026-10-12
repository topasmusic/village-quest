package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GuildCornerCandidateSearchTest {
    @Test
    void outsideFallbackHugsEachVillageSideAndRotatesAuthoredFrontTowardIt() {
        List<GuildCornerCandidateSearch.Candidate> candidates =
                GuildCornerCandidateSearch.around(100, 140, 200, 240, 9, 7);

        assertEquals(28, candidates.size());
        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 195,
                GuildCornerLandmarkState.CornerFacing.NORTH), candidates.get(0));
        assertEquals(new GuildCornerCandidateSearch.Candidate(145, 220,
                GuildCornerLandmarkState.CornerFacing.EAST), candidates.get(1));
        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 245,
                GuildCornerLandmarkState.CornerFacing.SOUTH), candidates.get(2));
        assertEquals(new GuildCornerCandidateSearch.Candidate(95, 220,
                GuildCornerLandmarkState.CornerFacing.WEST), candidates.get(3));
    }

    @Test
    void realSevenBySixPresetLeavesExactlyOneFreeBlockOnEveryOuterSide() {
        List<GuildCornerCandidateSearch.Candidate> candidates =
                GuildCornerCandidateSearch.around(100, 140, 200, 240, 7, 6);

        GuildCornerCandidateSearch.Candidate north = candidates.get(0);
        GuildCornerCandidateSearch.Candidate east = candidates.get(1);
        GuildCornerCandidateSearch.Candidate south = candidates.get(2);
        GuildCornerCandidateSearch.Candidate west = candidates.get(3);

        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 196,
                GuildCornerLandmarkState.CornerFacing.NORTH), north);
        assertEquals(198, north.centerZ() + 2); // rotated footprint max Z; free row is Z=199

        assertEquals(new GuildCornerCandidateSearch.Candidate(145, 220,
                GuildCornerLandmarkState.CornerFacing.EAST), east);
        assertEquals(142, east.centerX() - 3); // rotated footprint min X; free row is X=141

        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 245,
                GuildCornerLandmarkState.CornerFacing.SOUTH), south);
        assertEquals(242, south.centerZ() - 3); // rotated footprint min Z; free row is Z=241

        assertEquals(new GuildCornerCandidateSearch.Candidate(96, 220,
                GuildCornerLandmarkState.CornerFacing.WEST), west);
        assertEquals(98, west.centerX() + 2); // rotated footprint max X; free row is X=99
    }

    @Test
    void hybridSearchExhaustsBoundedInteriorSitesBeforeOutsideFallbacks() {
        List<GuildCornerCandidateSearch.Candidate> candidates =
                GuildCornerCandidateSearch.insideThenAround(100, 140, 200, 240, 9, 7);

        assertEquals(53, candidates.size());
        for (int index = 0; index < 25; index++) {
            GuildCornerCandidateSearch.Candidate interior = candidates.get(index);
            assertTrue(interior.centerX() >= 104 && interior.centerX() <= 136,
                    "interior X at " + index);
            assertTrue(interior.centerZ() >= 204 && interior.centerZ() <= 236,
                    "interior Z at " + index);
        }
        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 195,
                GuildCornerLandmarkState.CornerFacing.NORTH), candidates.get(25));
    }

    @Test
    void interiorStoredFacingPointsAwayFromCenterSoTheAuthoredFrontFacesInward() {
        List<GuildCornerCandidateSearch.Candidate> candidates =
                GuildCornerCandidateSearch.insideThenAround(100, 140, 200, 240, 9, 7);

        assertEquals(new GuildCornerCandidateSearch.Candidate(120, 220,
                GuildCornerLandmarkState.CornerFacing.NORTH), candidates.get(0));
        assertTrue(candidates.stream().limit(25)
                .anyMatch(candidate -> candidate.centerX() > 120
                        && candidate.facing() == GuildCornerLandmarkState.CornerFacing.EAST));
        assertTrue(candidates.stream().limit(25)
                .anyMatch(candidate -> candidate.centerZ() > 220
                        && candidate.facing() == GuildCornerLandmarkState.CornerFacing.SOUTH));
    }

    @Test
    void orderingAlternatesAroundTheCenterInsteadOfDriftingOneWay() {
        List<GuildCornerCandidateSearch.Candidate> candidates =
                GuildCornerCandidateSearch.around(0, 20, 0, 20, 5, 5);
        assertEquals(10, candidates.get(0).centerX());
        assertEquals(14, candidates.get(4).centerX());
        assertEquals(6, candidates.get(8).centerX());
    }

    @Test
    void invalidOrOversizedFootprintsHaveNoCandidates() {
        assertTrue(GuildCornerCandidateSearch.around(5, 4, 0, 4, 5, 5).isEmpty());
        assertTrue(GuildCornerCandidateSearch.around(0, 4, 0, 4, 17, 5).isEmpty());
        assertTrue(GuildCornerCandidateSearch.insideThenAround(5, 4, 0, 4, 5, 5).isEmpty());
    }

    @Test
    void oversizedVillageBoundsDoNotPutTheCornerInTheEmptyForestBetweenHouses() {
        var pieces = List.of(new GuildCornerCandidateSearch.StructureFootprint(130, 140, 20, 30),
                new GuildCornerCandidateSearch.StructureFootprint(150, 160, 80, 90));
        var candidates = GuildCornerCandidateSearch.nearStructures(0, 180, 0, 100, 7, 6, pieces);
        assertTrue(!candidates.isEmpty());
        for (var candidate : candidates) {
            assertTrue(pieces.stream().anyMatch(piece -> distanceSqr(candidate, piece) <= 12L * 12L),
                    "site must remain beside a real village piece: " + candidate);
        }
        assertTrue(candidates.getFirst().centerX() > 110, "empty bounding-box center must not win");
    }

    @Test
    void nearStructureSearchIsDeterministicBoundedAndNearestFirst() {
        var pieces = new java.util.ArrayList<GuildCornerCandidateSearch.StructureFootprint>();
        for (int x = -200; x <= 200; x += 20) for (int z = -200; z <= 200; z += 20)
            pieces.add(new GuildCornerCandidateSearch.StructureFootprint(x, x + 6, z, z + 8));
        var candidates = GuildCornerCandidateSearch.nearStructures(-220, 220, -220, 220, 7, 6, pieces);
        assertTrue(candidates.size() <= 96);
        assertEquals(candidates.size(), new java.util.HashSet<>(candidates).size());
        assertEquals(candidates, GuildCornerCandidateSearch.nearStructures(-220, 220, -220, 220, 7, 6, pieces));
        long previous = -1;
        for (var candidate : candidates) {
            long nearest = pieces.stream().mapToLong(piece -> distanceSqr(candidate, piece)).min().orElseThrow();
            assertTrue(nearest >= previous);
            previous = nearest;
        }
    }

    @Test
    void markersWithoutStructurePiecesKeepExistingFallbackButInvalidPiecesCannotPlace() {
        assertEquals(GuildCornerCandidateSearch.insideThenAround(0, 40, 0, 40, 7, 6),
                GuildCornerCandidateSearch.nearStructures(0, 40, 0, 40, 7, 6, List.of()));
        assertTrue(GuildCornerCandidateSearch.nearStructures(0, 40, 0, 40, 7, 6,
                List.of(new GuildCornerCandidateSearch.StructureFootprint(60, 70, 60, 70))).isEmpty());
        assertTrue(GuildCornerCandidateSearch.nearStructures(0, 40, 0, 40, 17, 6,
                List.of(new GuildCornerCandidateSearch.StructureFootprint(10, 20, 10, 20))).isEmpty());
    }

    private static long distanceSqr(GuildCornerCandidateSearch.Candidate candidate,
                                    GuildCornerCandidateSearch.StructureFootprint piece) {
        long dx = Math.max(0L, Math.max((long) piece.minX() - candidate.centerX(),
                (long) candidate.centerX() - piece.maxX()));
        long dz = Math.max(0L, Math.max((long) piece.minZ() - candidate.centerZ(),
                (long) candidate.centerZ() - piece.maxZ()));
        return dx * dx + dz * dz;
    }
}
