package de.quest.quest;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Announces each newly available repeatable or story assignment once.
 * Persistent tokens prevent login, restart, and per-tick chat spam.
 */
public final class QuestAvailabilityNotifier {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private QuestAvailabilityNotifier() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        ServerWorld world = server.getOverworld();
        if (world == null || world.getTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        QuestState state = QuestState.get(server);
        boolean changed = false;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerQuestData data = state.getPlayerData(player.getUuid());
            boolean playerChanged = notifyDaily(world, player, data);
            playerChanged |= notifyWeekly(world, player, data);
            playerChanged |= notifyStory(world, player, data);
            if (playerChanged) {
                QuestMasterUiService.refreshIfOpen(world, player);
            }
            changed |= playerChanged;
        }
        if (changed) {
            state.markDirty();
        }
    }

    private static boolean notifyDaily(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        long day = TimeUtil.currentDay();
        if (data.getDailyOfferNoticeDay() == day
                || !DailyQuestService.canShowDailyOffer(world, player.getUuid())
                || DailyQuestService.previewQuestChoice(world, player.getUuid()) == null) {
            return false;
        }
        data.setDailyOfferNoticeDay(day);
        announce(world, player, "message.village-quest.questmaster.new_daily");
        return true;
    }

    private static boolean notifyWeekly(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        long cycle = TimeUtil.currentWeekCycle();
        if (data.getWeeklyOfferNoticeCycle() == cycle
                || WeeklyQuestService.isAcceptedThisWeek(world, player.getUuid())
                || WeeklyQuestService.hasCompletedThisWeek(world, player.getUuid())
                || WeeklyQuestService.previewQuestChoice(world, player.getUuid()) == null) {
            return false;
        }
        data.setWeeklyOfferNoticeCycle(cycle);
        announce(world, player, "message.village-quest.questmaster.new_weekly");
        return true;
    }

    private static boolean notifyStory(ServerWorld world, ServerPlayerEntity player, PlayerQuestData data) {
        if (!QuestMasterProgressionService.isStoryCategoryUnlocked(world, player.getUuid())
                || StoryQuestService.activeArcType(world, player.getUuid()) != null) {
            return false;
        }

        StoryArcType story = StoryQuestService.availableArcType(world, player.getUuid());
        if (story == null) {
            return false;
        }
        int chapterIndex = StoryQuestService.chapterIndex(world, player.getUuid(), story);
        StoryChapterDefinition chapter = StoryQuestService.chapter(world, player.getUuid(), story);
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

    private static void announce(ServerWorld world, ServerPlayerEntity player, String translationKey) {
        player.sendMessage(Text.translatable(translationKey).formatted(Formatting.GOLD), false);
        QuestSoundFeedback.playNewOffer(world, player);
    }
}
