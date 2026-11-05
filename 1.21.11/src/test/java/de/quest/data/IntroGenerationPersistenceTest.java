package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

final class IntroGenerationPersistenceTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void malformedAndDuplicateGenerationNumbersRemainRawAndCannotBecomeFreshGenerationOne() {
        for (Tag value : java.util.List.of(StringTag.valueOf("future"), LongTag.valueOf(2), IntTag.valueOf(0))) {
            var owner = UUID.randomUUID(); var other = UUID.randomUUID();
            var state = QuestState.fromNbt(new CompoundTag());
            state.getPlayerData(owner).setCurrencyBalance(12);
            var image = QuestState.toNbt(state); var manager = image.getCompoundOrEmpty("questManager");
            var numbers = manager.getListOrEmpty("tradeRouteInts");
            var row = new CompoundTag(); row.putString("id", owner.toString());
            row.putString("key", PlayerQuestData.PROGRESS_GENERATION); row.put("v", value);
            numbers.add(row); manager.put("tradeRouteInts", numbers);
            for (int reload = 0; reload < 3; reload++) {
                state = QuestState.fromNbt(image);
                assertFalse(state.getPlayerData(owner).validProgressGeneration());
                var blocked = state.getPlayerData(owner);
                assertThrows(IllegalStateException.class, blocked::personalRestart);
                state.getPlayerData(other).setTradeRouteInt("ordinary.progress", reload + 1);
                image = QuestState.toNbt(state);
                assertTrue(image.getCompoundOrEmpty("questManager").getListOrEmpty("tradeRouteInts").contains(row));
                assertEquals(12, state.getPlayerData(owner).getCurrencyBalance());
            }
        }
        var owner = UUID.randomUUID(); var image = new CompoundTag(); var manager = new CompoundTag();
        var rows = new ListTag();
        for (int generation : new int[]{2, 3}) {
            var row = new CompoundTag(); row.putString("id", owner.toString());
            row.putString("key", PlayerQuestData.PROGRESS_GENERATION); row.putInt("v", generation); rows.add(row);
        }
        manager.put("tradeRouteInts", rows); image.put("questManager", manager);
        var state = QuestState.fromNbt(image);
        assertFalse(state.getPlayerData(owner).validProgressGeneration());
        assertEquals(rows, QuestState.toNbt(state).getCompoundOrEmpty("questManager").getListOrEmpty("tradeRouteInts"));
    }
}
