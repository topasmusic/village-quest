package de.quest.village;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Physical settlement population state, independent of route and player ownership. */
public final class VillageLifeState extends SavedData {
    private static final String ID = "village_quest_village_life";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_VILLAGES = 16_384;
    private static final int EMPTY_CONFIRMATIONS = 3;
    private static final int RESETTLEMENT_CONFIRMATIONS = 3;
    private static final long RECOVERY_LOADED_TICKS = 20L * 60L;
    private static final long MIN_CONFIRM_INTERVAL_TICKS = 20L * 5L;
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public static final SavedDataType<VillageLifeState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID), VillageLifeState::new,
            CompoundTag.CODEC.xmap(VillageLifeState::fromNbt, VillageLifeState::toNbt),
            DataFixTypes.LEVEL);

    private final Map<VillageKey, MutableVillage> villages = new HashMap<>();

    VillageLifeState() {}

    public static VillageLifeState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Missing 2.4.x data preserves existing operational behavior until loaded evidence is available. */
    public Status status(VillageKey key) {
        MutableVillage village = villages.get(key);
        return village == null ? Status.ACTIVE : village.status;
    }

    public int abandonmentCycle(VillageKey key) {
        MutableVillage village = villages.get(key);
        return village == null ? 0 : village.abandonmentCycle;
    }

    public Observation observe(VillageKey key, Evidence evidence, long gameTime) {
        if (key == null || evidence == null) return new Observation(Status.ACTIVE, false);
        MutableVillage village = villages.get(key);
        if (evidence != Evidence.EMPTY_LOADED) {
            if (village != null && village.emptyConfirmations != 0) {
                village.emptyConfirmations = 0;
                village.lastEmptyTick = 0;
                setDirty();
            }
            return new Observation(status(key), false);
        }
        if (village == null) {
            if (villages.size() >= MAX_VILLAGES) return new Observation(Status.ACTIVE, false);
            village = new MutableVillage();
            villages.put(key, village);
        }
        if (village.status == Status.ABANDONED) return new Observation(Status.ABANDONED, false);
        long now = Math.max(0L, gameTime);
        if (village.emptyConfirmations > 0
                && now - village.lastEmptyTick < MIN_CONFIRM_INTERVAL_TICKS) {
            return new Observation(village.status, false);
        }
        village.emptyConfirmations++;
        village.lastEmptyTick = now;
        if (village.emptyConfirmations >= EMPTY_CONFIRMATIONS) {
            boolean newCycle = village.status == Status.ACTIVE;
            village.status = Status.ABANDONED;
            if (newCycle) village.abandonmentCycle = Math.max(1, village.abandonmentCycle + 1);
            village.emptyConfirmations = 0;
            setDirty();
            return new Observation(Status.ABANDONED, true);
        }
        setDirty();
        return new Observation(village.status, false);
    }

    /** Counts only loaded, viable observations; elapsed unloaded time never advances recovery. */
    public RecoveryObservation observeResettlement(VillageKey key, int adultVillagers, int beds,
                                                   boolean fullyLoaded, long gameTime) {
        MutableVillage village = key == null ? null : villages.get(key);
        if (village == null || village.status == Status.ACTIVE) {
            return new RecoveryObservation(Status.ACTIVE, false, false);
        }
        if (!fullyLoaded) {
            if (village.resettlementConfirmations != 0 || village.lastResettlementTick != 0) {
                village.resettlementConfirmations = 0;
                village.lastResettlementTick = 0;
                setDirty();
            }
            return new RecoveryObservation(village.status, false, false);
        }
        if (adultVillagers < 2 || beds < 2) {
            boolean changed = village.resettlementConfirmations != 0
                    || village.lastResettlementTick != 0
                    || village.recoveryLoadedTicks != 0 || village.status != Status.ABANDONED
                    || village.emptyConfirmations != 0;
            village.resettlementConfirmations = 0;
            village.lastResettlementTick = 0;
            village.recoveryLoadedTicks = 0;
            village.emptyConfirmations = 0;
            village.lastEmptyTick = 0;
            village.status = Status.ABANDONED;
            if (changed) setDirty();
            return new RecoveryObservation(Status.ABANDONED, false, false);
        }
        long now = Math.max(0L, gameTime);
        if (village.lastResettlementTick > 0
                && now - village.lastResettlementTick < MIN_CONFIRM_INTERVAL_TICKS) {
            return new RecoveryObservation(village.status, false, false);
        }
        long elapsedLoaded = village.lastResettlementTick == 0 ? 0
                : Math.min(MIN_CONFIRM_INTERVAL_TICKS, now - village.lastResettlementTick);
        village.lastResettlementTick = now;
        if (village.status == Status.ABANDONED) {
            village.resettlementConfirmations++;
            if (village.resettlementConfirmations >= RESETTLEMENT_CONFIRMATIONS) {
                village.status = Status.RECOVERING;
                village.recoveryLoadedTicks = 0;
                setDirty();
                return new RecoveryObservation(Status.RECOVERING, true, false);
            }
        } else {
            village.recoveryLoadedTicks += elapsedLoaded;
            if (village.recoveryLoadedTicks >= RECOVERY_LOADED_TICKS) {
                village.status = Status.ACTIVE;
                village.resettlementConfirmations = 0;
                village.recoveryLoadedTicks = 0;
                setDirty();
                return new RecoveryObservation(Status.ACTIVE, false, true);
            }
        }
        setDirty();
        return new RecoveryObservation(village.status, false, false);
    }

    static VillageLifeState fromNbt(CompoundTag root) {
        VillageLifeState state = new VillageLifeState();
        if (root == null || root.getIntOr("schemaVersion", 0) > SCHEMA_VERSION) return state;
        ListTag entries = root.getListOrEmpty("villages");
        for (int index = 0; index < entries.size() && state.villages.size() < MAX_VILLAGES; index++) {
            CompoundTag entry = entries.getCompoundOrEmpty(index);
            try {
                VillageKey key = new VillageKey(entry.getStringOr("dimension", ""),
                        entry.getIntOr("x", 0), entry.getIntOr("z", 0));
                if (state.villages.containsKey(key)) continue;
                MutableVillage village = new MutableVillage();
                village.status = Status.byId(entry.getIntOr("status", 0));
                village.abandonmentCycle = Math.max(0, entry.getIntOr("abandonmentCycle",
                        village.status == Status.ACTIVE ? 0 : 1));
                village.emptyConfirmations = Math.max(0,
                        Math.min(EMPTY_CONFIRMATIONS - 1, entry.getIntOr("emptyConfirmations", 0)));
                village.lastEmptyTick = Math.max(0L, entry.getLongOr("lastEmptyTick", 0L));
                village.resettlementConfirmations = Math.max(0,
                        Math.min(RESETTLEMENT_CONFIRMATIONS - 1,
                                entry.getIntOr("resettlementConfirmations", 0)));
                village.lastResettlementTick = Math.max(0L,
                        entry.getLongOr("lastResettlementTick", 0L));
                village.recoveryLoadedTicks = Math.max(0L,
                        Math.min(RECOVERY_LOADED_TICKS, entry.getLongOr("recoveryLoadedTicks", 0L)));
                state.villages.put(key, village);
            } catch (IllegalArgumentException ignored) {
                // A malformed optional 2.5 record cannot invalidate the saved world.
            }
        }
        return state;
    }

    static CompoundTag toNbt(VillageLifeState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        state.villages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", item.getKey().dimension());
            entry.putInt("x", item.getKey().anchorX());
            entry.putInt("z", item.getKey().anchorZ());
            entry.putInt("status", item.getValue().status.id);
            entry.putInt("abandonmentCycle", item.getValue().abandonmentCycle);
            entry.putInt("emptyConfirmations", item.getValue().emptyConfirmations);
            entry.putLong("lastEmptyTick", item.getValue().lastEmptyTick);
            entry.putInt("resettlementConfirmations", item.getValue().resettlementConfirmations);
            entry.putLong("lastResettlementTick", item.getValue().lastResettlementTick);
            entry.putLong("recoveryLoadedTicks", item.getValue().recoveryLoadedTicks);
            entries.add(entry);
        });
        root.put("villages", entries);
        return root;
    }

    public enum Status {
        ACTIVE(0), ABANDONED(1), RECOVERING(2);

        private final int id;
        Status(int id) { this.id = id; }
        static Status byId(int id) {
            for (Status value : values()) if (value.id == id) return value;
            return ACTIVE;
        }
    }

    public enum Evidence { INHABITED, EMPTY_LOADED, UNKNOWN }

    public record Observation(Status status, boolean becameAbandoned) {}
    public record RecoveryObservation(Status status, boolean becameRecovering, boolean becameActive) {}

    public record VillageKey(String dimension, int anchorX, int anchorZ) implements Comparable<VillageKey> {
        public VillageKey {
            if (dimension == null || dimension.length() > 128
                    || !DIMENSION_PATTERN.matcher(dimension).matches()) {
                throw new IllegalArgumentException("Invalid dimension key");
            }
        }

        public static VillageKey overworld(int anchorX, int anchorZ) {
            return new VillageKey("minecraft:overworld", anchorX, anchorZ);
        }

        @Override
        public int compareTo(VillageKey other) {
            int dimensionOrder = dimension.compareTo(other.dimension);
            if (dimensionOrder != 0) return dimensionOrder;
            int xOrder = Integer.compare(anchorX, other.anchorX);
            return xOrder != 0 ? xOrder : Integer.compare(anchorZ, other.anchorZ);
        }
    }

    private static final class MutableVillage {
        private Status status = Status.ACTIVE;
        private int abandonmentCycle;
        private int emptyConfirmations;
        private long lastEmptyTick;
        private int resettlementConfirmations;
        private long lastResettlementTick;
        private long recoveryLoadedTicks;
    }
}
