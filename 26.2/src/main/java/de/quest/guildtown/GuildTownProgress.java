package de.quest.guildtown;

import de.quest.data.PlayerQuestData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Save-facing personal 2.4 vocabulary, backed by QuestState's existing generic maps. */
public final class GuildTownProgress {
    public static final int NONE = 0;
    public static final int ACTIVE = 1;
    public static final int READY = 2;
    public static final int COMPLETE = 3;
    public static final int PAUSED = 4;
    public static final int PREVENTIVE = 1;
    public static final int RECOVERY = 2;
    public static final int PERSONAL_CHRONICLE_CAP = 12;
    public static final int PERSONAL_MILESTONE_CAP = 128;

    private static final String PREFIX = "guild_town.";
    private static final String ACTIVE_STORY = PREFIX + "active_story";
    private static final String ACTIVE_STORY_VILLAGE = PREFIX + "active_story_village";
    private static final String STORY_MASK = PREFIX + "story_mask";
    private static final String ACTIVE_COMMISSION = PREFIX + "active_commission";
    private static final String COMMISSION_MASK = PREFIX + "commission_mask";
    private static final String COMMISSION_OWNER = PREFIX + "commission_owner";
    private static final String COMMISSION_FIRST_VILLAGE = PREFIX + "commission_first_village";
    private static final String COMMISSION_SECOND_VILLAGE = PREFIX + "commission_second_village";
    private static final String COMMISSION_FIRST_X = PREFIX + "commission_first_x";
    private static final String COMMISSION_FIRST_Z = PREFIX + "commission_first_z";
    private static final String COMMISSION_SECOND_X = PREFIX + "commission_second_x";
    private static final String COMMISSION_SECOND_Z = PREFIX + "commission_second_z";
    private static final String COMMISSION_HAS_IDENTITIES = PREFIX + "commission_has_identities";
    private static final String COMMISSION_FIRST_X_PRESENT = PREFIX + "commission_first_x_present";
    private static final String COMMISSION_FIRST_Z_PRESENT = PREFIX + "commission_first_z_present";
    private static final String COMMISSION_SECOND_X_PRESENT = PREFIX + "commission_second_x_present";
    private static final String COMMISSION_SECOND_Z_PRESENT = PREFIX + "commission_second_z_present";
    private static final String CHRONICLE_SEQUENCE = PREFIX + "chronicle.sequence";
    private static final String CHRONICLE_ENTRY_PREFIX = PREFIX + "chronicle.entry.";
    private static final String LONG_DRIVE_ANIMAL_PREFIX = PREFIX + "story.long_drive.escort_animal.";
    private static final String LONG_DRIVE_ESCORTED_PREFIX = PREFIX + "story.long_drive.escorted.";
    private static final String LONG_DRIVE_START_PREFIX = PREFIX + "story.long_drive.start.";
    private static final String LONG_DRIVE_LAST_PREFIX = PREFIX + "story.long_drive.last.";
    private static final String LONG_DRIVE_PLAYER_START_PREFIX = PREFIX + "story.long_drive.player_start.";
    private static final String LONG_DRIVE_PLAYER_LAST_PREFIX = PREFIX + "story.long_drive.player_last.";
    private static final String LONG_DRIVE_DISTANCE_PREFIX = PREFIX + "story.long_drive.distance.";
    private static final String LONG_DRIVE_NEAR_PREFIX = PREFIX + "story.long_drive.near.";
    private static final int LONG_DRIVE_DISTANCE_MILLIS = 32_000;
    private static final long LONG_DRIVE_MAX_SAMPLE_GAP = 40L;
    private static final double LONG_DRIVE_MAX_SEGMENT = 8.0D;
    private static final int MAX_ABS_VILLAGE_COORDINATE = 30_000_000;
    private static final String FINALE = PREFIX + "finale";

    private GuildTownProgress() {}

    public static int activeStoryId(PlayerQuestData data) { return data.getStoryInt(ACTIVE_STORY) - 1; }
    public static int activeStoryVillage(PlayerQuestData data) { return data.getStoryInt(ACTIVE_STORY_VILLAGE) - 1; }
    public static int storyState(PlayerQuestData data, GuildTownStory story) { return data.getStoryInt(storyKey(story, "state")); }
    public static int storyVariant(PlayerQuestData data, GuildTownStory story) { return data.getStoryInt(storyKey(story, "variant")); }
    public static int storyProgress(PlayerQuestData data, GuildTownStory story, int part) { return data.getStoryInt(storyKey(story, "p" + part)); }
    public static int completedStoryMask(PlayerQuestData data) {
        return data.getStoryInt(STORY_MASK) & ((1 << GuildTownStory.values().length) - 1);
    }
    public static int completedStoryCount(PlayerQuestData data) { return Integer.bitCount(completedStoryMask(data)); }
    public static boolean storyCompleted(PlayerQuestData data, GuildTownStory story) { return (completedStoryMask(data) & story.bit()) != 0; }

    public static boolean beginStory(PlayerQuestData data, GuildTownStory story, int villageIndex, int variant) {
        if (data == null || story == null || villageIndex < 0 || activeStoryId(data) >= 0 || storyCompleted(data, story)) return false;
        data.setStoryInt(ACTIVE_STORY, story.id() + 1);
        data.setStoryInt(ACTIVE_STORY_VILLAGE, villageIndex + 1);
        data.setStoryInt(storyKey(story, "state"), ACTIVE);
        data.setStoryInt(storyKey(story, "variant"), variant == RECOVERY ? RECOVERY : PREVENTIVE);
        data.setStoryInt(storyKey(story, "village"), villageIndex + 1);
        return true;
    }

    public static int addStoryProgress(PlayerQuestData data, GuildTownStory story, int part, int amount, int target) {
        if (data == null || story == null || activeStoryId(data) != story.id() || storyState(data, story) != ACTIVE || amount <= 0) {
            return data == null || story == null ? 0 : storyProgress(data, story, part);
        }
        String key = storyKey(story, "p" + part);
        int progress = (int) Math.min(Math.max(1, target),
                Math.max(0L, (long) storyProgress(data, story, part)) + amount);
        data.setStoryInt(key, progress);
        return progress;
    }

    public static boolean markStoryReady(PlayerQuestData data, GuildTownStory story) {
        if (data == null || story == null || activeStoryId(data) != story.id() || storyState(data, story) != ACTIVE) return false;
        data.setStoryInt(storyKey(story, "state"), READY);
        if (story == GuildTownStory.LONG_DRIVE) clearLongDriveEscort(data, false);
        return true;
    }

    public static boolean setStoryPaused(PlayerQuestData data, boolean paused) {
        GuildTownStory story = storyById(activeStoryId(data));
        if (story == null) return false;
        int state = storyState(data, story);
        if (paused && state == ACTIVE) data.setStoryInt(storyKey(story, "state"), PAUSED);
        else if (!paused && state == PAUSED) data.setStoryInt(storyKey(story, "state"), ACTIVE);
        else return false;
        return true;
    }

    public static boolean completeStory(PlayerQuestData data, GuildTownStory story) {
        if (data == null || story == null || activeStoryId(data) != story.id() || storyState(data, story) != READY) return false;
        data.setStoryInt(storyKey(story, "state"), COMPLETE);
        data.setStoryInt(STORY_MASK, completedStoryMask(data) | story.bit());
        data.setStoryInt(ACTIVE_STORY, 0);
        data.setStoryInt(ACTIVE_STORY_VILLAGE, 0);
        if (story == GuildTownStory.LONG_DRIVE) clearLongDriveEscort(data, false);
        return true;
    }

    public static List<UUID> longDriveAnimals(PlayerQuestData data) {
        if (data == null) return List.of();
        List<UUID> animals = new ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
            UUID id = parseUuid(data.getTradeRouteString(LONG_DRIVE_ANIMAL_PREFIX + slot));
            if (id != null && !animals.contains(id)) animals.add(id);
        }
        return List.copyOf(animals);
    }

    public static boolean registerLongDriveAnimal(PlayerQuestData data, UUID animalId) {
        return registerLongDriveAnimal(data, animalId, null, 0L);
    }

    public static boolean registerLongDriveAnimal(PlayerQuestData data, UUID animalId, BlockPos start, long gameTime) {
        return registerLongDriveAnimal(data, animalId, start, null, gameTime);
    }

    public static boolean registerLongDriveAnimal(PlayerQuestData data, UUID animalId, BlockPos animalStart,
                                                  BlockPos playerStart, long gameTime) {
        if (data == null || animalId == null || activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()
                || storyState(data, GuildTownStory.LONG_DRIVE) != ACTIVE) return false;
        List<UUID> animals = longDriveAnimals(data);
        if (animals.contains(animalId) || animals.size() >= 3) return false;
        for (int slot = 0; slot < 3; slot++) {
            String key = LONG_DRIVE_ANIMAL_PREFIX + slot;
            if (!data.getTradeRouteString(key).isBlank()) continue;
            data.setTradeRouteString(key, animalId.toString());
            if (animalStart != null) initializeLongDrivePosition(
                    data, animalId, animalStart, playerStart, gameTime, true);
            addStoryProgress(data, GuildTownStory.LONG_DRIVE, 1, 1, 2);
            return true;
        }
        return false;
    }

    public static boolean markLongDriveEscorted(PlayerQuestData data, UUID animalId) {
        if (data == null || animalId == null || activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()
                || storyState(data, GuildTownStory.LONG_DRIVE) != ACTIVE
                || !longDriveAnimals(data).contains(animalId)) return false;
        String key = LONG_DRIVE_ESCORTED_PREFIX + animalId;
        if (data.hasTradeRouteFlag(key)) return false;
        data.setTradeRouteFlag(key, true);
        addStoryProgress(data, GuildTownStory.LONG_DRIVE, 2, 1, 2);
        return true;
    }

    /** Source-compatible entry point; without a player position it can only establish a safe baseline. */
    public static LongDriveTrackResult trackLongDriveEscort(PlayerQuestData data, UUID animalId,
                                                            BlockPos position, long gameTime, boolean playerNear) {
        return trackLongDriveEscort(data, animalId, position, null, gameTime, playerNear);
    }

    /** Adds the jointly travelled part of sampled animal and player movement while both remain nearby. */
    public static LongDriveTrackResult trackLongDriveEscort(PlayerQuestData data, UUID animalId,
                                                            BlockPos animalPosition, BlockPos playerPosition,
                                                            long gameTime, boolean playerNear) {
        if (data == null || animalId == null || animalPosition == null
                || activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()
                || storyState(data, GuildTownStory.LONG_DRIVE) != ACTIVE
                || !longDriveAnimals(data).contains(animalId)
                || data.hasTradeRouteFlag(LONG_DRIVE_ESCORTED_PREFIX + animalId)) {
            return new LongDriveTrackResult(false, false, longDriveDistanceMillis(data, animalId));
        }
        String last = LONG_DRIVE_LAST_PREFIX + animalId + ".";
        String playerLast = LONG_DRIVE_PLAYER_LAST_PREFIX + animalId + ".";
        boolean hadLast = data.hasTradeRouteFlag(last + "set")
                && data.hasTradeRouteFlag(playerLast + "set");
        boolean wasNear = data.hasTradeRouteFlag(LONG_DRIVE_NEAR_PREFIX + animalId);
        long lastTick = parseNonNegativeLong(data.getTradeRouteString(last + "tick"));
        int distance = longDriveDistanceMillis(data, animalId);
        boolean continuous = playerPosition != null && playerNear && wasNear && hadLast && gameTime >= lastTick
                && gameTime - lastTick <= LONG_DRIVE_MAX_SAMPLE_GAP;
        if (continuous) {
            double animalSegment = segment(animalPosition, data, last);
            double playerSegment = segment(playerPosition, data, playerLast);
            if (animalSegment <= LONG_DRIVE_MAX_SEGMENT && playerSegment <= LONG_DRIVE_MAX_SEGMENT) {
                int addition = (int) Math.round(Math.min(animalSegment, playerSegment) * 1_000.0D);
                distance = (int) Math.min(LONG_DRIVE_DISTANCE_MILLIS, (long) distance + addition);
                data.setTradeRouteInt(LONG_DRIVE_DISTANCE_PREFIX + animalId, distance);
            }
        }
        storePosition(data, last, animalPosition);
        data.setTradeRouteString(last + "tick", Long.toString(Math.max(0L, gameTime)));
        data.setTradeRouteFlag(last + "set", true);
        if (playerPosition != null) {
            storePosition(data, playerLast, playerPosition);
            data.setTradeRouteFlag(playerLast + "set", true);
        } else {
            data.setTradeRouteFlag(playerLast + "set", false);
        }
        data.setTradeRouteFlag(LONG_DRIVE_NEAR_PREFIX + animalId, playerNear);
        boolean completed = distance >= LONG_DRIVE_DISTANCE_MILLIS && markLongDriveEscorted(data, animalId);
        return new LongDriveTrackResult(true, completed, distance);
    }

    public static int longDriveDistanceMillis(PlayerQuestData data, UUID animalId) {
        return data == null || animalId == null ? 0
                : Math.max(0, Math.min(LONG_DRIVE_DISTANCE_MILLIS,
                data.getTradeRouteInt(LONG_DRIVE_DISTANCE_PREFIX + animalId)));
    }

    private static void initializeLongDrivePosition(PlayerQuestData data, UUID animalId, BlockPos animalPosition,
                                                    BlockPos playerPosition, long gameTime, boolean playerNear) {
        String start = LONG_DRIVE_START_PREFIX + animalId + ".";
        storePosition(data, start, animalPosition);
        data.setTradeRouteFlag(start + "set", true);
        String last = LONG_DRIVE_LAST_PREFIX + animalId + ".";
        storePosition(data, last, animalPosition);
        data.setTradeRouteString(last + "tick", Long.toString(Math.max(0L, gameTime)));
        data.setTradeRouteFlag(last + "set", true);
        if (playerPosition != null) {
            String playerStart = LONG_DRIVE_PLAYER_START_PREFIX + animalId + ".";
            String playerLast = LONG_DRIVE_PLAYER_LAST_PREFIX + animalId + ".";
            storePosition(data, playerStart, playerPosition);
            storePosition(data, playerLast, playerPosition);
            data.setTradeRouteFlag(playerStart + "set", true);
            data.setTradeRouteFlag(playerLast + "set", true);
        }
        data.setTradeRouteFlag(LONG_DRIVE_NEAR_PREFIX + animalId, playerNear);
    }

    private static double segment(BlockPos current, PlayerQuestData data, String prefix) {
        long dx = (long) current.getX() - data.getTradeRouteInt(prefix + "x");
        long dy = (long) current.getY() - data.getTradeRouteInt(prefix + "y");
        long dz = (long) current.getZ() - data.getTradeRouteInt(prefix + "z");
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    private static void storePosition(PlayerQuestData data, String prefix, BlockPos position) {
        data.setTradeRouteInt(prefix + "x", position.getX());
        data.setTradeRouteInt(prefix + "y", position.getY());
        data.setTradeRouteInt(prefix + "z", position.getZ());
    }

    public static boolean recoverLongDriveEscort(PlayerQuestData data) {
        if (data == null || activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()) return false;
        int state = storyState(data, GuildTownStory.LONG_DRIVE);
        if (state != ACTIVE && state != PAUSED) return false;
        clearLongDriveEscort(data, true);
        return true;
    }

    public static boolean suspendLongDriveObservation(PlayerQuestData data) {
        if (data == null || activeStoryId(data) != GuildTownStory.LONG_DRIVE.id()) return false;
        boolean changed = false;
        for (UUID animal : longDriveAnimals(data)) {
            String key = LONG_DRIVE_NEAR_PREFIX + animal;
            if (!data.hasTradeRouteFlag(key)) continue;
            data.setTradeRouteFlag(key, false);
            changed = true;
        }
        return changed;
    }

    private static void clearLongDriveEscort(PlayerQuestData data, boolean resetProgress) {
        for (UUID animal : longDriveAnimals(data)) {
            data.setTradeRouteFlag(LONG_DRIVE_ESCORTED_PREFIX + animal, false);
            data.setTradeRouteInt(LONG_DRIVE_DISTANCE_PREFIX + animal, 0);
            data.setTradeRouteFlag(LONG_DRIVE_NEAR_PREFIX + animal, false);
            for (String axis : List.of("x", "y", "z")) {
                data.setTradeRouteInt(LONG_DRIVE_START_PREFIX + animal + "." + axis, 0);
                data.setTradeRouteInt(LONG_DRIVE_LAST_PREFIX + animal + "." + axis, 0);
                data.setTradeRouteInt(LONG_DRIVE_PLAYER_START_PREFIX + animal + "." + axis, 0);
                data.setTradeRouteInt(LONG_DRIVE_PLAYER_LAST_PREFIX + animal + "." + axis, 0);
            }
            data.setTradeRouteFlag(LONG_DRIVE_START_PREFIX + animal + ".set", false);
            data.setTradeRouteFlag(LONG_DRIVE_LAST_PREFIX + animal + ".set", false);
            data.setTradeRouteFlag(LONG_DRIVE_PLAYER_START_PREFIX + animal + ".set", false);
            data.setTradeRouteFlag(LONG_DRIVE_PLAYER_LAST_PREFIX + animal + ".set", false);
            data.setTradeRouteString(LONG_DRIVE_LAST_PREFIX + animal + ".tick", "");
        }
        for (int slot = 0; slot < 3; slot++) data.setTradeRouteString(LONG_DRIVE_ANIMAL_PREFIX + slot, "");
        if (resetProgress) {
            data.setStoryInt(storyKey(GuildTownStory.LONG_DRIVE, "p1"), 0);
            data.setStoryInt(storyKey(GuildTownStory.LONG_DRIVE, "p2"), 0);
        }
    }

    private static long parseNonNegativeLong(String value) {
        try { return Math.max(0L, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    public record LongDriveTrackResult(boolean changed, boolean completedNow, int distanceMillis) {}

    public static int activeCommissionId(PlayerQuestData data) { return data.getTradeRouteInt(ACTIVE_COMMISSION) - 1; }
    public static GuildTownCommission activeCommission(PlayerQuestData data) { return GuildTownCommission.byId(activeCommissionId(data)); }
    public static int commissionState(PlayerQuestData data, GuildTownCommission commission) { return data.getTradeRouteInt(commissionKey(commission, "state")); }
    public static int commissionProgress(PlayerQuestData data, GuildTownCommission commission, int part) { return data.getTradeRouteInt(commissionKey(commission, "p" + part)); }
    public static int completedCommissionMask(PlayerQuestData data) {
        return data.getTradeRouteInt(COMMISSION_MASK) & ((1 << GuildTownCommission.values().length) - 1);
    }
    public static int completedCommissionCount(PlayerQuestData data) { return Integer.bitCount(completedCommissionMask(data)); }
    public static boolean commissionCompleted(PlayerQuestData data, GuildTownCommission commission) { return (completedCommissionMask(data) & commission.bit()) != 0; }
    public static UUID commissionOwner(PlayerQuestData data) {
        if (data == null) return null;
        try { return UUID.fromString(data.getTradeRouteString(COMMISSION_OWNER)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static int commissionFirstVillage(PlayerQuestData data) { return data.getTradeRouteInt(COMMISSION_FIRST_VILLAGE) - 1; }
    public static int commissionSecondVillage(PlayerQuestData data) { return data.getTradeRouteInt(COMMISSION_SECOND_VILLAGE) - 1; }

    public static boolean beginCommission(PlayerQuestData data, GuildTownCommission commission, UUID networkOwner,
                                          int firstVillage, int secondVillage) {
        if (data == null || commission == null || networkOwner == null || firstVillage < 0 || secondVillage < 0
                || activeCommissionId(data) >= 0 || commissionCompleted(data, commission)) return false;
        initializeCommission(data, commission, networkOwner);
        data.setTradeRouteInt(COMMISSION_FIRST_VILLAGE, firstVillage + 1);
        data.setTradeRouteInt(COMMISSION_SECOND_VILLAGE, secondVillage + 1);
        return true;
    }

    private static void initializeCommission(PlayerQuestData data, GuildTownCommission commission, UUID networkOwner) {
        data.setTradeRouteInt(ACTIVE_COMMISSION, commission.id() + 1);
        data.setTradeRouteInt(commissionKey(commission, "state"), ACTIVE);
        data.setTradeRouteString(COMMISSION_OWNER, networkOwner.toString());
    }

    public static boolean beginCommission(PlayerQuestData data, GuildTownCommission commission,
                                          VillageIdentity first, VillageIdentity second) {
        if (data == null || commission == null || first == null || second == null
                || !first.ownerId().equals(second.ownerId()) || !validIdentity(first) || !validIdentity(second)
                || first.x() == second.x() && first.z() == second.z() || activeCommissionId(data) >= 0
                || commissionCompleted(data, commission)) return false;
        initializeCommission(data, commission, first.ownerId());
        // Identity-native saves have no invented legacy index. If identities are later malformed,
        // recovery must fail safely instead of retargeting villages 0/1.
        data.setTradeRouteInt(COMMISSION_FIRST_VILLAGE, 0);
        data.setTradeRouteInt(COMMISSION_SECOND_VILLAGE, 0);
        storeCommissionIdentities(data, first, second);
        return true;
    }

    public static boolean beginCommission(PlayerQuestData data, GuildTownCommission commission,
                                          VillageIdentity first, int firstVillage,
                                          VillageIdentity second, int secondVillage) {
        if (firstVillage < 0 || secondVillage < 0 || !beginCommission(data, commission, first, second)) return false;
        data.setTradeRouteInt(COMMISSION_FIRST_VILLAGE, firstVillage + 1);
        data.setTradeRouteInt(COMMISSION_SECOND_VILLAGE, secondVillage + 1);
        return true;
    }

    public static VillageIdentity commissionFirstIdentity(PlayerQuestData data) {
        return commissionIdentity(data, COMMISSION_FIRST_X, COMMISSION_FIRST_Z);
    }

    public static VillageIdentity commissionSecondIdentity(PlayerQuestData data) {
        return commissionIdentity(data, COMMISSION_SECOND_X, COMMISSION_SECOND_Z);
    }

    public static boolean attachCommissionIdentities(PlayerQuestData data, VillageIdentity first,
                                                     VillageIdentity second) {
        UUID owner = commissionOwner(data);
        if (data == null || activeCommission(data) == null || owner == null || first == null || second == null
                || !owner.equals(first.ownerId()) || !owner.equals(second.ownerId())) return false;
        if (!validIdentity(first) || !validIdentity(second)
                || first.x() == second.x() && first.z() == second.z()) return false;
        storeCommissionIdentities(data, first, second);
        return true;
    }

    public static boolean routeBelongsToCommission(PlayerQuestData data, UUID ownerId, int x, int z) {
        if (data == null || ownerId == null || !ownerId.equals(commissionOwner(data))) return false;
        VillageIdentity first = commissionFirstIdentity(data);
        VillageIdentity second = commissionSecondIdentity(data);
        return first != null && (first.matches(ownerId, x, z) || second != null && second.matches(ownerId, x, z));
    }

    public static boolean setCommissionPaused(PlayerQuestData data, boolean paused) {
        GuildTownCommission commission = activeCommission(data);
        if (commission == null) return false;
        int state = commissionState(data, commission);
        if (paused && state == ACTIVE) data.setTradeRouteInt(commissionKey(commission, "state"), PAUSED);
        else if (!paused && state == PAUSED) data.setTradeRouteInt(commissionKey(commission, "state"), ACTIVE);
        else return false;
        return true;
    }

    public static int addCommissionProgress(PlayerQuestData data, GuildTownCommission commission, int part,
                                            int amount, int target) {
        if (data == null || commission == null || activeCommissionId(data) != commission.id()
                || commissionState(data, commission) != ACTIVE || amount <= 0) {
            return data == null || commission == null ? 0 : commissionProgress(data, commission, part);
        }
        String key = commissionKey(commission, "p" + part);
        int progress = (int) Math.min(Math.max(1, target),
                Math.max(0L, (long) commissionProgress(data, commission, part)) + amount);
        data.setTradeRouteInt(key, progress);
        return progress;
    }

    public static boolean markCommissionReady(PlayerQuestData data, GuildTownCommission commission) {
        if (data == null || commission == null || activeCommissionId(data) != commission.id()
                || commissionState(data, commission) != ACTIVE) return false;
        data.setTradeRouteInt(commissionKey(commission, "state"), READY);
        return true;
    }

    public static boolean abandonCommission(PlayerQuestData data) {
        GuildTownCommission commission = activeCommission(data);
        if (data == null || commission == null) return false;
        int state = commissionState(data, commission);
        if (state != ACTIVE && state != PAUSED && state != READY) return false;
        data.setTradeRouteInt(commissionKey(commission, "state"), 0);
        data.setTradeRouteInt(commissionKey(commission, "p1"), 0);
        data.setTradeRouteInt(commissionKey(commission, "p2"), 0);
        clearActiveCommissionIdentity(data);
        return true;
    }

    public static boolean completeCommission(PlayerQuestData data, GuildTownCommission commission) {
        if (data == null || commission == null || activeCommissionId(data) != commission.id()
                || commissionState(data, commission) != READY) return false;
        data.setTradeRouteInt(commissionKey(commission, "state"), COMPLETE);
        data.setTradeRouteInt(COMMISSION_MASK, completedCommissionMask(data) | commission.bit());
        clearActiveCommissionIdentity(data);
        return true;
    }

    /** Clears malformed active slots without completing content or granting rewards. */
    public static boolean sanitizeActiveState(PlayerQuestData data) {
        if (data == null) return false;
        boolean changed = false;

        int storedStory = data.getStoryInt(ACTIVE_STORY);
        GuildTownStory story = storedStory > 0 ? storyById(storedStory - 1) : null;
        if (storedStory > 0 && (story == null || activeStoryVillage(data) < 0
                || !isActiveState(storyState(data, story)))) {
            if (story != null) {
                data.setStoryInt(storyKey(story, "state"), 0);
                data.setStoryInt(storyKey(story, "variant"), 0);
                data.setStoryInt(storyKey(story, "p1"), 0);
                data.setStoryInt(storyKey(story, "p2"), 0);
                if (story == GuildTownStory.LONG_DRIVE) clearLongDriveEscort(data, true);
            }
            data.setStoryInt(ACTIVE_STORY, 0);
            data.setStoryInt(ACTIVE_STORY_VILLAGE, 0);
            changed = true;
        }

        int storedCommission = data.getTradeRouteInt(ACTIVE_COMMISSION);
        GuildTownCommission commission = storedCommission > 0
                ? GuildTownCommission.byId(storedCommission - 1) : null;
        boolean validCommission = storedCommission <= 0 || commission != null
                && isActiveState(commissionState(data, commission))
                && commissionOwner(data) != null
                && (hasCompleteCommissionIdentityCoordinates(data)
                        || commissionFirstVillage(data) >= 0 && commissionSecondVillage(data) >= 0);
        if (storedCommission > 0 && !validCommission) {
            if (commission != null) {
                data.setTradeRouteInt(commissionKey(commission, "state"), 0);
                data.setTradeRouteInt(commissionKey(commission, "p1"), 0);
                data.setTradeRouteInt(commissionKey(commission, "p2"), 0);
            }
            clearActiveCommissionIdentity(data);
            changed = true;
        }
        return changed;
    }

    private static boolean isActiveState(int state) {
        return state == ACTIVE || state == READY || state == PAUSED;
    }

    private static void clearActiveCommissionIdentity(PlayerQuestData data) {
        data.setTradeRouteInt(ACTIVE_COMMISSION, 0);
        data.setTradeRouteString(COMMISSION_OWNER, "");
        data.setTradeRouteInt(COMMISSION_FIRST_VILLAGE, 0);
        data.setTradeRouteInt(COMMISSION_SECOND_VILLAGE, 0);
        data.setTradeRouteInt(COMMISSION_FIRST_X, 0);
        data.setTradeRouteInt(COMMISSION_FIRST_Z, 0);
        data.setTradeRouteInt(COMMISSION_SECOND_X, 0);
        data.setTradeRouteInt(COMMISSION_SECOND_Z, 0);
        data.setTradeRouteFlag(COMMISSION_HAS_IDENTITIES, false);
        data.setTradeRouteFlag(COMMISSION_FIRST_X_PRESENT, false);
        data.setTradeRouteFlag(COMMISSION_FIRST_Z_PRESENT, false);
        data.setTradeRouteFlag(COMMISSION_SECOND_X_PRESENT, false);
        data.setTradeRouteFlag(COMMISSION_SECOND_Z_PRESENT, false);
    }

    public static boolean addPersonalChronicle(PlayerQuestData data, int villageIndex, String eventId) {
        if (villageIndex < 0) return false;
        return addPersonalChronicle(data, new VillageIdentity(new UUID(0L, 0L), villageIndex, 0), eventId,
                isHistoricalMilestone(eventId), 0L);
    }

    public static boolean addPersonalChronicle(PlayerQuestData data, VillageIdentity village, String eventId,
                                               boolean milestone, long gameTime) {
        if (data == null || village == null || eventId == null || eventId.isBlank()) return false;
        String safe = eventId.replaceAll("[^a-z0-9_.-]", "");
        if (safe.isBlank()) return false;
        String identityKey = village.key();
        String category = milestone ? "milestone" : "event";
        String flag = PREFIX + "chronicle.personal." + identityKey + "." + category + "." + safe;
        if (data.hasMilestoneFlag(flag)) return false;
        String countKey = PREFIX + "chronicle.personal." + identityKey + "." + category + ".count";
        int cap = milestone ? PERSONAL_MILESTONE_CAP : PERSONAL_CHRONICLE_CAP;
        int count = Math.max(0, Math.min(cap, data.getStoryInt(countKey)));
        if (count >= cap) return false;
        int sequence = Math.max(0, data.getTradeRouteInt(CHRONICLE_SEQUENCE));
        if (sequence == Integer.MAX_VALUE) return false;
        sequence++;
        String entry = CHRONICLE_ENTRY_PREFIX + sequence + ".";
        data.setTradeRouteString(entry + "event", safe);
        data.setTradeRouteString(entry + "owner", village.ownerId().toString());
        data.setTradeRouteString(entry + "tick", Long.toString(Math.max(0L, gameTime)));
        data.setTradeRouteInt(entry + "x", village.x());
        data.setTradeRouteInt(entry + "z", village.z());
        data.setTradeRouteInt(entry + "milestone", milestone ? 1 : 0);
        data.setTradeRouteInt(CHRONICLE_SEQUENCE, sequence);
        data.setMilestoneFlag(flag, true);
        data.setStoryInt(countKey, count + 1);
        return true;
    }

    public static int personalChronicleCount(PlayerQuestData data, int villageIndex) {
        if (data == null || villageIndex < 0) return 0;
        VillageIdentity legacy = new VillageIdentity(new UUID(0L, 0L), villageIndex, 0);
        return Math.toIntExact(personalChronicle(data).stream().filter(entry -> entry.village().equals(legacy)).count());
    }

    public static List<ChronicleEntry> personalChronicle(PlayerQuestData data) {
        if (data == null) return List.of();
        List<ChronicleEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> stored : data.getTradeRouteStringState().entrySet()) {
            String key = stored.getKey();
            if (!key.startsWith(CHRONICLE_ENTRY_PREFIX) || !key.endsWith(".event")) continue;
            String rawSequence = key.substring(CHRONICLE_ENTRY_PREFIX.length(), key.length() - ".event".length());
            int sequence;
            try { sequence = Integer.parseInt(rawSequence); }
            catch (NumberFormatException ignored) { continue; }
            String prefix = CHRONICLE_ENTRY_PREFIX + sequence + ".";
            UUID owner = parseUuid(data.getTradeRouteString(prefix + "owner"));
            if (owner == null || stored.getValue() == null || stored.getValue().isBlank()) continue;
            long tick;
            try { tick = Math.max(0L, Long.parseLong(data.getTradeRouteString(prefix + "tick"))); }
            catch (NumberFormatException ignored) { tick = 0L; }
            entries.add(new ChronicleEntry(new VillageIdentity(owner, data.getTradeRouteInt(prefix + "x"),
                    data.getTradeRouteInt(prefix + "z")), stored.getValue(), sequence, tick,
                    data.getTradeRouteInt(prefix + "milestone") != 0));
        }
        // 2.4.0-unreleased.5 stored only deduplication flags. Recover those entries
        // with unknown time/order instead of silently discarding the player's history.
        String legacyPrefix = PREFIX + "chronicle.personal.";
        for (String flag : data.getMilestoneFlags()) {
            if (!flag.startsWith(legacyPrefix)) continue;
            String remainder = flag.substring(legacyPrefix.length());
            int separator = remainder.indexOf('.');
            if (separator <= 0) continue;
            int villageIndex;
            try { villageIndex = Integer.parseInt(remainder.substring(0, separator)); }
            catch (NumberFormatException ignored) { continue; }
            String event = remainder.substring(separator + 1);
            VillageIdentity village = new VillageIdentity(new UUID(0L, 0L), villageIndex, 0);
            if (!event.isBlank() && entries.stream().noneMatch(existing -> existing.village().equals(village)
                    && existing.eventId().equals(event))) {
                entries.add(new ChronicleEntry(village, event, 0, 0L, isHistoricalMilestone(event)));
            }
        }
        entries.sort(Comparator.comparingInt(ChronicleEntry::sequence).thenComparing(ChronicleEntry::eventId));
        return List.copyOf(entries);
    }

    public static boolean finaleClaimed(PlayerQuestData data) { return data != null && data.hasMilestoneFlag(FINALE); }
    public static boolean meetsConcordGate(PlayerQuestData data, int connectedVillages) {
        return data != null && completedStoryCount(data) >= 3
                && completedCommissionCount(data) >= 3 && connectedVillages >= 3;
    }
    public static boolean markFinaleClaimed(PlayerQuestData data) {
        if (data == null || finaleClaimed(data)) return false;
        data.setMilestoneFlag(FINALE, true);
        return true;
    }

    public static boolean hasReward(PlayerQuestData data, String reward) {
        return data != null && data.hasMilestoneFlag(PREFIX + "reward." + reward);
    }

    public static boolean grantReward(PlayerQuestData data, String reward) {
        if (data == null || reward == null || reward.isBlank() || hasReward(data, reward)) return false;
        data.setMilestoneFlag(PREFIX + "reward." + reward, true);
        return true;
    }

    private static String storyKey(GuildTownStory story, String suffix) { return PREFIX + "story." + story.key() + "." + suffix; }
    private static String commissionKey(GuildTownCommission commission, String suffix) { return PREFIX + "commission." + commission.key() + "." + suffix; }

    private static GuildTownStory storyById(int id) {
        for (GuildTownStory story : GuildTownStory.values()) if (story.id() == id) return story;
        return null;
    }

    private static VillageIdentity commissionIdentity(PlayerQuestData data, String xKey, String zKey) {
        UUID owner = commissionOwner(data);
        if (data == null || owner == null || !hasCompleteCommissionIdentityCoordinates(data)) return null;
        VillageIdentity identity = new VillageIdentity(owner, data.getTradeRouteInt(xKey), data.getTradeRouteInt(zKey));
        return validIdentity(identity) ? identity : null;
    }

    private static boolean hasCompleteCommissionIdentityCoordinates(PlayerQuestData data) {
        if (!data.hasTradeRouteFlag(COMMISSION_HAS_IDENTITIES)) return false;
        if (!coordinatePresent(data, COMMISSION_FIRST_X, COMMISSION_FIRST_X_PRESENT)
                || !coordinatePresent(data, COMMISSION_FIRST_Z, COMMISSION_FIRST_Z_PRESENT)
                || !coordinatePresent(data, COMMISSION_SECOND_X, COMMISSION_SECOND_X_PRESENT)
                || !coordinatePresent(data, COMMISSION_SECOND_Z, COMMISSION_SECOND_Z_PRESENT)) return false;
        int firstX = data.getTradeRouteInt(COMMISSION_FIRST_X);
        int firstZ = data.getTradeRouteInt(COMMISSION_FIRST_Z);
        int secondX = data.getTradeRouteInt(COMMISSION_SECOND_X);
        int secondZ = data.getTradeRouteInt(COMMISSION_SECOND_Z);
        return validCoordinate(firstX) && validCoordinate(firstZ)
                && validCoordinate(secondX) && validCoordinate(secondZ)
                && (firstX != secondX || firstZ != secondZ);
    }

    private static boolean coordinatePresent(PlayerQuestData data, String valueKey, String presenceKey) {
        // .7 did not write explicit presence markers. Non-zero legacy values remain recoverable;
        // zero-valued coordinates fall back to the stored real village indices in the service.
        return data.hasTradeRouteFlag(presenceKey) || data.getTradeRouteIntState().containsKey(valueKey);
    }

    private static boolean validIdentity(VillageIdentity identity) {
        return identity != null && validCoordinate(identity.x()) && validCoordinate(identity.z());
    }

    private static boolean validCoordinate(int value) {
        return value >= -MAX_ABS_VILLAGE_COORDINATE && value <= MAX_ABS_VILLAGE_COORDINATE;
    }

    private static void storeCommissionIdentities(PlayerQuestData data, VillageIdentity first,
                                                  VillageIdentity second) {
        data.setTradeRouteInt(COMMISSION_FIRST_X, first.x());
        data.setTradeRouteInt(COMMISSION_FIRST_Z, first.z());
        data.setTradeRouteInt(COMMISSION_SECOND_X, second.x());
        data.setTradeRouteInt(COMMISSION_SECOND_Z, second.z());
        data.setTradeRouteFlag(COMMISSION_FIRST_X_PRESENT, true);
        data.setTradeRouteFlag(COMMISSION_FIRST_Z_PRESENT, true);
        data.setTradeRouteFlag(COMMISSION_SECOND_X_PRESENT, true);
        data.setTradeRouteFlag(COMMISSION_SECOND_Z_PRESENT, true);
        data.setTradeRouteFlag(COMMISSION_HAS_IDENTITIES, true);
    }

    private static UUID parseUuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean isHistoricalMilestone(String eventId) {
        return eventId != null && (eventId.startsWith("story.completed.")
                || eventId.startsWith("commission.completed.") || eventId.startsWith("concord."));
    }

    public record VillageIdentity(UUID ownerId, int x, int z) {
        public VillageIdentity {
            if (ownerId == null) throw new IllegalArgumentException("ownerId");
        }
        public boolean matches(UUID owner, int targetX, int targetZ) {
            return ownerId.equals(owner) && x == targetX && z == targetZ;
        }
        private String key() {
            return ownerId.toString().replace("-", "") + "." + encodeCoordinate(x) + "." + encodeCoordinate(z);
        }
        private static String encodeCoordinate(int value) { return value < 0 ? "n" + -(long) value : "p" + value; }
    }

    public record ChronicleEntry(VillageIdentity village, String eventId, int sequence, long gameTime,
                                 boolean milestone) {}
}
