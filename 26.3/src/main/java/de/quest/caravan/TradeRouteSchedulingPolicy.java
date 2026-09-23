package de.quest.caravan;

/** Pure bounded-simulation and cleanup cadence policy for trade routes. */
final class TradeRouteSchedulingPolicy {
    private TradeRouteSchedulingPolicy() {}

    static int catchUpSeconds(int lastSimulatedSecond, int currentSecond, int maximum) {
        if (lastSimulatedSecond <= 0 || currentSecond <= lastSimulatedSecond || maximum <= 0) {
            return 0;
        }
        return Math.min(maximum, currentSecond - lastSimulatedSecond);
    }

    static boolean shouldCleanupPausedRoute(boolean wasStopped,
                                            boolean isStopped,
                                            boolean explicitRecovery) {
        return explicitRecovery || (!wasStopped && isStopped);
    }

    static boolean shouldRunOrphanSweep(long gameTime, int intervalTicks) {
        return intervalTicks > 0 && Math.floorMod(gameTime, intervalTicks) == 0L;
    }
}
