package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestStateTerrainRetentionTest {
    @Test
    void modifiedTerrainHistoryNeverEvictsOldSafetyMarksAndRoundTripsPastFormerBound() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        for (int chunk = 0; chunk <= QuestState.MAX_MODIFIED_TERRAIN_CHUNKS; chunk++) {
            state.markTerrainModified(new BlockPos(chunk << 4, 64, 0));
        }
        assertEquals(QuestState.MAX_MODIFIED_TERRAIN_CHUNKS + 1, state.modifiedTerrainChunkCount());
        assertTrue(state.isTerrainModified(new BlockPos(0, 64, 0), 0));
        assertTrue(state.isTerrainModified(new BlockPos(QuestState.MAX_MODIFIED_TERRAIN_CHUNKS << 4, 64, 0), 0));

        QuestState loaded = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(QuestState.MAX_MODIFIED_TERRAIN_CHUNKS + 1, loaded.modifiedTerrainChunkCount());
        assertTrue(loaded.isTerrainModified(new BlockPos(0, 64, 0), 0));
    }

    @Test
    void legacyChunkListMigratesToCompactRegionStorageIncludingNegativeCoordinates() {
        CompoundTag root = new CompoundTag();
        CompoundTag manager = new CompoundTag();
        net.minecraft.nbt.ListTag chunks = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        long packed = (-17 & 0xffffffffL) | ((long) -33 << 32);
        entry.putLong("chunk", packed);
        chunks.add(entry);
        manager.put("modifiedTerrainChunks", chunks);
        root.put("questManager", manager);

        QuestState migrated = QuestState.fromNbt(root);
        assertTrue(migrated.isTerrainModified(new BlockPos(-17 << 4, 64, -33 << 4), 0));
        CompoundTag savedManager = QuestState.toNbt(migrated).getCompoundOrEmpty("questManager");
        assertTrue(savedManager.getListOrEmpty("modifiedTerrainChunks").isEmpty());
        assertFalse(savedManager.getListOrEmpty("modifiedTerrainRegions").isEmpty());
    }
}
