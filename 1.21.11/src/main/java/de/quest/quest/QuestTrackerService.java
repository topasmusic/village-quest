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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestTrackerService {
    private static final String AUTO_HINT_SHOWN = "quest_tracker_auto_hint_shown";
    private static final Map<UUID, Long> LAST_TRACKER_REFRESH = new ConcurrentHashMap<>();

    private QuestTrackerService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isEnabled(ServerWorld world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).isQuestTrackerEnabled();
    }

    public static boolean setEnabled(ServerWorld world, ServerPlayerEntity player, boolean enabled) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.isQuestTrackerEnabled() == enabled) {
            refresh(world, player);
            return enabled;
        }
        data.setQuestTrackerEnabled(enabled);
        QuestState.get(world.getServer()).markDirty();
        if (!enabled) {
            LAST_TRACKER_REFRESH.remove(player.getUuid());
        }
        refresh(world, player);
        return enabled;
    }

    public static boolean toggle(ServerWorld world, ServerPlayerEntity player) {
        return setEnabled(world, player, !isEnabled(world, player.getUuid()));
    }

    public static void enableForAcceptedQuest(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }

        PlayerQuestData data = data(world, player.getUuid());
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
            QuestState.get(world.getServer()).markDirty();
        }

        if (showHint) {
            MutableText command = Text.literal("/questtracker").formatted(Formatting.AQUA);
            player.sendMessage(Text.translatable("message.village-quest.questtracker.auto_enabled", command).formatted(Formatting.GRAY), false);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isEnabled(world, player.getUuid())) {
                continue;
            }
            long now = world.getTime();
            long last = LAST_TRACKER_REFRESH.getOrDefault(player.getUuid(), -100L);
            if ((now - last) >= 20L) {
                refresh(world, player);
            }
        }
    }

    public static void refresh(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }
        ServerPlayNetworking.send(player, buildPayload(world, player));
        LAST_TRACKER_REFRESH.put(player.getUuid(), world.getTime());
    }

    private static Payloads.QuestTrackerPayload buildPayload(ServerWorld world, ServerPlayerEntity player) {
        boolean enabled = isEnabled(world, player.getUuid());
        if (!enabled) {
            return new Payloads.QuestTrackerPayload(false, false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of());
        }

        DailyQuestService.QuestStatus daily = DailyQuestService.openQuestStatus(world, player.getUuid());
        boolean dailyActive = daily != null;
        Text dailyTitle = dailyActive ? daily.title() : Text.empty();
        List<Text> dailyLines = dailyActive ? List.of(daily.progressLine()) : List.of();

        WeeklyQuestStatus weekly = WeeklyQuestService.openStatus(world, player.getUuid());
        boolean weeklyActive = weekly != null;
        Text weeklyTitle = weeklyActive ? weekly.title() : Text.empty();
        List<Text> weeklyLines = weeklyActive ? weekly.lines() : List.of();

        StoryQuestStatus story = StoryQuestService.openStatus(world, player.getUuid());
        boolean storyActive = story != null;
        Text storyTitle = storyActive ? story.title() : Text.empty();
        List<Text> storyLines = storyActive ? story.lines() : List.of();

        PilgrimContractService.PilgrimContractStatus pilgrim = PilgrimContractService.openStatus(world, player.getUuid());
        boolean pilgrimActive = pilgrim != null;
        Text pilgrimTitle = pilgrimActive ? pilgrim.title() : Text.empty();
        List<Text> pilgrimLines = pilgrimActive ? pilgrim.lines() : List.of();

        SpecialQuestStatus special = SpecialQuestService.openStatus(world, player.getUuid());
        boolean specialActive = special != null;
        Text specialTitle = specialActive ? special.title() : Text.empty();
        List<Text> specialLines = specialActive ? special.lines() : List.of();

        return new Payloads.QuestTrackerPayload(enabled, dailyActive, dailyTitle, dailyLines, weeklyActive, weeklyTitle, weeklyLines, storyActive, storyTitle, storyLines, pilgrimActive, pilgrimTitle, pilgrimLines, specialActive, specialTitle, specialLines);
    }
}
