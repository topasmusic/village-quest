package de.quest.village;

import de.quest.shrine.VillageBondType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Versioned 2.3 state for village supply conditions. The separate file keeps
 * mutable network conditions independent from the compatible 2.2 trust data.
 */
public final class LivingVillageNetworkState extends SavedData {
    static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int DEFAULT_SUPPORT = 50;
    private static final int SUPPORT_CYCLE_TARGET = 100;
    private static final int SUPPORT_AFTER_CYCLE = 55;
    private static final String ID = "village_quest_living_network";

    public static final SavedDataType<LivingVillageNetworkState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(ID),
            LivingVillageNetworkState::new,
            CompoundTag.CODEC.xmap(LivingVillageNetworkState::fromNbt, LivingVillageNetworkState::toNbt),
            DataFixTypes.LEVEL);

    private final Map<VillageKey, MutableVillage> villages = new HashMap<>();
    private final Map<UUID, MutableNetwork> networks = new HashMap<>();
    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    LivingVillageNetworkState() {}

    public static LivingVillageNetworkState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Map<VillageKey, VillageSnapshot> villagesView() {
        Map<VillageKey, VillageSnapshot> result = new HashMap<>();
        villages.forEach((key, value) -> result.put(key, value.snapshot(key.index())));
        return Collections.unmodifiableMap(result);
    }

    public int removeOwner(UUID ownerId) {
        if (ownerId == null) return 0;
        int before = villages.size();
        villages.keySet().removeIf(key -> key.ownerId().equals(ownerId));
        int removed = before - villages.size();
        boolean networkRemoved = networks.remove(ownerId) != null;
        if (removed > 0 || networkRemoved) setDirty();
        return removed;
    }

    public void resetAllProgress() {
        if (villages.isEmpty() && networks.isEmpty()) return;
        villages.clear();
        networks.clear();
        setDirty();
    }

    public VillageSnapshot ensureVillage(UUID ownerId, int index, int x, int z, VillageBondType type) {
        if (ownerId == null) throw new IllegalArgumentException("ownerId must not be null");
        int safeIndex = Math.max(0, index);
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        VillageKey key = new VillageKey(ownerId, safeIndex);
        MutableVillage existing = villages.get(key);
        if (existing == null) {
            VillageNeed need = VillageNeed.forVillage(safeType, initialNeedSeed(ownerId, safeIndex, x, z));
            existing = new MutableVillage(x, z, safeType, need, DEFAULT_SUPPORT, 0, 0L,
                    0, 0, 0, 0);
            villages.put(key, existing);
            networks.computeIfAbsent(ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
            setDirty();
        } else if (existing.x != x || existing.z != z || existing.type != safeType) {
            existing.x = x;
            existing.z = z;
            existing.type = safeType;
            if (existing.need.villageType() != safeType) {
                existing.need = VillageNeed.forVillage(safeType, initialNeedSeed(ownerId, safeIndex, x, z));
                existing.support = DEFAULT_SUPPORT;
                existing.cyclesCompleted = 0;
            }
            existing.revision++;
            setDirty();
        }
        return existing.snapshot(safeIndex);
    }

    public Optional<VillageSnapshot> snapshot(UUID ownerId, int index) {
        if (ownerId == null || index < 0) return Optional.empty();
        MutableVillage village = villages.get(new VillageKey(ownerId, index));
        return village == null ? Optional.empty() : Optional.of(village.snapshot(index));
    }

    public SupportResult addSupport(UUID ownerId, int index, int amount, long interactionDay) {
        if (ownerId == null || index < 0 || amount <= 0) {
            VillageSnapshot unchanged = snapshot(ownerId, index).orElse(null);
            return new SupportResult(unchanged, false, 0);
        }
        VillageKey key = new VillageKey(ownerId, index);
        MutableVillage village = villages.get(key);
        if (village == null) return new SupportResult(null, false, 0);

        int applied = Math.max(0, Math.min(100, amount));
        int accumulated = village.support + applied;
        boolean advanced = accumulated >= SUPPORT_CYCLE_TARGET;
        if (advanced) {
            village.need = VillageNeed.next(village.type, village.need);
            village.support = SUPPORT_AFTER_CYCLE;
            village.cyclesCompleted++;
        } else {
            village.support = Math.min(SUPPORT_CYCLE_TARGET, accumulated);
        }
        village.lastInteractionDay = Math.max(village.lastInteractionDay, Math.max(0L, interactionDay));
        village.revision++;
        MutableNetwork network = networks.computeIfAbsent(
                ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
        network.renown = Math.min(1_000_000, network.renown + Math.max(1, applied / 4) + (advanced ? 5 : 0));
        network.revision++;
        setDirty();
        return new SupportResult(village.snapshot(index), advanced, applied);
    }

    /** Applies a bounded, repairable setback without touching earned trust or completed cycles. */
    public VillageSnapshot addStrain(UUID ownerId, int index, int amount, long interactionDay) {
        if (ownerId == null || index < 0 || amount <= 0) return snapshot(ownerId, index).orElse(null);
        MutableVillage village = villages.get(new VillageKey(ownerId, index));
        if (village == null) return null;
        village.support = Math.max(10, village.support - Math.min(40, amount));
        village.routeFailures++;
        village.lastInteractionDay = Math.max(village.lastInteractionDay, Math.max(0L, interactionDay));
        village.revision++;
        setDirty();
        return village.snapshot(index);
    }

    /** Records a route arrival and converts every three energy steps into one Wayshrine charge. */
    public RouteResult recordRouteArrival(UUID ownerId, int index, int support,
                                          int energySteps, long interactionDay) {
        if (ownerId == null || index < 0) return new RouteResult(null, 0, false);
        MutableVillage village = villages.get(new VillageKey(ownerId, index));
        if (village == null) return new RouteResult(null, 0, false);
        village.routeArrivals++;
        int totalEnergy = village.energyProgress + Math.max(0, energySteps);
        int earnedCharges = totalEnergy / 3;
        village.energyProgress = totalEnergy % 3;
        village.lastInteractionDay = Math.max(village.lastInteractionDay, Math.max(0L, interactionDay));
        village.revision++;
        SupportResult supportResult = addSupport(ownerId, index, Math.max(0, support), interactionDay);
        MutableNetwork network = networks.computeIfAbsent(
                ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
        network.renown = Math.min(1_000_000, network.renown + 2 + Math.max(0, support / 6));
        network.revision++;
        setDirty();
        return new RouteResult(supportResult.village(), earnedCharges, supportResult.needAdvanced());
    }

    public NetworkSnapshot network(UUID ownerId) {
        if (ownerId == null) return new NetworkSnapshot(0, NetworkSpecialization.NONE, 1, 75, 0);
        MutableNetwork value = networks.computeIfAbsent(
                ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
        return value.snapshot();
    }

    public boolean specialize(UUID ownerId, NetworkSpecialization specialization) {
        if (ownerId == null || specialization == null || specialization == NetworkSpecialization.NONE) return false;
        MutableNetwork network = networks.computeIfAbsent(
                ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
        if (network.specialization != NetworkSpecialization.NONE || networkRank(network.renown) < 2) return false;
        network.specialization = specialization;
        network.revision++;
        setDirty();
        return true;
    }

    /** Exact, bounded village condition used only by explicit admin QA fixtures. */
    public VillageSnapshot adminSetVillageSupport(UUID ownerId, int index, int support) {
        if (ownerId == null || index < 0) return null;
        MutableVillage village = villages.get(new VillageKey(ownerId, index));
        if (village == null) return null;
        village.support = Math.max(0, Math.min(100, support));
        village.revision++;
        setDirty();
        return village.snapshot(index);
    }

    /** Exact, bounded prestige used only by explicit admin QA fixtures. */
    public NetworkSnapshot adminSetRenown(UUID ownerId, int renown) {
        if (ownerId == null) return network(null);
        MutableNetwork network = networks.computeIfAbsent(
                ownerId, ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0));
        network.renown = Math.max(0, Math.min(1_000_000, renown));
        network.specialization = NetworkSpecialization.NONE;
        network.revision++;
        setDirty();
        return network.snapshot();
    }

    public static int networkRank(int renown) {
        int safe = Math.max(0, renown);
        if (safe >= 800) return 5;
        if (safe >= 450) return 4;
        if (safe >= 200) return 3;
        if (safe >= 75) return 2;
        return 1;
    }

    public static int nextRankThreshold(int renown) {
        return switch (networkRank(renown)) {
            case 1 -> 75;
            case 2 -> 200;
            case 3 -> 450;
            case 4 -> 800;
            default -> 0;
        };
    }

    static LivingVillageNetworkState fromNbt(CompoundTag root) {
        LivingVillageNetworkState state = new LivingVillageNetworkState();
        if (root == null || root.isEmpty()) return state;

        int sourceVersion = Math.max(0, root.getIntOr("schemaVersion", 0));
        ListTag entries = root.getListOrEmpty("villages");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            UUID ownerId = parseUuid(entry.getStringOr("owner", ""));
            int index = entry.getIntOr("index", -1);
            if (ownerId == null || index < 0) continue;

            int x = entry.getIntOr("x", 0);
            int z = entry.getIntOr("z", 0);
            VillageBondType type = VillageBondType.byId(entry.getIntOr("type", 0));
            int seed = initialNeedSeed(ownerId, index, x, z);
            VillageNeed need = VillageNeed.byId(entry.getIntOr("need", -1), type, seed);
            int support = sourceVersion <= 0 && !entry.contains("support")
                    ? DEFAULT_SUPPORT
                    : Math.max(0, Math.min(100, entry.getIntOr("support", DEFAULT_SUPPORT)));
            int cycles = Math.max(0, entry.getIntOr("cyclesCompleted", 0));
            long lastDay = Math.max(0L, entry.getLongOr("lastInteractionDay", 0L));
            int routeArrivals = Math.max(0, entry.getIntOr("routeArrivals", 0));
            int routeFailures = Math.max(0, entry.getIntOr("routeFailures", 0));
            int energyProgress = Math.max(0, Math.min(2, entry.getIntOr("energyProgress", 0)));
            int revision = Math.max(0, entry.getIntOr("revision", 0));
            state.villages.put(new VillageKey(ownerId, index),
                    new MutableVillage(x, z, type, need, support, cycles, lastDay,
                            routeArrivals, routeFailures, energyProgress, revision));
        }
        ListTag networkEntries = root.getListOrEmpty("networks");
        for (int i = 0; i < networkEntries.size(); i++) {
            CompoundTag entry = networkEntries.getCompoundOrEmpty(i);
            UUID ownerId = parseUuid(entry.getStringOr("owner", ""));
            if (ownerId == null) continue;
            state.networks.put(ownerId, new MutableNetwork(
                    Math.max(0, entry.getIntOr("renown", 0)),
                    NetworkSpecialization.byId(entry.getIntOr("specialization", 0)),
                    Math.max(0, entry.getIntOr("revision", 0))));
        }
        state.villages.keySet().forEach(key -> state.networks.computeIfAbsent(
                key.ownerId(), ignored -> new MutableNetwork(0, NetworkSpecialization.NONE, 0)));
        state.schemaVersion = CURRENT_SCHEMA_VERSION;
        if (sourceVersion != CURRENT_SCHEMA_VERSION) state.setDirty();
        return state;
    }

    static CompoundTag toNbt(LivingVillageNetworkState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        ListTag entries = new ListTag();
        state.villages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(mapEntry -> {
                    VillageKey key = mapEntry.getKey();
                    MutableVillage village = mapEntry.getValue();
                    CompoundTag entry = new CompoundTag();
                    entry.putString("owner", key.ownerId().toString());
                    entry.putInt("index", key.index());
                    entry.putInt("x", village.x);
                    entry.putInt("z", village.z);
                    entry.putInt("type", village.type.id());
                    entry.putInt("need", village.need.id());
                    entry.putInt("support", village.support);
                    entry.putInt("cyclesCompleted", village.cyclesCompleted);
                    entry.putLong("lastInteractionDay", village.lastInteractionDay);
                    entry.putInt("routeArrivals", village.routeArrivals);
                    entry.putInt("routeFailures", village.routeFailures);
                    entry.putInt("energyProgress", village.energyProgress);
                    entry.putInt("revision", village.revision);
                    entries.add(entry);
                });
        root.put("villages", entries);
        ListTag networkEntries = new ListTag();
        state.networks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.toString().compareTo(right.toString())))
                .forEach(mapEntry -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("owner", mapEntry.getKey().toString());
                    entry.putInt("renown", mapEntry.getValue().renown);
                    entry.putInt("specialization", mapEntry.getValue().specialization.id());
                    entry.putInt("revision", mapEntry.getValue().revision);
                    networkEntries.add(entry);
                });
        root.put("networks", networkEntries);
        return root;
    }

    private static int initialNeedSeed(UUID ownerId, int index, int x, int z) {
        return ownerId.hashCode() * 31 + index * 17 + x * 13 + z;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record VillageKey(UUID ownerId, int index) implements Comparable<VillageKey> {
        @Override
        public int compareTo(VillageKey other) {
            int ownerOrder = ownerId.toString().compareTo(other.ownerId.toString());
            return ownerOrder != 0 ? ownerOrder : Integer.compare(index, other.index);
        }
    }

    public record VillageSnapshot(int index, int x, int z, VillageBondType type,
                                  VillageNeed need, int support, VillageCondition condition,
                                  int cyclesCompleted, long lastInteractionDay,
                                  int routeArrivals, int routeFailures, int energyProgress, int revision) {}

    public record SupportResult(VillageSnapshot village, boolean needAdvanced, int supportApplied) {}
    public record RouteResult(VillageSnapshot village, int earnedCharges, boolean needAdvanced) {}
    public record NetworkSnapshot(int renown, NetworkSpecialization specialization,
                                  int rank, int nextRankThreshold, int revision) {}

    private static final class MutableVillage {
        private int x;
        private int z;
        private VillageBondType type;
        private VillageNeed need;
        private int support;
        private int cyclesCompleted;
        private long lastInteractionDay;
        private int routeArrivals;
        private int routeFailures;
        private int energyProgress;
        private int revision;

        private MutableVillage(int x, int z, VillageBondType type, VillageNeed need,
                               int support, int cyclesCompleted, long lastInteractionDay,
                               int routeArrivals, int routeFailures, int energyProgress, int revision) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.need = need;
            this.support = support;
            this.cyclesCompleted = cyclesCompleted;
            this.lastInteractionDay = lastInteractionDay;
            this.routeArrivals = routeArrivals;
            this.routeFailures = routeFailures;
            this.energyProgress = energyProgress;
            this.revision = revision;
        }

        private VillageSnapshot snapshot(int index) {
            return new VillageSnapshot(index, x, z, type, need, support,
                    VillageCondition.fromSupport(support), cyclesCompleted, lastInteractionDay,
                    routeArrivals, routeFailures, energyProgress, revision);
        }
    }

    private static final class MutableNetwork {
        private int renown;
        private NetworkSpecialization specialization;
        private int revision;

        private MutableNetwork(int renown, NetworkSpecialization specialization, int revision) {
            this.renown = renown;
            this.specialization = specialization == null ? NetworkSpecialization.NONE : specialization;
            this.revision = revision;
        }

        private NetworkSnapshot snapshot() {
            return new NetworkSnapshot(renown, specialization, networkRank(renown),
                    nextRankThreshold(renown), revision);
        }
    }
}
