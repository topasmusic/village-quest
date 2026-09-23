package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TradeRouteWorldPolicyTest {
    @Test
    void onlyOverworldActorWithAuthoritativeOverworldCanMutateRoutes() {
        assertTrue(TradeRouteWorldPolicy.canMutate(true, true));
        assertFalse(TradeRouteWorldPolicy.canMutate(true, false));
        assertFalse(TradeRouteWorldPolicy.canMutate(false, true));
        assertFalse(TradeRouteWorldPolicy.canMutate(false, false));
    }
}
