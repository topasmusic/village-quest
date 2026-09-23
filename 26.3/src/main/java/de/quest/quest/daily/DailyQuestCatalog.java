package de.quest.quest.daily;

import de.quest.economy.CurrencyService;
import de.quest.quest.repeatable.RepeatableRewardTuning;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import net.minecraft.network.chat.Component;

/** Static metadata for daily quest types, kept out of runtime progression logic. */
final class DailyQuestCatalog {
    private DailyQuestCatalog() {}

    static Component displayName(DailyQuestService.DailyQuestType quest) {
        String option = switch (quest) {
            case HONEY -> "honey";
            case PET_COLLAR -> "pet";
            case WHEAT_HARVEST -> "wheat";
            case POTATO_HARVEST -> "potato";
            case WOODCUTTING -> "wood";
            case COAL_MINING -> "coal";
            case WOOL_WEAVING -> "wool";
            case RIVER_MEAL -> "river";
            case AUTUMN_HARVEST -> "autumn";
            case SMITH_SMELTING -> "smelt";
            case STALL_NEW_LIFE -> "stall";
            case VILLAGE_TRADING -> "trade";
            case MARKET_ROUNDS -> "market_rounds";
            case ZOMBIE_CULL -> "zombie";
            case SKELETON_PATROL -> "skeleton";
            case SPIDER_SWEEP -> "spider";
            case CREEPER_WATCH -> "creeper";
        };
        return Component.translatable("command.village-quest.setquest.option." + option);
    }

    static DailyQuestService.DailyQuestDifficulty difficulty(DailyQuestService.DailyQuestType type) {
        if (type == null) {
            return DailyQuestService.DailyQuestDifficulty.STANDARD;
        }
        return switch (type) {
            case HONEY, PET_COLLAR, WHEAT_HARVEST, POTATO_HARVEST, STALL_NEW_LIFE ->
                    DailyQuestService.DailyQuestDifficulty.EASY;
            case WOODCUTTING, WOOL_WEAVING, RIVER_MEAL, AUTUMN_HARVEST, MARKET_ROUNDS,
                    ZOMBIE_CULL, SPIDER_SWEEP -> DailyQuestService.DailyQuestDifficulty.STANDARD;
            case COAL_MINING, SMITH_SMELTING, VILLAGE_TRADING, SKELETON_PATROL, CREEPER_WATCH ->
                    DailyQuestService.DailyQuestDifficulty.HARD;
        };
    }

    static String alias(DailyQuestService.DailyQuestType quest) {
        if (quest == null) {
            return "-";
        }
        return switch (quest) {
            case HONEY -> "honey";
            case PET_COLLAR -> "pet";
            case WHEAT_HARVEST -> "bakery";
            case POTATO_HARVEST -> "kitchen";
            case WOODCUTTING -> "workshop";
            case COAL_MINING -> "smith";
            case WOOL_WEAVING -> "wool";
            case RIVER_MEAL -> "river";
            case AUTUMN_HARVEST -> "harvest";
            case SMITH_SMELTING -> "smelt";
            case STALL_NEW_LIFE -> "stall";
            case VILLAGE_TRADING -> "trade";
            case MARKET_ROUNDS -> "market_rounds";
            case ZOMBIE_CULL -> "zombie";
            case SKELETON_PATROL -> "skeleton";
            case SPIDER_SWEEP -> "spider";
            case CREEPER_WATCH -> "creeper";
        };
    }

    static String categoryId(DailyQuestService.DailyQuestCategory category) {
        if (category == null) {
            return "none";
        }
        return category.name().toLowerCase(java.util.Locale.ROOT);
    }

    static String difficultyId(DailyQuestService.DailyQuestDifficulty difficulty) {
        if (difficulty == null) {
            return "none";
        }
        return difficulty.name().toLowerCase(java.util.Locale.ROOT);
    }

    static String adminStateId(DailyQuestService.DailyAdminState state) {
        if (state == null) {
            return "not_generated";
        }
        return state.name().toLowerCase(java.util.Locale.ROOT);
    }

    static RewardProfile rewardProfile(DailyQuestService.DailyQuestType type, RepeatableTargetProfile profile) {
        return switch (difficulty(type)) {
            case EASY -> new RewardProfile(
                    RepeatableRewardTuning.adjustCurrency(CurrencyService.SILVERMARK * 3L, profile),
                    RepeatableRewardTuning.adjustLevels(2, profile));
            case STANDARD -> new RewardProfile(
                    RepeatableRewardTuning.adjustCurrency(CurrencyService.SILVERMARK * 6L, profile),
                    RepeatableRewardTuning.adjustLevels(4, profile));
            case HARD -> new RewardProfile(
                    RepeatableRewardTuning.adjustCurrency(CurrencyService.SILVERMARK * 12L, profile),
                    RepeatableRewardTuning.adjustLevels(6, profile));
        };
    }

    record RewardProfile(long currencyReward, int levels) {}
}
