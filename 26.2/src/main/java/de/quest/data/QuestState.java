package de.quest.data;

import de.quest.guildtown.GuildTownProgress;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.special.SpecialQuestKind;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.weekly.WeeklyQuestService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class QuestState extends SavedData {
    private static final String ID = "village_quest_state";
    /** Former .6 FIFO boundary, retained for migration/boundary regression coverage. */
    static final int MAX_MODIFIED_TERRAIN_CHUNKS = 32_768;
    public static final SavedDataType<QuestState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(ID), QuestState::new, CompoundTag.CODEC.xmap(QuestState::fromNbt, QuestState::toNbt), DataFixTypes.LEVEL);

    private CompoundTag preservedStoryLaneManager;
    public boolean storyLanesWritable() { return preservedStoryLaneManager == null && preservedPartyManager == null
            && de.quest.party.QuestPartyPersistence.storyLanesWritable(questPartyState); }

    private final Map<UUID, PlayerQuestData> players = new ConcurrentHashMap<>();
    /** Exact, durable 32x32-chunk region bitmaps; old safety marks are never evicted. */
    private final Map<Long, long[]> modifiedTerrainRegions = Collections.synchronizedMap(new HashMap<>());
    private long pilgrimNaturalSpawnCooldownUntil;
    private CompoundTag questPartyState = new CompoundTag();
    private net.minecraft.nbt.Tag preservedPartyManager;
    // Keep unknown containers and owner records byte-for-byte in NBT; absence alone is new state.
    private net.minecraft.nbt.Tag villageIntroTransactions = new CompoundTag();
    private net.minecraft.nbt.Tag personalResetTransactions = new CompoundTag();
    private final java.util.Set<UUID> resetUnlocked = new java.util.HashSet<>();
    boolean personalResetUnlocked(UUID actor) { return resetUnlocked.contains(actor); }
    boolean confirmPersonalResetTerminal(UUID actor,CompoundTag expected) {
        if(!java.util.Objects.equals(personalResetTransaction(actor),expected) || !expected.getBooleanOr("acknowledged",false)
                || !java.util.Set.of("COMMITTED","CANCELLED").contains(expected.getStringOr("stage","")))return false;
        resetUnlocked.add(actor);return true;
    }
    // Runtime permission only. After reload, reset must explicitly recover and verify the player document first.
    private final java.util.Set<UUID> introResetUnlocked = new java.util.HashSet<>();
    private boolean introAllowsRestart(UUID actor) { return introTransaction(actor) == null || introResetUnlocked.contains(actor); }
    public boolean confirmIntroTerminal(UUID actor, CompoundTag expected) {
        if (!java.util.Objects.equals(introTransaction(actor), expected)
                || !expected.getBooleanOr("acknowledged", false)
                || !java.util.Set.of("COMMITTED", "CANCELLED").contains(expected.getStringOr("stage", ""))) return false;
        introResetUnlocked.add(actor); return true;
    }
    private final Map<UUID, ListTag> preservedIntroNumbers = new HashMap<>();

    private static boolean introNumber(String key) {
        return key.equals(PlayerQuestData.PROGRESS_GENERATION) || key.startsWith("village_intro.") || key.startsWith("intro_credits.");
    }

    /** The legacy generic map reader coerces/drops wrong number types and zero values.
     * New intro/generation records instead retain affected owners' exact numeric rows and fail closed. */
    private void preserveMalformedIntroNumbers(CompoundTag root) {
        var rows = root.getListOrEmpty("tradeRouteInts");
        var seen = new HashMap<UUID, java.util.Set<String>>();
        var invalid = new java.util.HashSet<UUID>();
        for (var tag : rows) {
            if (!(tag instanceof CompoundTag row)) continue;
            var owner = parseUuid(row.getStringOr("id", "")); var key = row.getStringOr("key", "");
            if (owner == null || !introNumber(key)) continue;
            if (!seen.computeIfAbsent(owner, ignored -> new java.util.HashSet<>()).add(key)
                    || !(row.get("v") instanceof net.minecraft.nbt.IntTag)
                    || key.equals(PlayerQuestData.PROGRESS_GENERATION) && row.getIntOr("v", 0) <= 0) invalid.add(owner);
        }
        for (var owner : invalid) {
            var preserved = new ListTag();
            for (var tag : rows) if (tag instanceof CompoundTag row
                    && owner.equals(parseUuid(row.getStringOr("id", ""))) && introNumber(row.getStringOr("key", ""))) preserved.add(row.copy());
            preservedIntroNumbers.put(owner, preserved);
            getPlayerData(owner).preserveInvalidIntroProgress();
        }
    }

    public net.minecraft.nbt.Tag introTransaction(UUID actor) {
        if (!(villageIntroTransactions instanceof CompoundTag owners)) return villageIntroTransactions.copy();
        var entry = owners.get(actor.toString());
        return entry == null ? null : entry.copy();
    }

    net.minecraft.nbt.Tag personalResetTransaction(UUID actor) {
        if(!(personalResetTransactions instanceof CompoundTag owners))return personalResetTransactions.copy();
        var entry=owners.get(actor.toString());return entry==null?null:entry.copy();
    }
    boolean comparePersonalResetTransaction(UUID actor,net.minecraft.nbt.Tag expected,CompoundTag next) {
        if(actor==null || next==null || !(personalResetTransactions instanceof CompoundTag owners)
                || !java.util.Objects.equals(owners.get(actor.toString()),expected)
                || expected!=null && (!(expected instanceof CompoundTag old) || !resetHeader(actor,old))
                || !resetHeader(actor,next))return false;
        resetUnlocked.remove(actor);owners.put(actor.toString(),next.copy());setDirty();return true;
    }
    private static boolean resetHeader(UUID actor,CompoundTag tag) {
        return tag.get("schema") instanceof net.minecraft.nbt.IntTag && tag.getIntOr("schema",0)==1
                && actor.toString().equals(tag.getStringOr("actor",""));
    }
    /** Personal postimage and its PERSONAL stage are published together in this same saved document. */
    boolean commitPersonalReset(UUID actor,CompoundTag before,CompoundTag after,CompoundTag expectedJournal,CompoundTag nextJournal) {
        if(!(personalResetTransactions instanceof CompoundTag owners) || expectedJournal==null || nextJournal==null
                || !java.util.Objects.equals(owners.get(actor.toString()),expectedJournal) || !resetHeader(actor,expectedJournal)
                || !expectedJournal.getCompoundOrEmpty("before").equals(before) || !expectedJournal.getCompoundOrEmpty("after").equals(after)
                || !expectedJournal.getStringOr("stage","").equals("BEFORE"))return false;
        var required=expectedJournal.copy();required.remove("acknowledged");required.putString("stage","PERSONAL");
        if(!required.equals(nextJournal) || !publishPersonalReset(actor,before,after))return false;
        owners.put(actor.toString(),nextJournal.copy());setDirty();return true;
    }

    /** Compare-and-set prevents overwriting a foreign/unknown pending claim. No deletion API. */
    public boolean compareIntroTransaction(UUID actor, net.minecraft.nbt.Tag expected, CompoundTag next) {
        if (actor == null || next == null || !(villageIntroTransactions instanceof CompoundTag owners)
                || !java.util.Objects.equals(owners.get(actor.toString()), expected)) return false;
        introResetUnlocked.remove(actor);
        owners.put(actor.toString(), next.copy()); setDirty(); return true;
    }

    /** Detached native serialization copy, used to prepare all intro effects before publishing them. */
    public PlayerQuestData copyPersonalProgress(UUID actor) {
        return fromNbt(toNbt(this)).getPlayerData(actor);
    }

    /** Server-thread publication of one fully prepared player/effect-status image; no callbacks. */
    public boolean commitIntroEffects(UUID actor, PlayerQuestData expectedPlayer, net.minecraft.nbt.Tag expectedJournal,
                                     PlayerQuestData nextPlayer, CompoundTag nextJournal) {
        if (actor == null || nextPlayer == null || nextJournal == null || players.get(actor) != expectedPlayer
                || !(villageIntroTransactions instanceof CompoundTag owners)
                || !java.util.Objects.equals(owners.get(actor.toString()), expectedJournal)) return false;
        owners.put(actor.toString(), nextJournal.copy());
        introResetUnlocked.remove(actor);
        players.put(actor, nextPlayer); setDirty(); return true;
    }
    private long socialServerTick;
    private de.quest.quest.special.WeatherTonicState weatherTonic=new de.quest.quest.special.WeatherTonicState();
    public de.quest.quest.special.WeatherTonicState weatherTonic() { return weatherTonic; }
    private CompoundTag worldQuestSettings = new CompoundTag();
    public de.quest.config.WorldQuestSettings worldQuestSettings() {
        return de.quest.config.WorldQuestSettings.fromNbt(worldQuestSettings).orElse(null);
    }
    public boolean unknownWorldQuestSettings() { return !worldQuestSettings.isEmpty() && worldQuestSettings() == null; }
    public void setWorldQuestSettings(de.quest.config.WorldQuestSettings settings) {
        if (unknownWorldQuestSettings()) throw new IllegalStateException("Unsupported world settings are preserved");
        worldQuestSettings = settings.toNbt(); setDirty();
    }
    private de.quest.util.QuestCalendar questCalendar = new de.quest.util.QuestCalendar();
    public de.quest.util.QuestCalendar questCalendar() { return questCalendar; }
    private net.minecraft.nbt.Tag preservedSocialRecords;
    private net.minecraft.nbt.Tag preservedRewards;
    private CompoundTag orphanRewards = new CompoundTag();
    private net.minecraft.nbt.Tag preservedMarketTransfers;
    private CompoundTag orphanMarketTransfers=new CompoundTag();
    private net.minecraft.nbt.Tag preservedProtectedVillages;
    private de.quest.reputation.ProtectedVillageIndex protectedVillages = new de.quest.reputation.ProtectedVillageIndex();
    public de.quest.reputation.ProtectedVillageIndex protectedVillages() { return protectedVillages; }

    public long socialServerTick() { return socialServerTick; }

    public void advanceSocialServerTick() {
        if (socialServerTick < Long.MAX_VALUE) socialServerTick++;
        if (socialServerTick % 20 == 0) setDirty();
    }

    private QuestState() {}

    static QuestState fromNbt(CompoundTag nbt) {
        QuestState state = new QuestState();
        if (nbt != null) {
            state.readFromNbt(nbt);
            for(var player:state.players.values())if(player.restoreRetiredNpcTest())state.setDirty();
        }
        return state;
    }

    static CompoundTag toNbt(QuestState state) {
        CompoundTag root = new CompoundTag();
        root.put("questManager", state.writeDailyQuestData());
        root.put("questPartyManager", state.preservedPartyManager == null ? state.questPartyState : state.preservedPartyManager.copy());
        root.put("villageIntroTransactions", state.villageIntroTransactions.copy());
        root.put("personalResetTransactions",state.personalResetTransactions.copy());
        CompoundTag rewards = state.orphanRewards.copy();
        state.players.forEach((id, data) -> { if (!state.orphanRewards.contains(id.toString())) rewards.put(id.toString(), data.vanillaRewards().toNbt()); });
        root.put("vanillaRewards", state.preservedRewards == null ? rewards : state.preservedRewards.copy());
        var marketTransfers=state.orphanMarketTransfers.copy();
        state.players.forEach((id,data) -> { if(!state.orphanMarketTransfers.contains(id.toString())) marketTransfers.put(id.toString(),data.marketWallet().toNbt()); });
        root.put("marketTransfers",state.preservedMarketTransfers==null?marketTransfers:state.preservedMarketTransfers.copy());
        root.putLong("socialServerTick", state.socialServerTick);
        root.put("friendsWeatherTonic",state.weatherTonic.toNbt());
        root.put("questCalendar", state.questCalendar.toNbt());
        if (!state.worldQuestSettings.isEmpty()) root.put("worldQuestSettings26", state.worldQuestSettings.copy());
        root.put("protectedVillages", state.preservedProtectedVillages == null ? state.protectedVillages.toNbt() : state.preservedProtectedVillages.copy());
        return root;
    }

    public static QuestState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerQuestData getPlayerData(UUID playerId) {
        if (playerId == null) {
            return new PlayerQuestData();
        }
        var personal = players.computeIfAbsent(playerId, id -> {
            var data = new PlayerQuestData();
            if (preservedIntroNumbers.containsKey(id)) data.preserveInvalidIntroProgress();
            if(preservedMarketTransfers!=null || orphanMarketTransfers.contains(id.toString())) {
                var quarantine=new CompoundTag(); quarantine.putInt("schema",-1); data.loadMarketWallet(quarantine);
            }
            if (preservedSocialRecords != null) {
                CompoundTag quarantine = new CompoundTag(); quarantine.putInt("schema", -1);
                data.loadSocialReputation(quarantine);
            }
            if (preservedRewards != null || orphanRewards.contains(id.toString())) {
                CompoundTag quarantine = new CompoundTag(); quarantine.putInt("schema", -1); data.loadVanillaRewards(quarantine);
            }
            return data;
        });
        if (!storyLanesWritable()) personal.preserveUnknownStoryLanes();
        personal.bindIntroRestartGuard(() -> introAllowsRestart(playerId));
        return personal;
    }

    public Map<UUID, PlayerQuestData> getPlayersView() {
        return Collections.unmodifiableMap(players);
    }

    public void updateFromRuntime() {
        setDirty();
    }

    public void replacePersonalProgress(UUID playerId, PlayerQuestData fresh) {
        if (!storyLanesWritable()) throw new IllegalStateException("Unknown story lanes are preserved");
        if (playerId == null || fresh == null) throw new IllegalArgumentException("Missing personal reset state");
        if (preservedIntroNumbers.containsKey(playerId)) throw new IllegalStateException("Unknown intro progress is preserved");
        if (!introAllowsRestart(playerId)) throw new IllegalStateException("Intro delivery is not acknowledged");
        players.put(playerId, fresh); setDirty();
    }

    /** Actor-only native serialization. It contains no party/world snapshot and no other player's data. */
    CompoundTag personalResetImage(UUID actor) {
        var data=actor==null?null:players.get(actor);
        if(data==null)throw new IllegalStateException("Missing reset owner");
        return personalResetImage(actor,data);
    }
    static CompoundTag personalResetImage(UUID actor,PlayerQuestData data) {
        if(actor==null || data==null)throw new IllegalArgumentException("Missing reset owner");
        var isolated=new QuestState();isolated.players.put(actor,data);return toNbt(isolated);
    }
    /** Publish exactly the prevalidated next generation, without restoring any shared or foreign document. */
    boolean publishPersonalReset(UUID actor,CompoundTag before,CompoundTag after) {
        if(actor==null || before==null || after==null || !players.containsKey(actor)
                || !storyLanesWritable() || !personalResetImage(actor).equals(before))return false;
        try {
            var isolated=fromNbt(before);
            if(!isolated.getPlayersView().keySet().equals(java.util.Set.of(actor)) || !toNbt(isolated).equals(before))return false;
            var fresh=isolated.getPlayerData(actor).personalRestart();
            if(!personalResetImage(actor,fresh).equals(after))return false;
            players.put(actor,fresh);setDirty();return true;
        } catch(RuntimeException invalid) { return false; }
    }

    public void restartPersonalProgress(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("A personal restart requires an owner");
        players.put(playerId, getPlayerData(playerId).personalRestart());
        setDirty();
    }

    public void resetAllProgress() {
        if (!storyLanesWritable()) throw new IllegalStateException("Unknown story lanes are preserved");
        if (!(villageIntroTransactions instanceof CompoundTag owners)
                || owners.keySet().stream().map(QuestState::parseUuid).anyMatch(owner -> owner == null || !introAllowsRestart(owner)))
            throw new IllegalStateException("Intro deliveries must be recovered before an administrative reset");
        Map<UUID, CompoundTag> rewards = new HashMap<>();
        Map<UUID,net.minecraft.nbt.Tag> marketTransfers=new HashMap<>();
        players.forEach((id,data) -> marketTransfers.put(id,data.marketWallet().toNbt()));
        players.forEach((id, data) -> { data.vanillaRewards().resetSlots(); rewards.put(id, data.vanillaRewards().toNbt()); });
        players.clear();
        rewards.forEach((id, tag) -> getPlayerData(id).loadVanillaRewards(tag));
        marketTransfers.forEach((id,tag) -> getPlayerData(id).loadMarketWallet(tag));
        modifiedTerrainRegions.clear();
        pilgrimNaturalSpawnCooldownUntil = 0L;
        questPartyState = new CompoundTag();
        socialServerTick = 0;
        preservedSocialRecords = null;
        preservedProtectedVillages = null;
        protectedVillages = new de.quest.reputation.ProtectedVillageIndex();
        setDirty();
    }

    public void applyToRuntime() {
    }

    private void readFromNbt(CompoundTag root) {
        var resets=root.get("personalResetTransactions");
        personalResetTransactions=resets==null?new CompoundTag():resets.copy();
        var intro = root.get("villageIntroTransactions");
        villageIntroTransactions = intro == null ? new CompoundTag() : intro.copy();
        weatherTonic=de.quest.quest.special.WeatherTonicState.fromNbt(root.get("friendsWeatherTonic"));
        worldQuestSettings = root == null ? new CompoundTag() : root.getCompoundOrEmpty("worldQuestSettings26").copy();
        questCalendar = de.quest.util.QuestCalendar.fromNbt(root == null ? new CompoundTag() : root.getCompoundOrEmpty("questCalendar"));
        players.clear();
        preservedMarketTransfers=null; orphanMarketTransfers=new CompoundTag();
        preservedSocialRecords = null;
        preservedProtectedVillages = null;
        socialServerTick = Math.max(0, root == null ? 0 : root.getLongOr("socialServerTick", 0));
        var rawIndex = root == null ? null : root.get("protectedVillages");
        if (rawIndex != null && !(rawIndex instanceof CompoundTag)) preservedProtectedVillages = rawIndex.copy();
        protectedVillages = de.quest.reputation.ProtectedVillageIndex.fromNbt(rawIndex instanceof CompoundTag compound ? compound : new CompoundTag());
        modifiedTerrainRegions.clear();
        questPartyState = new CompoundTag();
        if (root == null || root.isEmpty()) {
            return;
        }
        CompoundTag daily = root.getCompoundOrEmpty("questManager");
        readDailyQuestData(daily);
        var rawMarketTransfers=root.get("marketTransfers");
        if(rawMarketTransfers!=null && !(rawMarketTransfers instanceof CompoundTag)) {
            preservedMarketTransfers=rawMarketTransfers.copy();
            players.values().forEach(data -> { var quarantine=new CompoundTag(); quarantine.putInt("schema",-1); data.loadMarketWallet(quarantine); });
        } else if(rawMarketTransfers instanceof CompoundTag owners) {
            for(String key:owners.keySet()) {
                var raw=owners.get(key);
                try {
                    UUID owner=UUID.fromString(key);
                    if(owner.equals(de.quest.kingdom.BuildingSessionPolicy.NONE)) throw new IllegalArgumentException("Invalid market owner");
                    getPlayerData(owner).loadMarketWallet(raw);
                    if(getPlayerData(owner).marketWallet().marker().filter(m -> !m.actor().equals(owner)).isPresent()) {
                        orphanMarketTransfers.put(key,raw.copy()); var quarantine=new CompoundTag(); quarantine.putInt("schema",-1); getPlayerData(owner).loadMarketWallet(quarantine);
                    }
                } catch(IllegalArgumentException invalid) { orphanMarketTransfers.put(key,raw.copy()); }
            }
        }
        var rawRewards = root.get("vanillaRewards");
        preservedRewards = rawRewards != null && !(rawRewards instanceof CompoundTag) ? rawRewards.copy() : null;
        orphanRewards = new CompoundTag();
        CompoundTag rewards = root.getCompoundOrEmpty("vanillaRewards");
        for (String owner : rewards.keySet()) {
            try {
                UUID id = UUID.fromString(owner);
                if (rewards.get(owner) instanceof CompoundTag reward) getPlayerData(id).loadVanillaRewards(reward);
                else { orphanRewards.put(owner,rewards.get(owner).copy()); CompoundTag quarantine = new CompoundTag(); quarantine.putInt("schema",-1); getPlayerData(id).loadVanillaRewards(quarantine); }
            } catch (IllegalArgumentException malformed) { orphanRewards.put(owner,rewards.get(owner).copy()); }
        }
        if (preservedRewards != null) players.values().forEach(data -> { CompoundTag quarantine = new CompoundTag(); quarantine.putInt("schema",-1); data.loadVanillaRewards(quarantine); });
        var rawParty=root.get("questPartyManager");
        preservedPartyManager=rawParty!=null && !(rawParty instanceof CompoundTag) ? rawParty.copy() : null;
        questPartyState = root.getCompoundOrEmpty("questPartyManager");
        if (preservedSocialRecords != null) de.quest.VillageQuest.LOGGER.warn("Social reputation container is malformed; preserved read-only. Other quest progress remains available.");
        else players.forEach((id, data) -> { if (!data.socialReputation().writable()) de.quest.VillageQuest.LOGGER.warn("Social reputation for {} has an unknown or damaged schema; preserved read-only.", id); });
        if (preservedProtectedVillages != null) de.quest.VillageQuest.LOGGER.warn("Protected village index has an unexpected type; original data preserved.");
    }

    public CompoundTag getQuestPartyState() {
        return questPartyState;
    }

    public void setQuestPartyState(CompoundTag questPartyState) {
        if (preservedPartyManager != null || !de.quest.party.QuestPartyPersistence.storyLanesWritable(this.questPartyState))
            throw new IllegalStateException("Unknown party story lanes are preserved");
        this.questPartyState = questPartyState == null ? new CompoundTag() : questPartyState;
        setDirty();
    }

    private static boolean validKingdomStoryOwners(net.minecraft.nbt.Tag raw) {
        if (!(raw instanceof ListTag rows)) return false;
        var owners = new java.util.HashSet<UUID>();
        for (var entry : rows) {
            if (!(entry instanceof CompoundTag row) || !row.keySet().equals(java.util.Set.of("id"))) return false;
            String name = row.getStringOr("id", "");
            UUID owner = parseUuid(name);
            if (owner == null || !owner.toString().equals(name) || !owners.add(owner)) return false;
        }
        return true;
    }

    private void readDailyQuestData(CompoundTag root) {
        // The shared generic maps contain lane-owned counters and receipts. A future format
        // cannot safely be partially rewritten: retain its complete manager and freeze actions.
        boolean knownFormat = !root.contains("storyLaneFormat")
                || root.get("storyLaneFormat") instanceof net.minecraft.nbt.IntTag
                    && root.getIntOr("storyLaneFormat", -1) == 1;
        boolean validActive = !root.contains("storyLaneFormat") && !root.contains("kingdomStoryActive")
                || validKingdomStoryOwners(root.get("kingdomStoryActive"));
        preservedStoryLaneManager = knownFormat && validActive ? null : root.copy();
        if (root.contains("socialReputation")) {
            var raw = root.get("socialReputation"); boolean valid = raw instanceof ListTag;
            java.util.Set<UUID> owners = new java.util.HashSet<>();
            if (raw instanceof ListTag entries) for (var entry : entries) {
                if (!(entry instanceof CompoundTag record) || !(record.get("data") instanceof CompoundTag)) { valid = false; break; }
                try { if (!owners.add(UUID.fromString(record.getStringOr("uuid", "")))) valid = false; }
                catch (IllegalArgumentException malformed) { valid = false; }
            }
            if (!valid) preservedSocialRecords = raw.copy();
        }
        this.pilgrimNaturalSpawnCooldownUntil = Math.max(0L, root.getLongOr("pilgrimNaturalSpawnCooldownUntil", 0L));
        ListTag modifiedRegions = root.getListOrEmpty("modifiedTerrainRegions");
        for (int i = 0; i < modifiedRegions.size(); i++) {
            CompoundTag item = modifiedRegions.getCompoundOrEmpty(i);
            if (!item.contains("x") || !item.contains("z")) continue;
            int regionX = item.getIntOr("x", 0);
            int regionZ = item.getIntOr("z", 0);
            long[] loadedMasks = new long[16];
            boolean nonEmpty = false;
            for (int word = 0; word < loadedMasks.length; word++) {
                loadedMasks[word] = item.getLongOr("m" + word, 0L);
                nonEmpty |= loadedMasks[word] != 0L;
            }
            if (!nonEmpty) continue;
            long[] masks = modifiedTerrainRegions.computeIfAbsent(packChunk(regionX, regionZ), ignored -> new long[16]);
            for (int word = 0; word < masks.length; word++) masks[word] |= loadedMasks[word];
        }
        // Legacy .6 and earlier saves stored one compound per chunk. Reading remains lossless;
        // the next save compacts them into region bitmaps.
        ListTag modifiedChunks = root.getListOrEmpty("modifiedTerrainChunks");
        for (int i = 0; i < modifiedChunks.size(); i++) {
            CompoundTag item = modifiedChunks.getCompoundOrEmpty(i);
            if (item.contains("chunk")) addModifiedTerrainChunk(item.getLongOr("chunk", 0L));
        }
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
        readUuidLongMap(root, "dailyOfferNoticeDay", (id, value) -> getPlayerData(id).setDailyOfferNoticeDay(value));
        readUuidLongMap(root, "weeklyOfferNoticeCycle", (id, value) -> getPlayerData(id).setWeeklyOfferNoticeCycle(value));
        readUuidStringMap(root, "storyOfferNoticeKey", (id, value) -> getPlayerData(id).setStoryOfferNoticeKey(value));
        readUuidQuestMap(root, "dailyChoice", (id, value) -> getPlayerData(id).setDailyChoice(value));
        readUuidLongMap(root, "dailyChoiceDay", (id, value) -> getPlayerData(id).setDailyChoiceDay(value));
        readUuidIntMap(root, "dailyTargetProfile", (id, value) -> getPlayerData(id).setDailyTargetProfile(RepeatableTargetProfile.byId(value)));
        readUuidQuestMap(root, "bonusChoice", (id, value) -> getPlayerData(id).setBonusChoice(value));
        readUuidLongMap(root, "bonusChoiceDay", (id, value) -> getPlayerData(id).setBonusChoiceDay(value));
        readUuidIntMap(root, "bonusTargetProfile", (id, value) -> getPlayerData(id).setBonusTargetProfile(RepeatableTargetProfile.byId(value)));
        readUuidWeeklyQuestMap(root, "weeklyChoice", (id, value) -> getPlayerData(id).setWeeklyChoice(value));
        readUuidLongMap(root, "weeklyChoiceCycle", (id, value) -> getPlayerData(id).setWeeklyChoiceCycle(value));
        readUuidIntMap(root, "weeklyTargetProfile", (id, value) -> getPlayerData(id).setWeeklyTargetProfile(RepeatableTargetProfile.byId(value)));
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
        if (storyLanesWritable()) readUuidSet(root, "kingdomStoryActive", id -> getPlayerData(id).setKingdomStoryActive(true));
        readUuidLongMap(root, "storyCooldownUntil", (id, value) -> getPlayerData(id).setStoryCooldownUntil(value));
        readUuidStringMap(root, "pilgrimActiveContract", (id, value) -> getPlayerData(id).setActivePilgrimContractId(value));
        readUuidIntMap(root, "pilgrimActiveContractProfile", (id, value) -> getPlayerData(id).setActivePilgrimTargetProfile(RepeatableTargetProfile.byId(value)));
        readUuidStringMap(root, "pilgrimOfferedContract", (id, value) -> getPlayerData(id).setOfferedPilgrimContractId(value));
        readUuidIntMap(root, "pilgrimOfferedContractProfile", (id, value) -> getPlayerData(id).setOfferedPilgrimTargetProfile(RepeatableTargetProfile.byId(value)));
        readUuidStringMap(root, "pilgrimOfferedContractAlt", (id, value) -> getPlayerData(id).setOfferedPilgrimContractAltId(value));
        readUuidIntMap(root, "pilgrimOfferedContractAltProfile", (id, value) -> getPlayerData(id).setOfferedPilgrimContractAltTargetProfile(RepeatableTargetProfile.byId(value)));
        readUuidLongMap(root, "pilgrimOfferDay", (id, value) -> getPlayerData(id).setPilgrimOfferDay(value));
        readUuidNamedIntMap(root, "reputation", (id, stateKey, value) -> getPlayerData(id).setReputation(stateKey, value));
        if (preservedSocialRecords == null) for (var raw : root.getListOrEmpty("socialReputation")) {
            if (!(raw instanceof CompoundTag entry)) continue;
            try {
                UUID id = UUID.fromString(entry.getStringOr("uuid", ""));
                if (entry.get("data") instanceof CompoundTag saved) getPlayerData(id).loadSocialReputation(saved);
            } catch (IllegalArgumentException malformedOwner) {
                // Optional social data cannot invalidate the rest of this world's quest progress.
            }
        }
        readUuidNamedIntMap(root, "storyProgressInts", (id, stateKey, value) -> getPlayerData(id).setStoryInt(stateKey, value));
        readUuidNamedSet(root, "storyProgressFlags", (id, stateKey) -> getPlayerData(id).setStoryFlag(stateKey, true));
        readUuidNamedIntMap(root, "tradeRouteInts", (id, stateKey, value) -> getPlayerData(id).setTradeRouteInt(stateKey, value));
        readUuidNamedStringMap(root, "tradeRouteStrings", (id, stateKey, value) -> getPlayerData(id).setTradeRouteString(stateKey, value));
        readUuidNamedSet(root, "tradeRouteFlags", (id, stateKey) -> getPlayerData(id).setTradeRouteFlag(stateKey, true));
        preserveMalformedIntroNumbers(root);
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
        boolean lanesMigrated = false;
        if (storyLanesWritable() && !root.contains("storyLaneFormat")) {
            for (PlayerQuestData data : players.values()) lanesMigrated |= data.migrateLegacyStoryLane();
        }
        boolean onboardingMigrated = players.values().stream()
                .map(FirstDailyChoiceService::migratePre24Progress)
                .reduce(false, (left, right) -> left || right);
        boolean sanitized = players.values().stream()
                .map(QuestState::sanitizeLoadedPlayerData)
                .reduce(false, (left, right) -> left || right);
        if (lanesMigrated || onboardingMigrated || sanitized) {
            setDirty();
        }
    }

    static boolean sanitizeLoadedPlayerData(PlayerQuestData data) {
        if (data == null || !data.storyLanesWritable()) return false;
        boolean changed = GuildTownProgress.sanitizeActiveState(data);
        StoryArcType active = data.getActiveStoryArc();
        for (Map.Entry<String, Integer> entry : Map.copyOf(data.getStoryChapterProgressState()).entrySet()) {
            StoryArcType type = StoryArcType.fromId(entry.getKey());
            if (type == null) {
                data.setStoryChapterProgress(entry.getKey(), 0);
                changed = true;
                continue;
            }
            int chapterCount = type.chapterCount();
            boolean completed = data.hasStoryCompleted(type.id());
            boolean malformed = entry.getValue() > chapterCount
                    || entry.getValue() == chapterCount && !completed;
            if (!malformed) continue;
            data.setStoryChapterProgress(type.id(), completed ? chapterCount : 0);
            if (type == StoryArcType.LIVING_KINGDOMS) data.setKingdomStoryActive(false);
            if (active == type) {
                data.setActiveStoryArc(null);
                active = null;
            }
            changed = true;
        }
        if (active != null && data.hasStoryCompleted(active.id())) {
            data.setActiveStoryArc(null);
            changed = true;
        }
        if (data.isKingdomStoryActive() && data.hasStoryCompleted(StoryArcType.LIVING_KINGDOMS.id())) {
            data.setKingdomStoryActive(false);
            changed = true;
        }
        return changed;
    }

    private CompoundTag writeDailyQuestData() {
        if (preservedStoryLaneManager != null) return preservedStoryLaneManager.copy();
        CompoundTag root = new CompoundTag();
        root.putLong("pilgrimNaturalSpawnCooldownUntil", this.pilgrimNaturalSpawnCooldownUntil);
        ListTag modifiedRegions = new ListTag();
        synchronized (modifiedTerrainRegions) {
            modifiedTerrainRegions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(region -> {
                CompoundTag item = new CompoundTag();
                item.putInt("x", (int) (long) region.getKey());
                item.putInt("z", (int) (region.getKey() >> 32));
                for (int word = 0; word < region.getValue().length; word++) {
                    long mask = region.getValue()[word];
                    if (mask != 0L) item.putLong("m" + word, mask);
                }
                modifiedRegions.add(item);
            });
        }
        root.put("modifiedTerrainRegions", modifiedRegions);
        ListTag currencyBalance = new ListTag();
        ListTag lastRewardDay = new ListTag();
        ListTag bonusRewardDay = new ListTag();
        ListTag progressDay = new ListTag();
        ListTag dailyProgressInts = new ListTag();
        ListTag dailyProgressFlags = new ListTag();
        ListTag acceptedDay = new ListTag();
        ListTag bonusAcceptedDay = new ListTag();
        ListTag questMasterSummonBlockedUntil = new ListTag();
        ListTag weeklyProgressCycle = new ListTag();
        ListTag weeklyAcceptedCycle = new ListTag();
        ListTag weeklyRewardCycle = new ListTag();
        ListTag dailyOfferNoticeDay = new ListTag();
        ListTag weeklyOfferNoticeCycle = new ListTag();
        ListTag storyOfferNoticeKey = new ListTag();
        ListTag dailyChoice = new ListTag();
        ListTag dailyChoiceDay = new ListTag();
        ListTag dailyTargetProfile = new ListTag();
        ListTag bonusChoice = new ListTag();
        ListTag bonusChoiceDay = new ListTag();
        ListTag bonusTargetProfile = new ListTag();
        ListTag weeklyChoice = new ListTag();
        ListTag weeklyChoiceCycle = new ListTag();
        ListTag weeklyTargetProfile = new ListTag();
        ListTag dailyDiscovered = new ListTag();
        ListTag weeklyDiscoveredEntries = new ListTag();
        ListTag weeklyCompletedEntries = new ListTag();
        ListTag storyDiscoveredEntries = new ListTag();
        ListTag storyCompletedEntries = new ListTag();
        ListTag storyUnlockedProjects = new ListTag();
        ListTag milestoneFlags = new ListTag();
        ListTag pendingDailyOffer = new ListTag();
        ListTag pendingShardOffer = new ListTag();
        ListTag pendingBonusOffer = new ListTag();
        ListTag questTrackerEnabled = new ListTag();
        ListTag starterShardGranted = new ListTag();
        ListTag pendingSpecialOfferType = new ListTag();
        ListTag activeStoryArc = new ListTag();
        ListTag kingdomStoryActive = new ListTag();
        ListTag storyCooldownUntil = new ListTag();
        ListTag pilgrimActiveContract = new ListTag();
        ListTag pilgrimActiveContractProfile = new ListTag();
        ListTag pilgrimOfferedContract = new ListTag();
        ListTag pilgrimOfferedContractProfile = new ListTag();
        ListTag pilgrimOfferedContractAlt = new ListTag();
        ListTag pilgrimOfferedContractAltProfile = new ListTag();
        ListTag pilgrimOfferDay = new ListTag();
        ListTag reputation = new ListTag();
        ListTag socialReputation = new ListTag();
        ListTag storyProgressInts = new ListTag();
        ListTag storyProgressFlags = new ListTag();
        ListTag tradeRouteInts = new ListTag();
        ListTag tradeRouteStrings = new ListTag();
        ListTag tradeRouteFlags = new ListTag();
        ListTag pilgrimProgressInts = new ListTag();
        ListTag pilgrimProgressFlags = new ListTag();
        ListTag storyChapterProgress = new ListTag();
        ListTag specialShardQuest = new ListTag();
        ListTag specialMerchantSealQuest = new ListTag();
        ListTag specialShepherdFluteQuest = new ListTag();
        ListTag specialApiaristSmokerQuest = new ListTag();
        ListTag specialSurveyorCompassQuest = new ListTag();
        ListTag weeklyProgressInts = new ListTag();
        ListTag weeklyProgressFlags = new ListTag();

        for (var entry : players.entrySet()) {
            UUID id = entry.getKey();
            PlayerQuestData data = entry.getValue();
            if (id == null || data == null) {
                continue;
            }
            CompoundTag socialEntry = new CompoundTag();
            socialEntry.putString("uuid", id.toString());
            socialEntry.put("data", data.socialReputation().toNbt());
            socialReputation.add(socialEntry);
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
            if (data.getDailyOfferNoticeDay() != PlayerQuestData.UNSET_DAY) {
                dailyOfferNoticeDay.add(entryLong(id, data.getDailyOfferNoticeDay()));
            }
            if (data.getWeeklyOfferNoticeCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyOfferNoticeCycle.add(entryLong(id, data.getWeeklyOfferNoticeCycle()));
            }
            if (data.getStoryOfferNoticeKey() != null) {
                storyOfferNoticeKey.add(entryString(id, data.getStoryOfferNoticeKey()));
            }
            if (data.getDailyChoice() != null) {
                dailyChoice.add(entryString(id, data.getDailyChoice().name()));
            }
            if (data.getDailyChoiceDay() != PlayerQuestData.UNSET_DAY) {
                dailyChoiceDay.add(entryLong(id, data.getDailyChoiceDay()));
            }
            if (data.getDailyTargetProfile() != RepeatableTargetProfile.NORMAL) {
                dailyTargetProfile.add(entryInt(id, data.getDailyTargetProfile().id()));
            }
            if (data.getBonusChoice() != null) {
                bonusChoice.add(entryString(id, data.getBonusChoice().name()));
            }
            if (data.getBonusChoiceDay() != PlayerQuestData.UNSET_DAY) {
                bonusChoiceDay.add(entryLong(id, data.getBonusChoiceDay()));
            }
            if (data.getBonusTargetProfile() != RepeatableTargetProfile.NORMAL) {
                bonusTargetProfile.add(entryInt(id, data.getBonusTargetProfile().id()));
            }
            if (data.getWeeklyChoice() != null) {
                weeklyChoice.add(entryString(id, data.getWeeklyChoice().name()));
            }
            if (data.getWeeklyChoiceCycle() != PlayerQuestData.UNSET_DAY) {
                weeklyChoiceCycle.add(entryLong(id, data.getWeeklyChoiceCycle()));
            }
            if (data.getWeeklyTargetProfile() != RepeatableTargetProfile.NORMAL) {
                weeklyTargetProfile.add(entryInt(id, data.getWeeklyTargetProfile().id()));
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
            if (data.isKingdomStoryActive()) kingdomStoryActive.add(entryId(id));
            if (data.getActiveStoryArc() != null) {
                activeStoryArc.add(entryString(id, data.getActiveStoryArc().id()));
            }
            if (data.getStoryCooldownUntil() > 0L) {
                storyCooldownUntil.add(entryLong(id, data.getStoryCooldownUntil()));
            }
            if (data.getActivePilgrimContractId() != null) {
                pilgrimActiveContract.add(entryString(id, data.getActivePilgrimContractId()));
            }
            if (data.getActivePilgrimTargetProfile() != RepeatableTargetProfile.NORMAL) {
                pilgrimActiveContractProfile.add(entryInt(id, data.getActivePilgrimTargetProfile().id()));
            }
            if (data.getOfferedPilgrimContractId() != null) {
                pilgrimOfferedContract.add(entryString(id, data.getOfferedPilgrimContractId()));
            }
            if (data.getOfferedPilgrimTargetProfile() != RepeatableTargetProfile.NORMAL) {
                pilgrimOfferedContractProfile.add(entryInt(id, data.getOfferedPilgrimTargetProfile().id()));
            }
            if (data.getOfferedPilgrimContractAltId() != null) {
                pilgrimOfferedContractAlt.add(entryString(id, data.getOfferedPilgrimContractAltId()));
            }
            if (data.getOfferedPilgrimContractAltTargetProfile() != RepeatableTargetProfile.NORMAL) {
                pilgrimOfferedContractAltProfile.add(entryInt(id, data.getOfferedPilgrimContractAltTargetProfile().id()));
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
            for (var stateEntry : data.getTradeRouteIntState().entrySet()) {
                if (stateEntry.getValue() != null && stateEntry.getValue() != 0
                        && !(preservedIntroNumbers.containsKey(id) && introNumber(stateEntry.getKey()))) {
                    tradeRouteInts.add(entryNamedInt(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (var stateEntry : data.getTradeRouteStringState().entrySet()) {
                if (stateEntry.getValue() != null && !stateEntry.getValue().isBlank()) {
                    tradeRouteStrings.add(entryNamedString(id, stateEntry.getKey(), stateEntry.getValue()));
                }
            }
            for (String stateKey : data.getTradeRouteFlags()) {
                tradeRouteFlags.add(entryNamedKey(id, stateKey));
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
        root.put("dailyOfferNoticeDay", dailyOfferNoticeDay);
        root.put("weeklyOfferNoticeCycle", weeklyOfferNoticeCycle);
        root.put("storyOfferNoticeKey", storyOfferNoticeKey);
        root.put("dailyChoice", dailyChoice);
        root.put("dailyChoiceDay", dailyChoiceDay);
        root.put("dailyTargetProfile", dailyTargetProfile);
        root.put("bonusChoice", bonusChoice);
        root.put("bonusChoiceDay", bonusChoiceDay);
        root.put("bonusTargetProfile", bonusTargetProfile);
        root.put("weeklyChoice", weeklyChoice);
        root.put("weeklyChoiceCycle", weeklyChoiceCycle);
        root.put("weeklyTargetProfile", weeklyTargetProfile);
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
        root.putInt("storyLaneFormat", 1);
        root.put("kingdomStoryActive", kingdomStoryActive);
        root.put("activeStoryArc", activeStoryArc);
        root.put("storyCooldownUntil", storyCooldownUntil);
        root.put("pilgrimActiveContract", pilgrimActiveContract);
        root.put("pilgrimActiveContractProfile", pilgrimActiveContractProfile);
        root.put("pilgrimOfferedContract", pilgrimOfferedContract);
        root.put("pilgrimOfferedContractProfile", pilgrimOfferedContractProfile);
        root.put("pilgrimOfferedContractAlt", pilgrimOfferedContractAlt);
        root.put("pilgrimOfferedContractAltProfile", pilgrimOfferedContractAltProfile);
        root.put("pilgrimOfferDay", pilgrimOfferDay);
        root.put("reputation", reputation);
        root.put("socialReputation", preservedSocialRecords == null ? socialReputation : preservedSocialRecords.copy());
        root.put("storyProgressInts", storyProgressInts);
        root.put("storyProgressFlags", storyProgressFlags);
        preservedIntroNumbers.values().forEach(rows -> rows.forEach(row -> tradeRouteInts.add(row.copy())));
        root.put("tradeRouteInts", tradeRouteInts);
        root.put("tradeRouteStrings", tradeRouteStrings);
        root.put("tradeRouteFlags", tradeRouteFlags);
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
        setDirty();
    }

    /** Event writers must supply their level; old persisted marks remain conservatively intact. */
    public void markTerrainModified(net.minecraft.server.level.ServerLevel world,BlockPos pos) {
        if(world!=null && world.dimension()==net.minecraft.world.level.Level.OVERWORLD) markTerrainModified(pos);
    }

    public void markTerrainModified(BlockPos pos) {
        if (pos != null && addModifiedTerrainChunk(packChunk(pos.getX() >> 4, pos.getZ() >> 4))) {
            setDirty();
        }
    }

    public boolean isTerrainModified(BlockPos pos, int chunkRadius) {
        if (pos == null) {
            return false;
        }
        int centerX = pos.getX() >> 4;
        int centerZ = pos.getZ() >> 4;
        int radius = Math.max(0, chunkRadius);
        synchronized (modifiedTerrainRegions) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (containsModifiedTerrainChunk(x, z)) return true;
                }
            }
        }
        return false;
    }

    private boolean addModifiedTerrainChunk(long packed) {
        int chunkX = (int) packed;
        int chunkZ = (int) (packed >> 32);
        int local = Math.floorMod(chunkZ, 32) * 32 + Math.floorMod(chunkX, 32);
        synchronized (modifiedTerrainRegions) {
            long[] masks = modifiedTerrainRegions.computeIfAbsent(
                    packChunk(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32)), ignored -> new long[16]);
            long bit = 1L << (local & 63);
            int word = local >> 6;
            if ((masks[word] & bit) != 0L) return false;
            masks[word] |= bit;
            return true;
        }
    }

    private boolean containsModifiedTerrainChunk(int chunkX, int chunkZ) {
        long[] masks = modifiedTerrainRegions.get(packChunk(
                Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32)));
        if (masks == null) return false;
        int local = Math.floorMod(chunkZ, 32) * 32 + Math.floorMod(chunkX, 32);
        return (masks[local >> 6] & 1L << (local & 63)) != 0L;
    }

    int modifiedTerrainChunkCount() {
        long count = 0L;
        synchronized (modifiedTerrainRegions) {
            for (long[] masks : modifiedTerrainRegions.values()) {
                for (long mask : masks) count += Long.bitCount(mask);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private static long packChunk(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    private static void readUuidLongMap(CompoundTag root, String key, BiConsumer<UUID, Long> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id != null) {
                consumer.accept(id, item.getLongOr("v", 0L));
            }
        }
    }

    private static void readUuidIntMap(CompoundTag root, String key, BiConsumer<UUID, Integer> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id != null) {
                consumer.accept(id, item.getIntOr("v", 0));
            }
        }
    }

    private static void readUuidBoolMap(CompoundTag root, String key, BiConsumer<UUID, Boolean> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id != null) {
                consumer.accept(id, item.getBooleanOr("v", false));
            }
        }
    }

    private static void readUuidNamedIntMap(CompoundTag root, String key, NamedIntConsumer consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            String stateKey = item.getStringOr("key", "");
            if (id != null && !stateKey.isEmpty()) {
                consumer.accept(id, stateKey, item.getIntOr("v", 0));
            }
        }
    }

    private static void readUuidNamedStringMap(CompoundTag root, String key, NamedStringConsumer consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            String stateKey = item.getStringOr("key", "");
            String value = item.getStringOr("v", "");
            if (id != null && !stateKey.isEmpty() && !value.isBlank()) {
                consumer.accept(id, stateKey, value);
            }
        }
    }

    private static void readUuidStringMap(CompoundTag root, String key, BiConsumer<UUID, String> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            String value = item.getStringOr("v", "");
            if (id != null && !value.isEmpty()) {
                consumer.accept(id, value);
            }
        }
    }

    private static void readUuidNamedSet(CompoundTag root, String key, NamedKeyConsumer consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            String stateKey = item.getStringOr("key", "");
            if (id != null && !stateKey.isEmpty()) {
                consumer.accept(id, stateKey);
            }
        }
    }

    private static void readUuidQuestMap(CompoundTag root, String key, BiConsumer<UUID, DailyQuestService.DailyQuestType> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            String value = item.getStringOr("v", "");
            try {
                consumer.accept(id, DailyQuestService.DailyQuestType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                // ignore invalid entries
            }
        }
    }

    private static void readUuidWeeklyQuestMap(CompoundTag root, String key, BiConsumer<UUID, WeeklyQuestService.WeeklyQuestType> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            String value = item.getStringOr("v", "");
            try {
                consumer.accept(id, WeeklyQuestService.WeeklyQuestType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                // ignore invalid entries
            }
        }
    }

    private static void readUuidSet(CompoundTag root, String key, java.util.function.Consumer<UUID> consumer) {
        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id != null) {
                consumer.accept(id);
            }
        }
    }

    private static CompoundTag entryLong(UUID id, long value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putLong("v", value);
        return item;
    }

    private static CompoundTag entryInt(UUID id, int value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putInt("v", value);
        return item;
    }

    private static CompoundTag entryBool(UUID id, boolean value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putBoolean("v", value);
        return item;
    }

    private static CompoundTag entryNamedInt(UUID id, String key, int value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("key", key);
        item.putInt("v", value);
        return item;
    }

    private static CompoundTag entryNamedString(UUID id, String key, String value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("key", key);
        item.putString("v", value);
        return item;
    }

    private static CompoundTag entryNamedKey(UUID id, String key) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("key", key);
        return item;
    }

    private static CompoundTag entryString(UUID id, String value) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("v", value);
        return item;
    }

    private static CompoundTag entryId(UUID id) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        return item;
    }

    private void readSpecialShardQuestData(CompoundTag root) {
        ListTag list = root.getListOrEmpty("specialShardQuest");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            if (item.getBooleanOr("pendingOffer", false) && data.getPendingSpecialOfferKind() == null) {
                data.setPendingSpecialOfferKind(SpecialQuestKind.SHARD_RELIC);
            }
            try {
                data.setShardRelicQuestStage(ShardRelicQuestStage.valueOf(item.getStringOr("stage", ShardRelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setShardRelicQuestStage(ShardRelicQuestStage.NONE);
            }
            data.setShardRelicAmethystProgress(item.getIntOr("amethyst", 0));
            data.setShardRelicPotionProgress(item.getIntOr("potions", 0));
            data.setShardRelicEnchantProgress(item.getIntOr("enchants", 0));
            data.setShardRelicEnderPearlProgress(item.getIntOr("enderPearls", 0));
            data.setShardRelicBlazeRodProgress(item.getIntOr("blazeRods", 0));
            data.setShardRelicEnchantBaseline(item.getIntOr("enchantBase", 0));
            data.setShardRelicEnderPearlBaseline(item.getIntOr("enderPearlBase", 0));
            data.setShardRelicBlazeRodBaseline(item.getIntOr("blazeRodBase", 0));
            data.setShardRelicChestX(item.getIntOr("chestX", 0));
            data.setShardRelicChestY(item.getIntOr("chestY", Integer.MIN_VALUE));
            data.setShardRelicChestZ(item.getIntOr("chestZ", 0));
        }
    }

    private static CompoundTag entrySpecialShardQuest(UUID id, PlayerQuestData data) {
        CompoundTag item = new CompoundTag();
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

    private void readSpecialMerchantSealQuestData(CompoundTag root) {
        ListTag list = root.getListOrEmpty("specialMerchantSealQuest");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setMerchantSealQuestStage(RelicQuestStage.valueOf(item.getStringOr("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setMerchantSealQuestStage(RelicQuestStage.NONE);
            }
            data.setMerchantSealTradeProgress(item.getIntOr("trades", 0));
            data.setMerchantSealEmeraldProgress(item.getIntOr("emeralds", 0));
            data.setMerchantSealVillagerPurchaseProgress(item.getIntOr("villagerPurchases", 0));
            data.setMerchantSealPilgrimPurchaseProgress(item.getIntOr("pilgrimPurchases", 0));
            data.setMerchantSealLastUseDay(item.getLongOr("lastUseDay", PlayerQuestData.UNSET_DAY));
        }
    }

    private static CompoundTag entrySpecialMerchantSealQuest(UUID id, PlayerQuestData data) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("stage", data.getMerchantSealQuestStage().name());
        item.putInt("trades", data.getMerchantSealTradeProgress());
        item.putInt("emeralds", data.getMerchantSealEmeraldProgress());
        item.putInt("villagerPurchases", data.getMerchantSealVillagerPurchaseProgress());
        item.putInt("pilgrimPurchases", data.getMerchantSealPilgrimPurchaseProgress());
        item.putLong("lastUseDay", data.getMerchantSealLastUseDay());
        return item;
    }

    private void readSpecialShepherdFluteQuestData(CompoundTag root) {
        ListTag list = root.getListOrEmpty("specialShepherdFluteQuest");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setShepherdFluteQuestStage(RelicQuestStage.valueOf(item.getStringOr("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setShepherdFluteQuestStage(RelicQuestStage.NONE);
            }
            data.setShepherdFluteBreedProgress(item.getIntOr("breed", 0));
            data.setShepherdFluteShearProgress(item.getIntOr("shear", 0));
            data.setShepherdFluteWoolProgress(item.getIntOr("wool", 0));
            data.setShepherdFluteWoolBaseline(item.getIntOr("woolBaseline", 0));
        }
    }

    private static CompoundTag entrySpecialShepherdFluteQuest(UUID id, PlayerQuestData data) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("stage", data.getShepherdFluteQuestStage().name());
        item.putInt("breed", data.getShepherdFluteBreedProgress());
        item.putInt("shear", data.getShepherdFluteShearProgress());
        item.putInt("wool", data.getShepherdFluteWoolProgress());
        item.putInt("woolBaseline", data.getShepherdFluteWoolBaseline());
        return item;
    }

    private void readSpecialApiaristSmokerQuestData(CompoundTag root) {
        ListTag list = root.getListOrEmpty("specialApiaristSmokerQuest");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setApiaristSmokerQuestStage(RelicQuestStage.valueOf(item.getStringOr("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setApiaristSmokerQuestStage(RelicQuestStage.NONE);
            }
            data.setApiaristSmokerHoneyProgress(item.getIntOr("honey", 0));
            data.setApiaristSmokerCombProgress(item.getIntOr("comb", 0));
            data.setApiaristSmokerBeeBreedProgress(item.getIntOr("beeBreed", 0));
            data.setApiaristSmokerHoneyBlockProgress(item.getIntOr("honeyBlock", 0));
            data.setApiaristSmokerHoneyBlockBaseline(item.getIntOr("honeyBlockBaseline", 0));
            data.setApiaristSmokerLastUseDay(item.getLongOr("lastUseDay", PlayerQuestData.UNSET_DAY));
            data.setApiaristSmokerUsesToday(item.getIntOr("usesToday", 0));
        }
    }

    private static CompoundTag entrySpecialApiaristSmokerQuest(UUID id, PlayerQuestData data) {
        CompoundTag item = new CompoundTag();
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

    private void readSpecialSurveyorCompassQuestData(CompoundTag root) {
        ListTag list = root.getListOrEmpty("specialSurveyorCompassQuest");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompoundOrEmpty(i);
            UUID id = parseUuid(item.getStringOr("id", ""));
            if (id == null) {
                continue;
            }
            PlayerQuestData data = getPlayerData(id);
            try {
                data.setSurveyorCompassQuestStage(RelicQuestStage.valueOf(item.getStringOr("stage", RelicQuestStage.NONE.name())));
            } catch (IllegalArgumentException ex) {
                data.setSurveyorCompassQuestStage(RelicQuestStage.NONE);
            }
            data.setSurveyorCompassRedstoneProgress(item.getIntOr("redstone", 0));
            data.setSurveyorCompassLapisProgress(item.getIntOr("lapis", 0));
            data.setSurveyorCompassCraftedProgress(item.getIntOr("crafted", 0));
            data.setSurveyorCompassPickaxeReadyProgress(item.getIntOr("pickaxeReady", 0));
            data.setSurveyorCompassPickaxeBaseline(item.getIntOr("pickaxeBaseline", 0));
            data.setSurveyorCompassModeIndex(item.getIntOr("modeIndex", 0));
            data.setSurveyorCompassSearchCooldownUntil(item.getLongOr("searchCooldownUntil", 0L));
            data.setSurveyorCompassHomeCooldownUntil(item.getLongOr("homeCooldownUntil", 0L));
            data.setSurveyorCompassHomeConfirmUntil(item.getLongOr("homeConfirmUntil", 0L));
        }
    }

    private static CompoundTag entrySpecialSurveyorCompassQuest(UUID id, PlayerQuestData data) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id.toString());
        item.putString("stage", data.getSurveyorCompassQuestStage().name());
        item.putInt("redstone", data.getSurveyorCompassRedstoneProgress());
        item.putInt("lapis", data.getSurveyorCompassLapisProgress());
        item.putInt("crafted", data.getSurveyorCompassCraftedProgress());
        item.putInt("pickaxeReady", data.getSurveyorCompassPickaxeReadyProgress());
        item.putInt("pickaxeBaseline", data.getSurveyorCompassPickaxeBaseline());
        item.putInt("modeIndex", data.getSurveyorCompassModeIndex());
        if (data.getSurveyorCompassSearchCooldownUntil() > 0L) item.putLong("searchCooldownUntil", data.getSurveyorCompassSearchCooldownUntil());
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
    private interface NamedStringConsumer {
        void accept(UUID id, String key, String value);
    }

    @FunctionalInterface
    private interface NamedKeyConsumer {
        void accept(UUID id, String key);
    }
}
