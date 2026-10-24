package de.quest.kingdom;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Private short-lived building desk. Village management rights are checked again by every mutation service. */
public final class BuildingSessionPolicy {
    public static final UUID NONE = new UUID(0, 0);
    private final UUID id = UUID.randomUUID(), owner;
    private final String dimension;
    private final BlockPos position;
    private final long opened;
    private Map<UUID, Long> offered = Map.of();
    private UUID selected = NONE;
    private UUID official = NONE;
    private long anchorRevision = -1;
    private long villageRevision = -1, revision;
    private boolean closed;

    public BuildingSessionPolicy(UUID owner, String dimension, BlockPos position, long tick) {
        if (owner == null || dimension == null || position == null || tick < 0) throw new IllegalArgumentException("Invalid building session");
        this.owner = owner; this.dimension = dimension; this.position = position.immutable(); this.opened = tick;
    }
    public Access validate(UUID actor, String dimension, BlockPos position, long tick, long expectedRevision) {
        if (closed || !owner.equals(actor) || !this.dimension.equals(dimension) || position == null
                || this.position.distSqr(position) > 64 || tick < opened) return Access.INVALID;
        if (tick - opened > 1200) return Access.EXPIRED;
        return expectedRevision == revision ? Access.ALLOWED : Access.STALE;
    }
    public void offer(Map<UUID, Long> candidates) {
        if (candidates == null || candidates.size() > 8 || candidates.containsKey(NONE)
                || candidates.values().stream().anyMatch(value -> value == null || value < 0)) throw new IllegalArgumentException("Invalid village choices");
        offered = Map.copyOf(candidates);
        if (!selected.equals(NONE) && !offered.containsKey(selected)) { selected = NONE; villageRevision = -1; changed(); }
    }
    public boolean select(UUID village) {
        Long expected = offered.get(village);
        if (closed || expected == null) return false;
        if (!selected.equals(village) || villageRevision != expected) {
            selected = village; villageRevision = expected; changed();
        }
        return true;
    }
    public void clearSelection() {
        if (!closed && !selected.equals(NONE)) { selected = NONE; villageRevision = -1; changed(); }
    }
    public void offerAnchor(UUID anchor, long expectedRevision) {
        if (anchor == null || expectedRevision < -1 || anchor.equals(NONE) != (expectedRevision == -1))
            throw new IllegalArgumentException("Invalid official anchor snapshot");
        if (!official.equals(anchor) || anchorRevision != expectedRevision) {
            official = anchor; anchorRevision = expectedRevision; changed();
        }
    }
    public boolean matchesAnchor(UUID anchor, long expectedRevision) {
        return !closed && official.equals(anchor) && anchorRevision == expectedRevision;
    }
    public void changed() { if (revision == Long.MAX_VALUE) close(); else revision++; }
    public void close() { closed = true; }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String dimension() { return dimension; }
    public BlockPos position() { return position; }
    public UUID selected() { return selected; }
    public long villageRevision() { return villageRevision; }
    public long revision() { return revision; }
    public enum Access { ALLOWED, INVALID, EXPIRED, STALE }
}
