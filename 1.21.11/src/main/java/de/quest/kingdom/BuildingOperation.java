package de.quest.kingdom;

import net.minecraft.nbt.CompoundTag;

/** Earned credit is durable; operation and spawning always require recent loaded evidence. */
public final class BuildingOperation {
    private static final int SCHEMA = 1;
    private static final long GRACE_TICKS = 600, FRESH_TICKS = 40, MAX_OBSERVED_GAP = 100;
    private Phase phase = Phase.UNCONFIRMED;
    private boolean earned, readOnly;
    private long invalidTicks, lastTick = -1;
    private BuildingScanner.Status latest = BuildingScanner.Status.UNKNOWN;
    private CompoundTag retained = new CompoundTag();

    public void observe(BuildingScanner.Status status, long tick) {
        if (readOnly || status == null || tick < 0 || lastTick >= 0 && tick < lastTick) return;
        if (status == BuildingScanner.Status.UNKNOWN) { latest = status; lastTick = -1; return; }
        long delta = lastTick < 0 || tick - lastTick > MAX_OBSERVED_GAP ? 0 : tick - lastTick;
        if (status == BuildingScanner.Status.VALID) {
            earned = true; phase = Phase.ACTIVE; invalidTicks = 0;
        } else if (earned) {
            if (latest == BuildingScanner.Status.INVALID) invalidTicks = Math.min(GRACE_TICKS, invalidTicks + delta);
            phase = invalidTicks >= GRACE_TICKS ? Phase.PAUSED : Phase.GRACE;
        }
        latest = status; lastTick = tick;
    }

    public boolean earned() { return earned; }
    boolean readOnly() { return readOnly; }
    void invalidate() { latest = BuildingScanner.Status.UNKNOWN; lastTick = -1; }
    public Phase phase() { return phase; }
    public long invalidTicks() { return invalidTicks; }
    public boolean operational(long now) {
        return fresh(now) && (phase == Phase.ACTIVE || phase == Phase.GRACE);
    }
    public boolean maySpawn(long now) { return fresh(now) && latest == BuildingScanner.Status.VALID; }
    private boolean fresh(long now) {
        return !readOnly && latest != BuildingScanner.Status.UNKNOWN && lastTick >= 0
                && now >= lastTick && now - lastTick <= FRESH_TICKS;
    }
    public enum Phase { UNCONFIRMED, ACTIVE, GRACE, PAUSED }

    CompoundTag toNbt() {
        CompoundTag tag = retained.copy();
        if (!readOnly) {
            tag.putInt("schema", SCHEMA); tag.putString("phase", phase.name());
            tag.putBoolean("earned", earned); tag.putLong("invalidTicks", invalidTicks);
        }
        return tag;
    }
    static BuildingOperation fromNbt(CompoundTag tag) {
        var operation = new BuildingOperation();
        if (tag == null) return operation;
        operation.retained = tag.copy();
        int schema = tag.getIntOr("schema", 0);
        if (schema < 0 || schema > SCHEMA) { operation.readOnly = true; return operation; }
        try {
            operation.phase = Phase.valueOf(tag.getStringOr("phase", "UNCONFIRMED"));
            operation.earned = tag.getBooleanOr("earned", false);
            operation.invalidTicks = tag.getLongOr("invalidTicks", 0);
            if (operation.invalidTicks < 0 || operation.invalidTicks > GRACE_TICKS
                    || !operation.earned && operation.phase != Phase.UNCONFIRMED
                    || operation.phase == Phase.PAUSED && operation.invalidTicks != GRACE_TICKS)
                throw new IllegalArgumentException("Invalid operation");
        } catch (IllegalArgumentException badData) {
            operation.readOnly = true; operation.earned = false; operation.phase = Phase.UNCONFIRMED;
        }
        return operation;
    }
}
