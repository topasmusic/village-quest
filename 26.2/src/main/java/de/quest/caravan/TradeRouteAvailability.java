package de.quest.caravan;

import de.quest.village.VillageLifeState;
import net.minecraft.network.chat.Component;

/** Explains why a saved connection is not making normal commercial trips. */
final class TradeRouteAvailability {
    private TradeRouteAvailability() {}

    static Reason reason(boolean playerPaused, boolean surveyInProgress, boolean incident,
                         boolean playerBuiltHub, VillageLifeState.Status hub,
                         VillageLifeState.Status destination) {
        if (!playerBuiltHub && hub == VillageLifeState.Status.ABANDONED) return Reason.HUB_UNAVAILABLE;
        if (destination == VillageLifeState.Status.ABANDONED) return Reason.DESTINATION_ABANDONED;
        if ((!playerBuiltHub && hub == VillageLifeState.Status.RECOVERING)
                || destination == VillageLifeState.Status.RECOVERING) {
            return Reason.RECOVERING_SETTLEMENT;
        }
        if (playerPaused) return Reason.PLAYER_PAUSED;
        if (surveyInProgress) return Reason.SURVEY_IN_PROGRESS;
        if (incident) return Reason.INCIDENT;
        return Reason.ACTIVE;
    }

    enum Reason {
        ACTIVE, PLAYER_PAUSED, SURVEY_IN_PROGRESS, INCIDENT,
        DESTINATION_ABANDONED, HUB_UNAVAILABLE, RECOVERING_SETTLEMENT;

        Component label() {
            return Component.translatable("screen.village-quest.trade_route.availability."
                    + name().toLowerCase(java.util.Locale.ROOT));
        }

        boolean suspendsForSettlement() {
            return this == DESTINATION_ABANDONED || this == HUB_UNAVAILABLE
                    || this == RECOVERING_SETTLEMENT;
        }
    }
}
