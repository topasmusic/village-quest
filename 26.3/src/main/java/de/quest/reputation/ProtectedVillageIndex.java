package de.quest.reputation;

import de.quest.data.QuestState;
import de.quest.shrine.VillageBondService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Physical registered anchors, not owner-qualified route slots. Queries touch only nine 64-block cells. */
public final class ProtectedVillageIndex {
    private record Cell(String dimension, int x, int z) {}
    private record AnchorChunk(String dimension, int x, int z) {}
    private final Map<Cell, Set<VillageKey>> buckets = new HashMap<>();
    private final Map<VillageKey, Integer> heights = new HashMap<>();
    private final Map<AnchorChunk, Set<VillageKey>> pendingHeights = new HashMap<>();
    private final List<net.minecraft.nbt.Tag> rejectedAnchors = new ArrayList<>();
    private int lastCandidates;
    private CompoundTag preserved;
    public int lastCandidatesExamined() { return lastCandidates; }
    public boolean upsert(VillageKey key) {
        if (preserved != null) return false;
        if (!heights.containsKey(key)) pendingHeights.computeIfAbsent(anchorChunk(key), ignored -> new HashSet<>()).add(key);
        return buckets.computeIfAbsent(new Cell(key.dimension(), key.anchorX() >> 6, key.anchorZ() >> 6), ignored -> new HashSet<>()).add(key);
    }
    public boolean upsert(VillageKey key, int anchorY) {
        if (preserved != null) return false;
        upsert(key); Integer previous = heights.put(key, anchorY);
        var pending = pendingHeights.get(anchorChunk(key));
        if (pending != null) { pending.remove(key); if (pending.isEmpty()) pendingHeights.remove(anchorChunk(key)); }
        return previous == null || previous != anchorY;
    }
    private static AnchorChunk anchorChunk(VillageKey key) {
        return new AnchorChunk(key.dimension(), key.anchorX() >> 4, key.anchorZ() >> 4);
    }
    public Optional<VillageKey> resolve(String dimension, BlockPos position) {
        return resolve(dimension, position, null);
    }
    public Optional<VillageKey> resolve(ServerLevel world, BlockPos position) {
        return resolve(world.dimension().identifier().toString(), position, world);
    }
    private Optional<VillageKey> resolve(String dimension, BlockPos position, ServerLevel world) {
        int cellX = position.getX() >> 6, cellZ = position.getZ() >> 6;
        long best = Long.MAX_VALUE; VillageKey result = null; lastCandidates = 0;
        for (int x = cellX - 1; x <= cellX + 1; x++) for (int z = cellZ - 1; z <= cellZ + 1; z++) {
            for (VillageKey key : buckets.getOrDefault(new Cell(dimension, x, z), Set.of())) {
                lastCandidates++;
                long dx = (long) position.getX() - key.anchorX(), dz = (long) position.getZ() - key.anchorZ();
                long distance = dx * dx + dz * dz; if (distance > 64L * 64) continue;
                Integer y = heights.get(key);
                if (y == null && world != null) {
                    BlockPos anchor = new BlockPos(key.anchorX(), position.getY(), key.anchorZ());
                    if (!world.hasChunkAt(anchor)) continue;
                    y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, key.anchorX(), key.anchorZ());
                    upsert(key, y); QuestState.get(world.getServer()).setDirty();
                }
                if (y == null || Math.abs((long) position.getY() - y) > 32) continue;
                if (distance < best || distance == best && (result == null || key.compareTo(result) < 0)) { best = distance; result = key; }
            }
        }
        return Optional.ofNullable(result);
    }
    public CompoundTag toNbt() {
        if (preserved != null) return preserved.copy();
        CompoundTag root = new CompoundTag(); root.putInt("schema", 1); ListTag anchors = new ListTag();
        buckets.values().stream().flatMap(Set::stream).sorted().forEach(key -> {
            CompoundTag tag = SocialReputationData.villageTag(key); Integer y = heights.get(key);
            if (y != null) tag.putInt("y", y); anchors.add(tag);
        }); rejectedAnchors.forEach(tag -> anchors.add(tag.copy()));
        root.put("anchors", anchors); return root;
    }
    public static ProtectedVillageIndex fromNbt(CompoundTag root) {
        ProtectedVillageIndex index = new ProtectedVillageIndex();
        if (root == null || root.isEmpty()) return index;
        if (!(root.get("schema") instanceof net.minecraft.nbt.IntTag) || root.getIntOr("schema", 0) != 1
                || !(root.get("anchors") instanceof ListTag)) { index.preserved = root.copy(); return index; }
        for (var raw : root.getListOrEmpty("anchors")) {
            if (!(raw instanceof CompoundTag tag)) { index.rejectedAnchors.add(raw.copy()); continue; }
            try {
                VillageKey key = SocialReputationData.villageFromTag(tag);
                if (tag.contains("y") && !(tag.get("y") instanceof net.minecraft.nbt.IntTag))
                    throw new IllegalArgumentException("Invalid anchor height");
                if (tag.get("y") instanceof net.minecraft.nbt.IntTag) index.upsert(key, tag.getIntOr("y", 0));
                else index.upsert(key);
            } catch (IllegalArgumentException invalid) {
                // Preserve original metadata for repair without using it to assign guilt.
                index.rejectedAnchors.add(tag.copy());
            }
        }
        return index;
    }
    public static void register(ServerLevel world, VillageKey key) {
        QuestState state = QuestState.get(world.getServer());
        var index = state.protectedVillages(); boolean changed = index.upsert(key);
        changed |= index.captureHeight(world, key);
        if (changed) state.setDirty();
    }
    private boolean captureHeight(ServerLevel world, VillageKey key) {
        if (preserved != null || !world.dimension().identifier().toString().equals(key.dimension())
                || !world.hasChunkAt(new BlockPos(key.anchorX(), 0, key.anchorZ()))) return false;
        return upsert(key, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, key.anchorX(), key.anchorZ()));
    }
    /** Repair only pending anchors in this already-loaded chunk; never request a chunk load. */
    public static void onChunkLoad(ServerLevel world, net.minecraft.world.level.ChunkPos chunk) {
        QuestState state = QuestState.get(world.getServer()); var index = state.protectedVillages();
        var pending = index.pendingHeights.get(new AnchorChunk(world.dimension().identifier().toString(), chunk.x(), chunk.z()));
        if (pending == null) return;
        boolean changed = false;
        for (VillageKey key : Set.copyOf(pending)) changed |= index.captureHeight(world, key);
        if (changed) state.setDirty();
    }
    public static void primeHistorical(MinecraftServer server) {
        QuestState state = QuestState.get(server);
        state.getPlayersView().values().forEach(data -> {
            for (int i = 0; i < VillageBondService.historicalVillageCount(data); i++) {
                String dimension = data.getTradeRouteString(VillageBondService.villageKey(i, "dimension"));
                if (dimension.isBlank()) dimension = "minecraft:overworld";
                try {
                    var key = new VillageKey(dimension, data.getTradeRouteInt(VillageBondService.villageKey(i, "x")),
                            data.getTradeRouteInt(VillageBondService.villageKey(i, "z")));
                    state.protectedVillages().upsert(key);
                }
                catch (IllegalArgumentException invalid) { /* Unknown metadata stays unprotected. */ }
            }
        });
        // Spawn chunks may have loaded before SERVER_STARTED, including anchors already in SavedData.
        var index = state.protectedVillages();
        for (var entry : List.copyOf(index.pendingHeights.entrySet())) {
            var world = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(entry.getKey().dimension())));
            if (world != null) for (var key : Set.copyOf(entry.getValue())) index.captureHeight(world, key);
        }
        state.setDirty();
    }
}
