package de.quest.quest.daily;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One-time, low-friction choice that introduces the regular Daily system. */
public final class FirstDailyChoiceService {
    private static final String ACTIVE = "guild_intro.first_daily_active";
    private static final String COMPLETED = "guild_intro.first_daily_completed";
    private static final String MIGRATION_CHECKED = "guild_intro.first_daily_migration_checked";
    private static final List<DailyQuestService.DailyQuestType> CHOICES = List.of(
            DailyQuestService.DailyQuestType.WHEAT_HARVEST,
            DailyQuestService.DailyQuestType.WOODCUTTING,
            DailyQuestService.DailyQuestType.WOOL_WEAVING
    );

    private FirstDailyChoiceService() {}

    public static List<DailyQuestService.DailyQuestType> choices() {
        return CHOICES;
    }

    public static boolean canChoose(PlayerQuestData data) {
        return data != null
                && !data.isDailyDiscovered()
                && data.getLastRewardDay() == PlayerQuestData.UNSET_DAY
                && data.getBonusRewardDay() == PlayerQuestData.UNSET_DAY
                && data.getAcceptedDay() == PlayerQuestData.UNSET_DAY
                && data.getReputationState().isEmpty()
                && !data.hasTradeRouteFlag(ACTIVE)
                && !data.hasTradeRouteFlag(COMPLETED);
    }

    public static boolean canChoose(ServerLevel world, UUID playerId) {
        return world != null && playerId != null
                && canChoose(QuestState.get(world.getServer()).getPlayerData(playerId));
    }

    public static boolean accept(ServerLevel world, ServerPlayer player,
                                 DailyQuestService.DailyQuestType choice) {
        if (world == null || player == null || !CHOICES.contains(choice)) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        if (!canChoose(data)) {
            return false;
        }
        data.setTradeRouteFlag(ACTIVE, true);
        data.setDailyChoice(choice);
        data.setDailyChoiceDay(de.quest.util.TimeUtil.currentDay());
        data.setDailyTargetProfile(RepeatableTargetProfile.LIGHT);
        QuestState.get(world.getServer()).setDirty();
        DailyQuestService.acceptQuest(world, player);
        return DailyQuestService.isAcceptedToday(world, player.getUUID());
    }

    /** Returns true exactly once, when the introductory Daily is completed. */
    public static boolean complete(PlayerQuestData data) {
        if (data == null || !data.hasTradeRouteFlag(ACTIVE)) {
            return false;
        }
        data.setTradeRouteFlag(ACTIVE, false);
        data.setTradeRouteFlag(COMPLETED, true);
        return true;
    }

    public static boolean isActive(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(ACTIVE);
    }

    public static boolean isCompleted(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(COMPLETED);
    }

    /**
     * Marks clearly established pre-2.4 profiles as having passed the new gate.
     * This only records onboarding eligibility; it never creates/completes a Daily
     * and never advances the separate welcome assignment.
     */
    public static boolean migratePre24Progress(PlayerQuestData data) {
        if (data == null || data.hasTradeRouteFlag(MIGRATION_CHECKED)) {
            return false;
        }
        data.setTradeRouteFlag(MIGRATION_CHECKED, true);
        if (!isActive(data) && !isCompleted(data) && hasRecognizableLegacyProgress(data)) {
            data.setTradeRouteFlag(COMPLETED, true);
        }
        return true;
    }

    public static boolean canUseSharedDaily(PlayerQuestData data) {
        return isCompleted(data);
    }

    private static boolean hasRecognizableLegacyProgress(PlayerQuestData data) {
        return data.isDailyDiscovered()
                || data.getTradeRouteInt("route_count") > 0
                || data.getLastRewardDay() != PlayerQuestData.UNSET_DAY
                || data.getBonusRewardDay() != PlayerQuestData.UNSET_DAY
                || data.getAcceptedDay() != PlayerQuestData.UNSET_DAY
                || data.getBonusAcceptedDay() != PlayerQuestData.UNSET_DAY
                || data.getWeeklyAcceptedCycle() != PlayerQuestData.UNSET_DAY
                || data.getWeeklyRewardCycle() != PlayerQuestData.UNSET_DAY
                || !data.getDailyIntState().isEmpty()
                || !data.getDailyFlags().isEmpty()
                || !data.getWeeklyIntState().isEmpty()
                || !data.getWeeklyFlags().isEmpty()
                || !data.getWeeklyDiscovered().isEmpty()
                || !data.getWeeklyCompleted().isEmpty()
                || data.getActiveStoryArc() != null
                || !data.getStoryDiscovered().isEmpty()
                || !data.getStoryCompleted().isEmpty()
                || data.getActivePilgrimContractId() != null
                || !data.getReputationState().isEmpty();
    }

    public static int introductoryTarget(String salt, int regularTarget) {
        if (salt == null) {
            return regularTarget;
        }
        return switch (salt) {
            case "daily.wheat.crop" -> 8;
            case "daily.wheat.bread" -> 2;
            case "daily.wood.log" -> 12;
            case "daily.coal.coal" -> 4;
            case "daily.wool.sheep" -> 4;
            default -> regularTarget;
        };
    }
}
