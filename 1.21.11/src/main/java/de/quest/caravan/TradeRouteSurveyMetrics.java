package de.quest.caravan;

import java.util.List;

/** Advisory horizontal distances. Route validity and actual timing remain server-authoritative. */
final class TradeRouteSurveyMetrics {
    private static final long LONG_ROUTE_BLOCKS = 3_000;

    private TradeRouteSurveyMetrics() {}

    record Point(int x, int z, boolean ocean) {}

    record Estimate(long straightBlocks, long surveyedBlocks, long estimatedBlocks,
                    long estimatedMinutes, int expectedLegs, boolean unusuallyLong) {}

    static Estimate calculate(Point home, Point destination, List<Point> draft, double blocksPerSecond) {
        double straight = distance(home, destination);
        double surveyed = 0.0;
        Point previous = home;
        int ferryGroups = 0;
        boolean onFerry = false;
        for (Point point : draft) {
            surveyed += distance(previous, point);
            if (point.ocean() && !onFerry) ferryGroups++;
            onFerry = point.ocean();
            previous = point;
        }
        double estimated = surveyed + distance(previous, destination);
        long estimatedBlocks = Math.round(estimated);
        int progressPerSecond = Math.max(1, (int) Math.round(
                blocksPerSecond * TradeRouteService.PROGRESS_MAX / Math.max(96.0, estimated)));
        long minutes = Math.max(1L, (long) Math.ceil(
                (double) TradeRouteService.PROGRESS_MAX / progressPerSecond / 60.0));
        return new Estimate(Math.round(straight), Math.round(surveyed), estimatedBlocks,
                minutes, 1 + ferryGroups * 2, estimatedBlocks >= LONG_ROUTE_BLOCKS);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot((double) b.x() - a.x(), (double) b.z() - a.z());
    }
}
