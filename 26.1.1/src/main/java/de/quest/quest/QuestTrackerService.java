package de.quest.quest;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.network.Payloads;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.SpecialQuestStatus;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.StoryQuestStatus;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.weekly.WeeklyQuestStatus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestTrackerService {
    private static final String AUTO_HINT_SHOWN = "quest_tracker_auto_hint_shown";
    private static final Map<UUID, Long> LAST_TRACKER_REFRESH = new ConcurrentHashMap<>();

    private QuestTrackerService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isEnabled(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).isQuestTrackerEnabled();
    }

    public static boolean setEnabled(ServerLevel world, ServerPlayer player, boolean enabled) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.isQuestTrackerEnabled() == enabled) {
            refresh(world, player);
            return enabled;
        }
        data.setQuestTrackerEnabled(enabled);
        QuestState.get(world.getServer()).setDirty();
        if (!enabled) {
            LAST_TRACKER_REFRESH.remove(player.getUUID());
        }
        refresh(world, player);
        return enabled;
    }

    public static boolean toggle(ServerLevel world, ServerPlayer player) {
        return setEnabled(world, player, !isEnabled(world, player.getUUID()));
    }

    public static void enableForAcceptedQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        boolean changed = false;
        if (!data.isQuestTrackerEnabled()) {
            data.setQuestTrackerEnabled(true);
            changed = true;
        }

        boolean showHint = !data.hasMilestoneFlag(AUTO_HINT_SHOWN);
        if (showHint) {
            data.setMilestoneFlag(AUTO_HINT_SHOWN, true);
            changed = true;
        }

        if (changed) {
            QuestState.get(world.getServer()).setDirty();
        }

        if (showHint) {
            MutableComponent command = Component.literal("/questtracker").withStyle(ChatFormatting.AQUA);
            player.sendSystemMessage(Component.translatable("message.village-quest.questtracker.auto_enabled", command).withStyle(ChatFormatting.GRAY), false);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEnabled(world, player.getUUID())) {
                continue;
            }
            long now = world.getGameTime();
            long last = LAST_TRACKER_REFRESH.getOrDefault(player.getUUID(), -100L);
            if ((now - last) >= 20L) {
                refresh(world, player);
            }
        }
    }

    public static void refresh(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(world, player));
        LAST_TRACKER_REFRESH.put(player.getUUID(), world.getGameTime());
    }

    private static Payloads.QuestTrackerPayload buildPayload(ServerLevel world, ServerPlayer player) {
        boolean enabled = isEnabled(world, player.getUUID());
        if (!enabled) {
            return new Payloads.QuestTrackerPayload(false, false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of());
        }

        DailyQuestService.QuestStatus daily = DailyQuestService.openQuestStatus(world, player.getUUID());
        boolean dailyActive = daily != null;
        Component dailyTitle = dailyActive ? daily.title() : Component.empty();
        List<Component> dailyLines = dailyActive ? List.of(daily.progressLine()) : List.of();

        WeeklyQuestStatus weekly = WeeklyQuestService.openStatus(world, player.getUUID());
        boolean weeklyActive = weekly != null;
        Component weeklyTitle = weeklyActive ? weekly.title() : Component.empty();
        List<Component> weeklyLines = weeklyActive ? weekly.lines() : List.of();

        StoryQuestStatus story = StoryQuestService.openStatus(world, player.getUUID());
        boolean storyActive = story != null;
        Component storyTitle = storyActive ? story.title() : Component.empty();
        List<Component> storyLines = storyActive ? story.lines() : List.of();

        PilgrimContractService.PilgrimContractStatus pilgrim = PilgrimContractService.openStatus(world, player.getUUID());
        boolean pilgrimActive = pilgrim != null;
        Component pilgrimTitle = pilgrimActive ? pilgrim.title() : Component.empty();
        List<Component> pilgrimLines = pilgrimActive ? pilgrim.lines() : List.of();

        SpecialQuestStatus special = SpecialQuestService.openStatus(world, player.getUUID());
        boolean specialActive = special != null;
        Component specialTitle = specialActive ? special.title() : Component.empty();
        List<Component> specialLines = specialActive ? special.lines() : List.of();

        return new Payloads.QuestTrackerPayload(enabled, dailyActive, dailyTitle, dailyLines, weeklyActive, weeklyTitle, weeklyLines, storyActive, storyTitle, storyLines, pilgrimActive, pilgrimTitle, pilgrimLines, specialActive, specialTitle, specialLines);
    }
}
