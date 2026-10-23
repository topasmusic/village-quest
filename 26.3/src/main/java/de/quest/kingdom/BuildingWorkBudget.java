package de.quest.kingdom;

/** Shared per-server tick allowance for native building scans, desk actions and founding paths. */
public final class BuildingWorkBudget {
    private long tick = -1;
    private int blocks, paths;
    public void begin(long now) {
        if (now >= 0 && now > tick) { tick = now; blocks = 256; paths = 12; }
    }
    public int blocks() { return blocks; }
    public int paths() { return paths; }
    public boolean takeBlock() { return takeBlocks(1); }
    public boolean takeBlocks(int count) {
        if (count < 0 || count > blocks) return false;
        blocks -= count; return true;
    }
    public boolean takePaths(int count) {
        if (count < 0 || count > paths) return false;
        paths -= count; return true;
    }
}
