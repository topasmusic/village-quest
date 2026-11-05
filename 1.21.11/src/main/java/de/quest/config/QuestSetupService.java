package de.quest.config;

import de.quest.data.QuestState;
import de.quest.network.QuestSetupPayloads;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.util.QuestCalendarService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Explicit journal settings, with fresh server permissions and scoped editing. */
public final class QuestSetupService {
    public static final String COMPLETED = "config.journal_setup_completed.2_6";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private record Session(UUID id, WorldQuestSettings snapshot, long opened) {}
    private QuestSetupService() {}
    public static void start(MinecraftServer server) {
        SESSIONS.clear();
        VillageQuestServerConfig.bindWorld(QuestState.get(server).worldQuestSettings());
    }
    public static void stop() { SESSIONS.clear(); VillageQuestServerConfig.bindWorld(null); }
    public static void disconnect(UUID player) { SESSIONS.remove(player); }
    public static boolean canEdit(ServerPlayer player) {
        if (player == null) return false;
        var server = player.level().getServer();
        return (server.isSingleplayerOwner(player.nameAndId()) || server.getPlayerList().isOp(player.nameAndId()))
                && !QuestState.get(server).unknownWorldQuestSettings();
    }
    public static void open(ServerPlayer player) { open(player, 0); }
    private static void open(ServerPlayer player, int status) {
        var server = player.level().getServer();
        var data = QuestState.get(server).getPlayerData(player.getUUID());
        var settings = VillageQuestServerConfig.get().worldSettings();
        var session = new Session(UUID.randomUUID(), settings, server.overworld().getGameTime());
        SESSIONS.put(player.getUUID(), session);
        QuestBookHelper.closeJournal(player);
        ServerPlayNetworking.send(player, new QuestSetupPayloads.View(session.id, false, canEdit(player), settings,
                data.isQuestTrackerEnabled(), de.quest.caravan.TradeRouteService.isMinimapEnabled(player.getUUID()), status));
    }
    public static void save(ServerPlayer player, QuestSetupPayloads.Save payload) {
        var server = player.level().getServer();
        if (!server.isSameThread()) return;
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(payload.session())) {
            if (payload.save()) open(player, 3);
            return;
        }
        if (!payload.save()) {
            SESSIONS.remove(player.getUUID());
            QuestBookHelper.openJournal(player.level(), player);
            return;
        }
        long age = server.overworld().getGameTime() - session.opened;
        if (age < 0 || age > 36_000) { open(player, 3); return; }
        if (payload.changeWorld()) {
            if (!canEdit(player)) { open(player, 1); return; }
            if (!VillageQuestServerConfig.get().worldSettings().equals(session.snapshot)) { open(player, 2); return; }
            // Sample the previous clock before applying the new mode; ongoing IDs and claims survive.
            QuestCalendarService.currentDay();
            QuestState.get(server).setWorldQuestSettings(payload.settings());
            VillageQuestServerConfig.bindWorld(payload.settings());
            QuestCalendarService.currentDay();
        }
        var state = QuestState.get(server);
        state.getPlayerData(player.getUUID()).setMilestoneFlag(COMPLETED, true);
        state.setDirty();
        QuestTrackerService.setEnabled(server.overworld(), player, payload.tracker());
        de.quest.caravan.TradeRouteService.setMinimapEnabled(server.overworld(), player, payload.minimap());
        SESSIONS.remove(player.getUUID());
        QuestBookHelper.openJournal(player.level(), player);
    }
}
