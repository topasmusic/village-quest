package de.quest.kingdom;

import de.quest.village.VillageLifeState.VillageKey;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
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
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.ChunkPos;

/** Shared physical identity. Never replaces personal bonds, route records or VillageLifeState. */
public final class LivingKingdomsState extends SavedData {
    private static final int SCHEMA = 1;
    private static final int MAX_VILLAGES = 16_384;
    private static final int MAX_DELEGATES = 64;
    private static final int MAX_ZONES = 16;
    private static final int MAX_ALIASES = 32;
    public static final SavedDataType<LivingKingdomsState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("village_quest_living_kingdoms"), LivingKingdomsState::new,
            CompoundTag.CODEC.xmap(LivingKingdomsState::fromNbt, LivingKingdomsState::toNbt), DataFixTypes.LEVEL);

    private final Map<UUID, MutableVillage> villages = new HashMap<>();
    private final Map<VillageKey, UUID> legacyAliases = new HashMap<>();
    private final List<CompoundTag> quarantined = new ArrayList<>();
    private final Set<UUID> reservedIds = new HashSet<>();
    private final Set<VillageKey> reservedKeys = new HashSet<>();
    private final Map<String, Map<Long, Set<UUID>>> zoneIndex = new HashMap<>();
    private final Map<String, Map<Long, Set<UUID>>> arrivalIndex = new HashMap<>();
    private CompoundTag retainedRoot = new CompoundTag();
    private boolean readOnly;
    private final KingdomAnchorRegistry anchors = new KingdomAnchorRegistry(this);
    private final VillageFoundingRegistry founding = new VillageFoundingRegistry(this);
    private final KingdomTavernGuests tavernGuests=new KingdomTavernGuests(this);
    private final KingdomPersonRegistry people=new KingdomPersonRegistry(this);
    private final KingdomMarketLedger markets=new KingdomMarketLedger(this);
    private final KingdomMarketPlaces marketPlaces=new KingdomMarketPlaces(this);
    private final KingdomMarketHomes marketHomes=new KingdomMarketHomes(this);
    private final KingdomMarketDayPlan marketDays=new KingdomMarketDayPlan(this);
    private final KingdomMarketTravel marketTravel=new KingdomMarketTravel(this);
    private final KingdomRecognition recognition=new KingdomRecognition(this);
    private final KingdomDiplomacy diplomacy=new KingdomDiplomacy(this);
    private final KingdomRelations relations=new KingdomRelations(this);
    private final KingdomAutonomy autonomy=new KingdomAutonomy(this);
    private final KingdomCrisis crises=new KingdomCrisis(this);
    private final KingdomWorksites worksites=new KingdomWorksites(this);
    private final MarketSourceCache marketSources=new MarketSourceCache(markets::invalidateQuotes);

    public LivingKingdomsState() {}

    public static LivingKingdomsState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean readOnly() { return readOnly; }
    public KingdomAnchorRegistry anchors() { return anchors; }
    public VillageFoundingRegistry founding() { return founding; }
    public KingdomTavernGuests tavernGuests() { return tavernGuests; }
    public KingdomPersonRegistry people() { return people; }
    public KingdomMarketLedger markets() { return markets; }
    public KingdomMarketPlaces marketPlaces() { return marketPlaces; }
    public KingdomMarketHomes marketHomes() { return marketHomes; }
    public KingdomMarketDayPlan marketDays() { return marketDays; }
    public KingdomMarketTravel marketTravel() { return marketTravel; }
    public KingdomRecognition recognition() { return recognition; }
    public KingdomDiplomacy diplomacy() { return diplomacy; }
    public KingdomRelations relations() { return relations; }
    public KingdomAutonomy autonomy() { return autonomy; }
    public KingdomCrisis crises() { return crises; }
    public KingdomWorksites worksites() { return worksites; }
    public MarketSourceCache marketSources() { return marketSources; }
    public Optional<VillagePopulationHistory.View> population(UUID id) {
        return village(id).map(ignored -> villages.get(id).population.view());
    }
    /** Native loaded-only population adapter supplies this evidence; never expose counts in a mutation payload. */
    public boolean observePopulation(UUID id,int adults,boolean confirmed,long tick) {
        if (readOnly || village(id).isEmpty() || !villages.get(id).population.observe(adults,confirmed,tick)) return false;
        setDirty(); return true;
    }

    public Optional<VillageView> village(UUID id) {
        MutableVillage village = id == null ? null : villages.get(id);
        return village == null || reservedIds.contains(id) || reservedKeys.contains(village.legacyKey)
                ? Optional.empty() : Optional.of(village.view());
    }

    public Optional<VillageView> byLegacy(VillageKey key) {
        return key == null || reservedKeys.contains(key) ? Optional.empty() : village(legacyAliases.get(key));
    }

    /** Trusted generated-structure migration only; never applies tolerance to custom villages. */
    public boolean recognizeLegacyAlias(UUID id, VillageKey key) {
        MutableVillage village = id == null ? null : villages.get(id);
        if (readOnly || village == null || village.origin != Origin.GENERATED || !validLegacyKey(key)
                || reservedIds.contains(id) || reservedKeys.contains(key)
                || !key.dimension().equals(village.legacyKey.dimension())
                || Math.abs((long) key.anchorX() - village.legacyKey.anchorX()) > 8
                || Math.abs((long) key.anchorZ() - village.legacyKey.anchorZ()) > 8) return false;
        UUID existing = legacyAliases.get(key);
        if (existing != null) return id.equals(existing);
        if (village.aliases.size() >= MAX_ALIASES || village.revision == Long.MAX_VALUE) return false;
        village.aliases.add(key);
        legacyAliases.put(key, id);
        changed(village);
        return true;
    }

    public List<VillageView> villages() {
        return villages.values().stream().filter(v -> !reservedIds.contains(v.id)
                && !reservedKeys.contains(v.legacyKey)).sorted((a, b) -> a.id.compareTo(b.id))
                .map(MutableVillage::view).toList();
    }

    public List<Zone> zones(UUID id) {
        return village(id).isEmpty() ? List.of() : List.copyOf(villages.get(id).zones);
    }
    public List<VillageView> nearVillages(String dimension, BlockPos pos, int radius, int limit) {
        if (dimension == null || pos == null || radius < 0 || radius > 128 || limit <= 0) return List.of();
        var index = arrivalIndex.getOrDefault(dimension, Map.of()); Set<UUID> candidates = new HashSet<>();
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++)
                candidates.addAll(index.getOrDefault(ChunkPos.pack(x, z), Set.of()));
        return candidates.stream().map(this::village).flatMap(Optional::stream)
                .filter(v -> Math.abs((long) v.arrival.getY() - pos.getY()) <= 32
                        && Math.abs((long) v.arrival.getX() - pos.getX()) <= radius && Math.abs((long) v.arrival.getZ() - pos.getZ()) <= radius)
                .sorted(java.util.Comparator.comparingDouble(v -> v.arrival.distSqr(pos))).limit(Math.min(128, limit)).toList();
    }
    private void indexArrival(MutableVillage village) {
        arrivalIndex.computeIfAbsent(village.legacyKey.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(ChunkPos.pack(village.arrival.getX() >> 4, village.arrival.getZ() >> 4), ignored -> new HashSet<>()).add(village.id);
    }
    private void unindexArrival(MutableVillage village) {
        var dimension = arrivalIndex.get(village.legacyKey.dimension());
        if (dimension == null) return;
        long chunk = ChunkPos.pack(village.arrival.getX() >> 4, village.arrival.getZ() >> 4); Set<UUID> ids = dimension.get(chunk);
        if (ids != null) { ids.remove(village.id); if (ids.isEmpty()) dimension.remove(chunk); }
        if (dimension.isEmpty()) arrivalIndex.remove(village.legacyKey.dimension());
    }

    /** Ambiguous neighbors need an explicit anchor/ID selection, never a proximity merge. */
    public Optional<VillageView> resolveAt(String dimension, BlockPos pos) {
        List<VillageView> matches = candidatesAt(dimension, pos);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public boolean isAmbiguousAt(String dimension, BlockPos pos) {
        return candidatesAt(dimension, pos).size() > 1;
    }

    private List<VillageView> candidatesAt(String dimension, BlockPos pos) {
        if (pos == null || dimension == null) return List.of();
        Set<UUID> ids = zoneIndex.getOrDefault(dimension, Map.of()).getOrDefault(
                ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), Set.of());
        return ids.stream().map(this::village).flatMap(Optional::stream)
                .filter(v -> !reservedKeys.contains(v.legacyKey)
                        && zones(v.id).stream().anyMatch(zone -> zone.contains(pos))).limit(2).toList();
    }

    private void indexZone(MutableVillage village, Zone zone) {
        var dimension = zoneIndex.computeIfAbsent(village.legacyKey.dimension(), ignored -> new HashMap<>());
        for (int x = zone.minX >> 4; x <= zone.maxX >> 4; x++) {
            for (int z = zone.minZ >> 4; z <= zone.maxZ >> 4; z++) {
                dimension.computeIfAbsent(ChunkPos.pack(x, z), ignored -> new HashSet<>()).add(village.id);
            }
        }
    }

    /** Server-side persistence only: the building service must verify loaded, connected evidence first. */
    public Mutation confirmZone(UUID actor, UUID id, long expectedRevision, Zone zone) {
        Mutation access = access(actor, id, expectedRevision);
        if (access != Mutation.APPLIED) return access;
        MutableVillage village = villages.get(id);
        if (zone == null || !zone.valid()) return Mutation.INVALID;
        if (village.zones.contains(zone)) return Mutation.UNCHANGED;
        if (village.zones.size() >= MAX_ZONES || !zone.localTo(village.arrival, 128)
                || (village.zones.isEmpty() ? !zone.contains(village.arrival)
                : village.zones.stream().noneMatch(existing -> existing.touches(zone)))) return Mutation.INVALID;
        village.zones.add(zone);
        indexZone(village, zone);
        changed(village);
        return Mutation.APPLIED;
    }

    /** Lazy exact-coordinate alias, not the historical personal ±8 proximity lookup. */
    public Optional<VillageView> registerGenerated(VillageKey key, BlockPos arrival, String name) {
        if (readOnly || key == null || reservedKeys.contains(key)) return Optional.empty();
        Optional<VillageView> existing = byLegacy(key);
        if (existing.isPresent()) return existing;
        UUID id = UUID.nameUUIDFromBytes(("village-quest/generated/" + key.dimension() + "/"
                + key.anchorX() + "/" + key.anchorZ()).getBytes(StandardCharsets.UTF_8));
        return register(id, null, Origin.GENERATED, key, arrival, name);
    }

    /** Caller validates founding evidence before this server-thread persistence boundary. */
    public Optional<VillageView> registerCustom(UUID id, UUID founder, VillageKey key,
                                               BlockPos arrival, String name) {
        if (founder == null) return Optional.empty();
        return register(id, founder, Origin.CUSTOM, key, arrival, name);
    }

    /** Server-thread transaction after fresh native observations. Rejected proposals never enter the live registry. */
    public FoundingResult foundCustom(UUID id, UUID actor, VillageKey key, BlockPos arrival, String name,
                                     UUID anchorId, net.minecraft.core.Direction facing, BuildingScanner.Range range,
                                     BuildingScanner.Report report, long reportTick, long now,
                                     List<VillageFoundingRegistry.Resident> residents, boolean fullyLoaded) {
        if (readOnly || founding.readOnly()) return new FoundingResult(FoundingCode.READ_ONLY, id);
        if (!fullyLoaded) return new FoundingResult(FoundingCode.UNKNOWN, id);
        if (actor == null || id == null || anchorId == null || !validPosition(arrival) || !validLegacyKey(key)
                || safeName(name) == null || range == null || facing == null || !facing.getAxis().isHorizontal()
                || report == null || report.role() != BuildingScanner.Role.COUNCIL
                || report.status() != BuildingScanner.Status.VALID || reportTick < 0 || now < reportTick
                || now - reportTick > 40 || residents == null || residents.size() > 64
                || !residents.isEmpty() && VillageFoundingRegistry.select(residents, arrival).size() != 3)
            return new FoundingResult(FoundingCode.INVALID, id);
        MutableVillage existing = villages.get(id);
        if (existing != null) {
            boolean same = existing.origin == Origin.CUSTOM && actor.equals(existing.founder) && key.equals(existing.legacyKey)
                    && anchors.official(id, BuildingScanner.Role.COUNCIL).filter(a -> a.id().equals(anchorId)).isPresent()
                    ;
            return new FoundingResult(same ? FoundingCode.UNCHANGED : FoundingCode.CONFLICT, id);
        }
        LivingKingdomsState staged = fromNbt(toNbt(this));
        var proposed = staged.registerCustom(id, actor, key, arrival, name);
        if (proposed.isEmpty()) return new FoundingResult(FoundingCode.CONFLICT, id);
        Zone zone = new Zone(arrival.getX() - range.horizontal, arrival.getX() + range.horizontal,
                arrival.getY() - range.vertical, arrival.getY() + range.vertical,
                arrival.getZ() - range.horizontal, arrival.getZ() + range.horizontal);
        if (staged.confirmZone(actor, id, 0, zone) != Mutation.APPLIED) return new FoundingResult(FoundingCode.INVALID, id);
        long revision = staged.village(id).orElseThrow().revision();
        var anchor = staged.anchors.bind(actor, id, revision, anchorId, -1, arrival, facing, range, report, reportTick, now);
        if (anchor != KingdomAnchorRegistry.Result.APPLIED) return new FoundingResult(
                anchor == KingdomAnchorRegistry.Result.READ_ONLY ? FoundingCode.READ_ONLY : FoundingCode.CONFLICT, id);
        var proof = residents.isEmpty() ? VillageFoundingRegistry.Result.APPLIED : staged.founding.claim(actor, id, revision, residents, true, true);
        if (proof != VillageFoundingRegistry.Result.APPLIED) return new FoundingResult(
                proof == VillageFoundingRegistry.Result.READ_ONLY ? FoundingCode.READ_ONLY : FoundingCode.CONFLICT, id);
        // Commit only the validated new records. Existing runtime reports/NPC evidence remain untouched.
        MutableVillage village = staged.villages.get(id);
        villages.put(id, village); legacyAliases.put(village.legacyKey, id);
        indexArrival(village);
        village.zones.forEach(bounds -> indexZone(village, bounds));
        anchors.adoptNew(staged.anchors, anchorId);
        if (!residents.isEmpty()) founding.adoptNew(staged.founding, id);
        setDirty(); return new FoundingResult(FoundingCode.APPLIED, id);
    }

    /** Existing village binding/relocation, staged so area and arrival cannot partially change on a station conflict. */
    public KingdomAnchorRegistry.Result confirmBuilding(UUID actor, UUID id, long villageRevision,
            UUID anchorId, long anchorRevision, BlockPos position, net.minecraft.core.Direction facing,
            BuildingScanner.Range range, BuildingScanner.Report report, long reportTick, long now) {
        return confirmBuilding(actor,id,villageRevision,anchorId,anchorRevision,position,facing,range,report,reportTick,now,null);
    }
    /** First council naming is committed together with its building, never before a successful bind. */
    public KingdomAnchorRegistry.Result confirmBuilding(UUID actor, UUID id, long villageRevision,
            UUID anchorId, long anchorRevision, BlockPos position, net.minecraft.core.Direction facing,
            BuildingScanner.Range range, BuildingScanner.Report report, long reportTick, long now,String name) {
        if (readOnly || founding.readOnly()) return KingdomAnchorRegistry.Result.READ_ONLY;
        if (!canManageVillage(actor, id)) return KingdomAnchorRegistry.Result.UNAUTHORIZED;
        var current = village(id).orElseThrow();
        if (current.revision() != villageRevision || villageRevision < 0) return KingdomAnchorRegistry.Result.STALE;
        if (!validPosition(position) || range == null || report == null || report.status() != BuildingScanner.Status.VALID
                || now < reportTick || reportTick < 0 || now - reportTick > 40) return KingdomAnchorRegistry.Result.NO_EVIDENCE;
        LivingKingdomsState staged = fromNbt(toNbt(this));
        long revision = villageRevision;
        boolean council = report.role() == BuildingScanner.Role.COUNCIL;
        if (council && current.origin() == Origin.GENERATED && staged.zones(id).isEmpty()) {
            Mutation moved = staged.moveCouncil(actor, id, revision, position);
            if (moved != Mutation.APPLIED && moved != Mutation.UNCHANGED) return KingdomAnchorRegistry.Result.INVALID;
            revision = staged.village(id).orElseThrow().revision();
        }
        if (current.origin() == Origin.CUSTOM || council) {
            Zone zone = new Zone(position.getX() - range.horizontal, position.getX() + range.horizontal,
                    position.getY() - range.vertical, position.getY() + range.vertical,
                    position.getZ() - range.horizontal, position.getZ() + range.horizontal);
            Mutation extended = staged.confirmZone(actor, id, revision, zone);
            if (extended != Mutation.APPLIED && extended != Mutation.UNCHANGED) return KingdomAnchorRegistry.Result.INVALID;
            revision = staged.village(id).orElseThrow().revision();
        }
        if (council) {
            Mutation moved = staged.moveCouncil(actor, id, revision, position);
            if (moved != Mutation.APPLIED && moved != Mutation.UNCHANGED) return KingdomAnchorRegistry.Result.INVALID;
            revision = staged.village(id).orElseThrow().revision();
        }
        var result = staged.anchors.bind(actor, id, revision, anchorId, anchorRevision, position, facing, range, report, reportTick, now);
        if (result != KingdomAnchorRegistry.Result.APPLIED && result != KingdomAnchorRegistry.Result.UNCHANGED) return result;
        if(name!=null) {
            if(!council || !validName(name)) return KingdomAnchorRegistry.Result.INVALID;
            var renamed=staged.rename(actor,id,staged.village(id).orElseThrow().revision(),name);
            if(renamed!=Mutation.APPLIED && renamed!=Mutation.UNCHANGED) return KingdomAnchorRegistry.Result.INVALID;
        }
        MutableVillage changed = staged.villages.get(id);
        unindexArrival(villages.get(id)); villages.put(id, changed); indexArrival(changed);
        changed.zones.forEach(zone -> indexZone(changed, zone));
        anchors.adoptReplacement(staged.anchors, anchorId); setDirty(); return result;
    }

    private Optional<VillageView> register(UUID id, UUID founder, Origin origin, VillageKey key,
                                          BlockPos arrival, String name) {
        String safeName = safeName(name);
        if (readOnly || id == null || !validLegacyKey(key) || !validPosition(arrival) || safeName == null
                || reservedIds.contains(id) || reservedKeys.contains(key)) return Optional.empty();
        MutableVillage existing = villages.get(id);
        if (existing != null) {
            return existing.origin == origin && java.util.Objects.equals(existing.founder, founder)
                    && existing.legacyKey.equals(key) ? Optional.of(existing.view()) : Optional.empty();
        }
        if (legacyAliases.containsKey(key) || villages.size() + quarantined.size() >= MAX_VILLAGES) {
            return Optional.empty();
        }
        MutableVillage village = new MutableVillage(id, founder, origin, key, arrival, safeName);
        villages.put(id, village);
        indexArrival(village);
        legacyAliases.put(key, id);
        setDirty();
        return Optional.of(village.view());
    }

    public boolean canManageVillage(UUID actor, UUID id) {
        MutableVillage village = id == null ? null : villages.get(id);
        return !readOnly && actor != null && village != null && !reservedIds.contains(id)
                && !reservedKeys.contains(village.legacyKey)
                && (actor.equals(village.founder) || village.delegates.contains(actor));
    }

    public Mutation rename(UUID actor, UUID id, long expectedRevision, String name) {
        Mutation access = access(actor, id, expectedRevision);
        if (access != Mutation.APPLIED) return access;
        String safeName = safeName(name);
        if (safeName == null) return Mutation.INVALID;
        MutableVillage village = villages.get(id);
        if (village.name.equals(safeName)) return Mutation.UNCHANGED;
        village.name = safeName;
        changed(village);
        return Mutation.APPLIED;
    }

    public Mutation moveCouncil(UUID actor, UUID id, long expectedRevision, BlockPos arrival) {
        Mutation access = access(actor, id, expectedRevision);
        if (access != Mutation.APPLIED) return access;
        if (!validPosition(arrival)) return Mutation.INVALID;
        MutableVillage village = villages.get(id);
        if (village.arrival.equals(arrival)) return Mutation.UNCHANGED;
        if (!village.zones.isEmpty() && (village.zones.stream().noneMatch(zone -> zone.contains(arrival))
                || village.zones.stream().anyMatch(zone -> !zone.localTo(arrival, 128)))) return Mutation.INVALID;
        // Physical position is mutable; old route/life/Chronicle keys never move.
        unindexArrival(village); village.arrival = arrival.immutable(); indexArrival(village);
        changed(village);
        return Mutation.APPLIED;
    }

    public Mutation setDelegate(UUID actor, UUID id, long expectedRevision, UUID delegate, boolean allowed) {
        MutableVillage village = id == null ? null : villages.get(id);
        if (readOnly) return Mutation.READ_ONLY;
        if (village == null || actor == null || !actor.equals(village.founder)) return Mutation.UNAUTHORIZED;
        Mutation access = access(actor, id, expectedRevision);
        if (access != Mutation.APPLIED) return access;
        if (delegate == null || delegate.equals(village.founder)) return Mutation.INVALID;
        if (allowed && village.delegates.size() >= MAX_DELEGATES && !village.delegates.contains(delegate)) {
            return Mutation.INVALID;
        }
        boolean changed = allowed ? village.delegates.add(delegate) : village.delegates.remove(delegate);
        if (!changed) return Mutation.UNCHANGED;
        changed(village);
        return Mutation.APPLIED;
    }

    /** Trusted server policy supplies eligibility after checking actual trust and explicit acceptance.
     * Never map a client-provided boolean to this method. A generated village has no founder. */
    public boolean recognizeMediator(UUID id, UUID actor, boolean serverVerifiedEligible) {
        MutableVillage village = id == null ? null : villages.get(id);
        if (readOnly || !serverVerifiedEligible || actor == null || village == null
                || village.origin != Origin.GENERATED || reservedIds.contains(id)
                || reservedKeys.contains(village.legacyKey) || village.delegates.size() >= MAX_DELEGATES
                || village.revision == Long.MAX_VALUE) return false;
        if (!village.delegates.add(actor)) return false;
        changed(village);
        return true;
    }

    private Mutation access(UUID actor, UUID id, long expectedRevision) {
        if (readOnly) return Mutation.READ_ONLY;
        if (!canManageVillage(actor, id)) return Mutation.UNAUTHORIZED;
        MutableVillage village = villages.get(id);
        if (expectedRevision < 0 || village.revision != expectedRevision) return Mutation.STALE;
        return village.revision == Long.MAX_VALUE ? Mutation.READ_ONLY : Mutation.APPLIED;
    }

    private void changed(MutableVillage village) {
        village.revision++;
        setDirty();
    }

    private static boolean validPosition(BlockPos pos) {
        return pos != null && Math.abs((long) pos.getX()) <= 30_000_000L
                && Math.abs((long) pos.getZ()) <= 30_000_000L && Math.abs((long) pos.getY()) <= 4096L;
    }

    private static boolean validLegacyKey(VillageKey key) {
        return key != null && Math.abs((long) key.anchorX()) <= 30_000_000L
                && Math.abs((long) key.anchorZ()) <= 30_000_000L;
    }

    /** The editor uses the same acceptance rule; authoritative mutations still validate again. */
    public static boolean validName(String raw) { return safeName(raw)!=null; }
    private static String safeName(String raw) {
        if (raw == null || raw.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                || Character.getType(cp) == Character.FORMAT || cp == '\u00a7')) return null;
        String value = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        return value.isBlank() || value.codePointCount(0, value.length()) > 48 ? null : value;
    }

    static LivingKingdomsState fromNbt(CompoundTag root) {
        LivingKingdomsState state = new LivingKingdomsState();
        if (root == null) return state;
        state.retainedRoot = root.copy();
        int schema = root.getIntOr("schemaVersion", 0);
        ListTag entries = root.getListOrEmpty("villages");
        if (schema > SCHEMA || schema < 0 || entries.size() > MAX_VILLAGES) {
            state.readOnly = true;
            return state;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof CompoundTag)) {
                state.readOnly = true;
                return state;
            }
            CompoundTag entry = entries.getCompoundOrEmpty(index);
            try {
                UUID id = UUID.fromString(entry.getStringOr("id", ""));
                VillageKey key = readLegacyKey(entry);
                Origin origin = Origin.valueOf(entry.getStringOr("origin", ""));
                UUID founder = origin == Origin.CUSTOM
                        ? UUID.fromString(entry.getStringOr("founder", "")) : null;
                BlockPos arrival = new BlockPos(entry.getIntOr("arrivalX", Integer.MIN_VALUE),
                        entry.getIntOr("arrivalY", Integer.MIN_VALUE), entry.getIntOr("arrivalZ", Integer.MIN_VALUE));
                String name = safeName(entry.getStringOr("name", ""));
                if (name == null || !validPosition(arrival) || state.villages.containsKey(id)
                        || state.legacyAliases.containsKey(key)) throw new IllegalArgumentException("Conflicting village");
                MutableVillage village = new MutableVillage(id, founder, origin, key, arrival, name);
                village.retained = entry.copy();
                village.population=VillagePopulationHistory.fromNbt(entry.get("population"));
                for (String field : List.of("zones", "aliases", "delegates")) {
                    if (entry.contains(field) && !(entry.get(field) instanceof ListTag)) {
                        throw new IllegalArgumentException("Invalid list payload");
                    }
                }
                village.revision = Math.max(0, entry.getLongOr("revision", 0));
                ListTag delegates = entry.getListOrEmpty("delegates");
                if (delegates.size() > MAX_DELEGATES) throw new IllegalArgumentException("Too many delegates");
                for (int delegate = 0; delegate < delegates.size(); delegate++) {
                    village.delegates.add(UUID.fromString(delegates.getString(delegate).orElseThrow()));
                }
                ListTag zones = entry.getListOrEmpty("zones");
                if (zones.size() > MAX_ZONES) throw new IllegalArgumentException("Too many zones");
                for (int z = 0; z < zones.size(); z++) {
                    if (!(zones.get(z) instanceof CompoundTag bounds)) throw new IllegalArgumentException("Invalid zone");
                    Zone zone = new Zone(bounds.getIntOr("minX", Integer.MIN_VALUE),
                            bounds.getIntOr("maxX", Integer.MIN_VALUE), bounds.getIntOr("minY", Integer.MIN_VALUE),
                            bounds.getIntOr("maxY", Integer.MIN_VALUE), bounds.getIntOr("minZ", Integer.MIN_VALUE),
                            bounds.getIntOr("maxZ", Integer.MIN_VALUE));
                    if (!zone.valid() || !zone.localTo(arrival, 128)) throw new IllegalArgumentException("Invalid bounds");
                    village.zones.add(zone);
                    village.zonePayloads.put(zone, bounds.copy());
                }
                ListTag aliases = entry.getListOrEmpty("aliases");
                if (aliases.size() > MAX_ALIASES) throw new IllegalArgumentException("Too many aliases");
                for (int a = 0; a < aliases.size(); a++) {
                    if (!(aliases.get(a) instanceof CompoundTag aliasTag)) throw new IllegalArgumentException("Invalid alias");
                    VillageKey alias = readLegacyKey(aliasTag);
                    if (origin != Origin.GENERATED || !alias.dimension().equals(key.dimension())
                            || Math.abs((long) alias.anchorX() - key.anchorX()) > 8
                            || Math.abs((long) alias.anchorZ() - key.anchorZ()) > 8
                            || state.legacyAliases.containsKey(alias)) throw new IllegalArgumentException("Conflicting alias");
                    village.aliases.add(alias);
                }
                state.villages.put(id, village);
                state.indexArrival(village);
                state.legacyAliases.put(key, id);
                village.aliases.forEach(alias -> state.legacyAliases.put(alias, id));
                village.zones.forEach(zone -> state.indexZone(village, zone));
            } catch (IllegalArgumentException | java.util.NoSuchElementException ignored) {
                // Keep original optional data and reserve any readable identity: no silent reuse.
                state.quarantined.add(entry.copy());
                try { state.reservedIds.add(UUID.fromString(entry.getStringOr("id", ""))); }
                catch (IllegalArgumentException invalid) { /* ID cannot be interpreted. */ }
                try { state.reservedKeys.add(readLegacyKey(entry)); }
                catch (IllegalArgumentException invalid) { /* No valid alias to reserve. */ }
            }
        }
        state.founding.load(root.get("founding"));
        state.anchors.load(root.get("anchors"));
        state.people.load(root.get("people"));
        state.tavernGuests.load(root.get("tavernGuests"));
        state.markets.load(root.get("markets"));
        state.marketPlaces.load(root.get("marketPlaces"));
        state.marketHomes.load(root.get("marketHomes"));
        state.marketDays.load(root.get("marketDays"));
        state.recognition.load(root.get("recognition"));
        state.diplomacy.load(root.get("diplomacy"));
        state.relations.load(root.get("relations"));
        state.autonomy.load(root.get("autonomy"));
        state.diplomacy.validateAutonomousEvidence();
        state.crises.load(root.get("crises"));
        state.marketTravel.load(root.get("marketTravel"));
        return state;
    }

    private static VillageKey readLegacyKey(CompoundTag entry) {
        int x = entry.getIntOr("legacyX", Integer.MIN_VALUE);
        int z = entry.getIntOr("legacyZ", Integer.MIN_VALUE);
        if (Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L) {
            throw new IllegalArgumentException("Invalid legacy coordinate");
        }
        return new VillageKey(entry.getStringOr("dimension", ""), x, z);
    }

    static CompoundTag toNbt(LivingKingdomsState state) {
        if (state.readOnly) return state.retainedRoot.copy();
        CompoundTag root = state.retainedRoot.copy();
        root.putInt("schemaVersion", SCHEMA);
        ListTag entries = new ListTag();
        state.villages.values().stream().sorted((a, b) -> a.id.compareTo(b.id)).forEach(village -> {
            CompoundTag entry = village.retained.copy();
            entry.putString("id", village.id.toString());
            entry.putString("origin", village.origin.name());
            entry.putString("dimension", village.legacyKey.dimension());
            entry.putInt("legacyX", village.legacyKey.anchorX());
            entry.putInt("legacyZ", village.legacyKey.anchorZ());
            entry.putInt("arrivalX", village.arrival.getX());
            entry.putInt("arrivalY", village.arrival.getY());
            entry.putInt("arrivalZ", village.arrival.getZ());
            entry.putString("name", village.name);
            entry.putLong("revision", village.revision);
            entry.put("population",village.population.toNbt());
            if (village.founder != null) entry.putString("founder", village.founder.toString());
            ListTag delegates = new ListTag();
            village.delegates.stream().sorted().forEach(id -> delegates.add(StringTag.valueOf(id.toString())));
            entry.put("delegates", delegates);
            ListTag zones = new ListTag();
            for (Zone zone : village.zones) {
                CompoundTag bounds = village.zonePayloads.getOrDefault(zone, new CompoundTag()).copy();
                bounds.putInt("minX", zone.minX); bounds.putInt("maxX", zone.maxX);
                bounds.putInt("minY", zone.minY); bounds.putInt("maxY", zone.maxY);
                bounds.putInt("minZ", zone.minZ); bounds.putInt("maxZ", zone.maxZ);
                zones.add(bounds);
            }
            entry.put("zones", zones);
            ListTag aliases = new ListTag();
            village.aliases.stream().sorted((a, b) -> a.anchorX() == b.anchorX()
                    ? Integer.compare(a.anchorZ(), b.anchorZ()) : Integer.compare(a.anchorX(), b.anchorX()))
                    .forEach(alias -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putString("dimension", alias.dimension());
                        tag.putInt("legacyX", alias.anchorX()); tag.putInt("legacyZ", alias.anchorZ());
                        aliases.add(tag);
                    });
            entry.put("aliases", aliases);
            entries.add(entry);
        });
        state.quarantined.forEach(entry -> entries.add(entry.copy()));
        root.put("villages", entries);
        root.put("anchors", state.anchors.toNbt());
        root.put("founding", state.founding.toNbt());
        root.put("people",state.people.toNbt());
        root.put("tavernGuests",state.tavernGuests.toNbt());
        root.put("markets",state.markets.toNbt());
        root.put("marketPlaces",state.marketPlaces.toNbt());
        root.put("marketHomes",state.marketHomes.toNbt());
        root.put("marketDays",state.marketDays.toNbt());
        root.put("marketTravel",state.marketTravel.toNbt());
        root.put("recognition",state.recognition.toNbt());
        root.put("diplomacy",state.diplomacy.toNbt());
        root.put("relations",state.relations.toNbt());
        root.put("autonomy",state.autonomy.toNbt());
        root.put("crises",state.crises.toNbt());
        return root;
    }

    public enum Origin { GENERATED, CUSTOM }
    public enum FoundingCode { APPLIED, UNCHANGED, INVALID, UNKNOWN, CONFLICT, READ_ONLY }
    public record FoundingResult(FoundingCode code, UUID villageId) {}
    public enum Mutation { APPLIED, UNCHANGED, UNAUTHORIZED, STALE, INVALID, READ_ONLY }
    public record VillageView(UUID id, UUID founder, Origin origin, VillageKey legacyKey,
                              BlockPos arrival, String name, long revision, Set<UUID> delegates) {
        public VillageView { arrival = arrival.immutable(); delegates = Set.copyOf(delegates); }
    }

    /** Inclusive bounded building neighborhood; no world reads or automatic chunk loading. */
    public record Zone(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean contains(BlockPos pos) {
            return pos != null && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
        private boolean valid() {
            return validPosition(new BlockPos(minX, minY, minZ)) && validPosition(new BlockPos(maxX, maxY, maxZ))
                    && minX <= maxX && minY <= maxY && minZ <= maxZ
                    && (long) maxX - minX <= 24 && (long) maxY - minY <= 16 && (long) maxZ - minZ <= 24;
        }
        private boolean localTo(BlockPos arrival, int radius) {
            return Math.abs((long) minX - arrival.getX()) <= radius
                    && Math.abs((long) maxX - arrival.getX()) <= radius
                    && Math.abs((long) minZ - arrival.getZ()) <= radius
                    && Math.abs((long) maxZ - arrival.getZ()) <= radius
                    && Math.abs((long) minY - arrival.getY()) <= 32
                    && Math.abs((long) maxY - arrival.getY()) <= 32;
        }
        boolean touches(Zone other) {
            return (long) minX <= (long) other.maxX + 2 && (long) maxX + 2 >= other.minX
                    && (long) minY <= (long) other.maxY + 2 && (long) maxY + 2 >= other.minY
                    && (long) minZ <= (long) other.maxZ + 2 && (long) maxZ + 2 >= other.minZ;
        }
    }

    private static final class MutableVillage {
        final UUID id;
        final UUID founder;
        final Origin origin;
        final VillageKey legacyKey;
        BlockPos arrival;
        String name;
        long revision;
        final Set<UUID> delegates = new HashSet<>();
        final List<Zone> zones = new ArrayList<>();
        final Map<Zone, CompoundTag> zonePayloads = new HashMap<>();
        final Set<VillageKey> aliases = new HashSet<>();
        CompoundTag retained = new CompoundTag();
        VillagePopulationHistory population=new VillagePopulationHistory();
        MutableVillage(UUID id, UUID founder, Origin origin, VillageKey legacyKey, BlockPos arrival, String name) {
            this.id = id; this.founder = founder; this.origin = origin; this.legacyKey = legacyKey;
            this.arrival = arrival.immutable(); this.name = name;
        }
        VillageView view() { return new VillageView(id, founder, origin, legacyKey, arrival, name, revision, delegates); }
    }
}
