package de.quest.caravan;

/** Classifies only the loaded evidence of a generated village without loading chunks. */
final class VillageInhabitantPolicy {
    private static final long MAX_CONFIDENT_CHUNKS = 1024;

    enum Status { INHABITED, ABANDONED, UNKNOWN }

    @FunctionalInterface
    interface ChunkLoaded {
        boolean test(int chunkX, int chunkZ);
    }

    private VillageInhabitantPolicy() {}

    static Status classify(boolean hasLivingVillager, int minChunkX, int maxChunkX,
                           int minChunkZ, int maxChunkZ, ChunkLoaded loaded) {
        if (hasLivingVillager) {
            return Status.INHABITED;
        }
        long width = (long) maxChunkX - minChunkX + 1;
        long depth = (long) maxChunkZ - minChunkZ + 1;
        if (width <= 0 || depth <= 0 || width * depth > MAX_CONFIDENT_CHUNKS) {
            return Status.UNKNOWN;
        }
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!loaded.test(x, z)) {
                    return Status.UNKNOWN;
                }
            }
        }
        return Status.ABANDONED;
    }
}
