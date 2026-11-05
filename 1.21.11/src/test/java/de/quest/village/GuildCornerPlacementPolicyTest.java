package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class GuildCornerPlacementPolicyTest {
    private static final GuildCornerLandmarkState.VillageKey VILLAGE =
            GuildCornerLandmarkState.VillageKey.overworld(100, 200);
    private static final BlockPos POST = new BlockPos(104, 70, 205);

    @Test
    void untouchedLoadedVillageCanReserveAutomaticPlacement() {
        assertEquals(GuildCornerPlacementPolicy.Decision.RESERVE_AND_PLACE_AUTOMATICALLY,
                GuildCornerPlacementPolicy.decide(null, context(true, true, false, false,
                        true, false, true)));
    }

    @Test
    void ctovUsesTheSameSafeCandidateDecisionAsVanilla() {
        var vanilla = GuildCornerPlacementPolicy.decide(null,
                context(true, true, false, false, true, false, true));
        var ctov = GuildCornerPlacementPolicy.decide(null,
                context(true, true, false, true, true, false, true));
        assertEquals(vanilla, ctov);
    }

    @Test
    void existingEditedVillageRequiresExplicitPlacement() {
        assertEquals(GuildCornerPlacementPolicy.Decision.REQUIRE_MANUAL_PLACEMENT,
                GuildCornerPlacementPolicy.decide(null, context(true, true, true, false,
                        true, false, true)));
        assertEquals(GuildCornerPlacementPolicy.Decision.RESERVE_AND_PLACE_MANUALLY,
                GuildCornerPlacementPolicy.decide(null, context(true, true, true, false,
                        true, true, true)));
    }

    @Test
    void incompleteChunksWaitWithoutCreatingPermanentFailure() {
        assertEquals(GuildCornerPlacementPolicy.Decision.RETRY_WHEN_LOADED,
                GuildCornerPlacementPolicy.decide(null, context(true, false, false, false,
                        false, false, true)));
        assertEquals(GuildCornerPlacementPolicy.Decision.RETRY_WHEN_LOADED,
                GuildCornerPlacementPolicy.decide(pending(0), context(true, false, false, false,
                        false, false, true)));
    }

    @Test
    void exhaustedPendingAutomaticRetriesRemainManual() {
        assertEquals(GuildCornerPlacementPolicy.Decision.REQUIRE_MANUAL_PLACEMENT,
                GuildCornerPlacementPolicy.decide(pending(GuildCornerLandmarkState.MAX_RETRY_ATTEMPTS),
                        context(true, true, false, false, true, false, false)));
        assertEquals(GuildCornerPlacementPolicy.Decision.PLACE_RESERVED_MANUALLY,
                GuildCornerPlacementPolicy.decide(pending(GuildCornerLandmarkState.MAX_RETRY_ATTEMPTS),
                        context(true, true, false, false, true, true, false)));
    }

    @Test
    void generatedAndRemovedRecordsNeverTriggerRepairOrRegeneration() {
        assertEquals(GuildCornerPlacementPolicy.Decision.NO_ACTION,
                GuildCornerPlacementPolicy.decide(snapshot(GuildCornerLandmarkState.LandmarkStatus.GENERATED, 0),
                        context(true, true, false, false, true, false, true)));
        assertEquals(GuildCornerPlacementPolicy.Decision.NO_ACTION,
                GuildCornerPlacementPolicy.decide(snapshot(
                                GuildCornerLandmarkState.LandmarkStatus.REMOVED_BY_PLAYER, 0),
                        context(true, true, false, false, true, true, true)));
    }

    @Test
    void noBackgroundOrStartupScanCanRequestPlacement() {
        assertEquals(GuildCornerPlacementPolicy.Decision.NO_ACTION,
                GuildCornerPlacementPolicy.decide(null, context(false, true, false, false,
                        true, false, true)));
    }

    private static GuildCornerPlacementPolicy.VisitContext context(
            boolean localVisit, boolean loaded, boolean existing, boolean ctov,
            boolean safe, boolean explicit, boolean retryAvailable) {
        return new GuildCornerPlacementPolicy.VisitContext(
                localVisit, loaded, existing, ctov, safe, explicit, retryAvailable);
    }

    private static GuildCornerLandmarkState.LandmarkSnapshot pending(int attempts) {
        return snapshot(GuildCornerLandmarkState.LandmarkStatus.PENDING, attempts);
    }

    private static GuildCornerLandmarkState.LandmarkSnapshot snapshot(
            GuildCornerLandmarkState.LandmarkStatus status, int attempts) {
        return new GuildCornerLandmarkState.LandmarkSnapshot(
                VILLAGE, POST, GuildCornerLandmarkState.CornerFacing.NORTH,
                GuildCornerLandmarkState.CornerStyle.PLAINS,
                GuildCornerLandmarkState.Footprint.singleBlock(POST), status, attempts, 0L, 0);
    }
}
