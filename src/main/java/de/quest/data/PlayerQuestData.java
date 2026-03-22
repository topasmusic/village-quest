package de.quest.data;

import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.special.SpecialQuestKind;
import de.quest.quest.weekly.WeeklyQuestService;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerQuestData {
    public static final long UNSET_DAY = -1L;

    private long currencyBalance;
    private long lastRewardDay = UNSET_DAY;
    private long bonusRewardDay = UNSET_DAY;
    private final Map<String, Integer> dailyIntState = new HashMap<>();
    private final Set<String> dailyFlags = new HashSet<>();
    private final Map<String, Integer> weeklyIntState = new HashMap<>();
    private final Set<String> weeklyFlags = new HashSet<>();
    private final Map<String, Integer> reputationState = new HashMap<>();
    private long progressDay = UNSET_DAY;
    private long acceptedDay = UNSET_DAY;
    private long bonusAcceptedDay = UNSET_DAY;
    private long weeklyProgressCycle = UNSET_DAY;
    private long weeklyAcceptedCycle = UNSET_DAY;
    private long weeklyRewardCycle = UNSET_DAY;
    private DailyQuestService.DailyQuestType dailyChoice;
    private long dailyChoiceDay = UNSET_DAY;
    private DailyQuestService.DailyQuestType bonusChoice;
    private long bonusChoiceDay = UNSET_DAY;
    private WeeklyQuestService.WeeklyQuestType weeklyChoice;
    private long weeklyChoiceCycle = UNSET_DAY;
    private boolean dailyDiscovered;
    private final Set<String> weeklyDiscovered = new HashSet<>();
    private final Set<String> weeklyCompleted = new HashSet<>();
    private boolean pendingDailyOffer;
    private boolean pendingShardOffer;
    private boolean pendingBonusOffer;
    private SpecialQuestKind pendingSpecialOfferKind;
    private boolean questTrackerEnabled;
    private boolean starterShardGranted;
    private ShardRelicQuestStage shardRelicQuestStage = ShardRelicQuestStage.NONE;
    private RelicQuestStage merchantSealQuestStage = RelicQuestStage.NONE;
    private RelicQuestStage shepherdFluteQuestStage = RelicQuestStage.NONE;
    private RelicQuestStage apiaristSmokerQuestStage = RelicQuestStage.NONE;
    private RelicQuestStage surveyorCompassQuestStage = RelicQuestStage.NONE;
    private int shardRelicAmethystProgress;
    private int shardRelicPotionProgress;
    private int shardRelicEnchantProgress;
    private int shardRelicEnderPearlProgress;
    private int shardRelicBlazeRodProgress;
    private int shardRelicEnchantBaseline;
    private int shardRelicEnderPearlBaseline;
    private int shardRelicBlazeRodBaseline;
    private int shardRelicChestX;
    private int shardRelicChestY = Integer.MIN_VALUE;
    private int shardRelicChestZ;
    private int merchantSealTradeProgress;
    private int merchantSealEmeraldProgress;
    private int merchantSealPilgrimPurchaseProgress;
    private long merchantSealLastUseDay = UNSET_DAY;
    private int shepherdFluteBreedProgress;
    private int shepherdFluteShearProgress;
    private int shepherdFluteWoolProgress;
    private int shepherdFluteWoolBaseline;
    private int apiaristSmokerHoneyProgress;
    private int apiaristSmokerCombProgress;
    private long apiaristSmokerLastUseDay = UNSET_DAY;
    private int apiaristSmokerUsesToday;
    private int surveyorCompassRedstoneProgress;
    private int surveyorCompassCraftedProgress;
    private int surveyorCompassPickaxeReadyProgress;
    private int surveyorCompassPickaxeBaseline;
    private int surveyorCompassModeIndex;
    private long surveyorCompassHomeCooldownUntil;

    public long getCurrencyBalance() {
        return currencyBalance;
    }

    public void setCurrencyBalance(long currencyBalance) {
        this.currencyBalance = Math.max(0L, currencyBalance);
    }

    public long getLastRewardDay() {
        return lastRewardDay;
    }

    public void setLastRewardDay(long lastRewardDay) {
        this.lastRewardDay = lastRewardDay;
    }

    public long getBonusRewardDay() {
        return bonusRewardDay;
    }

    public void setBonusRewardDay(long bonusRewardDay) {
        this.bonusRewardDay = bonusRewardDay;
    }

    public int getDailyInt(String key) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        return dailyIntState.getOrDefault(key, 0);
    }

    public void setDailyInt(String key, int value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (value == 0) {
            dailyIntState.remove(key);
        } else {
            dailyIntState.put(key, value);
        }
    }

    public void addDailyInt(String key, int amount) {
        if (amount == 0) {
            return;
        }
        setDailyInt(key, getDailyInt(key) + amount);
    }

    public Map<String, Integer> getDailyIntState() {
        return Collections.unmodifiableMap(dailyIntState);
    }

    public int getWeeklyInt(String key) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        return weeklyIntState.getOrDefault(key, 0);
    }

    public void setWeeklyInt(String key, int value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (value == 0) {
            weeklyIntState.remove(key);
        } else {
            weeklyIntState.put(key, value);
        }
    }

    public void addWeeklyInt(String key, int amount) {
        if (amount == 0) {
            return;
        }
        setWeeklyInt(key, getWeeklyInt(key) + amount);
    }

    public Map<String, Integer> getWeeklyIntState() {
        return Collections.unmodifiableMap(weeklyIntState);
    }

    public boolean hasDailyFlag(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return dailyFlags.contains(key);
    }

    public void setDailyFlag(String key, boolean enabled) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (enabled) {
            dailyFlags.add(key);
        } else {
            dailyFlags.remove(key);
        }
    }

    public Set<String> getDailyFlags() {
        return Collections.unmodifiableSet(dailyFlags);
    }

    public boolean hasWeeklyFlag(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return weeklyFlags.contains(key);
    }

    public void setWeeklyFlag(String key, boolean enabled) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (enabled) {
            weeklyFlags.add(key);
        } else {
            weeklyFlags.remove(key);
        }
    }

    public Set<String> getWeeklyFlags() {
        return Collections.unmodifiableSet(weeklyFlags);
    }

    public int getReputation(String key) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        return reputationState.getOrDefault(key, 0);
    }

    public void setReputation(String key, int value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (value <= 0) {
            reputationState.remove(key);
        } else {
            reputationState.put(key, value);
        }
    }

    public void addReputation(String key, int amount) {
        if (amount == 0) {
            return;
        }
        setReputation(key, getReputation(key) + amount);
    }

    public Map<String, Integer> getReputationState() {
        return Collections.unmodifiableMap(reputationState);
    }

    public void clearDailyProgress() {
        dailyIntState.clear();
        dailyFlags.clear();
    }

    public void clearWeeklyProgress() {
        weeklyIntState.clear();
        weeklyFlags.clear();
    }

    public long getProgressDay() {
        return progressDay;
    }

    public void setProgressDay(long progressDay) {
        this.progressDay = progressDay;
    }

    public long getAcceptedDay() {
        return acceptedDay;
    }

    public void setAcceptedDay(long acceptedDay) {
        this.acceptedDay = acceptedDay;
    }

    public long getBonusAcceptedDay() {
        return bonusAcceptedDay;
    }

    public void setBonusAcceptedDay(long bonusAcceptedDay) {
        this.bonusAcceptedDay = bonusAcceptedDay;
    }

    public long getWeeklyProgressCycle() {
        return weeklyProgressCycle;
    }

    public void setWeeklyProgressCycle(long weeklyProgressCycle) {
        this.weeklyProgressCycle = weeklyProgressCycle;
    }

    public long getWeeklyAcceptedCycle() {
        return weeklyAcceptedCycle;
    }

    public void setWeeklyAcceptedCycle(long weeklyAcceptedCycle) {
        this.weeklyAcceptedCycle = weeklyAcceptedCycle;
    }

    public long getWeeklyRewardCycle() {
        return weeklyRewardCycle;
    }

    public void setWeeklyRewardCycle(long weeklyRewardCycle) {
        this.weeklyRewardCycle = weeklyRewardCycle;
    }

    public DailyQuestService.DailyQuestType getDailyChoice() {
        return dailyChoice;
    }

    public void setDailyChoice(DailyQuestService.DailyQuestType dailyChoice) {
        this.dailyChoice = dailyChoice;
    }

    public long getDailyChoiceDay() {
        return dailyChoiceDay;
    }

    public void setDailyChoiceDay(long dailyChoiceDay) {
        this.dailyChoiceDay = dailyChoiceDay;
    }

    public DailyQuestService.DailyQuestType getBonusChoice() {
        return bonusChoice;
    }

    public void setBonusChoice(DailyQuestService.DailyQuestType bonusChoice) {
        this.bonusChoice = bonusChoice;
    }

    public long getBonusChoiceDay() {
        return bonusChoiceDay;
    }

    public void setBonusChoiceDay(long bonusChoiceDay) {
        this.bonusChoiceDay = bonusChoiceDay;
    }

    public WeeklyQuestService.WeeklyQuestType getWeeklyChoice() {
        return weeklyChoice;
    }

    public void setWeeklyChoice(WeeklyQuestService.WeeklyQuestType weeklyChoice) {
        this.weeklyChoice = weeklyChoice;
    }

    public long getWeeklyChoiceCycle() {
        return weeklyChoiceCycle;
    }

    public void setWeeklyChoiceCycle(long weeklyChoiceCycle) {
        this.weeklyChoiceCycle = weeklyChoiceCycle;
    }

    public boolean isDailyDiscovered() {
        return dailyDiscovered;
    }

    public void setDailyDiscovered(boolean dailyDiscovered) {
        this.dailyDiscovered = dailyDiscovered;
    }

    public void markWeeklyDiscovered(String weeklyId) {
        if (weeklyId != null && !weeklyId.isEmpty()) {
            weeklyDiscovered.add(weeklyId);
        }
    }

    public Set<String> getWeeklyDiscovered() {
        return Collections.unmodifiableSet(weeklyDiscovered);
    }

    public void markWeeklyCompleted(String weeklyId) {
        if (weeklyId != null && !weeklyId.isEmpty()) {
            weeklyCompleted.add(weeklyId);
        }
    }

    public Set<String> getWeeklyCompleted() {
        return Collections.unmodifiableSet(weeklyCompleted);
    }

    public boolean isPendingDailyOffer() {
        return pendingDailyOffer;
    }

    public void setPendingDailyOffer(boolean pendingDailyOffer) {
        this.pendingDailyOffer = pendingDailyOffer;
    }

    public boolean isPendingShardOffer() {
        return pendingShardOffer;
    }

    public void setPendingShardOffer(boolean pendingShardOffer) {
        this.pendingShardOffer = pendingShardOffer;
    }

    public boolean isPendingBonusOffer() {
        return pendingBonusOffer;
    }

    public void setPendingBonusOffer(boolean pendingBonusOffer) {
        this.pendingBonusOffer = pendingBonusOffer;
    }

    public boolean isPendingSpecialOffer() {
        return pendingSpecialOfferKind != null;
    }

    public void setPendingSpecialOffer(boolean pendingSpecialOffer) {
        if (!pendingSpecialOffer) {
            this.pendingSpecialOfferKind = null;
        } else if (this.pendingSpecialOfferKind == null) {
            this.pendingSpecialOfferKind = SpecialQuestKind.SHARD_RELIC;
        }
    }

    public SpecialQuestKind getPendingSpecialOfferKind() {
        return pendingSpecialOfferKind;
    }

    public void setPendingSpecialOfferKind(SpecialQuestKind pendingSpecialOfferKind) {
        this.pendingSpecialOfferKind = pendingSpecialOfferKind;
    }

    public boolean isQuestTrackerEnabled() {
        return questTrackerEnabled;
    }

    public void setQuestTrackerEnabled(boolean questTrackerEnabled) {
        this.questTrackerEnabled = questTrackerEnabled;
    }

    public boolean isStarterShardGranted() {
        return starterShardGranted;
    }

    public void setStarterShardGranted(boolean starterShardGranted) {
        this.starterShardGranted = starterShardGranted;
    }

    public ShardRelicQuestStage getShardRelicQuestStage() {
        return shardRelicQuestStage;
    }

    public void setShardRelicQuestStage(ShardRelicQuestStage shardRelicQuestStage) {
        this.shardRelicQuestStage = shardRelicQuestStage == null ? ShardRelicQuestStage.NONE : shardRelicQuestStage;
    }

    public RelicQuestStage getMerchantSealQuestStage() {
        return merchantSealQuestStage;
    }

    public void setMerchantSealQuestStage(RelicQuestStage merchantSealQuestStage) {
        this.merchantSealQuestStage = merchantSealQuestStage == null ? RelicQuestStage.NONE : merchantSealQuestStage;
    }

    public RelicQuestStage getShepherdFluteQuestStage() {
        return shepherdFluteQuestStage;
    }

    public void setShepherdFluteQuestStage(RelicQuestStage shepherdFluteQuestStage) {
        this.shepherdFluteQuestStage = shepherdFluteQuestStage == null ? RelicQuestStage.NONE : shepherdFluteQuestStage;
    }

    public RelicQuestStage getApiaristSmokerQuestStage() {
        return apiaristSmokerQuestStage;
    }

    public void setApiaristSmokerQuestStage(RelicQuestStage apiaristSmokerQuestStage) {
        this.apiaristSmokerQuestStage = apiaristSmokerQuestStage == null ? RelicQuestStage.NONE : apiaristSmokerQuestStage;
    }

    public RelicQuestStage getSurveyorCompassQuestStage() {
        return surveyorCompassQuestStage;
    }

    public void setSurveyorCompassQuestStage(RelicQuestStage surveyorCompassQuestStage) {
        this.surveyorCompassQuestStage = surveyorCompassQuestStage == null ? RelicQuestStage.NONE : surveyorCompassQuestStage;
    }

    public int getShardRelicAmethystProgress() {
        return shardRelicAmethystProgress;
    }

    public void setShardRelicAmethystProgress(int shardRelicAmethystProgress) {
        this.shardRelicAmethystProgress = Math.max(0, shardRelicAmethystProgress);
    }

    public int getShardRelicPotionProgress() {
        return shardRelicPotionProgress;
    }

    public void setShardRelicPotionProgress(int shardRelicPotionProgress) {
        this.shardRelicPotionProgress = Math.max(0, shardRelicPotionProgress);
    }

    public int getShardRelicEnchantProgress() {
        return shardRelicEnchantProgress;
    }

    public void setShardRelicEnchantProgress(int shardRelicEnchantProgress) {
        this.shardRelicEnchantProgress = Math.max(0, shardRelicEnchantProgress);
    }

    public int getShardRelicEnderPearlProgress() {
        return shardRelicEnderPearlProgress;
    }

    public void setShardRelicEnderPearlProgress(int shardRelicEnderPearlProgress) {
        this.shardRelicEnderPearlProgress = Math.max(0, shardRelicEnderPearlProgress);
    }

    public int getShardRelicBlazeRodProgress() {
        return shardRelicBlazeRodProgress;
    }

    public void setShardRelicBlazeRodProgress(int shardRelicBlazeRodProgress) {
        this.shardRelicBlazeRodProgress = Math.max(0, shardRelicBlazeRodProgress);
    }

    public int getShardRelicEnchantBaseline() {
        return shardRelicEnchantBaseline;
    }

    public void setShardRelicEnchantBaseline(int shardRelicEnchantBaseline) {
        this.shardRelicEnchantBaseline = Math.max(0, shardRelicEnchantBaseline);
    }

    public int getShardRelicEnderPearlBaseline() {
        return shardRelicEnderPearlBaseline;
    }

    public void setShardRelicEnderPearlBaseline(int shardRelicEnderPearlBaseline) {
        this.shardRelicEnderPearlBaseline = Math.max(0, shardRelicEnderPearlBaseline);
    }

    public int getShardRelicBlazeRodBaseline() {
        return shardRelicBlazeRodBaseline;
    }

    public void setShardRelicBlazeRodBaseline(int shardRelicBlazeRodBaseline) {
        this.shardRelicBlazeRodBaseline = Math.max(0, shardRelicBlazeRodBaseline);
    }

    public int getShardRelicChestX() {
        return shardRelicChestX;
    }

    public void setShardRelicChestX(int shardRelicChestX) {
        this.shardRelicChestX = shardRelicChestX;
    }

    public int getShardRelicChestY() {
        return shardRelicChestY;
    }

    public void setShardRelicChestY(int shardRelicChestY) {
        this.shardRelicChestY = shardRelicChestY;
    }

    public int getShardRelicChestZ() {
        return shardRelicChestZ;
    }

    public void setShardRelicChestZ(int shardRelicChestZ) {
        this.shardRelicChestZ = shardRelicChestZ;
    }

    public int getMerchantSealTradeProgress() {
        return merchantSealTradeProgress;
    }

    public void setMerchantSealTradeProgress(int merchantSealTradeProgress) {
        this.merchantSealTradeProgress = Math.max(0, merchantSealTradeProgress);
    }

    public int getMerchantSealEmeraldProgress() {
        return merchantSealEmeraldProgress;
    }

    public void setMerchantSealEmeraldProgress(int merchantSealEmeraldProgress) {
        this.merchantSealEmeraldProgress = Math.max(0, merchantSealEmeraldProgress);
    }

    public int getMerchantSealPilgrimPurchaseProgress() {
        return merchantSealPilgrimPurchaseProgress;
    }

    public void setMerchantSealPilgrimPurchaseProgress(int merchantSealPilgrimPurchaseProgress) {
        this.merchantSealPilgrimPurchaseProgress = Math.max(0, merchantSealPilgrimPurchaseProgress);
    }

    public long getMerchantSealLastUseDay() {
        return merchantSealLastUseDay;
    }

    public void setMerchantSealLastUseDay(long merchantSealLastUseDay) {
        this.merchantSealLastUseDay = merchantSealLastUseDay;
    }

    public int getShepherdFluteBreedProgress() {
        return shepherdFluteBreedProgress;
    }

    public void setShepherdFluteBreedProgress(int shepherdFluteBreedProgress) {
        this.shepherdFluteBreedProgress = Math.max(0, shepherdFluteBreedProgress);
    }

    public int getShepherdFluteShearProgress() {
        return shepherdFluteShearProgress;
    }

    public void setShepherdFluteShearProgress(int shepherdFluteShearProgress) {
        this.shepherdFluteShearProgress = Math.max(0, shepherdFluteShearProgress);
    }

    public int getShepherdFluteWoolProgress() {
        return shepherdFluteWoolProgress;
    }

    public void setShepherdFluteWoolProgress(int shepherdFluteWoolProgress) {
        this.shepherdFluteWoolProgress = Math.max(0, shepherdFluteWoolProgress);
    }

    public int getShepherdFluteWoolBaseline() {
        return shepherdFluteWoolBaseline;
    }

    public void setShepherdFluteWoolBaseline(int shepherdFluteWoolBaseline) {
        this.shepherdFluteWoolBaseline = Math.max(0, shepherdFluteWoolBaseline);
    }

    public int getApiaristSmokerHoneyProgress() {
        return apiaristSmokerHoneyProgress;
    }

    public void setApiaristSmokerHoneyProgress(int apiaristSmokerHoneyProgress) {
        this.apiaristSmokerHoneyProgress = Math.max(0, apiaristSmokerHoneyProgress);
    }

    public int getApiaristSmokerCombProgress() {
        return apiaristSmokerCombProgress;
    }

    public void setApiaristSmokerCombProgress(int apiaristSmokerCombProgress) {
        this.apiaristSmokerCombProgress = Math.max(0, apiaristSmokerCombProgress);
    }

    public long getApiaristSmokerLastUseDay() {
        return apiaristSmokerLastUseDay;
    }

    public void setApiaristSmokerLastUseDay(long apiaristSmokerLastUseDay) {
        this.apiaristSmokerLastUseDay = apiaristSmokerLastUseDay;
    }

    public int getApiaristSmokerUsesToday() {
        return apiaristSmokerUsesToday;
    }

    public void setApiaristSmokerUsesToday(int apiaristSmokerUsesToday) {
        this.apiaristSmokerUsesToday = Math.max(0, apiaristSmokerUsesToday);
    }

    public int getSurveyorCompassRedstoneProgress() {
        return surveyorCompassRedstoneProgress;
    }

    public void setSurveyorCompassRedstoneProgress(int surveyorCompassRedstoneProgress) {
        this.surveyorCompassRedstoneProgress = Math.max(0, surveyorCompassRedstoneProgress);
    }

    public int getSurveyorCompassCraftedProgress() {
        return surveyorCompassCraftedProgress;
    }

    public void setSurveyorCompassCraftedProgress(int surveyorCompassCraftedProgress) {
        this.surveyorCompassCraftedProgress = Math.max(0, surveyorCompassCraftedProgress);
    }

    public int getSurveyorCompassPickaxeReadyProgress() {
        return surveyorCompassPickaxeReadyProgress;
    }

    public void setSurveyorCompassPickaxeReadyProgress(int surveyorCompassPickaxeReadyProgress) {
        this.surveyorCompassPickaxeReadyProgress = Math.max(0, surveyorCompassPickaxeReadyProgress);
    }

    public int getSurveyorCompassPickaxeBaseline() {
        return surveyorCompassPickaxeBaseline;
    }

    public void setSurveyorCompassPickaxeBaseline(int surveyorCompassPickaxeBaseline) {
        this.surveyorCompassPickaxeBaseline = Math.max(0, surveyorCompassPickaxeBaseline);
    }

    public int getSurveyorCompassModeIndex() {
        return surveyorCompassModeIndex;
    }

    public void setSurveyorCompassModeIndex(int surveyorCompassModeIndex) {
        this.surveyorCompassModeIndex = Math.max(0, surveyorCompassModeIndex);
    }

    public long getSurveyorCompassHomeCooldownUntil() {
        return surveyorCompassHomeCooldownUntil;
    }

    public void setSurveyorCompassHomeCooldownUntil(long surveyorCompassHomeCooldownUntil) {
        this.surveyorCompassHomeCooldownUntil = Math.max(0L, surveyorCompassHomeCooldownUntil);
    }

    public void resetShardRelicQuest() {
        if (this.pendingSpecialOfferKind == SpecialQuestKind.SHARD_RELIC) {
            this.pendingSpecialOfferKind = null;
        }
        this.shardRelicQuestStage = ShardRelicQuestStage.NONE;
        this.shardRelicAmethystProgress = 0;
        this.shardRelicPotionProgress = 0;
        this.shardRelicEnchantProgress = 0;
        this.shardRelicEnderPearlProgress = 0;
        this.shardRelicBlazeRodProgress = 0;
        this.shardRelicEnchantBaseline = 0;
        this.shardRelicEnderPearlBaseline = 0;
        this.shardRelicBlazeRodBaseline = 0;
        this.shardRelicChestX = 0;
        this.shardRelicChestY = Integer.MIN_VALUE;
        this.shardRelicChestZ = 0;
    }

    public void resetMerchantSealQuest() {
        if (this.pendingSpecialOfferKind == SpecialQuestKind.MERCHANT_SEAL) {
            this.pendingSpecialOfferKind = null;
        }
        this.merchantSealQuestStage = RelicQuestStage.NONE;
        this.merchantSealTradeProgress = 0;
        this.merchantSealEmeraldProgress = 0;
        this.merchantSealPilgrimPurchaseProgress = 0;
    }

    public void resetShepherdFluteQuest() {
        if (this.pendingSpecialOfferKind == SpecialQuestKind.SHEPHERD_FLUTE) {
            this.pendingSpecialOfferKind = null;
        }
        this.shepherdFluteQuestStage = RelicQuestStage.NONE;
        this.shepherdFluteBreedProgress = 0;
        this.shepherdFluteShearProgress = 0;
        this.shepherdFluteWoolProgress = 0;
        this.shepherdFluteWoolBaseline = 0;
    }

    public void resetApiaristSmokerQuest() {
        if (this.pendingSpecialOfferKind == SpecialQuestKind.APIARIST_SMOKER) {
            this.pendingSpecialOfferKind = null;
        }
        this.apiaristSmokerQuestStage = RelicQuestStage.NONE;
        this.apiaristSmokerHoneyProgress = 0;
        this.apiaristSmokerCombProgress = 0;
        this.apiaristSmokerLastUseDay = UNSET_DAY;
        this.apiaristSmokerUsesToday = 0;
    }

    public void resetSurveyorCompassQuest() {
        if (this.pendingSpecialOfferKind == SpecialQuestKind.SURVEYOR_COMPASS) {
            this.pendingSpecialOfferKind = null;
        }
        this.surveyorCompassQuestStage = RelicQuestStage.NONE;
        this.surveyorCompassRedstoneProgress = 0;
        this.surveyorCompassCraftedProgress = 0;
        this.surveyorCompassPickaxeReadyProgress = 0;
        this.surveyorCompassPickaxeBaseline = 0;
        this.surveyorCompassModeIndex = 0;
        this.surveyorCompassHomeCooldownUntil = 0L;
    }

    public boolean hasShardRelicQuestData() {
        return this.pendingSpecialOfferKind == SpecialQuestKind.SHARD_RELIC
                || this.shardRelicQuestStage != ShardRelicQuestStage.NONE
                || this.shardRelicAmethystProgress > 0
                || this.shardRelicPotionProgress > 0
                || this.shardRelicEnchantProgress > 0
                || this.shardRelicEnderPearlProgress > 0
                || this.shardRelicBlazeRodProgress > 0
                || this.shardRelicEnchantBaseline > 0
                || this.shardRelicEnderPearlBaseline > 0
                || this.shardRelicBlazeRodBaseline > 0
                || this.shardRelicChestY != Integer.MIN_VALUE;
    }

    public boolean hasMerchantSealQuestData() {
        return this.pendingSpecialOfferKind == SpecialQuestKind.MERCHANT_SEAL
                || this.merchantSealQuestStage != RelicQuestStage.NONE
                || this.merchantSealTradeProgress > 0
                || this.merchantSealEmeraldProgress > 0
                || this.merchantSealPilgrimPurchaseProgress > 0
                || this.merchantSealLastUseDay != UNSET_DAY;
    }

    public boolean hasShepherdFluteQuestData() {
        return this.pendingSpecialOfferKind == SpecialQuestKind.SHEPHERD_FLUTE
                || this.shepherdFluteQuestStage != RelicQuestStage.NONE
                || this.shepherdFluteBreedProgress > 0
                || this.shepherdFluteShearProgress > 0
                || this.shepherdFluteWoolProgress > 0
                || this.shepherdFluteWoolBaseline > 0;
    }

    public boolean hasApiaristSmokerQuestData() {
        return this.pendingSpecialOfferKind == SpecialQuestKind.APIARIST_SMOKER
                || this.apiaristSmokerQuestStage != RelicQuestStage.NONE
                || this.apiaristSmokerHoneyProgress > 0
                || this.apiaristSmokerCombProgress > 0
                || this.apiaristSmokerLastUseDay != UNSET_DAY
                || this.apiaristSmokerUsesToday > 0;
    }

    public boolean hasSurveyorCompassQuestData() {
        return this.pendingSpecialOfferKind == SpecialQuestKind.SURVEYOR_COMPASS
                || this.surveyorCompassQuestStage != RelicQuestStage.NONE
                || this.surveyorCompassRedstoneProgress > 0
                || this.surveyorCompassCraftedProgress > 0
                || this.surveyorCompassPickaxeReadyProgress > 0
                || this.surveyorCompassPickaxeBaseline > 0
                || this.surveyorCompassModeIndex > 0
                || this.surveyorCompassHomeCooldownUntil > 0L;
    }
}
