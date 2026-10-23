package de.quest.kingdom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Founding certificates only. Does not replace the existing physical population/life service. */
public final class VillageFoundingRegistry {
    private static final int SCHEMA = 1, MAX_PROOFS = 16_384;
    private final LivingKingdomsState state;
    private final Map<UUID, Proof> proofs = new HashMap<>();
    private final Map<UUID, UUID> residents = new HashMap<>();
    private final Map<Place, UUID> beds = new HashMap<>(), jobs = new HashMap<>();
    private Tag retained = new CompoundTag();
    private boolean readOnly;
    VillageFoundingRegistry(LivingKingdomsState state) { this.state = state; }
    void adoptNew(VillageFoundingRegistry staged, UUID id) {
        Proof proof = staged.proofs.get(id);
        if (proof == null || proofs.containsKey(id)) throw new IllegalStateException("Invalid prepared founding proof");
        proofs.put(id, proof); index(proof);
    }

    /** Evidence supplied by the loaded-only native observer; never accept client resident lists. */
    public Result claim(UUID actor, UUID villageId, long revision, List<Resident> evidence,
                        boolean fullyLoaded, boolean validCouncil) {
        Result access = access(actor, villageId, revision);
        if (access != Result.APPLIED) return access;
        if (residents(villageId).size()==3) return Result.UNCHANGED;
        if (!fullyLoaded) return Result.UNKNOWN;
        if (state.people().beds().readOnly()) return Result.UNKNOWN;
        if (!validCouncil || evidence == null || evidence.size() > 64) return Result.INSUFFICIENT;
        var village = state.village(villageId).orElseThrow();
        List<Resident> selected = select(evidence, village.arrival());
        if (selected.size() != 3) return Result.INSUFFICIENT;
        if (conflict(villageId, village.legacyKey().dimension(), selected)) return Result.CONFLICT;
        Proof previous = proofs.get(villageId);
        if (previous != null && previous.active && previous.residents.equals(selected)) return Result.UNCHANGED;
        if (previous == null && proofs.size() >= MAX_PROOFS) return Result.READ_ONLY;
        if (previous != null) unindex(previous);
        Proof proof = new Proof(villageId, village.legacyKey().dimension(), village.arrival(), selected);
        if (previous != null) { proof.retained = previous.retained; proof.residentPayloads.putAll(previous.residentPayloads); }
        proofs.put(villageId, proof); index(proof); state.setDirty(); return Result.APPLIED;
    }
    public Result release(UUID actor, UUID villageId, long revision) {
        Result access = access(actor, villageId, revision);
        if (access != Result.APPLIED) return access;
        Proof proof = proofs.get(villageId);
        if (proof == null || !proof.active) return Result.UNCHANGED;
        unindex(proof); proof.active = false; state.setDirty(); return Result.APPLIED;
    }
    public List<Resident> residents(UUID villageId) {
        Proof proof = proofs.get(villageId);
        return readOnly || proof == null || !proof.active ? List.of() : proof.residents;
    }
    public Optional<UUID> residentOwner(UUID resident) { return Optional.ofNullable(residents.get(resident)); }
    public Optional<UUID> bedOwner(String dimension, BlockPos bed) { return Optional.ofNullable(beds.get(new Place(dimension, bed))); }
    public Optional<UUID> jobOwner(String dimension, BlockPos job) { return Optional.ofNullable(jobs.get(new Place(dimension, job))); }
    public boolean readOnly() { return readOnly || state.readOnly(); }

    private Result access(UUID actor, UUID villageId, long revision) {
        if (readOnly()) return Result.READ_ONLY;
        if (!state.canManageVillage(actor, villageId)) return Result.UNAUTHORIZED;
        return state.village(villageId).orElseThrow().revision() != revision || revision < 0 ? Result.STALE : Result.APPLIED;
    }
    static List<Resident> select(List<Resident> evidence, BlockPos arrival) {
        Set<UUID> ids = new HashSet<>(); Set<BlockPos> bedPositions = new HashSet<>();
        List<Resident> result = new ArrayList<>();
        evidence.stream().filter(r -> r != null && r.id != null && local(r.bed, arrival)
                && (r.job == null || local(r.job, arrival)))
                .sorted(Comparator.comparing((Resident r) -> r.job == null).thenComparing(Resident::id)).forEach(r -> {
                    if (result.size() < 3 && !ids.contains(r.id) && !bedPositions.contains(r.bed)) {
                        result.add(r); ids.add(r.id); bedPositions.add(r.bed);
                    }
                });
        return result.size() == 3 && result.stream().anyMatch(r -> r.job != null) ? List.copyOf(result) : List.of();
    }
    private boolean conflict(UUID village, String dimension, List<Resident> selected) {
        for (Resident resident : selected) {
            UUID owner = residents.get(resident.id);
            if (owner != null && !owner.equals(village)) return true;
            owner = beds.get(new Place(dimension, resident.bed));
            if (owner != null && !owner.equals(village)) return true;
            if (state.anchors().stationOwner(dimension, resident.bed).isPresent()) return true;
            if (state.people().beds().owner(dimension,resident.bed).isPresent()) return true;
            if (resident.job != null) {
                owner = jobs.get(new Place(dimension, resident.job));
                if (owner != null && !owner.equals(village)) return true;
                var anchor = state.anchors().stationOwner(dimension, resident.job).flatMap(state.anchors()::anchor);
                if (anchor.isPresent() && !anchor.get().villageId().equals(village)) return true;
            }
        }
        return false;
    }
    private void index(Proof proof) {
        if (!proof.active) return;
        for (Resident resident : proof.residents) {
            residents.put(resident.id, proof.village); beds.put(new Place(proof.dimension, resident.bed), proof.village);
            if (resident.job != null) jobs.put(new Place(proof.dimension, resident.job), proof.village);
        }
    }
    private void unindex(Proof proof) {
        for (Resident resident : proof.residents) {
            residents.remove(resident.id, proof.village); beds.remove(new Place(proof.dimension, resident.bed), proof.village);
            if (resident.job != null) jobs.remove(new Place(proof.dimension, resident.job), proof.village);
        }
    }
    private static boolean local(BlockPos pos, BlockPos arrival) {
        return pos != null && Math.abs((long) pos.getX()) <= 30_000_000 && Math.abs((long) pos.getZ()) <= 30_000_000
                && Math.abs((long) pos.getY()) <= 4096 && Math.abs((long) pos.getX() - arrival.getX()) <= 32
                && Math.abs((long) pos.getZ() - arrival.getZ()) <= 32 && Math.abs((long) pos.getY() - arrival.getY()) <= 16;
    }
    void load(Tag raw) {
        if (raw == null) return;
        retained = raw.copy();
        if (!(raw instanceof CompoundTag root) || root.getIntOr("schema", 0) > SCHEMA || root.getIntOr("schema", 0) < 0
                || root.contains("proofs") && !(root.get("proofs") instanceof ListTag)) { readOnly = true; return; }
        ListTag entries = root.getListOrEmpty("proofs");
        if (entries.size() > MAX_PROOFS) { readOnly = true; return; }
        try {
            for (Tag entry : entries) {
                if (!(entry instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid proof");
                UUID id = UUID.fromString(tag.getStringOr("village", ""));
                var village = state.village(id).orElseThrow();
                if (proofs.containsKey(id) || !(tag.get("residents") instanceof ListTag)) throw new IllegalArgumentException("Duplicate proof");
                List<Resident> evidence = new ArrayList<>(); Map<UUID, CompoundTag> payloads = new HashMap<>();
                for (Tag item : tag.getListOrEmpty("residents")) {
                    if (!(item instanceof CompoundTag resident)) throw new IllegalArgumentException("Invalid resident");
                    UUID residentId = UUID.fromString(resident.getStringOr("id", ""));
                    evidence.add(new Resident(residentId, readPos(resident, "bed"),
                            resident.getBooleanOr("hasJob", false) ? readPos(resident, "job") : null));
                    payloads.put(residentId, resident.copy());
                }
                BlockPos origin = tag.contains("originX") ? readPos(tag, "origin") : village.arrival();
                if (!validOrigin(origin)) throw new IllegalArgumentException("Invalid certificate origin");
                List<Resident> selected = select(evidence, origin);
                boolean active = tag.getBooleanOr("active", true);
                if (evidence.size() != 3 || selected.size() != 3 || active && conflict(id, village.legacyKey().dimension(), selected))
                    throw new IllegalArgumentException("Conflicting evidence");
                Proof proof = new Proof(id, village.legacyKey().dimension(), origin, selected); proof.active = active;
                proof.retained = tag.copy(); proof.residentPayloads.putAll(payloads); proofs.put(id, proof); index(proof);
            }
        } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) {
            // Optional corrupt certificates stay opaque; no silent erasure or reuse of their physical evidence.
            readOnly = true; proofs.clear(); residents.clear(); beds.clear(); jobs.clear();
        }
    }
    Tag toNbt() {
        if (readOnly) return retained.copy();
        CompoundTag root = retained instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
        root.putInt("schema", SCHEMA); ListTag entries = new ListTag();
        proofs.values().stream().sorted(Comparator.comparing(p -> p.village)).forEach(proof -> {
            CompoundTag tag = proof.retained.copy(); tag.putString("village", proof.village.toString()); tag.putBoolean("active", proof.active);
            writePos(tag, "origin", proof.origin);
            ListTag residents = new ListTag();
            for (Resident resident : proof.residents) {
                CompoundTag item = proof.residentPayloads.getOrDefault(resident.id, new CompoundTag()).copy();
                item.putString("id", resident.id.toString()); writePos(item, "bed", resident.bed);
                item.putBoolean("hasJob", resident.job != null);
                if (resident.job != null) writePos(item, "job", resident.job);
                residents.add(item);
            }
            tag.put("residents", residents); entries.add(tag);
        });
        root.put("proofs", entries); return root;
    }
    private static BlockPos readPos(CompoundTag tag, String prefix) {
        return new BlockPos(tag.getIntOr(prefix + "X", Integer.MIN_VALUE), tag.getIntOr(prefix + "Y", Integer.MIN_VALUE), tag.getIntOr(prefix + "Z", Integer.MIN_VALUE));
    }
    private static boolean validOrigin(BlockPos origin) {
        return Math.abs((long) origin.getX()) <= 30_000_000 && Math.abs((long) origin.getZ()) <= 30_000_000
                && Math.abs((long) origin.getY()) <= 4096;
    }
    private static void writePos(CompoundTag tag, String prefix, BlockPos pos) {
        tag.putInt(prefix + "X", pos.getX()); tag.putInt(prefix + "Y", pos.getY()); tag.putInt(prefix + "Z", pos.getZ());
    }
    public enum Result { APPLIED, UNCHANGED, UNAUTHORIZED, STALE, UNKNOWN, INSUFFICIENT, CONFLICT, READ_ONLY }
    public record Resident(UUID id, BlockPos bed, BlockPos job) {
        public Resident { if (bed != null) bed = bed.immutable(); if (job != null) job = job.immutable(); }
    }
    private record Place(String dimension, BlockPos position) {}
    private static final class Proof {
        final UUID village; final String dimension; final List<Resident> residents;
        final BlockPos origin;
        boolean active = true;
        CompoundTag retained = new CompoundTag();
        final Map<UUID, CompoundTag> residentPayloads = new HashMap<>();
        Proof(UUID village, String dimension, BlockPos origin, List<Resident> residents) {
            this.village = village; this.dimension = dimension; this.origin = origin.immutable(); this.residents = residents;
        }
    }
}
