package de.quest.kingdom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Shared official buildings and exclusive stations, persisted inside the canonical village state. */
public final class KingdomAnchorRegistry {
    private static final int SCHEMA = 1, MAX_RECORDS = 65_536, MAX_STATIONS = 256;
    private final LivingKingdomsState state;
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private final Map<Slot, UUID> officials = new HashMap<>();
    private final Map<Place, UUID> positions = new HashMap<>(), stations = new HashMap<>();
    private final Map<String, Map<Long, Set<UUID>>> nearby = new HashMap<>();
    private final Set<UUID> reservedIds = new HashSet<>();
    private final Set<Slot> reservedSlots = new HashSet<>();
    private final Set<Place> reservedPlaces = new HashSet<>(), reservedStations = new HashSet<>();
    private final List<CompoundTag> quarantined = new ArrayList<>();
    private Tag retained = new CompoundTag();
    private boolean readOnly;

    KingdomAnchorRegistry(LivingKingdomsState state) { this.state = state; }
    void adoptNew(KingdomAnchorRegistry staged, UUID id) {
        Anchor anchor = staged.anchors.get(id);
        if (anchor == null || anchors.containsKey(id)) throw new IllegalStateException("Invalid prepared anchor");
        anchors.put(id, anchor); officials.put(anchor.slot, id); positions.put(anchor.place, id);
        index(anchor);
        anchor.claims.forEach(key -> stations.put(key, id));
    }
    void adoptReplacement(KingdomAnchorRegistry staged, UUID id) {
        Anchor anchor = staged.anchors.get(id);
        if (anchor == null) throw new IllegalStateException("Missing prepared anchor");
        Anchor previous = anchors.remove(id);
        if (previous != null) release(previous);
        adoptNew(staged, id);
    }
    public Optional<AnchorView> official(UUID village, BuildingScanner.Role role) {
        Slot slot = new Slot(village, role);
        return reservedSlots.contains(slot) ? Optional.empty() : anchor(officials.get(slot));
    }
    public boolean knownDetached(UUID village,BuildingScanner.Role role) {
        var anchor=anchors.get(officials.get(new Slot(village,role)));
        return !readOnly && !state.readOnly() && anchor!=null && !blocked(anchor) && !anchor.operation.readOnly() && !anchor.bound;
    }
    public Optional<AnchorView> anchor(UUID id) {
        Anchor anchor = anchors.get(id);
        return anchor == null || blocked(anchor) ? Optional.empty() : Optional.of(anchor.view());
    }
    public Optional<AnchorView> at(String dimension, BlockPos pos) { return anchor(positions.get(new Place(dimension, pos))); }
    public Optional<UUID> stationOwner(String dimension, BlockPos pos) {
        Place key = new Place(dimension, pos);
        return reservedStations.contains(key) ? Optional.empty() : Optional.ofNullable(stations.get(key));
    }
    public List<AnchorView> all() {
        return anchors.values().stream().filter(a -> !blocked(a)).sorted((a, b) -> a.id.compareTo(b.id))
                .map(Anchor::view).toList();
    }
    public List<AnchorView> near(String dimension, BlockPos pos, int radius, int limit) {
        if (dimension == null || pos == null || radius < 0 || radius > 128 || limit <= 0) return List.of();
        Set<UUID> candidates = new HashSet<>(); var index = nearby.getOrDefault(dimension, Map.of());
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++)
                candidates.addAll(index.getOrDefault(net.minecraft.world.level.ChunkPos.pack(x, z), Set.of()));
        return candidates.stream().map(this::anchor).flatMap(Optional::stream)
                .filter(a -> a.position.distSqr(pos) <= (long) radius * radius)
                .sorted(java.util.Comparator.comparingDouble(a -> a.position.distSqr(pos)))
                .limit(Math.min(128, limit)).toList();
    }
    public boolean maySpawn(UUID id, long tick) {
        Anchor anchor = anchors.get(id);
        return !readOnly && !state.readOnly() && anchor != null && anchor.bound && !blocked(anchor)
                && tick >= anchor.reportTick && anchor.reportTick >= 0 && tick - anchor.reportTick <= 40 && anchor.operation.maySpawn(tick);
    }
    public boolean operational(UUID id, long tick) {
        Anchor anchor = anchors.get(id);
        return !readOnly && !state.readOnly() && anchor != null && anchor.bound && !blocked(anchor) && anchor.operation.operational(tick);
    }
    /** Continue a loaded, unchanged certified service. New bindings/spawns still use maySpawn; trades use this unchanged certificate. */
    public boolean mayContinue(UUID id,long tick) {
        Anchor anchor=anchors.get(id);
        return !readOnly && !state.readOnly() && anchor!=null && anchor.bound && !blocked(anchor)
                && anchor.report!=null && anchor.report.status()==BuildingScanner.Status.VALID && anchor.operation.maySpawn(tick);
    }
    /** New trades may use a continuously maintained unchanged certificate. Dirtiness/unload revokes it immediately. */
    public boolean mayTrade(UUID id,long tick) { return mayContinue(id,tick); }
    public Optional<BuildingScanner.Report> report(UUID id) {
        Anchor anchor = anchors.get(id);
        return anchor == null || blocked(anchor) ? Optional.empty() : Optional.ofNullable(anchor.report);
    }
    /** Only the native watch adapter may refresh loaded operation after verifying unchanged cache revision and loaded region. */
    boolean maintainCached(UUID id, long configurationRevision, long completedTick, long now) {
        Anchor anchor = anchors.get(id);
        if (readOnly || state.readOnly() || anchor == null || !anchor.bound || blocked(anchor)
                || anchor.revision != configurationRevision || anchor.report == null || anchor.reportTick != completedTick
                || now < completedTick) return false;
        long invalid = anchor.operation.invalidTicks(); var phase = anchor.operation.phase();
        anchor.operation.observe(anchor.report.status(), now);
        if (invalid != anchor.operation.invalidTicks() || phase != anchor.operation.phase()) state.setDirty();
        return true;
    }
    void invalidateEvidence(UUID id) {
        Anchor anchor = anchors.get(id);
        if (anchor != null) { anchor.report = null; anchor.operation.invalidate(); }
    }

    /** Only a server-produced completed report is accepted; clients submit IDs/revisions, never reports. */
    public Result bind(UUID actor, UUID villageId, long villageRevision, UUID id, long anchorRevision,
                       BlockPos pos, Direction facing, BuildingScanner.Range range,
                       BuildingScanner.Report report, long reportTick, long now) {
        if (readOnly || state.readOnly() || state.founding().readOnly()) return Result.READ_ONLY;
        var village = state.village(villageId).orElse(null);
        if (village == null || !state.canManageVillage(actor, villageId)) return Result.UNAUTHORIZED;
        if (village.revision() != villageRevision || villageRevision < 0) return Result.STALE;
        if (id == null || !valid(pos) || facing == null || !facing.getAxis().isHorizontal() || range == null
                || Math.abs((long) pos.getX() - village.arrival().getX()) > 128
                || Math.abs((long) pos.getZ() - village.arrival().getZ()) > 128
                || Math.abs((long) pos.getY() - village.arrival().getY()) > 32) return Result.INVALID;
        if (report == null || report.status() != BuildingScanner.Status.VALID || now < reportTick
                || reportTick < 0 || now - reportTick > 40) return Result.NO_EVIDENCE;
        Slot slot = new Slot(villageId, report.role());
        Place place = new Place(village.legacyKey().dimension(), pos);
        if (reservedIds.contains(id) || reservedSlots.contains(slot) || reservedPlaces.contains(place)) return Result.READ_ONLY;
        UUID official = officials.get(slot);
        if (official != null && !official.equals(id)) return Result.ALREADY_ASSIGNED;
        Anchor existing = anchors.get(id);
        if (existing == null ? anchorRevision != -1 : existing.revision != anchorRevision) return Result.STALE;
        if (existing != null && now < existing.reportTick) return Result.STALE;
        if (existing != null && (!existing.slot.equals(slot) || existing.revision == Long.MAX_VALUE)) return Result.INVALID;
        UUID occupying = positions.get(place);
        if (occupying != null && !occupying.equals(id)) return Result.POSITION_CONFLICT;
        if (existing == null && anchors.size() + quarantined.size() >= MAX_RECORDS) return Result.INVALID;
        Set<Place> claims = claims(place.dimension, report);
        if (!localClaims(claims, pos, range)) return Result.INVALID;
        if (conflict(id, claims) || foundingConflict(villageId, claims)) return Result.STATION_CONFLICT;
        boolean unchanged = existing != null && existing.bound && existing.place.equals(place) && existing.facing == facing
                && existing.range == range && existing.claims.equals(claims);
        Anchor anchor = existing == null ? new Anchor(id, slot, place, facing, range) : existing;
        release(anchor);
        anchor.place = place; anchor.facing = facing; anchor.range = range; anchor.claims = claims;
        anchor.bound = true; anchor.reportTick = reportTick;
        if (existing != null && !unchanged) anchor.revision++;
        anchor.report = report; anchor.operation.observe(report.status(), now);
        anchors.put(id, anchor); officials.put(slot, id); positions.put(place, id);
        index(anchor);
        claims.forEach(key -> stations.put(key, id)); state.setDirty();
        return unchanged ? Result.UNCHANGED : Result.APPLIED;
    }

    /** Configuration revision prevents a queued result for the old position from overwriting a move. */
    public Result observe(UUID id, long expectedRevision, BuildingScanner.Report report, long tick) {
        if (readOnly || state.readOnly()) return Result.READ_ONLY;
        Anchor anchor = anchors.get(id);
        if (anchor == null || blocked(anchor)) return Result.INVALID;
        if (anchor.revision != expectedRevision) return Result.STALE;
        if (tick < anchor.reportTick) return Result.STALE;
        if (!anchor.bound) return Result.NO_EVIDENCE;
        if (report == null || report.role() != anchor.slot.role || tick < 0) return Result.NO_EVIDENCE;
        anchor.reportTick = tick;
        Set<Place> claims = claims(anchor.place.dimension, report);
        if (report.status() == BuildingScanner.Status.VALID
                && (!localClaims(claims, anchor.place.pos, anchor.range) || conflict(id, claims)
                || foundingConflict(anchor.slot.village, claims))) {
            anchor.operation.observe(BuildingScanner.Status.INVALID, tick); anchor.report = null;
            state.setDirty(); return Result.STATION_CONFLICT;
        }
        if (report.status() == BuildingScanner.Status.VALID) {
            release(anchor); anchor.claims = claims;
            positions.put(anchor.place, id); claims.forEach(key -> stations.put(key, id));
            index(anchor);
        }
        anchor.report = report; anchor.operation.observe(report.status(), tick); state.setDirty();
        return Result.APPLIED;
    }

    public Result detach(UUID actor, UUID id, long expectedRevision) {
        if (readOnly || state.readOnly()) return Result.READ_ONLY;
        Anchor anchor = anchors.get(id);
        if (anchor == null || blocked(anchor)) return Result.INVALID;
        if (!state.canManageVillage(actor, anchor.slot.village)) return Result.UNAUTHORIZED;
        if (anchor.revision != expectedRevision || anchor.revision == Long.MAX_VALUE) return Result.STALE;
        // Keep logical identity/earned credit for rebuilding. Breaking a block cannot mint another paid NPC.
        anchor.operation.invalidate(); anchor.report = null; anchor.bound = false;
        release(anchor); anchor.claims = Set.of(); anchor.revision++; state.setDirty(); return Result.APPLIED;
    }

    private boolean blocked(Anchor anchor) {
        return reservedIds.contains(anchor.id) || reservedSlots.contains(anchor.slot) || reservedPlaces.contains(anchor.place)
                || state.village(anchor.slot.village).isEmpty();
    }
    private void release(Anchor anchor) {
        positions.remove(anchor.place, anchor.id); anchor.claims.forEach(key -> stations.remove(key, anchor.id));
        var dimension = nearby.get(anchor.place.dimension);
        long chunk = net.minecraft.world.level.ChunkPos.pack(anchor.place.pos.getX() >> 4, anchor.place.pos.getZ() >> 4);
        if (dimension != null) {
            Set<UUID> ids = dimension.get(chunk);
            if (ids != null) { ids.remove(anchor.id); if (ids.isEmpty()) dimension.remove(chunk); }
            if (dimension.isEmpty()) nearby.remove(anchor.place.dimension);
        }
    }
    private void index(Anchor anchor) {
        if (!anchor.bound) return;
        nearby.computeIfAbsent(anchor.place.dimension, ignored -> new HashMap<>())
                .computeIfAbsent(net.minecraft.world.level.ChunkPos.pack(anchor.place.pos.getX() >> 4, anchor.place.pos.getZ() >> 4),
                        ignored -> new HashSet<>()).add(anchor.id);
    }
    void removedPhysical(UUID id) {
        Anchor anchor = anchors.get(id);
        if (readOnly || state.readOnly() || anchor == null || !anchor.bound || blocked(anchor)) return;
        release(anchor); anchor.claims = Set.of(); anchor.bound = false; invalidateEvidence(id);
        if (anchor.revision < Long.MAX_VALUE) anchor.revision++;
        state.setDirty();
    }
    private boolean conflict(UUID id, Set<Place> claims) {
        return claims.stream().anyMatch(key -> reservedStations.contains(key)
                || stations.containsKey(key) && !id.equals(stations.get(key)));
    }
    private static Set<Place> claims(String dimension, BuildingScanner.Report report) {
        Set<Place> claims = new HashSet<>();
        Set<BuildingScanner.Feature> used = switch (report.role()) {
            case COUNCIL -> Set.of(BuildingScanner.Feature.TABLE, BuildingScanner.Feature.SEAT, BuildingScanner.Feature.BOOKS);
            case INN -> Set.of(BuildingScanner.Feature.TABLE, BuildingScanner.Feature.SEAT, BuildingScanner.Feature.BARREL,
                    BuildingScanner.Feature.KITCHEN, BuildingScanner.Feature.BED);
            case MARKET -> Set.of(BuildingScanner.Feature.CONTAINER, BuildingScanner.Feature.COUNTER);
            case STOREHOUSE -> Set.of(BuildingScanner.Feature.CONTAINER, BuildingScanner.Feature.WORKSTATION);
        };
        report.stations().forEach((feature, positions) -> {
            if (used.contains(feature))
                positions.forEach(pos -> claims.add(new Place(dimension, pos)));
        });
        return Set.copyOf(claims);
    }
    private boolean foundingConflict(UUID village, Set<Place> claims) {
        return state.founding().readOnly() || claims.stream().anyMatch(key -> state.founding().bedOwner(key.dimension, key.pos).isPresent()
                || state.founding().jobOwner(key.dimension, key.pos).filter(owner -> !owner.equals(village)).isPresent());
    }
    private static boolean localClaims(Set<Place> claims, BlockPos anchor, BuildingScanner.Range range) {
        return claims.size() <= MAX_STATIONS && claims.stream().allMatch(key -> valid(key.pos)
                && Math.abs((long) key.pos.getX() - anchor.getX()) <= range.horizontal
                && Math.abs((long) key.pos.getZ() - anchor.getZ()) <= range.horizontal
                && Math.abs((long) key.pos.getY() - anchor.getY()) <= range.vertical);
    }
    private static boolean valid(BlockPos pos) {
        return pos != null && Math.abs((long) pos.getX()) <= 30_000_000
                && Math.abs((long) pos.getZ()) <= 30_000_000 && Math.abs((long) pos.getY()) <= 4096;
    }

    void load(Tag raw) {
        if (raw == null) return;
        retained = raw.copy();
        if (!(raw instanceof CompoundTag root) || root.getIntOr("schema", 0) > SCHEMA
                || root.getIntOr("schema", 0) < 0 || root.contains("records") && !(root.get("records") instanceof ListTag)) {
            readOnly = true; return;
        }
        ListTag records = root.getListOrEmpty("records");
        if (records.size() > MAX_RECORDS) { readOnly = true; return; }
        for (Tag record : records) {
            if (!(record instanceof CompoundTag tag)) { readOnly = true; return; }
            try {
                UUID id = UUID.fromString(tag.getStringOr("id", ""));
                Slot slot = new Slot(UUID.fromString(tag.getStringOr("village", "")),
                        BuildingScanner.Role.valueOf(tag.getStringOr("role", "")));
                var village = state.village(slot.village).orElseThrow();
                Place place = new Place(village.legacyKey().dimension(), readPos(tag));
                Direction facing = Direction.valueOf(tag.getStringOr("facing", ""));
                var range = BuildingScanner.Range.valueOf(tag.getStringOr("range", ""));
                long revision = tag.getLongOr("revision", -1);
                if (!valid(place.pos) || !facing.getAxis().isHorizontal() || revision < 0 || anchors.containsKey(id)
                        || officials.containsKey(slot) || tag.getBooleanOr("bound", true) && positions.containsKey(place)) throw new IllegalArgumentException("Conflict");
                Anchor anchor = new Anchor(id, slot, place, facing, range);
                anchor.retained = tag.copy(); anchor.revision = revision;
                anchor.bound = tag.getBooleanOr("bound", true);
                if (tag.contains("operation") && !(tag.get("operation") instanceof CompoundTag)
                        || !(tag.get("stations") instanceof ListTag)) throw new IllegalArgumentException("Invalid fields");
                anchor.operation = BuildingOperation.fromNbt(tag.getCompoundOrEmpty("operation"));
                if (anchor.operation.readOnly()) throw new IllegalArgumentException("Unknown operation");
                Set<Place> claims = new HashSet<>();
                for (Tag station : tag.getListOrEmpty("stations")) {
                    if (!(station instanceof CompoundTag stationTag)) throw new IllegalArgumentException("Invalid station");
                    Place key = new Place(place.dimension, readPos(stationTag));
                    claims.add(key); anchor.stationPayloads.put(key, stationTag.copy());
                }
                if (!localClaims(claims, place.pos, range) || conflict(id, claims) || foundingConflict(slot.village, claims)
                        || !anchor.bound && !claims.isEmpty()) throw new IllegalArgumentException("Station conflict");
                anchor.claims = Set.copyOf(claims);
                anchors.put(id, anchor); officials.put(slot, id);
                if (anchor.bound) positions.put(place, id);
                index(anchor);
                claims.forEach(key -> stations.put(key, id));
            } catch (IllegalArgumentException | java.util.NoSuchElementException badData) {
                quarantine(tag);
            }
        }
    }
    private void quarantine(CompoundTag tag) {
        quarantined.add(tag.copy());
        try { reservedIds.add(UUID.fromString(tag.getStringOr("id", ""))); } catch (IllegalArgumentException ignored) {}
        try {
            UUID villageId = UUID.fromString(tag.getStringOr("village", ""));
            reservedSlots.add(new Slot(villageId, BuildingScanner.Role.valueOf(tag.getStringOr("role", ""))));
            String dimension = state.village(villageId).orElseThrow().legacyKey().dimension();
            if (valid(readPos(tag))) reservedPlaces.add(new Place(dimension, readPos(tag)));
            for (Tag station : tag.getListOrEmpty("stations")) if (station instanceof CompoundTag stationTag && valid(readPos(stationTag)))
                reservedStations.add(new Place(dimension, readPos(stationTag)));
        } catch (IllegalArgumentException | java.util.NoSuchElementException ignored) { /* Raw data remains preserved. */ }
    }
    Tag toNbt() {
        if (readOnly) return retained.copy();
        CompoundTag root = retained instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
        root.putInt("schema", SCHEMA); ListTag records = new ListTag();
        anchors.values().stream().sorted((a, b) -> a.id.compareTo(b.id)).forEach(anchor -> {
            CompoundTag tag = anchor.retained.copy();
            tag.putString("id", anchor.id.toString()); tag.putString("village", anchor.slot.village.toString());
            tag.putString("role", anchor.slot.role.name()); writePos(tag, anchor.place.pos);
            tag.putString("facing", anchor.facing.name()); tag.putString("range", anchor.range.name());
            tag.putLong("revision", anchor.revision); tag.putBoolean("bound", anchor.bound); tag.put("operation", anchor.operation.toNbt());
            ListTag claims = new ListTag();
            anchor.claims.stream().sorted((a, b) -> Long.compare(a.pos.asLong(), b.pos.asLong())).forEach(key -> {
                CompoundTag point = anchor.stationPayloads.getOrDefault(key, new CompoundTag()).copy();
                writePos(point, key.pos); claims.add(point);
            });
            tag.put("stations", claims); records.add(tag);
        });
        quarantined.forEach(tag -> records.add(tag.copy())); root.put("records", records); return root;
    }
    private static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getIntOr("x", Integer.MIN_VALUE), tag.getIntOr("y", Integer.MIN_VALUE), tag.getIntOr("z", Integer.MIN_VALUE));
    }
    private static void writePos(CompoundTag tag, BlockPos pos) {
        tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
    }
    public enum Result { APPLIED, UNCHANGED, UNAUTHORIZED, STALE, INVALID, READ_ONLY, NO_EVIDENCE,
        ALREADY_ASSIGNED, POSITION_CONFLICT, STATION_CONFLICT }
    public record AnchorView(UUID id, UUID villageId, BuildingScanner.Role role, String dimension,
                             BlockPos position, Direction facing, BuildingScanner.Range range, long revision,
                             boolean earned, BuildingOperation.Phase phase) {}
    private record Slot(UUID village, BuildingScanner.Role role) {}
    private record Place(String dimension, BlockPos pos) {
        private Place { if (pos != null) pos = pos.immutable(); }
    }
    private static final class Anchor {
        final UUID id; final Slot slot;
        Place place; Direction facing; BuildingScanner.Range range;
        long revision;
        long reportTick = -1;
        boolean bound = true;
        Set<Place> claims = Set.of();
        final Map<Place, CompoundTag> stationPayloads = new HashMap<>();
        BuildingOperation operation = new BuildingOperation();
        BuildingScanner.Report report;
        CompoundTag retained = new CompoundTag();
        Anchor(UUID id, Slot slot, Place place, Direction facing, BuildingScanner.Range range) {
            this.id = id; this.slot = slot; this.place = place; this.facing = facing; this.range = range;
        }
        AnchorView view() { return new AnchorView(id, slot.village, slot.role, place.dimension, place.pos, facing,
                range, revision, operation.earned(), operation.phase()); }
    }
}
