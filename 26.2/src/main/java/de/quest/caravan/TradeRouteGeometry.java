package de.quest.caravan;

import java.util.ArrayList;
import java.util.List;

/** Pure route interpolation and ferry-boundary calculations. */
final class TradeRouteGeometry {
    private static final int PROGRESS_MAX = TradeRouteService.PROGRESS_MAX;

    private TradeRouteGeometry() {}

    static RoutePoint pointAlong(List<RoutePoint> path, int progress) {
        if (path == null || path.size() < 2) {
            return path == null || path.isEmpty() ? new RoutePoint(0, 0) : path.getFirst();
        }
        double totalDistance = pathDistance(path);
        if (totalDistance <= 0.0) {
            return path.getFirst();
        }
        double remaining = totalDistance * clamp(progress) / PROGRESS_MAX;
        for (int i = 1; i < path.size(); i++) {
            RoutePoint from = path.get(i - 1);
            RoutePoint to = path.get(i);
            double segmentDistance = from.distance(to);
            if (segmentDistance <= 0.0) {
                continue;
            }
            if (remaining <= segmentDistance) {
                double t = remaining / segmentDistance;
                int x = (int) Math.round(from.x() + (to.x() - from.x()) * t);
                int z = (int) Math.round(from.z() + (to.z() - from.z()) * t);
                if (from.hasElevation() && to.hasElevation()) {
                    int y = (int) Math.round(from.y() + (to.y() - from.y()) * t);
                    return new RoutePoint(x, y, z);
                }
                return new RoutePoint(x, z);
            }
            remaining -= segmentDistance;
        }
        return path.getLast();
    }

    static double pathDistance(List<RoutePoint> points) {
        if (points == null) {
            return 0.0;
        }
        double distance = 0.0;
        for (int i = 1; i < points.size(); i++) {
            distance += points.get(i - 1).distance(points.get(i));
        }
        return distance;
    }

    /** Physical land distance; ferry legs and legacy routes retain horizontal distance. */
    static double traversalDistance(List<RouteSurveyPoint> path) {
        if (path == null) {
            return 0.0;
        }
        double distance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            distance += segmentTraversalDistance(path.get(i - 1), path.get(i));
        }
        return distance;
    }

    static double segmentTraversalDistance(RouteSurveyPoint from, RouteSurveyPoint to) {
        RoutePoint a = from.point();
        RoutePoint b = to.point();
        return isFerrySegment(from, to) || !a.hasElevation() || !b.hasElevation()
                ? a.distance(b) : Math.sqrt(a.spatialDistanceSquared(b));
    }

    static RoutePoint pointAlongTraversal(List<RouteSurveyPoint> path, int progress) {
        if (path == null || path.isEmpty()) {
            return new RoutePoint(0, 0);
        }
        if (path.size() < 2) {
            return path.getFirst().point();
        }
        double total = traversalDistance(path);
        if (total <= 0.0) {
            return path.getFirst().point();
        }
        double remaining = total * clamp(progress) / PROGRESS_MAX;
        for (int i = 1; i < path.size(); i++) {
            RouteSurveyPoint from = path.get(i - 1);
            RouteSurveyPoint to = path.get(i);
            double length = segmentTraversalDistance(from, to);
            if (length <= 0.0) {
                continue;
            }
            if (remaining <= length) {
                double t = remaining / length;
                RoutePoint a = from.point();
                RoutePoint b = to.point();
                int x = (int) Math.round(a.x() + (b.x() - a.x()) * t);
                int z = (int) Math.round(a.z() + (b.z() - a.z()) * t);
                return a.hasElevation() && b.hasElevation()
                        ? new RoutePoint(x, (int) Math.round(a.y() + (b.y() - a.y()) * t), z)
                        : new RoutePoint(x, z);
            }
            remaining -= length;
        }
        return path.getLast().point();
    }

    static FerryState ferryState(List<RouteSurveyPoint> path, int progress, int direction,
                                 double blocksPerSecond) {
        if (path == null || path.size() < 2) {
            return FerryState.NONE;
        }
        double totalDistance = traversalDistance(path);
        if (totalDistance <= 0.0 || isLandNodeProgress(path, progress, totalDistance)) {
            return FerryState.NONE;
        }
        double traveled = totalDistance * clamp(progress) / PROGRESS_MAX;
        double cursor = 0.0;
        int activeSegment = -1;
        for (int segment = 1; segment < path.size(); segment++) {
            double length = segmentTraversalDistance(path.get(segment - 1), path.get(segment));
            if (traveled <= cursor + length || segment == path.size() - 1) {
                activeSegment = segment;
                break;
            }
            cursor += length;
        }
        if (activeSegment < 1 || !isFerrySegment(path.get(activeSegment - 1), path.get(activeSegment))) {
            return FerryState.NONE;
        }
        double remaining;
        if (direction >= 0) {
            double ferryEnd = cursor + segmentTraversalDistance(path.get(activeSegment - 1), path.get(activeSegment));
            for (int segment = activeSegment + 1; segment < path.size(); segment++) {
                if (!isFerrySegment(path.get(segment - 1), path.get(segment))) {
                    break;
                }
                ferryEnd += segmentTraversalDistance(path.get(segment - 1), path.get(segment));
            }
            remaining = Math.max(0.0, ferryEnd - traveled);
        } else {
            double ferryStart = cursor;
            for (int segment = activeSegment - 1; segment >= 1; segment--) {
                if (!isFerrySegment(path.get(segment - 1), path.get(segment))) {
                    break;
                }
                ferryStart -= segmentTraversalDistance(path.get(segment - 1), path.get(segment));
            }
            remaining = Math.max(0.0, traveled - ferryStart);
        }
        return new FerryState(true, Math.max(1,
                (int) Math.ceil(remaining / Math.max(0.1, blocksPerSecond))));
    }

    static FerryBoarding crossedBoarding(List<RouteSurveyPoint> path, int progress,
                                         int proposedProgress, int direction) {
        for (FerryBoarding boarding : ferryBoardings(path, direction)) {
            if (direction >= 0
                    ? progress <= boarding.progress() && proposedProgress > boarding.progress()
                    : progress >= boarding.progress() && proposedProgress < boarding.progress()) {
                return boarding;
            }
        }
        return null;
    }

    static FerryBoarding boardingAt(List<RouteSurveyPoint> path, int progress, int direction) {
        for (FerryBoarding boarding : ferryBoardings(path, direction)) {
            if (boarding.progress() == clamp(progress)) {
                return boarding;
            }
        }
        return null;
    }

    static boolean isFerrySegment(RouteSurveyPoint from, RouteSurveyPoint to) {
        return from.ocean() || to.ocean();
    }

    private static List<FerryBoarding> ferryBoardings(List<RouteSurveyPoint> path, int direction) {
        if (path == null || path.size() < 2) {
            return List.of();
        }
        double totalDistance = traversalDistance(path);
        if (totalDistance <= 0.0) {
            return List.of();
        }
        List<FerryBoarding> boardings = new ArrayList<>();
        double cumulative = 0.0;
        for (int node = 0; node < path.size(); node++) {
            RouteSurveyPoint point = path.get(node);
            if (!point.ocean()) {
                boolean departing = direction >= 0
                        ? node < path.size() - 1 && isFerrySegment(point, path.get(node + 1))
                        : node > 0 && isFerrySegment(path.get(node - 1), point);
                if (departing) {
                    boardings.add(new FerryBoarding(point.point(), progressAtDistance(cumulative, totalDistance)));
                }
            }
            if (node < path.size() - 1) {
                cumulative += segmentTraversalDistance(point, path.get(node + 1));
            }
        }
        return List.copyOf(boardings);
    }

    private static boolean isLandNodeProgress(List<RouteSurveyPoint> path, int progress, double totalDistance) {
        double cumulative = 0.0;
        for (int node = 0; node < path.size(); node++) {
            if (!path.get(node).ocean()
                    && progressAtDistance(cumulative, totalDistance) == clamp(progress)) {
                return true;
            }
            if (node < path.size() - 1) {
                cumulative += segmentTraversalDistance(path.get(node), path.get(node + 1));
            }
        }
        return false;
    }

    private static int progressAtDistance(double distance, double totalDistance) {
        return totalDistance <= 0.0 ? 0 : clamp((int) Math.round(distance * PROGRESS_MAX / totalDistance));
    }

    private static int clamp(int progress) {
        return Math.max(0, Math.min(PROGRESS_MAX, progress));
    }

    record RouteSurveyPoint(RoutePoint point, boolean ocean) {}

    record FerryBoarding(RoutePoint point, int progress) {}

    record FerryState(boolean active, int secondsRemaining) {
        static final FerryState NONE = new FerryState(false, 0);
    }

    record RoutePoint(int x, int y, int z, boolean hasElevation) {
        RoutePoint(int x, int z) {
            this(x, 0, z, false);
        }

        RoutePoint(int x, int y, int z) {
            this(x, y, z, true);
        }

        double distance(RoutePoint other) {
            return Math.sqrt(distanceSquared(other));
        }

        double distanceSquared(RoutePoint other) {
            double dx = other.x - x;
            double dz = other.z - z;
            return dx * dx + dz * dz;
        }

        double spatialDistanceSquared(RoutePoint other) {
            double horizontal = distanceSquared(other);
            if (!hasElevation || !other.hasElevation) {
                return horizontal;
            }
            double dy = other.y - y;
            return horizontal + dy * dy;
        }
    }
}
