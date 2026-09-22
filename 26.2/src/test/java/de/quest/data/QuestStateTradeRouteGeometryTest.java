package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestStateTradeRouteGeometryTest {
    private static final UUID OWNER = UUID.fromString("78217f1f-1291-4422-9cfa-34d79988fe22");

    @Test
    void legacyAndV2RouteCoordinatesSurviveSaveReload() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(OWNER);
        data.setTradeRouteInt("route_0_waypoint_count", 1);
        data.setTradeRouteInt("route_0_waypoint_0_x", 12);
        data.setTradeRouteInt("route_0_waypoint_0_z", 34);
        data.setTradeRouteInt("route_1_geometry_version", 2);
        data.setTradeRouteInt("route_1_waypoint_count", 1);
        data.setTradeRouteInt("route_1_waypoint_0_x", 56);
        data.setTradeRouteInt("route_1_waypoint_0_y", -19);
        data.setTradeRouteInt("route_1_waypoint_0_z", 78);
        data.setTradeRouteInt("home_physical_x", 4);
        data.setTradeRouteInt("home_physical_y", -42);
        data.setTradeRouteInt("home_physical_z", 8);
        data.setTradeRouteFlag("home_physical_anchor", true);

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(OWNER);

        assertEquals(0, loaded.getTradeRouteInt("route_0_geometry_version"));
        assertEquals(12, loaded.getTradeRouteInt("route_0_waypoint_0_x"));
        assertEquals(34, loaded.getTradeRouteInt("route_0_waypoint_0_z"));
        assertEquals(2, loaded.getTradeRouteInt("route_1_geometry_version"));
        assertEquals(56, loaded.getTradeRouteInt("route_1_waypoint_0_x"));
        assertEquals(-19, loaded.getTradeRouteInt("route_1_waypoint_0_y"));
        assertEquals(78, loaded.getTradeRouteInt("route_1_waypoint_0_z"));
        assertEquals(4, loaded.getTradeRouteInt("home_physical_x"));
        assertEquals(-42, loaded.getTradeRouteInt("home_physical_y"));
        assertEquals(8, loaded.getTradeRouteInt("home_physical_z"));
    }

    @Test
    void incidentOnlineClockAndOwnerFinalizationStageSurviveSaveReload() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(OWNER);
        data.setTradeRouteInt("route_0_event", 7);
        data.setTradeRouteInt("route_0_event_online_seconds", 1217);
        data.setTradeRouteInt("route_0_event_progress", 2);

        PlayerQuestData loaded = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(OWNER);

        assertEquals(1217, loaded.getTradeRouteInt("route_0_event_online_seconds"));
        assertEquals(2, loaded.getTradeRouteInt("route_0_event_progress"));
    }
}
