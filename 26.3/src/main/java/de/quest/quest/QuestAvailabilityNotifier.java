package de.quest.quest;

import de.quest.config.ClientPreferenceService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.questmaster.QuestMasterProgressionService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Announces each newly available repeatable or story assignment once.
 * Persistent tokens prevent login, restart, and per-tick chat spam.
 */
public final class QuestAvailabilityNotifier {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private QuestAvailabilityNotifier() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel world = server.overworld();
        QuestState state = QuestState.get(server);
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerQuestData data = state.getPlayerData(player.getUUID());
            boolean playerChanged = notifyDaily(world, player, data);
            playerChanged |= notifyWeekly(world, player, data);
            playerChanged |= notifyStory(world, player, data);
            if (playerChanged) {
                QuestMasterUiService.refreshIfOpen(world, player);
            }
            changed |= playerChanged;
        }
        if (changed) {
            state.setDirty();
        }
    }

    private static boolean notifyDaily(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        long day = TimeUtil.currentDay();
        if (data.getDailyOfferNoticeDay() == day
                || !DailyQuestService.canShowDailyOffer(world, player.getUUID())
                || DailyQuestService.previewQuestChoice(world, player.getUUID()) == null) {
            return false;
        }
        data.setDailyOfferNoticeDay(day);
        announce(world, player, "message.village-quest.questmaster.new_daily");
        return true;
    }

    private static boolean notifyWeekly(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        long cycle = TimeUtil.currentWeekCycle();
        if (data.getWeeklyOfferNoticeCycle() == cycle
                || WeeklyQuestService.isAcceptedThisWeek(world, player.getUUID())
                || WeeklyQuestService.hasCompletedThisWeek(world, player.getUUID())
                || WeeklyQuestService.previewQuestChoice(world, player.getUUID()) == null) {
            return false;
        }
        data.setWeeklyOfferNoticeCycle(cycle);
        announce(world, player, "message.village-quest.questmaster.new_weekly");
        return true;
    }

    private static boolean notifyStory(ServerLevel world, ServerPlayer player, PlayerQuestData data) {
        if (!QuestMasterProgressionService.isStoryCategoryUnlocked(world, player.getUUID())
                || StoryQuestService.activeArcType(world, player.getUUID()) != null) {
            return false;
        }

        StoryArcType story = StoryQuestService.availableArcType(world, player.getUUID());
        if (story == null) {
            return false;
        }
        int chapterIndex = StoryQuestService.chapterIndex(world, player.getUUID(), story);
        StoryChapterDefinition chapter = StoryQuestService.chapter(world, player.getUUID(), story);
        if (chapter == null || !chapter.canAccept(world, player)) {
            return false;
        }

        String offerKey = story.id() + ":" + chapterIndex;
        if (offerKey.equals(data.getStoryOfferNoticeKey())) {
            return false;
        }
        data.setStoryOfferNoticeKey(offerKey);
        announce(world, player, "message.village-quest.questmaster.new_story");
        return true;
    }

    private static void announce(ServerLevel world, ServerPlayer player, String translationKey) {
        if (ClientPreferenceService.questAvailableChatNotifications(player)) {
            player.sendSystemMessage(Component.translatable(translationKey).withStyle(ChatFormatting.GOLD), false);
        }
        QuestSoundFeedback.playNewOffer(world, player);
    }
}
