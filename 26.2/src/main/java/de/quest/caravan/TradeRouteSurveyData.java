package de.quest.caravan;

import de.quest.caravan.TradeRouteGeometry.RoutePoint;
import de.quest.caravan.TradeRouteGeometry.RouteSurveyPoint;
import de.quest.data.PlayerQuestData;

import java.util.ArrayList;
import java.util.List;

/** Persistence adapter for route waypoints and in-progress survey drafts. */
final class TradeRouteSurveyData {
    private static final String HOME_X = "home_x";
    private static final String HOME_Z = "home_z";
    private static final String SURVEY_ROUTE = "survey_route";
    private static final String SURVEY_POINT_COUNT = "survey_point_count";
    private static final String SURVEY_POINT_PREFIX = "survey_point_";
    private static final String SURVEY_WAS_STOPPED = "survey_was_stopped";

    private TradeRouteSurveyData() {}

    static int activeSurveyIndex(PlayerQuestData data) {
        int stored = data == null ? 0 : data.getTradeRouteInt(SURVEY_ROUTE);
        return stored <= 0 ? -1 : stored - 1;
    }

    static List<RouteSurveyPoint> routeWaypointsWithModes(PlayerQuestData data, int routeIndex) {
        int count = Math.min(TradeRouteService.MAX_WAYPOINTS,
                Math.max(0, routeInt(data, routeIndex, "waypoint_count")));
        List<RouteSurveyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new RouteSurveyPoint(new RoutePoint(
                    routeInt(data, routeIndex, "waypoint_" + i + "_x"),
                    routeInt(data, routeIndex, "waypoint_" + i + "_z")),
                    data.hasTradeRouteFlag(routeKey(routeIndex, "waypoint_" + i + "_ocean"))));
        }
        return List.copyOf(points);
    }

    static List<RoutePoint> routeWaypoints(PlayerQuestData data, int routeIndex) {
        return routeWaypointsWithModes(data, routeIndex).stream().map(RouteSurveyPoint::point).toList();
    }

    static void setRouteWaypoints(PlayerQuestData data, int routeIndex, List<RoutePoint> points) {
        setRouteWaypointsWithModes(data, routeIndex, points == null ? List.of() : points.stream()
                .map(point -> new RouteSurveyPoint(point, false)).toList());
    }

    static void setRouteWaypointsWithModes(PlayerQuestData data, int routeIndex,
                                           List<RouteSurveyPoint> points) {
        String prefix = routeKey(routeIndex, "waypoint_");
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.startsWith(prefix)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(prefix)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        int count = Math.min(TradeRouteService.MAX_WAYPOINTS, points == null ? 0 : points.size());
        setRouteInt(data, routeIndex, "waypoint_count", count);
        for (int i = 0; i < count; i++) {
            RouteSurveyPoint routed = points.get(i);
            setRouteInt(data, routeIndex, "waypoint_" + i + "_x", routed.point().x());
            setRouteInt(data, routeIndex, "waypoint_" + i + "_z", routed.point().z());
            data.setTradeRouteFlag(routeKey(routeIndex, "waypoint_" + i + "_ocean"), routed.ocean());
        }
    }

    static List<RouteSurveyPoint> surveyPointsWithModes(PlayerQuestData data) {
        int count = Math.min(TradeRouteService.MAX_WAYPOINTS,
                Math.max(0, data.getTradeRouteInt(SURVEY_POINT_COUNT)));
        List<RouteSurveyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(surveyPointWithMode(data, i));
        }
        return List.copyOf(points);
    }

    static List<RoutePoint> surveyPoints(PlayerQuestData data) {
        return surveyPointsWithModes(data).stream().map(RouteSurveyPoint::point).toList();
    }

    static RouteSurveyPoint surveyPointWithMode(PlayerQuestData data, int pointIndex) {
        return new RouteSurveyPoint(new RoutePoint(
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x"),
                data.getTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z")),
                data.hasTradeRouteFlag(SURVEY_POINT_PREFIX + pointIndex + "_ocean"));
    }

    static RoutePoint surveyPoint(PlayerQuestData data, int pointIndex) {
        return surveyPointWithMode(data, pointIndex).point();
    }

    static void setSurveyPoint(PlayerQuestData data, int pointIndex, RoutePoint point, boolean ocean) {
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_x", point.x());
        data.setTradeRouteInt(SURVEY_POINT_PREFIX + pointIndex + "_z", point.z());
        data.setTradeRouteFlag(SURVEY_POINT_PREFIX + pointIndex + "_ocean", ocean);
    }

    static List<RouteSurveyPoint> normalizedSurveyPoints(PlayerQuestData data, int routeIndex) {
        RoutePoint home = new RoutePoint(data.getTradeRouteInt(HOME_X), data.getTradeRouteInt(HOME_Z));
        RoutePoint destination = new RoutePoint(routeInt(data, routeIndex, "x"), routeInt(data, routeIndex, "z"));
        List<RouteSurveyPoint> normalized = new ArrayList<>();
        RoutePoint previous = home;
        for (RouteSurveyPoint routed : surveyPointsWithModes(data)) {
            if (previous.distanceSquared(routed.point()) < 16.0) {
                continue;
            }
            normalized.add(routed);
            previous = routed.point();
        }
        if (!normalized.isEmpty() && normalized.getLast().point().distanceSquared(destination) < 16.0) {
            normalized.removeLast();
        }
        return List.copyOf(normalized);
    }

    static void restoreSurveyPauseState(PlayerQuestData data, int routeIndex) {
        data.setTradeRouteFlag(routeKey(routeIndex, "stopped"), data.hasTradeRouteFlag(SURVEY_WAS_STOPPED));
    }

    static void clearSurveyDraft(PlayerQuestData data) {
        for (String key : List.copyOf(data.getTradeRouteIntState().keySet())) {
            if (key.equals(SURVEY_ROUTE) || key.equals(SURVEY_POINT_COUNT) || key.startsWith(SURVEY_POINT_PREFIX)) {
                data.setTradeRouteInt(key, 0);
            }
        }
        for (String flag : List.copyOf(data.getTradeRouteFlags())) {
            if (flag.startsWith(SURVEY_POINT_PREFIX)) {
                data.setTradeRouteFlag(flag, false);
            }
        }
        data.setTradeRouteFlag(SURVEY_WAS_STOPPED, false);
    }

    private static int routeInt(PlayerQuestData data, int routeIndex, String suffix) {
        return data.getTradeRouteInt(routeKey(routeIndex, suffix));
    }

    private static void setRouteInt(PlayerQuestData data, int routeIndex, String suffix, int value) {
        data.setTradeRouteInt(routeKey(routeIndex, suffix), value);
    }

    private static String routeKey(int routeIndex, String suffix) {
        return "route_" + routeIndex + "_" + suffix;
    }
}
