package de.quest.map;

/** Shared screen/HUD limits. No world access or clock reads in this policy. */
public final class MapWorkBudget {
    public static final int SAMPLES = 384;
    public static final int TILES = 3;
    public static final int UPLOAD_INTERVAL = 4;
    private long tick = Long.MIN_VALUE;
    private long lastUpload = Long.MIN_VALUE;
    private int samples, tiles, reads;

    public void begin(long now) {
        if (now != tick) {
            if (now < tick) lastUpload = Long.MIN_VALUE;
            tick = now;
            samples = tiles = reads = 0;
        }
    }

    public boolean sample() { return samples++ < SAMPLES; }
    public boolean tile() { return tiles++ < TILES; }
    public boolean diskRead() { return reads++ < 1; }
    public boolean upload() {
        if (lastUpload != Long.MIN_VALUE && tick - lastUpload < UPLOAD_INTERVAL) return false;
        lastUpload = tick;
        return true;
    }
}
