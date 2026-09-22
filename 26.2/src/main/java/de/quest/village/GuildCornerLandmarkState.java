package de.quest.village;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-global history for generated Guild Corners.
 *
 * <p>This state deliberately stores no player or guild ownership. Personal Village Contacts,
 * stories, bonds, and Chronicle progress remain in their existing player-owned data. The saved
 * footprint is historical and used for duplicate/removal checks only; it is never a repair plan.</p>
 */
public final class GuildCornerLandmarkState extends SavedData {
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final int MAX_LANDMARKS = 16_384;
    static final int MAX_RETRY_ATTEMPTS = 5;
    static final long RETRY_DELAY_TICKS = 20L * 15L;
    private static final int MAX_FOOTPRINT_SPAN = 16;
    private static final String ID = "village_quest_guild_corners";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");

    public static final SavedDataType<GuildCornerLandmarkState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID), GuildCornerLandmarkState::new,
            CompoundTag.CODEC.xmap(GuildCornerLandmarkState::fromNbt, GuildCornerLandmarkState::toNbt),
            DataFixTypes.LEVEL);

    private final Map<VillageKey, MutableLandmark> landmarks = new LinkedHashMap<>();
    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    GuildCornerLandmarkState() {}

    public static GuildCornerLandmarkState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static String dimensionKey(ServerLevel world) {
        return world == null ? OVERWORLD : world.dimension().identifier().toString();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public int size() {
        return landmarks.size();
    }

    public Map<VillageKey, LandmarkSnapshot> landmarksView() {
        Map<VillageKey, LandmarkSnapshot> result = new LinkedHashMap<>();
        landmarks.forEach((key, value) -> result.put(key, value.snapshot(key)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Reserves one candidate for a village. Existing records always win, including removed ones,
     * so revisits, reloads, and a second player can never move or duplicate the same Guild Corner.
     */
    public ReservationResult reserve(VillageKey key, BlockPos cornerPos, CornerFacing facing,
                                     CornerStyle style, Footprint footprint) {
        if (key == null || cornerPos == null || facing == null || style == null || footprint == null
                || !footprint.contains(cornerPos)) {
            return ReservationResult.rejected();
        }
        MutableLandmark existing = landmarks.get(key);
        if (existing != null) {
            return new ReservationResult(existing.snapshot(key), false);
        }
        if (landmarks.size() >= MAX_LANDMARKS) {
            return ReservationResult.rejected();
        }
        MutableLandmark created = new MutableLandmark(cornerPos, facing, style, footprint,
                LandmarkStatus.PENDING, 0, 0L, 0);
        landmarks.put(key, created);
        setDirty();
        return new ReservationResult(created.snapshot(key), true);
    }

    public Optional<LandmarkSnapshot> snapshot(VillageKey key) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        return landmark == null ? Optional.empty() : Optional.of(landmark.snapshot(key));
    }

    public Optional<LandmarkSnapshot> findAt(String dimension, BlockPos cornerPos) {
        if (!validDimension(dimension) || cornerPos == null) return Optional.empty();
        return landmarks.entrySet().stream()
                .filter(entry -> entry.getKey().dimension().equals(dimension)
                        && entry.getValue().cornerPos.equals(cornerPos))
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .findFirst();
    }

    /** Marks a successfully placed candidate. Generated records are never used to restore blocks. */
    public boolean markGenerated(VillageKey key) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        if (landmark == null || landmark.status != LandmarkStatus.PENDING) return false;
        landmark.status = LandmarkStatus.GENERATED;
        landmark.retryAfterGameTime = 0L;
        landmark.revision++;
        setDirty();
        return true;
    }

    /**
     * Records deliberate removal of the generated notice post. This is terminal: reserve and
     * automatic generation will continue to return the same removed record instead of rebuilding.
     */
    public boolean markRemovedByPlayer(VillageKey key) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        if (landmark == null || landmark.status != LandmarkStatus.GENERATED) return false;
        landmark.status = LandmarkStatus.REMOVED_BY_PLAYER;
        landmark.retryAfterGameTime = 0L;
        landmark.revision++;
        setDirty();
        return true;
    }

    public boolean markRemovedByPlayer(String dimension, BlockPos cornerPos) {
        Optional<LandmarkSnapshot> landmark = findAt(dimension, cornerPos);
        return landmark.isPresent() && markRemovedByPlayer(landmark.get().key());
    }

    /**
     * Delays a placement check without recording a permanent failure. Once the bounded automatic
     * attempts are exhausted, the record remains pending for an explicit/manual placement path.
     */
    public RetryResult scheduleRetry(VillageKey key, long currentGameTime) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        if (landmark == null || landmark.status != LandmarkStatus.PENDING) {
            return new RetryResult(landmark == null ? null : landmark.snapshot(key), false);
        }
        if (landmark.retryAttempts >= MAX_RETRY_ATTEMPTS) {
            return new RetryResult(landmark.snapshot(key), false);
        }
        landmark.retryAttempts++;
        landmark.retryAfterGameTime = saturatedAdd(Math.max(0L, currentGameTime), RETRY_DELAY_TICKS);
        landmark.revision++;
        setDirty();
        return new RetryResult(landmark.snapshot(key), true);
    }

    public boolean canAttemptAutomaticPlacement(VillageKey key, long currentGameTime) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        return landmark != null
                && landmark.status == LandmarkStatus.PENDING
                && landmark.retryAttempts < MAX_RETRY_ATTEMPTS
                && Math.max(0L, currentGameTime) >= landmark.retryAfterGameTime;
    }

    public boolean needsManualPlacement(VillageKey key) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        return landmark != null
                && landmark.status == LandmarkStatus.PENDING
                && landmark.retryAttempts >= MAX_RETRY_ATTEMPTS;
    }

    /** Stops automatic retries after an unsafe or incompatible reserved site is observed. */
    public boolean requireManualPlacement(VillageKey key) {
        MutableLandmark landmark = key == null ? null : landmarks.get(key);
        if (landmark == null || landmark.status != LandmarkStatus.PENDING
                || landmark.retryAttempts >= MAX_RETRY_ATTEMPTS) return false;
        landmark.retryAttempts = MAX_RETRY_ATTEMPTS;
        landmark.retryAfterGameTime = 0L;
        landmark.revision++;
        setDirty();
        return true;
    }

    static GuildCornerLandmarkState fromNbt(CompoundTag root) {
        GuildCornerLandmarkState state = new GuildCornerLandmarkState();
        if (root == null || root.isEmpty()) return state;
        int sourceVersion = Math.max(0, root.getIntOr("schemaVersion", 0));
        if (sourceVersion > CURRENT_SCHEMA_VERSION) return state;
        ListTag entries = root.getListOrEmpty("landmarks");
        for (int index = 0; index < entries.size() && state.landmarks.size() < MAX_LANDMARKS; index++) {
            CompoundTag entry = entries.getCompoundOrEmpty(index);
            VillageKey key = parseKey(entry);
            if (key == null || state.landmarks.containsKey(key)) continue;
            BlockPos cornerPos = new BlockPos(
                    entry.getIntOr("cornerX", 0),
                    entry.getIntOr("cornerY", 0),
                    entry.getIntOr("cornerZ", 0));
            Footprint footprint = parseFootprint(entry, cornerPos);
            MutableLandmark landmark = new MutableLandmark(
                    cornerPos,
                    CornerFacing.byId(entry.getIntOr("facing", 0)),
                    CornerStyle.byId(entry.getIntOr("style", 0)),
                    footprint,
                    LandmarkStatus.byId(entry.getIntOr("status", 0)),
                    Math.max(0, Math.min(MAX_RETRY_ATTEMPTS, entry.getIntOr("retryAttempts", 0))),
                    Math.max(0L, entry.getLongOr("retryAfterGameTime", 0L)),
                    Math.max(0, entry.getIntOr("revision", 0)));
            if (landmark.status != LandmarkStatus.PENDING) {
                landmark.retryAfterGameTime = 0L;
            }
            state.landmarks.put(key, landmark);
        }
        state.schemaVersion = CURRENT_SCHEMA_VERSION;
        if (sourceVersion != CURRENT_SCHEMA_VERSION) state.setDirty();
        return state;
    }

    static CompoundTag toNbt(GuildCornerLandmarkState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        ListTag entries = new ListTag();
        state.landmarks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(mapEntry -> {
                    VillageKey key = mapEntry.getKey();
                    MutableLandmark landmark = mapEntry.getValue();
                    CompoundTag entry = new CompoundTag();
                    entry.putString("dimension", key.dimension());
                    entry.putInt("villageX", key.anchorX());
                    entry.putInt("villageZ", key.anchorZ());
                    entry.putInt("cornerX", landmark.cornerPos.getX());
                    entry.putInt("cornerY", landmark.cornerPos.getY());
                    entry.putInt("cornerZ", landmark.cornerPos.getZ());
                    entry.putInt("facing", landmark.facing.id());
                    entry.putInt("style", landmark.style.id());
                    entry.putInt("minX", landmark.footprint.minX());
                    entry.putInt("minY", landmark.footprint.minY());
                    entry.putInt("minZ", landmark.footprint.minZ());
                    entry.putInt("maxX", landmark.footprint.maxX());
                    entry.putInt("maxY", landmark.footprint.maxY());
                    entry.putInt("maxZ", landmark.footprint.maxZ());
                    entry.putInt("status", landmark.status.id());
                    entry.putInt("retryAttempts", landmark.retryAttempts);
                    entry.putLong("retryAfterGameTime", landmark.retryAfterGameTime);
                    entry.putInt("revision", landmark.revision);
                    entries.add(entry);
                });
        root.put("landmarks", entries);
        return root;
    }

    private static VillageKey parseKey(CompoundTag entry) {
        String dimension = entry.getStringOr("dimension", "");
        if (!validDimension(dimension)) return null;
        try {
            return new VillageKey(dimension,
                    entry.getIntOr("villageX", 0), entry.getIntOr("villageZ", 0));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Footprint parseFootprint(CompoundTag entry, BlockPos cornerPos) {
        try {
            return new Footprint(
                    entry.getIntOr("minX", cornerPos.getX()),
                    entry.getIntOr("minY", cornerPos.getY()),
                    entry.getIntOr("minZ", cornerPos.getZ()),
                    entry.getIntOr("maxX", cornerPos.getX()),
                    entry.getIntOr("maxY", cornerPos.getY()),
                    entry.getIntOr("maxZ", cornerPos.getZ()));
        } catch (IllegalArgumentException ignored) {
            return Footprint.singleBlock(cornerPos);
        }
    }

    private static boolean validDimension(String dimension) {
        return dimension != null && dimension.length() <= 128
                && DIMENSION_PATTERN.matcher(dimension).matches();
    }

    private static long saturatedAdd(long value, long addend) {
        return value > Long.MAX_VALUE - addend ? Long.MAX_VALUE : value + addend;
    }

    public record VillageKey(String dimension, int anchorX, int anchorZ) implements Comparable<VillageKey> {
        public VillageKey {
            if (!validDimension(dimension)) {
                throw new IllegalArgumentException("Invalid dimension key");
            }
        }

        public static VillageKey overworld(int anchorX, int anchorZ) {
            return new VillageKey(OVERWORLD, anchorX, anchorZ);
        }

        @Override
        public int compareTo(VillageKey other) {
            int dimensionOrder = dimension.compareTo(other.dimension);
            if (dimensionOrder != 0) return dimensionOrder;
            int xOrder = Integer.compare(anchorX, other.anchorX);
            return xOrder != 0 ? xOrder : Integer.compare(anchorZ, other.anchorZ);
        }
    }

    public record Footprint(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Footprint {
            if (minX > maxX || minY > maxY || minZ > maxZ
                    || (long) maxX - minX + 1L > MAX_FOOTPRINT_SPAN
                    || (long) maxY - minY + 1L > MAX_FOOTPRINT_SPAN
                    || (long) maxZ - minZ + 1L > MAX_FOOTPRINT_SPAN) {
                throw new IllegalArgumentException("Invalid Guild Corner footprint");
            }
        }

        public static Footprint singleBlock(BlockPos pos) {
            return new Footprint(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
        }

        public boolean contains(BlockPos pos) {
            return pos != null
                    && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    public enum CornerFacing {
        NORTH(0), EAST(1), SOUTH(2), WEST(3);

        private final int id;

        CornerFacing(int id) { this.id = id; }
        public int id() { return id; }

        public static CornerFacing byId(int id) {
            for (CornerFacing value : values()) if (value.id == id) return value;
            return NORTH;
        }
    }

    public enum CornerStyle {
        GENERIC(0), PLAINS(1), DESERT(2), SAVANNA(3), TAIGA(4), SNOWY(5),
        JUNGLE(6), SWAMP(7), CHERRY(8);

        private final int id;

        CornerStyle(int id) { this.id = id; }
        public int id() { return id; }

        public static CornerStyle byId(int id) {
            for (CornerStyle value : values()) if (value.id == id) return value;
            return GENERIC;
        }
    }

    public enum LandmarkStatus {
        PENDING(0), GENERATED(1), REMOVED_BY_PLAYER(2);

        private final int id;

        LandmarkStatus(int id) { this.id = id; }
        public int id() { return id; }

        public static LandmarkStatus byId(int id) {
            for (LandmarkStatus value : values()) if (value.id == id) return value;
            return PENDING;
        }
    }

    public record LandmarkSnapshot(VillageKey key, BlockPos cornerPos, CornerFacing facing,
                                   CornerStyle style, Footprint footprint, LandmarkStatus status,
                                   int retryAttempts, long retryAfterGameTime, int revision) {}

    public record ReservationResult(LandmarkSnapshot landmark, boolean created) {
        static ReservationResult rejected() { return new ReservationResult(null, false); }
        public boolean accepted() { return landmark != null; }
    }

    public record RetryResult(LandmarkSnapshot landmark, boolean scheduled) {}

    private static final class MutableLandmark {
        private final BlockPos cornerPos;
        private final CornerFacing facing;
        private final CornerStyle style;
        private final Footprint footprint;
        private LandmarkStatus status;
        private int retryAttempts;
        private long retryAfterGameTime;
        private int revision;

        private MutableLandmark(BlockPos cornerPos, CornerFacing facing, CornerStyle style,
                                Footprint footprint, LandmarkStatus status, int retryAttempts,
                                long retryAfterGameTime, int revision) {
            this.cornerPos = cornerPos.immutable();
            this.facing = facing;
            this.style = style;
            this.footprint = footprint;
            this.status = status;
            this.retryAttempts = retryAttempts;
            this.retryAfterGameTime = retryAfterGameTime;
            this.revision = revision;
        }

        private LandmarkSnapshot snapshot(VillageKey key) {
            return new LandmarkSnapshot(key, cornerPos, facing, style, footprint, status,
                    retryAttempts, retryAfterGameTime, revision);
        }
    }
}
