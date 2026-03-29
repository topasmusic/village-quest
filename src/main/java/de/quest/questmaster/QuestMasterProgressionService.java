package de.quest.questmaster;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class QuestMasterProgressionService {
    private static final String STORY_UNLOCK_NOTIFIED = "questmaster_story_unlock_notified";
    private static final String SPECIAL_UNLOCK_NOTIFIED = "questmaster_special_unlock_notified";
    private static final String SHARD_UNLOCK_NOTIFIED = "questmaster_shard_unlock_notified";

    private QuestMasterProgressionService() {}

    private static PlayerQuestData data(ServerWorld world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isStoryCategoryUnlocked(ServerWorld world, UUID playerId) {
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

    public static boolean isSpecialCategoryUnlocked(ServerWorld world, UUID playerId) {
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

    public static void onNormalDailyCompleted(ServerWorld world, ServerPlayerEntity player, boolean storyWasUnlocked) {
        if (world == null || player == null || storyWasUnlocked || !isStoryCategoryUnlocked(world, player.getUuid())) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(STORY_UNLOCK_NOTIFIED)) {
            return;
        }
        data.setMilestoneFlag(STORY_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).markDirty();
        player.sendMessage(Text.translatable("message.village-quest.questmaster.story_unlocked").formatted(Formatting.GOLD), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onReputationChanged(ServerWorld world, UUID playerId, int previousTotal) {
        if (world == null || playerId == null || previousTotal > 0 || !isSpecialCategoryUnlocked(world, playerId)) {
            return;
        }
        PlayerQuestData data = data(world, playerId);
        if (data.hasMilestoneFlag(SPECIAL_UNLOCK_NOTIFIED)) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        data.setMilestoneFlag(SPECIAL_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).markDirty();
        player.sendMessage(Text.translatable("message.village-quest.questmaster.special_unlocked").formatted(Formatting.AQUA), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }

    public static void onMagicShardCountChanged(ServerWorld world, ServerPlayerEntity player, int beforeCount, int afterCount) {
        if (world == null || player == null || beforeCount >= 10 || afterCount < 10) {
            return;
        }
        PlayerQuestData data = data(world, player.getUuid());
        if (data.hasMilestoneFlag(SHARD_UNLOCK_NOTIFIED)) {
            return;
        }
        data.setMilestoneFlag(SHARD_UNLOCK_NOTIFIED, true);
        QuestState.get(world.getServer()).markDirty();
        player.sendMessage(Text.translatable("message.village-quest.questmaster.shard_special_unlocked").formatted(Formatting.LIGHT_PURPLE), false);
        QuestMasterUiService.refreshIfOpen(world, player);
    }
}
