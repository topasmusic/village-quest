package de.quest.caravan;

/** Presentation math for the current caravan leg; the route simulation remains authoritative. */
final class CaravanMasterArrival {
    private CaravanMasterArrival() {}

    static int legPercent(int progress, int direction) {
        int clamped = Math.max(0, Math.min(TradeRouteService.PROGRESS_MAX, progress));
        int travelled = direction < 0 ? TradeRouteService.PROGRESS_MAX - clamped : clamped;
        return travelled / 100;
    }

    static int secondsToNextStop(int progress, int direction, int stepPerSecond, boolean moving) {
        if (!moving || stepPerSecond <= 0) return -1;
        int clamped = Math.max(0, Math.min(TradeRouteService.PROGRESS_MAX, progress));
        int remaining = direction < 0 ? clamped : TradeRouteService.PROGRESS_MAX - clamped;
        return (remaining + stepPerSecond - 1) / stepPerSecond;
    }
}
