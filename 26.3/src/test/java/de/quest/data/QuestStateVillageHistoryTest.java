package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class QuestStateVillageHistoryTest {
    private static final UUID OWNER = UUID.fromString("270a7704-aa46-49e2-b882-7d4746171f30");

    @Test
    void saveLoadPreservesMoreThanEightHistoricalVillageRecords() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(OWNER);
        data.setTradeRouteInt("bond_village_count", 9);
        for (int i = 0; i < 9; i++) {
            data.setTradeRouteInt("bond_village_" + i + "_x", 100 + i * 32);
            data.setTradeRouteInt("bond_village_" + i + "_z", -200 - i * 48);
            data.setTradeRouteInt("bond_village_" + i + "_type", i % 5 + 1);
            data.setTradeRouteInt("bond_village_" + i + "_completions", i);
        }

        QuestState loaded = QuestState.fromNbt(QuestState.toNbt(state));
        PlayerQuestData restored = loaded.getPlayerData(OWNER);

        assertEquals(9, restored.getTradeRouteInt("bond_village_count"));
        assertEquals(324, restored.getTradeRouteInt("bond_village_7_x"));
        assertEquals(356, restored.getTradeRouteInt("bond_village_8_x"));
        assertEquals(-584, restored.getTradeRouteInt("bond_village_8_z"));
        assertEquals(8, restored.getTradeRouteInt("bond_village_8_completions"));
    }

    @Test
    void existingEightVillageSaveShapeRemainsReadable() {
        CompoundTag oldRoot = new CompoundTag();
        CompoundTag oldManager = new CompoundTag();
        ListTag oldTradeRouteInts = new ListTag();
        oldTradeRouteInts.add(namedInt("bond_village_count", 8));
        oldTradeRouteInts.add(namedInt("bond_village_7_x", 777));
        oldTradeRouteInts.add(namedInt("bond_village_7_z", -888));
        oldTradeRouteInts.add(namedInt("bond_village_7_level", 3));
        oldManager.put("tradeRouteInts", oldTradeRouteInts);
        oldRoot.put("questManager", oldManager);

        PlayerQuestData restored = QuestState.fromNbt(oldRoot).getPlayerData(OWNER);

        assertEquals(8, restored.getTradeRouteInt("bond_village_count"));
        assertEquals(777, restored.getTradeRouteInt("bond_village_7_x"));
        assertEquals(-888, restored.getTradeRouteInt("bond_village_7_z"));
        assertEquals(3, restored.getTradeRouteInt("bond_village_7_level"));
    }

    private static CompoundTag namedInt(String key, int value) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", OWNER.toString());
        entry.putString("key", key);
        entry.putInt("v", value);
        return entry;
    }
}
