package de.quest.data;

import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.special.SpecialQuestKind;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.weekly.WeeklyQuestService;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class QuestState extends PersistentState {
    private static final String ID = "village_quest_state";
    public static final PersistentStateType<QuestState> TYPE =
            new PersistentStateType<>(ID, QuestState::new, NbtCompound.CODEC.xmap(QuestState::fromNbt, QuestState::toNbt), DataFixTypes.LEVEL);

    private final Map<UUID, PlayerQuestData> players = new ConcurrentHashMap<>();
    private long pilgrimNaturalSpawnCooldownUntil;

    private QuestState() {}

    private static QuestState fromNbt(NbtCompound nbt) {
        QuestState state = new QuestState();
        if (nbt != null) {
            state.readFromNbt(nbt);
        }
        return state;
    }

    private static NbtCompound toNbt(QuestState state) {
        NbtCompound root = new NbtCompound();
        root.put("questManager", state.writeDailyQuestData());
        return root;
    }

    public static QuestState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public PlayerQuestData getPlayerData(UUID playerId) {
        if (playerId == null) {
            return new PlayerQuestData();
        }
        return players.computeIfAbsent(playerId, id -> new PlayerQuestData());
    }

    public Map<UUID, PlayerQuestData> getPlayersView() {
        return Collections.unmodifiableMap(players);
    }

    public void updateFromRuntime() {
        markDirty();
    }

    public void resetAllProgress() {
        players.clear();
        pilgrimNaturalSpawnCooldownUntil = 0L;
        markDirty();
    }

    public void applyToRuntime() {
    }

    private void readFromNbt(NbtCompound root) {
        players.clear();
        if (root == null || root.isEmpty()) {
            return;
        }
        NbtCompound daily = root.getCompoundOrEmpty("questManager");
        readDailyQuestData(daily);
    }

    private void readDailyQuestData(NbtCompound root) {
        this.pilgrimNaturalSpawnCooldownUntil = Math.max(0L, root.getLong("pilgrimNaturalSpawnCooldownUntil", 0L));
        readUuidLongMap(root, "currencyBalance", (id, value) -> getPlayerData(id).setCurrencyBalance(value));
        readUuidLongMap(root, "lastRewardDay", (id, value) -> getPlayerData(id).setLastRewardDay(value));
        readUuidLongMap(root, "bonusRewardDay", (id, value) -> getPlayerData(id).setBonusRewardDay(value));
        readUuidLongMap(root, "progressDay", (id, value) -> getPlayerData(id).setProgressDay(value));
        readUuidLongMap(root, "acceptedDay", (id, value) -> getPlayerData(id).setAcceptedDay(value));
        readUuidLongMap(root, "bonusAcceptedDay", (id, value) -> getPlayerData(id).setBonusAcceptedDay(value));
        readUuidLongMap(root, "questMasterSummonBlockedUntil", (id, value) -> getPlayerData(id).setQuestMasterSummonBlockedUntil(value));
        readUuidLongMap(root, "weeklyProgressCycle", (id, value) -> getPlayerData(id).setWeeklyProgressCycle(value));
        readUuidLongMap(root, "weeklyAcceptedCycle", (id, value) -> getPlayerData(id).setWeeklyAcceptedCycle(value));
        readUuidLongMap(root, "weeklyRewardCycle", (id, value) -> getPlayerData(id).setWeeklyRewardCycle(value));
        readUuidQuestMap(root, "dailyChoice", (id, value) -> getPlayerData(id).setDailyChoice(value));
        readUuidLongMap(root, "dailyChoiceDay", (id, value) -> getPlayerData(id).setDailyChoiceDay(value));
        readUuidQuestMap(root, "bonusChoice", (id, value) -> getPlayerData(id).setBonusChoice(value));
        readUuidLongMap(root, "bonusChoiceDay", (id, value) -> getPlayerData(id).setBonusChoiceDay(value));
        readUuidWeeklyQuestMap(root, "weeklyChoice", (id, value) -> getPlayerData(id).setWeeklyChoice(value));
        readUuidLongMap(root, "weeklyChoiceCycle", (id, value) -> getPlayerData(id).setWeeklyChoiceCycle(value));
        readUuidSet(root, "dailyDiscovered", id -> getPlayerData(id).setDailyDiscovered(true));
        readUuidNamedSet(root, "weeklyDiscoveredEntries", (id, weeklyId) -> getPlayerData(id).markWeeklyDiscovered(weeklyId));
        readUuidNamedSet(root, "weeklyCompletedEntries", (id, weeklyId) -> getPlayerData(id).markWeeklyCompleted(weeklyId));
        readUuidNamedSet(root, "storyDiscoveredEntries", (id, storyId) -> getPlayerData(id).setStoryDiscovered(storyId, true));
        readUuidNamedSet(root, "storyCompletedEntries", (id, storyId) -> getPlayerData(id).setStoryCompleted(storyId, true));
        readUuidNamedSet(root, "storyUnlockedProjects", (id, projectId) -> getPlayerData(id).setUnlockedProject(projectId, true));
        readUuidNamedSet(root, "milestoneFlags", (id, flag) -> getPlayerData(id).setMilestoneFlag(flag, true));
        readUuidSet(root, "pendingDailyOffer", id -> getPlayerData(id).setPendingDailyOffer(true));
        readUuidSet(root, "pendingShardOffer", id -> getPlayerData(id).setPendingShardOffer(true));
        readUuidSet(root, "pendingBonusOffer", id -> getPlayerData(id).setPendingBonusOffer(true));
        readUuidSet(root, "questTrackerEnabled", id -> getPlayerData(id).setQuestTrackerEnabled(true));
        readUuidSet(root, "starterShardGranted", id -> getPlayerData(id).setStarterShardGranted(true));
        readUuidStringMap(root, "pendingSpecialOfferType", (id, value) -> getPlayerData(id).setPendingSpecialOfferKind(SpecialQuestKind.fromId(value)));
        readUuidStringMap(root, "activeStoryArc", (id, value) -> getPlayerData(id).setActiveStoryArc(StoryArcType.fromId(value)));
        readUuidLongMap(root, "storyCooldownUntil", (id, value) -> getPlayerData(id).setStoryCooldownUntil(value));
        readUuidStringMap(root, "pilgrimActiveContract", (id, value) -> getPlayerData(id).setActivePilgrimContractId(value));
        readUuidStringMap(root, "pilgrimOfferedContract", (id, value) -> getPlayerData(id).setOfferedPilgrimContractId(value));
        readUuidStringMap(root, "pilgrimOfferedContractAlt", (id, value) -> getPlayerData(id).setOfferedPilgrimContractAltId(value));
        readUuidLongMap(root, "pilgrimOfferDay", (id, value) -> getPlayerData(id).setPilgrimOfferDay(value));
        readUuidNamedIntMap(root, "reputation", (id, stateKey, value) -> getPlayerData(id).setReputation(stateKey, value));
        readUuidNamedIntMap(root, "storyProgressInts", (id, stateKey, value) -> getPlayerData(id).setStoryInt(stateKey, value));
        readUuidNamedSet(root, "storyProgressFlags", (id, stateKey) -> getPlayerData(id).setStoryFlag(stateKey, true));
        readUuidNamedIntMap(root, "pilgrimProgressInts", (id, stateKey, value) -> getPlayerData(id).setPilgrimInt(stateKey, value));
        readUuidNamedSet(root, "pilgrimProgressFlags", (id, stateKey) -> getPlayerData(id).setPilgrimFlag(stateKey, true));
        readUuidNamedIntMap(root, "storyChapterProgress", (id, storyId, value) -> getPlayerData(id).setStoryChapterProgress(storyId, value));
        readSpecialShardQuestData(root);
        readSpecialMerchantSealQuestData(root);
        readSpecialShepherdFluteQuestData(root);
        readSpecialApiaristSmokerQuestData(root);
        readSpecialSurveyorCompassQuestData(root);

        // Legacy progress data for worlds saved before the generic quest-state refactor.
        readUuidIntMap(root, "honeyProgress", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.HONEY_PROGRESS, value));
        readUuidIntMap(root, "combProgress", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.COMB_PROGRESS, value));
        readUuidIntMap(root, "expectedHoney", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.EXPECTED_HONEY, value));
        readUuidIntMap(root, "expectedComb", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.EXPECTED_COMB, value));
        readUuidIntMap(root, "lastHoneyCount", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.LAST_HONEY_COUNT, value));
        readUuidIntMap(root, "lastCombCount", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.LAST_COMB_COUNT, value));
        readUuidIntMap(root, "expectedExpireTicks", (id, value) -> getPlayerData(id).setDailyInt(DailyQuestKeys.EXPECTED_EXPIRE_TICKS, value));
        readUuidBoolMap(root, "collarDone", (id, value) -> getPlayerData(id).setDailyFlag(DailyQuestKeys.PET_COLLAR_DONE, value));

        readUuidNamedIntMap(root, "dailyProgressInts", (id, stateKey, value) -> getPlayerData(id).setDailyInt(stateKey, value));
        readUuidNamedSet(root, "dailyProgressFlags", (id, stateKey) -> getPlayerData(id).setDailyFlag(stateKey, true));
        readUuidNamedIntMap(root, "weeklyProgressInts", (id, stateKey, value) -> getPlayerData(id).setWeeklyInt(stateKey, value));
        readUuidNamedSet(root, "weeklyProgressFlags", (id, stateKey) -> getPlayerData(id).setWeeklyFlag(stateKey, true));
    }

    private NbtCompound writeDailyQuestData() {
        NbtCompound root = new NbtCompound();
        root.putLong("pilgrimNaturalSpawnCooldownUntil", this.pilgrimNaturalSpawnCooldownUntil);
        NbtList currencyBalance = new NbtList();
        NbtList lastRewardDay = new NbtList();
        NbtList bonusRewardDay = new NbtList();
        NbtList progressDay = new NbtList();
        NbtList dailyProgressInts = new NbtList();
        NbtList dailyProgressFlags = new NbtList();
        NbtList acceptedDay = new NbtList();
        NbtList bonusAcceptedDay = new NbtList();
        NbtList questMasterSummonBlockedUntil = new NbtList();
        NbtList weeklyProgressCycle = new NbtList();
        NbtList weeklyAcceptedCycle = new NbtList();
        NbtList weeklyRewardCycle = new NbtList();
        NbtList dailyChoice = new NbtList();
        NbtList dailyChoiceDay = new NbtList();
        NbtList bonusChoice = new NbtList();
        NbtList bonusChoiceDay = new NbtList();
        NbtList weeklyChoice = new NbtList();
        NbtList weeklyChoiceCycle = new NbtList();
        NbtList dailyDiscovered = new NbtList();
        NbtList weeklyDiscoveredEntries = new NbtList();
        NbtList weeklyCompletedEntries = new NbtList();
        NbtList storyDiscoveredEntries = new NbtList();
        NbtList storyCompletedEntries = new NbtList();
        NbtList storyUnlockedProjects = new NbtList();
        NbtList milestoneFlags = new NbtList();
        NbtList pendingDailyOffer = new NbtList();
        NbtList pendingShardOffer = new NbtList();
        NbtList pendingBonusOffer = new NbtList();
        NbtList questTrackerEnabled = new NbtList();
        NbtList starterShardGranted = new NbtList();
        NbtList pendingSpecialOfferType = new NbtList();
        NbtList activeStoryArc = new NbtList();
        NbtList storyCooldownUntil = new NbtList();
        NbtList pilgrimActiveContract = new NbtList();
        NbtList pilgrimOfferedContract = new NbtList();
        NbtList pilgrimOfferedContractAlt = new NbtList();
        NbtList pilgrimOfferDay = new NbtList();
        NbtList reputation = new NbtList();
        NbtList storyProgressInts = new NbtList();
        NbtList storyProgressFlags = new NbtList();
        NbtList pilgrimProgressInts = new NbtList();
        NbtList pilgrimProgressFlags = new NbtList();
        NbtList storyChapterProgress = new NbtList();
        NbtList specialShardQuest = new NbtList();
        NbtList specialMerchantSealQuest = new NbtList();
        NbtList specialShepherdFluteQuest = new NbtList();
        NbtList specialApiaristSmokerQuest = new NbtList();
        NbtList specialSurveyorCompassQuest = new NbtList();
        NbtList weeklyProgressInts = new NbtList();
        NbtList weeklyProgressFlags = new NbtList();

        for (var entry : players.entrySet()) {
            UUID id = entry.getKey();
            PlayerQuestData data = entry.getValue();
            if (id == null || data == null) {
                continue;
            }
            if (data.getCurrencyBalance() > 0L) {
                currencyBalance.add(entryLong(id, data.getCurrencyBalance()));
            }
            if (data.getLastRewardDay() != PlayerQuestData.UNSET_DAY) {
                lastRewardDay.add(entryLong(id, data.getLastRewardDay()));
            }
            if (data.getBonusRewardDay() != PlayerQuestData.UNSET_DAY) {
                bonusRewardDay.add(entryLong(id, data.getBonusRewardDay()));
            }
            if (data.getProgressDay() != PlayerQuestData.UNSET_DAY) {
                progressDay.add(entryLong(id, data.getProgressDay()));
            }
            for (var stateEntry : data.getDailyIntState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() != 0) {
                    dailyProgressInts.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (String stateKey : data.getDailyFlags()) {
                dailyProgressFlags.add(entryNamedKey(id, stateKey));
            }
            for (var stateEntry : data.getWeeklyIntState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() != 0) {
                    weeklyProgressInts.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (String stateKey : data.getWeeklyFlags()) {
                weeklyProgressFlags.add(entryNamedKey(id, stateKey));
            }
            if (data.getAcceptedDay() != PlayerQuestData.UNSET_DAY) {
                acceptedDay.add(entryLong(id, data.getAcceptedDay()));
            }
            if (data.getBonusAcceptedDay() != PlayerQuestData.UNSET_DAY) {
                bonusAcceptedDay.add(entryLong(id, data.getBonusAcceptedDay()));
            }
            if (data.getQuestMasterSummonBlockedUntil() > 0L) {
                questMasterSummonBlockedUntil.add(entryLong(id, data.getQuestMasterSummonBlockedUntil()));
            }
            if (data.getWeeklyProgressCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyProgressCycle.add(entryLong(id, data.getWeeklyProgressCycle()));
            }
            if (data.getWeeklyAcceptedCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyAcceptedCycle.add(entryLong(id, data.getWeeklyAcceptedCycle()));
            }
            if (data.getWeeklyRewardCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyRewardCycle.add(entryLong(id, data.getWeeklyRewardCycle()));
            }
            if (data.getDailyChoice() != null) {
                dailyChoice.add(entryString(id, data.getDailyChoice().name()));
            }
            if (data.getDailyChoiceDay() != PlayerQuestData.UNSET_DAY) {
                dailyChoiceDay.add(entryLong(id, data.getDailyChoiceDay()));
            }
            if (data.getBonusChoice() != null) {
                bonusChoice.add(entryString(id, data.getBonusChoice().name()));
            }
            if (data.getBonusChoiceDay() != PlayerQuestData.UNSET_DAY) {
                bonusChoiceDay.add(entryLong(id, data.getBonusChoiceDay()));
            }
            if (data.getWeeklyChoice() != null) {
                weeklyChoice.add(entryString(id, data.getWeeklyChoice().name()));
            }
            if (data.getWeeklyChoiceCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyChoiceCycle.add(entryLong(id, data.getWeeklyChoiceCycle()));
            }
            if (data.isDailyDiscovered()) {
                dailyDiscovered.add(entryId(id));
            }
            for (String weeklyId : data.getWeeklyDiscovered()) {
                weeklyDiscoveredEntries.add(entryNamedKey(id, weeklyId));
            }
            for (String weeklyId : data.getWeeklyCompleted()) {
                weeklyCompletedEntries.add(entryNamedKey(id, weeklyId));
            }
            for (String storyId : data.getStoryDiscovered()) {
                storyDiscoveredEntries.add(entryNamedKey(id, storyId));
            }
            for (String storyId : data.getStoryCompleted()) {
                storyCompletedEntries.add(entryNamedKey(id, storyId));
            }
            for (String projectId : data.getUnlockedProjects()) {
                storyUnlockedProjects.add(entryNamedKey(id, projectId));
            }
            for (String flag : data.getMilestoneFlags()) {
                milestoneFlags.add(entryNamedKey(id, flag));
            }
            if (data.isPendingDailyOffer()) {
                pendingDailyOffer.add(entryId(id));
            }
            if (data.isPendingShardOffer()) {
                pendingShardOffer.add(entryId(id));
            }
            if (data.isPendingBonusOffer()) {
                pendingBonusOffer.add(entryId(id));
            }
            if (data.isQuestTrackerEnabled()) {
                questTrackerEnabled.add(entryId(id));
            }
            if (data.isStarterShardGranted()) {
                starterShardGranted.add(entryId(id));
            }
            if (data.getPendingSpecialOfferKind() != null) {
                pendingSpecialOfferType.add(entryString(id, data.getPendingSpecialOfferKind().id()));
            }
            if (data.getActiveStoryArc() != null) {
                activeStoryArc.add(entryString(id, data.getActiveStoryArc().id()));
            }
            if (data.getStoryCooldownUntil() > 0L) {
                storyCooldownUntil.add(entryLong(id, data.getStoryCooldownUntil()));
            }
            if (data.getActivePilgrimContractId() != null) {
                pilgrimActiveContract.add(entryString(id, data.getActivePilgrimContractId()));
            }
            if (data.getOfferedPilgrimContractId() != null) {
                pilgrimOfferedContract.add(entryString(id, data.getOfferedPilgrimContractId()));
            }
            if (data.getOfferedPilgrimContractAltId() != null) {
                pilgrimOfferedContractAlt.add(entryString(id, data.getOfferedPilgrimContractAltId()));
            }
            if (data.getPilgrimOfferDay() != PlayerQuestData.UNSET_DAY) {
                pilgrimOfferDay.add(entryLong(id, data.getPilgrimOfferDay()));
            }
            for (var reputationEntry : data.getReputationState().entrySet()) {
                if (reputationEntry.getValue() != null && reputationEntry.getValue() > 0) {
                    reputation.add(entryNamedInt(id, reputationEntry.getKey(), reputationEntry.getValue()));
                }
            }
            for (var stateEntry : data.getStoryIntState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() != 0) {
                    storyProgressInts.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (String stateKey : data.getStoryFlags()) {
                storyProgressFlags.add(entryNamedKey(id, stateKey));
            }
            for (var stateEntry : data.getPilgrimIntState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() != 0) {
                    pilgrimProgressInts.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (String stateKey : data.getPilgrimFlags()) {
                pilgrimProgressFlags.add(entryNamedKey(id, stateKey));
            }
            for (var stateEntry : data.getStoryChapterProgressState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() > 0) {
                    storyChapterProgress.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            if (data.hasShardRelicQuestData()) {
                specialShardQuest.add(entrySpecialShardQuest(id, data));
            }
            if (data.hasMerchantSealQuestData()) {
                specialMerchantSealQuest.add(entrySpecialMerchantSealQuest(id, data));
            }
            if (data.hasShepherdFluteQuestData()) {
                specialShepherdFluteQuest.add(entrySpecialShepherdFluteQuest(id, data));
            }
            if (data.hasApiaristSmokerQuestData()) {
                specialApiaristSmokerQuest.add(entrySpecialApiaristSmokerQuest(id, data));
            }
            if (data.hasSurveyorCompassQuestData()) {
                specialSurveyorCompassQuest.add(entrySpecialSurveyorCompassQuest(id, data));
            }
        }

        root.put("currencyBalance", currencyBalance);
        root.put("lastRewardDay", lastRewardDay);
        root.put("bonusRewardDay", bonusRewardDay);
        root.put("progressDay", progressDay);
        root.put("dailyProgressInts", dailyProgressInts);
        root.put("dailyProgressFlags", dailyProgressFlags);
        root.put("weeklyProgressInts", weeklyProgressInts);
        root.put("weeklyProgressFlags", weeklyProgressFlags);
        root.put("acceptedDay", acceptedDay);
        root.put("bonusAcceptedDay", bonusAcceptedDay);
        root.put("questMasterSummonBlockedUntil", questMasterSummonBlockedUntil);
        root.put("weeklyProgressCycle", weeklyProgressCycle);
        root.put("weeklyAcceptedCycle", weeklyAcceptedCycle);
        root.put("weeklyRewardCycle", weeklyRewardCycle);
        root.put("dailyChoice", dailyChoice);
        root.put("dailyChoiceDay", dailyChoiceDay);
        root.put("bonusChoice", bonusChoice);
        root.put("bonusChoiceDay", bonusChoiceDay);
        root.put("weeklyChoice", weeklyChoice);
        root.put("weeklyChoiceCycle", weeklyChoiceCycle);
        root.put("dailyDiscovered", dailyDiscovered);
        root.put("weeklyDiscoveredEntries", weeklyDiscoveredEntries);
        root.put("weeklyCompletedEntries", weeklyCompletedEntries);
        root.put("storyDiscoveredEntries", storyDiscoveredEntries);
        root.put("storyCompletedEntries", storyCompletedEntries);
        root.put("storyUnlockedProjects", storyUnlockedProjects);
        root.put("milestoneFlags", milestoneFlags);
        root.put("pendingDailyOffer", pendingDailyOffer);
        root.put("pendingShardOffer", pendingShardOffer);
        root.put("pendingBonusOffer", pendingBonusOffer);
        root.put("questTrackerEnabled", questTrackerEnabled);
        root.put("starterShardGranted", starterShardGranted);
        root.put("pendingSpecialOfferType", pendingSpecialOfferType);
        root.put("activeStoryArc", activeStoryArc);
        root.put("storyCooldownUntil", storyCooldownUntil);
        root.put("pilgrimActiveContract", pilgrimActiveContract);
        root.put("pilgrimOfferedContract", pilgrimOfferedContract);
        root.put("pilgrimOfferedContractAlt", pilgrimOfferedContractAlt);
        root.put("pilgrimOfferDay", pilgrimOfferDay);
        root.put("reputation", reputation);
        root.put("storyProgressInts", storyProgressInts);
        root.put("storyProgressFlags", storyProgressFlags);
        root.put("pilgrimProgressInts", pilgrimProgressInts);
        root.put("pilgrimProgressFlags", pilgrimProgressFlags);
        root.put("storyChapterProgress", storyChapterProgress);
        root.put("specialShardQuest", specialShardQuest);
        root.put("specialMerchantSealQuest", specialMerchantSealQuest);
        root.put("specialShepherdFluteQuest", specialShepherdFluteQuest);
        root.put("specialApiaristSmokerQuest", specialApiaristSmokerQuest);
        root.put("specialSurveyorCompassQuest", specialSurveyorCompassQuest);
        return root;
    }

    public long getPilgrimNaturalSpawnCooldownUntil() {
        return this.pilgrimNaturalSpawnCooldownUntil;
    }

    public void setPilgrimNaturalSpawnCooldownUntil(long pilgrimNaturalSpawnCooldownUntil) {
        long clamped = Math.max(0L, pilgrimNaturalSpawnCooldownUntil);
        if (this.pilgrimNaturalSpawnCooldownUntil == clamped) {
            return;
        }
        this.pilgrimNaturalSpawnCooldownUntil = clamped;
        markDirty();
    }

    private static void readUuidLongMap(NbtCompound root, String key, BiConsumer<UUID, Long> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id != null) {
                consumer.accept(id, item.getLong("v", 0L));
            }
        }
    }

    private static void readUuidIntMap(NbtCompound root, String key, BiConsumer<UUID, Integer> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id != null) {
                consumer.accept(id, item.getInt("v", 0));
            }
        }
    }

    private static void readUuidBoolMap(NbtCompound root, String key, BiConsumer<UUID, Boolean> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id != null) {
                consumer.accept(id, item.getBoolean("v", false));
            }
        }
    }

    private static void readUuidNamedIntMap(NbtCompound root, String key, NamedIntConsumer consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            String stateKey = item.getString("key", "");
            if (id != null && !stateKey.isEmpty()) {
                consumer.accept(id, stateKey, item.getInt("v", 0));
            }
        }
    }

    private static void readUuidStringMap(NbtCompound root, String key, BiConsumer<UUID, String> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            String value = item.getString("v", "");
            if (id != null && !value.isEmpty()) {
                consumer.accept(id, value);
            }
        }
    }

    private static void readUuidNamedSet(NbtCompound root, String key, NamedKeyConsumer consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            String stateKey = item.getString("key", "");
            if (id != null && !stateKey.isEmpty()) {
                consumer.accept(id, stateKey);
            }
        }
    }

    private static void readUuidQuestMap(NbtCompound root, String key, BiConsumer<UUID, DailyQuestService.DailyQuestType> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            String value = item.getString("v", "");
            try {
                consumer.accept(id, DailyQuestService.DailyQuestType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                // ignore invalid entries
            }
        }
    }

    private static void readUuidWeeklyQuestMap(NbtCompound root, String key, BiConsumer<UUID, WeeklyQuestService.WeeklyQuestType> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            String value = item.getString("v", "");
            try {
                consumer.accept(id, WeeklyQuestService.WeeklyQuestType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                // ignore invalid entries
            }
        }
    }

    private static void readUuidSet(NbtCompound root, String key, java.util.function.Consumer<UUID> consumer) {
        NbtList list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id != null) {
                consumer.accept(id);
            }
        }
    }

    private static NbtCompound entryLong(UUID id, long value) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putLong("v", value);
        return item;
    }

    private static NbtCompound entryInt(UUID id, int value) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putInt("v", value);
        return item;
    }

    private static NbtCompound entryBool(UUID id, boolean value) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putBoolean("v", value);
        return item;
    }

    private static NbtCompound entryNamedInt(UUID id, String key, int value) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("key", key);
        item.putInt("v", value);
        return item;
    }

    private static NbtCompound entryNamedKey(UUID id, String key) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("key", key);
        return item;
    }

    private static NbtCompound entryString(UUID id, String value) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("v", value);
        return item;
    }

    private static NbtCompound entryId(UUID id) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        return item;
    }

    private void readSpecialShardQuestData(NbtCompound root) {
        NbtList list = root.getListOrEmpty("specialShardQuest");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            if (item.getBoolean("pendingOffer", false) && data.getPendingSpecialOfferKind() == null) {
                data.setPendingSpecialOfferKind(SpecialQuestKind.SHARD_RELIC);
            }
            try {
                data.setShardRelicQuestStage(ShardRelicQuestStage.valueOf(item.getString("stage", ShardRelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setShardRelicQuestStage(ShardRelicQuestStage.NONE);
            }
            data.setShardRelicAmethystProgress(item.getInt("amethyst", 0));
            data.setShardRelicPotionProgress(item.getInt("potions", 0));
            data.setShardRelicEnchantProgress(item.getInt("enchants", 0));
            data.setShardRelicEnderPearlProgress(item.getInt("enderPearls", 0));
            data.setShardRelicBlazeRodProgress(item.getInt("blazeRods", 0));
            data.setShardRelicEnchantBaseline(item.getInt("enchantBase", 0));
            data.setShardRelicEnderPearlBaseline(item.getInt("enderPearlBase", 0));
            data.setShardRelicBlazeRodBaseline(item.getInt("blazeRodBase", 0));
            data.setShardRelicChestX(item.getInt("chestX", 0));
            data.setShardRelicChestY(item.getInt("chestY", Integer.MIN_VALUE));
            data.setShardRelicChestZ(item.getInt("chestZ", 0));
        }
    }

    private static NbtCompound entrySpecialShardQuest(UUID id, PlayerQuestData data) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putBoolean("pendingOffer", data.getPendingSpecialOfferKind() == SpecialQuestKind.SHARD_RELIC);
        item.putString("stage", data.getShardRelicQuestStage().name());
        item.putInt("amethyst", data.getShardRelicAmethystProgress());
        item.putInt("potions", data.getShardRelicPotionProgress());
        item.putInt("enchants", data.getShardRelicEnchantProgress());
        item.putInt("enderPearls", data.getShardRelicEnderPearlProgress());
        item.putInt("blazeRods", data.getShardRelicBlazeRodProgress());
        item.putInt("enchantBase", data.getShardRelicEnchantBaseline());
        item.putInt("enderPearlBase", data.getShardRelicEnderPearlBaseline());
        item.putInt("blazeRodBase", data.getShardRelicBlazeRodBaseline());
        item.putInt("chestX", data.getShardRelicChestX());
        item.putInt("chestY", data.getShardRelicChestY());
        item.putInt("chestZ", data.getShardRelicChestZ());
        return item;
    }

    private void readSpecialMerchantSealQuestData(NbtCompound root) {
        NbtList list = root.getListOrEmpty("specialMerchantSealQuest");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setMerchantSealQuestStage(RelicQuestStage.valueOf(item.getString("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setMerchantSealQuestStage(RelicQuestStage.NONE);
            }
            data.setMerchantSealTradeProgress(item.getInt("trades", 0));
            data.setMerchantSealEmeraldProgress(item.getInt("emeralds", 0));
            data.setMerchantSealVillagerPurchaseProgress(item.getInt("villagerPurchases", 0));
            data.setMerchantSealPilgrimPurchaseProgress(item.getInt("pilgrimPurchases", 0));
            data.setMerchantSealLastUseDay(item.getLong("lastUseDay", PlayerQuestData.UNSET_DAY));
        }
    }

    private static NbtCompound entrySpecialMerchantSealQuest(UUID id, PlayerQuestData data) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("stage", data.getMerchantSealQuestStage().name());
        item.putInt("trades", data.getMerchantSealTradeProgress());
        item.putInt("emeralds", data.getMerchantSealEmeraldProgress());
        item.putInt("villagerPurchases", data.getMerchantSealVillagerPurchaseProgress());
        item.putInt("pilgrimPurchases", data.getMerchantSealPilgrimPurchaseProgress());
        item.putLong("lastUseDay", data.getMerchantSealLastUseDay());
        return item;
    }

    private void readSpecialShepherdFluteQuestData(NbtCompound root) {
        NbtList list = root.getListOrEmpty("specialShepherdFluteQuest");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setShepherdFluteQuestStage(RelicQuestStage.valueOf(item.getString("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setShepherdFluteQuestStage(RelicQuestStage.NONE);
            }
            data.setShepherdFluteBreedProgress(item.getInt("breed", 0));
            data.setShepherdFluteShearProgress(item.getInt("shear", 0));
            data.setShepherdFluteWoolProgress(item.getInt("wool", 0));
            data.setShepherdFluteWoolBaseline(item.getInt("woolBaseline", 0));
        }
    }

    private static NbtCompound entrySpecialShepherdFluteQuest(UUID id, PlayerQuestData data) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("stage", data.getShepherdFluteQuestStage().name());
        item.putInt("breed", data.getShepherdFluteBreedProgress());
        item.putInt("shear", data.getShepherdFluteShearProgress());
        item.putInt("wool", data.getShepherdFluteWoolProgress());
        item.putInt("woolBaseline", data.getShepherdFluteWoolBaseline());
        return item;
    }

    private void readSpecialApiaristSmokerQuestData(NbtCompound root) {
        NbtList list = root.getListOrEmpty("specialApiaristSmokerQuest");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setApiaristSmokerQuestStage(RelicQuestStage.valueOf(item.getString("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setApiaristSmokerQuestStage(RelicQuestStage.NONE);
            }
            data.setApiaristSmokerHoneyProgress(item.getInt("honey", 0));
            data.setApiaristSmokerCombProgress(item.getInt("comb", 0));
            data.setApiaristSmokerBeeBreedProgress(item.getInt("beeBreed", 0));
            data.setApiaristSmokerHoneyBlockProgress(item.getInt("honeyBlock", 0));
            data.setApiaristSmokerHoneyBlockBaseline(item.getInt("honeyBlockBaseline", 0));
            data.setApiaristSmokerLastUseDay(item.getLong("lastUseDay", PlayerQuestData.UNSET_DAY));
            data.setApiaristSmokerUsesToday(item.getInt("usesToday", 0));
        }
    }

    private static NbtCompound entrySpecialApiaristSmokerQuest(UUID id, PlayerQuestData data) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("stage", data.getApiaristSmokerQuestStage().name());
        item.putInt("honey", data.getApiaristSmokerHoneyProgress());
        item.putInt("comb", data.getApiaristSmokerCombProgress());
        item.putInt("beeBreed", data.getApiaristSmokerBeeBreedProgress());
        item.putInt("honeyBlock", data.getApiaristSmokerHoneyBlockProgress());
        item.putInt("honeyBlockBaseline", data.getApiaristSmokerHoneyBlockBaseline());
        item.putLong("lastUseDay", data.getApiaristSmokerLastUseDay());
        item.putInt("usesToday", data.getApiaristSmokerUsesToday());
        return item;
    }

    private void readSpecialSurveyorCompassQuestData(NbtCompound root) {
        NbtList list = root.getListOrEmpty("specialSurveyorCompassQuest");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getString("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setSurveyorCompassQuestStage(RelicQuestStage.valueOf(item.getString("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setSurveyorCompassQuestStage(RelicQuestStage.NONE);
            }
            data.setSurveyorCompassRedstoneProgress(item.getInt("redstone", 0));
            data.setSurveyorCompassLapisProgress(item.getInt("lapis", 0));
            data.setSurveyorCompassCraftedProgress(item.getInt("crafted", 0));
            data.setSurveyorCompassPickaxeReadyProgress(item.getInt("pickaxeReady", 0));
            data.setSurveyorCompassPickaxeBaseline(item.getInt("pickaxeBaseline", 0));
            data.setSurveyorCompassModeIndex(item.getInt("modeIndex", 0));
            data.setSurveyorCompassHomeCooldownUntil(item.getLong("homeCooldownUntil", 0L));
            data.setSurveyorCompassHomeConfirmUntil(item.getLong("homeConfirmUntil", 0L));
        }
    }

    private static NbtCompound entrySpecialSurveyorCompassQuest(UUID id, PlayerQuestData data) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id.toString());
        item.putString("stage", data.getSurveyorCompassQuestStage().name());
        item.putInt("redstone", data.getSurveyorCompassRedstoneProgress());
        item.putInt("lapis", data.getSurveyorCompassLapisProgress());
        item.putInt("crafted", data.getSurveyorCompassCraftedProgress());
        item.putInt("pickaxeReady", data.getSurveyorCompassPickaxeReadyProgress());
        item.putInt("pickaxeBaseline", data.getSurveyorCompassPickaxeBaseline());
        item.putInt("modeIndex", data.getSurveyorCompassModeIndex());
        item.putLong("homeCooldownUntil", data.getSurveyorCompassHomeCooldownUntil());
        item.putLong("homeConfirmUntil", data.getSurveyorCompassHomeConfirmUntil());
        return item;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface NamedIntConsumer {
        void accept(UUID id, String key, int value);
    }

    @FunctionalInterface
    private interface NamedKeyConsumer {
        void accept(UUID id, String key);
    }
}
