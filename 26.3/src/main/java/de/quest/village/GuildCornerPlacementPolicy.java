package de.quest.village;

/**
 * Pure decision boundary for local Guild Corner placement attempts.
 *
 * <p>The caller supplies facts gathered during a player-triggered village visit. This policy does
 * not scan or load chunks and treats vanilla and CTOV structure footprints identically once a safe
 * candidate has been validated.</p>
 */
public final class GuildCornerPlacementPolicy {
    private GuildCornerPlacementPolicy() {}

    public static Decision decide(GuildCornerLandmarkState.LandmarkSnapshot existing,
                                  VisitContext context) {
        if (context == null || !context.localPlayerVisit()) return Decision.NO_ACTION;
        if (existing != null) {
            if (existing.status() == GuildCornerLandmarkState.LandmarkStatus.GENERATED
                    || existing.status() == GuildCornerLandmarkState.LandmarkStatus.REMOVED_BY_PLAYER) {
                return Decision.NO_ACTION;
            }
            if (!context.requiredChunksLoaded()) return Decision.RETRY_WHEN_LOADED;
            if (context.explicitPlayerPlacement() && context.safeCandidate()) {
                return Decision.PLACE_RESERVED_MANUALLY;
            }
            if (!context.automaticRetryAvailable() || !context.safeCandidate()) {
                return Decision.REQUIRE_MANUAL_PLACEMENT;
            }
            return Decision.PLACE_RESERVED_AUTOMATICALLY;
        }

        if (!context.requiredChunksLoaded()) return Decision.RETRY_WHEN_LOADED;
        if (context.explicitPlayerPlacement()) {
            return context.safeCandidate()
                    ? Decision.RESERVE_AND_PLACE_MANUALLY
                    : Decision.REQUIRE_MANUAL_PLACEMENT;
        }
        if (context.existingOrEditedVillage() || !context.safeCandidate()) {
            return Decision.REQUIRE_MANUAL_PLACEMENT;
        }
        return Decision.RESERVE_AND_PLACE_AUTOMATICALLY;
    }

    public enum Decision {
        NO_ACTION,
        RETRY_WHEN_LOADED,
        REQUIRE_MANUAL_PLACEMENT,
        RESERVE_AND_PLACE_AUTOMATICALLY,
        PLACE_RESERVED_AUTOMATICALLY,
        RESERVE_AND_PLACE_MANUALLY,
        PLACE_RESERVED_MANUALLY
    }

    /**
     * CTOV is recorded for diagnostics and candidate selection, but never weakens the same loaded,
     * footprint-aware safety checks used for vanilla villages.
     */
    public record VisitContext(boolean localPlayerVisit, boolean requiredChunksLoaded,
                               boolean existingOrEditedVillage, boolean ctovLayout,
                               boolean safeCandidate, boolean explicitPlayerPlacement,
                               boolean automaticRetryAvailable) {}
}
