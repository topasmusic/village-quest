package de.quest.kingdom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/** Bounded server-thread cache/index. No world access: dirtiness immediately revokes old evidence. */
public final class BuildingWatchIndex {
    private static final int CAPACITY = 256;
    private final Map<UUID, Watch> watches = new HashMap<>();
    private final Map<String, Map<Long, Set<UUID>>> chunks = new HashMap<>();
    private long sequence;

    public boolean watch(UUID id, Spec spec) {
        if (id == null || spec == null || sequence == Long.MAX_VALUE) return false;
        Watch previous = watches.get(id);
        if (previous != null && previous.spec.equals(spec)) return true;
        if (previous == null && watches.size() >= CAPACITY) return false;
        forget(id);
        Watch watch = new Watch(spec, ++sequence); watches.put(id, watch);
        chunkKeys(spec, key -> chunks.computeIfAbsent(spec.dimension, ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new HashSet<>()).add(id));
        return true;
    }
    public Optional<Spec> spec(UUID id) { Watch watch = watches.get(id); return watch == null ? Optional.empty() : Optional.of(watch.spec); }
    public long revision(UUID id) { Watch watch = watches.get(id); return watch == null ? -1 : watch.revision; }
    public boolean publish(UUID id, long revision, BuildingScanner.Report report, long tick) {
        Watch watch = watches.get(id);
        if (watch == null || sequence == Long.MAX_VALUE || watch.revision != revision || report == null || tick < 0
                || report.role() != watch.spec.role || watch.snapshot != null && tick < watch.snapshot.tick
                || watch.unloaded && report.status() != BuildingScanner.Status.UNKNOWN) return false;
        watch.snapshot = new Snapshot(report, tick, revision); return true;
    }
    /** Last completed unchanged report for advisory UI only; mutations must still use fresh(). */
    public Optional<Snapshot> display(UUID id,long tick) { return cached(id,tick,1600); }
    public Optional<Snapshot> fresh(UUID id, long tick) { return cached(id, tick, 40); }
    public Optional<Snapshot> cached(UUID id, long tick, long maxAge) {
        Watch watch = watches.get(id);
        return watch == null || watch.unloaded || watch.snapshot == null || maxAge < 0 || tick < watch.snapshot.tick
                || tick - watch.snapshot.tick > maxAge || watch.revision != watch.snapshot.revision
                ? Optional.empty() : Optional.of(watch.snapshot);
    }
    public Set<UUID> dirty(String dimension, BlockPos pos) {
        if (dimension == null || pos == null) return Set.of();
        Set<UUID> candidates = chunks.getOrDefault(dimension, Map.of()).getOrDefault(
                ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), Set.of());
        Set<UUID> affected = new HashSet<>();
        for (UUID id : candidates) {
            Watch watch = watches.get(id);
            if (watch != null && watch.spec.contains(pos)) { invalidate(watch); affected.add(id); }
        }
        return Set.copyOf(affected);
    }
    public void unloaded(UUID id) {
        Watch watch = watches.get(id);
        if (watch != null && !watch.unloaded) { invalidate(watch); watch.unloaded = true; }
    }
    public void resumed(UUID id) {
        Watch watch = watches.get(id);
        if (watch != null && watch.unloaded) { invalidate(watch); watch.unloaded = false; }
    }
    public void forget(UUID id) {
        Watch watch = watches.remove(id);
        if (watch == null) return;
        Map<Long, Set<UUID>> dimension = chunks.get(watch.spec.dimension);
        if (dimension == null) return;
        chunkKeys(watch.spec, key -> {
            Set<UUID> entries = dimension.get(key);
            if (entries != null) { entries.remove(id); if (entries.isEmpty()) dimension.remove(key); }
        });
        if (dimension.isEmpty()) chunks.remove(watch.spec.dimension);
    }
    public int size() { return watches.size(); }
    private void invalidate(Watch watch) {
        watch.snapshot = null;
        if (sequence < Long.MAX_VALUE) watch.revision = ++sequence;
    }
    private static void chunkKeys(Spec spec, java.util.function.LongConsumer consumer) {
        for (int x = (spec.position.getX() - spec.range.horizontal) >> 4; x <= (spec.position.getX() + spec.range.horizontal) >> 4; x++)
            for (int z = (spec.position.getZ() - spec.range.horizontal) >> 4; z <= (spec.position.getZ() + spec.range.horizontal) >> 4; z++)
                consumer.accept(ChunkPos.pack(x, z));
    }
    public record Spec(String dimension, BlockPos position, Direction facing, BuildingScanner.Role role,
                       BuildingScanner.Range range, long configurationRevision) {
        public Spec {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || position == null
                    || Math.abs((long) position.getX()) > 30_000_000 || Math.abs((long) position.getZ()) > 30_000_000
                    || Math.abs((long) position.getY()) > 4096 || facing == null || !facing.getAxis().isHorizontal()
                    || role == null || range == null || configurationRevision < 0) throw new IllegalArgumentException("Invalid building watch");
            position = position.immutable();
        }
        boolean contains(BlockPos pos) {
            return Math.abs((long) pos.getX() - position.getX()) <= range.horizontal
                    && Math.abs((long) pos.getZ() - position.getZ()) <= range.horizontal
                    && Math.abs((long) pos.getY() - position.getY()) <= range.vertical;
        }
    }
    public record Snapshot(BuildingScanner.Report report, long tick, long revision) {}
    private static final class Watch {
        final Spec spec; long revision; boolean unloaded;
        Snapshot snapshot;
        Watch(Spec spec, long revision) { this.spec = spec; this.revision = revision; }
    }
}
