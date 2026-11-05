package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import java.util.List;

/** Pure local-anchor, observed-sync, and formation policy for physical caravans. */
final class TradeRouteNavigationPolicy {
    private static final double STRAIGHT_LOOKAHEAD_BLOCKS = 14.0;
    private static final double CORNER_LOOKAHEAD_BLOCKS = 6.0;
    private static final double SWITCHBACK_LOOKAHEAD_BLOCKS = 4.0;
    private static final double MAX_OBSERVED_DRIFT_BLOCKS = 32.0;
    private static final int OBSERVED_STUCK_HOLD_SECONDS = 5;
    private static final List<Double> FORMATION_SPACINGS = List.of(2.05, 1.10);

    private TradeRouteNavigationPolicy() {}

    static int lookaheadProgress(List<RoutePoint> path, int progress, int direction) {
        double total = TradeRouteGeometry.pathDistance(path);
        if (path == null || path.size() < 2 || total <= 0.0) {
            return 1;
        }
        int clamped = Math.max(0, Math.min(TradeRouteService.PROGRESS_MAX, progress));
        double traveled = total * clamped / TradeRouteService.PROGRESS_MAX;
        double desired = localLookaheadBlocks(path, traveled, direction, null);
        return Math.max(1, (int) Math.round(desired * TradeRouteService.PROGRESS_MAX / total));
    }

    static int lookaheadProgressForTraversal(List<RouteSurveyPoint> path, int progress, int direction) {
        double total = TradeRouteGeometry.traversalDistance(path);
        if (path == null || path.size() < 2 || total <= 0.0) {
            return 1;
        }
        List<RoutePoint> points = path.stream().map(RouteSurveyPoint::point).toList();
        double traveled = total * Math.max(0, Math.min(TradeRouteService.PROGRESS_MAX, progress))
                / TradeRouteService.PROGRESS_MAX;
        double desired = Math.min(4.0, localLookaheadBlocks(points, traveled, direction, path));
        double cursor = 0.0;
        if (direction >= 0) {
            for (int i = 1; i < path.size(); i++) {
                cursor += TradeRouteGeometry.segmentTraversalDistance(path.get(i - 1), path.get(i));
                if (cursor > traveled + 0.001) {
                    desired = Math.min(desired, cursor - traveled);
                    break;
                }
            }
        } else {
            for (int i = 1; i < path.size(); i++) {
                double next = cursor + TradeRouteGeometry.segmentTraversalDistance(path.get(i - 1), path.get(i));
                if (next >= traveled - 0.001) {
                    desired = Math.min(desired, traveled - cursor);
                    break;
                }
                cursor = next;
            }
        }
        return Math.max(1, (int) Math.round(desired * TradeRouteService.PROGRESS_MAX / total));
    }

    static boolean withinSurveyCorridor(List<RouteSurveyPoint> path, RoutePoint actual) {
        if (path == null || actual == null || !actual.hasElevation()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            RouteSurveyPoint point = path.get(i);
            boolean dock = i > 0 && TradeRouteGeometry.isFerrySegment(path.get(i - 1), point)
                    || i < path.size() - 1 && TradeRouteGeometry.isFerrySegment(point, path.get(i + 1));
            if (!point.ocean() && point.point().hasElevation()
                    && Math.hypot(actual.x() - point.point().x(),
                            actual.z() - point.point().z()) <= (dock ? 8.0 : 5.0)
                    && Math.abs(actual.y() - point.point().y()) <= 3.0) {
                return true;
            }
        }
        for (int i = 1; i < path.size(); i++) {
            RouteSurveyPoint from = path.get(i - 1);
            RouteSurveyPoint to = path.get(i);
            if (from.ocean() || to.ocean()
                    || !from.point().hasElevation() || !to.point().hasElevation()) {
                continue;
            }
            double dx = to.point().x() - from.point().x();
            double dy = to.point().y() - from.point().y();
            double dz = to.point().z() - from.point().z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            double t = lengthSquared <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
                    ((actual.x() - from.point().x()) * dx
                            + (actual.y() - from.point().y()) * dy
                            + (actual.z() - from.point().z()) * dz) / lengthSquared));
            double horizontal = Math.hypot(actual.x() - (from.point().x() + t * dx),
                    actual.z() - (from.point().z() + t * dz));
            double vertical = Math.abs(actual.y() - (from.point().y() + t * dy));
            if (horizontal <= 5.0 && vertical <= 3.0) {
                return true;
            }
        }
        return false;
    }

    static boolean holdVirtualProgress(boolean observed, double driftBlocks, int stuckSeconds) {
        return observed && (driftBlocks > MAX_OBSERVED_DRIFT_BLOCKS
                || stuckSeconds >= OBSERVED_STUCK_HOLD_SECONDS);
    }

    static List<Double> formationSpacings() {
        return FORMATION_SPACINGS;
    }

    private static double localLookaheadBlocks(List<RoutePoint> path,
                                               double traveled,
                                               int direction,
                                               List<RouteSurveyPoint> routed) {
        double cursor = 0.0;
        int segment = path.size() - 2;
        for (int i = 1; i < path.size(); i++) {
            double length = routed == null ? path.get(i - 1).distance(path.get(i))
                    : TradeRouteGeometry.segmentTraversalDistance(routed.get(i - 1), routed.get(i));
            if (traveled <= cursor + length || i == path.size() - 1) {
                segment = i - 1;
                break;
            }
            cursor += length;
        }
        int vertex = direction >= 0 ? segment + 1 : segment;
        if (vertex <= 0 || vertex >= path.size() - 1) {
            return STRAIGHT_LOOKAHEAD_BLOCKS;
        }
        RoutePoint before = path.get(vertex - 1);
        RoutePoint at = path.get(vertex);
        RoutePoint after = path.get(vertex + 1);
        double ax = at.x() - before.x();
        double az = at.z() - before.z();
        double bx = after.x() - at.x();
        double bz = after.z() - at.z();
        double denom = Math.sqrt((ax * ax + az * az) * (bx * bx + bz * bz));
        double cosine = denom <= 0.0 ? 1.0 : (ax * bx + az * bz) / denom;
        double lookahead = cosine < -0.25
                ? SWITCHBACK_LOOKAHEAD_BLOCKS
                : cosine < 0.70 ? CORNER_LOOKAHEAD_BLOCKS : STRAIGHT_LOOKAHEAD_BLOCKS;
        if (before.hasElevation() && at.hasElevation() && after.hasElevation()) {
            int elevationChange = Math.max(Math.abs(at.y() - before.y()),
                    Math.abs(after.y() - at.y()));
            if (elevationChange >= 10) {
                lookahead = Math.min(lookahead, SWITCHBACK_LOOKAHEAD_BLOCKS);
            } else if (elevationChange >= 5) {
                lookahead = Math.min(lookahead, CORNER_LOOKAHEAD_BLOCKS);
            }
        }
        return lookahead;
    }
}
