package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Validates sampled footing close to a surveyed land segment. */
final class TradeRouteSurveyPathPolicy {
    private static final int ROAD_CORRIDOR_RADIUS = 3;
    private static final int ENDPOINT_RADIUS = 1;
    private static final int ELEVATION_RADIUS = 3;

    private TradeRouteSurveyPathPolicy() {}

    @FunctionalInterface
    interface SafeFooting {
        boolean exists(int x, int y, int z);
    }

    static boolean hasSafeLandSegment(RoutePoint from, RoutePoint to, SafeFooting safeFooting) {
        if (from == null || to == null || !from.hasElevation() || !to.hasElevation()
                || safeFooting == null) {
            return false;
        }
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();
        for (int dx = -ENDPOINT_RADIUS; dx <= ENDPOINT_RADIUS; dx++) {
            for (int dz = -ENDPOINT_RADIUS; dz <= ENDPOINT_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Cell start = new Cell(from.x() + dx, from.y() + dy, from.z() + dz);
                    if (insideCorridor(from, to, start) && safeFooting.exists(start.x, start.y, start.z)
                            && visited.add(start)) {
                        pending.add(start);
                    }
                }
            }
        }
        int maxVisited = Math.max(512, (int) Math.ceil(
                Math.sqrt(from.spatialDistanceSquared(to))) * 320);
        while (!pending.isEmpty() && visited.size() <= maxVisited) {
            Cell current = pending.removeFirst();
            if (Math.abs(current.x - to.x()) <= ENDPOINT_RADIUS
                    && Math.abs(current.z - to.z()) <= ENDPOINT_RADIUS
                    && Math.abs(current.y - to.y()) <= 2) {
                return true;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        Cell next = new Cell(current.x + dx, current.y + dy, current.z + dz);
                        if (insideCorridor(from, to, next) && visited.add(next)
                                && safeFooting.exists(next.x, next.y, next.z)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean insideCorridor(RoutePoint from, RoutePoint to, Cell cell) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        double t = lengthSquared <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
                ((cell.x - from.x()) * dx + (cell.y - from.y()) * dy
                        + (cell.z - from.z()) * dz) / lengthSquared));
        return Math.hypot(cell.x - (from.x() + t * dx),
                cell.z - (from.z() + t * dz)) <= ROAD_CORRIDOR_RADIUS + 0.5
                && Math.abs(cell.y - (from.y() + t * dy)) <= ELEVATION_RADIUS;
    }

    private record Cell(int x, int y, int z) {}
}
