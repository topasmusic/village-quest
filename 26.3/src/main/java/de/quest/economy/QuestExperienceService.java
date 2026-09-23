package de.quest.economy;

import java.math.BigDecimal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Converts authored quest reward units into bounded fractions of the player's experience bar.
 *
 * <p>The displayed reward is independent of the player's current level. A reward of three bars
 * therefore advances a level-10 player and a level-200 player by the same three level bars, while
 * vanilla still determines how many raw experience points those bars cost.</p>
 */
public final class QuestExperienceService {
    private static final double QUARTER_BAR = 0.25d;
    private static final double PROJECT_BONUS_BARS_PER_UNIT = 0.375d;

    public enum RewardType {
        DAILY(0.75d, 0.0d, 6.0d),
        WEEKLY(0.375d, 0.0d, 9.0d),
        PILGRIM(0.375d, 0.75d, 7.5d),
        STORY(0.375d, 0.0d, 7.5d);

        private final double barsPerUnit;
        private final double baseBars;
        private final double maximumBars;

        RewardType(double barsPerUnit, double baseBars, double maximumBars) {
            this.barsPerUnit = barsPerUnit;
            this.baseBars = baseBars;
            this.maximumBars = maximumBars;
        }
    }

    private QuestExperienceService() {}

    public static double levelBars(int rewardUnits, RewardType rewardType) {
        return levelBars(rewardUnits, 0, rewardType);
    }

    public static double levelBars(int rewardUnits, int projectBonusUnits, RewardType rewardType) {
        RewardType effectiveType = rewardType == null ? RewardType.STORY : rewardType;
        double baseReward = rewardUnits <= 0
                ? 0.0d
                : Math.min(effectiveType.maximumBars,
                        effectiveType.baseBars + rewardUnits * effectiveType.barsPerUnit);
        double projectBonus = projectBonusBars(projectBonusUnits);
        return roundToQuarterBar(baseReward + projectBonus);
    }

    public static double projectBonusBars(int projectBonusUnits) {
        if (projectBonusUnits <= 0) {
            return 0.0d;
        }
        return roundToQuarterBar(projectBonusUnits * PROJECT_BONUS_BARS_PER_UNIT);
    }

    public static double grant(ServerPlayer player,
                               int rewardUnits,
                               int projectBonusUnits,
                               RewardType rewardType) {
        double bars = levelBars(rewardUnits, projectBonusUnits, rewardType);
        if (player == null || bars <= 0.0d) {
            return bars;
        }

        int wholeLevels = (int) Math.floor(bars);
        if (wholeLevels > 0) {
            player.giveExperienceLevels(wholeLevels);
        }

        double partialBar = bars - wholeLevels;
        if (partialBar > 0.0d) {
            int points = Math.max(1, (int) Math.round(player.getXpNeededForNextLevel() * partialBar));
            player.giveExperiencePoints(points);
        }
        return bars;
    }

    public static Component rewardLine(int rewardUnits, RewardType rewardType) {
        return rewardLine(rewardUnits, 0, rewardType);
    }

    public static Component rewardLine(int rewardUnits,
                                       int projectBonusUnits,
                                       RewardType rewardType) {
        return Component.translatable(
                "screen.village-quest.questmaster.reward.experience",
                formatBars(levelBars(rewardUnits, projectBonusUnits, rewardType))
        ).withStyle(ChatFormatting.GREEN);
    }

    public static Component projectBonusAmount(int projectBonusUnits) {
        return Component.translatable(
                "text.village-quest.experience.level_amount",
                formatBars(projectBonusBars(projectBonusUnits))
        ).withStyle(ChatFormatting.GREEN);
    }

    public static String formatBars(double bars) {
        return BigDecimal.valueOf(roundToQuarterBar(Math.max(0.0d, bars)))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static double roundToQuarterBar(double bars) {
        return Math.round(Math.max(0.0d, bars) / QUARTER_BAR) * QUARTER_BAR;
    }
}
