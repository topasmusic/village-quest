package de.quest.questmaster;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.story.StoryQuestService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class QuestMasterProgressionService {
    private static final String STORY_UNLOCK_NOTIFIED = "questmaster_story_unlock_notified";
    private static final String SPECIAL_UNLOCK_NOTIFIED = "questmaster_special_unlock_notified";
    private static final String SHARD_UNLOCK_NOTIFIED = "questmaster_shard_unlock_notified";

    private QuestMasterProgressionService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isStoryCategoryUnlocked(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return data.getLastRewardDay() != PlayerQuestData.UNSET_DAY
                || data.getActiveStoryArc() != null
                || !data.getStoryDiscovered().isEmpty()
                || !data.getStoryCompleted().isEmpty()
                || StoryQuestService.activeCount(world, playerId) > 0
                || StoryQuestService.completedCount(world, playerId) > 0;
    }

    public static boolean isSpecialCategoryUnlocked(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return false;
        }
        PlayerQuestData data = data(world, playerId);
        return !data.getReputationState().isEmpty()
                || data.getPendingSpecialOfferKind() != null
                || data.getShardRelicQuestStage() != ShardRelicQuestStage.NONE
                || data.getMerchantSealQuestStage() != RelicQuestStage.NONE
                || data.getShepherdFluteQuestStage() != RelicQuestStage.NONE
                || data.getApiaristSmokerQuestStage() != RelicQuestStage.NONE
                || data.getSurveyorCompassQuestStage() != RelicQuestStage.NONE;
    }

    public static void onNormalDailyCompleted(ServerLevel world, ServerPlayer player, boolean storyWasUnlocked) {
        if (world == null || player == null || storyWasUnlocked || !isStoryCategoryUnlocked(world, player.getUUID())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(STORY_UNLOCK_NOTIFIED)) {
            return;
        }
        data.setMilestoneFlag(STORY_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.story_unlocked").withStyle(ChatFormatting.GOLD), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onReputationChanged(ServerLevel world, UUID playerId, int previousTotal) {
        if (world == null || playerId == null || previousTotal > 0 || !isSpecialCategoryUnlocked(world, playerId)) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.hasMilestoneFlag(SPECIAL_UNLOCK_NOTIFIED)) {
            return;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        data.setMilestoneFlag(SPECIAL_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.special_unlocked").withStyle(ChatFormatting.AQUA), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onMagicShardCountChanged(ServerLevel world, ServerPlayer player, int beforeCount, int afterCount) {
        if (world == null || player == null || beforeCount >= 10 || afterCount < 10) {
            return;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(SHARD_UNLOCK_NOTIFIED)) {
            return;
        }
        data.setMilestoneFlag(SHARD_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.questmaster.shard_special_unlocked").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
