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
                return new RoutePoint(
                        (int) Math.round(from.x() + (to.x() - from.x()) * t),
                        (int) Math.round(from.z() + (to.z() - from.z()) * t));
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

    static FerryState ferryState(List<RouteSurveyPoint> path, int progress, int direction,
                                 double blocksPerSecond) {
        if (path == null || path.size() < 2) {
            return FerryState.NONE;
        }
        double totalDistance = pathDistance(path.stream().map(RouteSurveyPoint::point).toList());
        if (totalDistance <= 0.0 || isLandNodeProgress(path, progress, totalDistance)) {
            return FerryState.NONE;
        }
        double traveled = totalDistance * clamp(progress) / PROGRESS_MAX;
        double cursor = 0.0;
        int activeSegment = -1;
        for (int segment = 1; segment < path.size(); segment++) {
            double length = path.get(segment - 1).point().distance(path.get(segment).point());
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
            double ferryEnd = cursor + path.get(activeSegment - 1).point().distance(path.get(activeSegment).point());
            for (int segment = activeSegment + 1; segment < path.size(); segment++) {
                if (!isFerrySegment(path.get(segment - 1), path.get(segment))) {
                    break;
                }
                ferryEnd += path.get(segment - 1).point().distance(path.get(segment).point());
            }
            remaining = Math.max(0.0, ferryEnd - traveled);
        } else {
            double ferryStart = cursor;
            for (int segment = activeSegment - 1; segment >= 1; segment--) {
                if (!isFerrySegment(path.get(segment - 1), path.get(segment))) {
                    break;
                }
                ferryStart -= path.get(segment - 1).point().distance(path.get(segment).point());
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
        double totalDistance = pathDistance(path.stream().map(RouteSurveyPoint::point).toList());
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
                cumulative += point.point().distance(path.get(node + 1).point());
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
                cumulative += path.get(node).point().distance(path.get(node + 1).point());
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

    record RoutePoint(int x, int z) {
        double distance(RoutePoint other) {
            return Math.sqrt(distanceSquared(other));
        }

        double distanceSquared(RoutePoint other) {
            double dx = other.x - x;
            double dz = other.z - z;
            return dx * dx + dz * dz;
        }
    }
}
