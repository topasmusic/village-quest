package de.quest.caravan;

/** Mutating physical routes requires both the authoritative world and the actor there. */
final class TradeRouteWorldPolicy {
    private TradeRouteWorldPolicy() {}

    static boolean canMutate(boolean authoritativeOverworld, boolean actorInThatWorld) {
        return authoritativeOverworld && actorInThatWorld;
    }
}
