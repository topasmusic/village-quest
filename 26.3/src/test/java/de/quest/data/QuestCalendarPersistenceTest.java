package de.quest.data;

import de.quest.util.QuestCalendar;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class QuestCalendarPersistenceTest {
    @Test void saveReloadPreservesWorldCalendarPersonalProgressAndOfflineRewardMarkers() {
        var state = QuestState.fromNbt(new CompoundTag());
        state.questCalendar().update(true, 0, 20000, 19999);
        state.questCalendar().update(true, 12000, 20000, 19999);
        var offline = UUID.randomUUID(); var data = state.getPlayerData(offline);
        data.setAcceptedDay(20000); data.setProgressDay(20000);
        data.setDailyInt("progress", 8); data.setLastRewardDay(20000); data.setWeeklyRewardCycle(19999);
        var snapshot = new CompoundTag(); snapshot.putInt("version", 1);
        assertTrue(data.vanillaRewards().accept("daily", "WHEAT_HARVEST:20000", snapshot));
        assertFalse(data.vanillaRewards().redeem("daily", "WHEAT_HARVEST:20000").isEmpty());
        var party = new CompoundTag(); party.putString("fixture", "retain"); state.setQuestPartyState(party);
        var restored = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(12000, restored.questCalendar().remainingTicks(false));
        assertEquals(20000, restored.questCalendar().day());
        assertEquals(19999, restored.questCalendar().week());
        assertEquals(8, restored.getPlayerData(offline).getDailyInt("progress"));
        assertEquals(20000, restored.getPlayerData(offline).getLastRewardDay());
        assertEquals(19999, restored.getPlayerData(offline).getWeeklyRewardCycle());
        assertTrue(restored.getPlayerData(offline).vanillaRewards().redeem("daily", "WHEAT_HARVEST:20000").isEmpty());
        assertFalse(restored.getPlayerData(offline).vanillaRewards().accept("daily", "WHEAT_HARVEST:20000", snapshot));
        assertEquals("retain", restored.getQuestPartyState().getStringOr("fixture", ""));
        restored.resetAllProgress();
        assertEquals(20000, restored.questCalendar().day());
    }

    @Test void legacySaveInitializesWithoutChangingExistingPlayerDays() {
        var state = QuestState.fromNbt(new CompoundTag());
        var id = UUID.randomUUID(); state.getPlayerData(id).setLastRewardDay(20000);
        state.questCalendar().update(true, 90000, 20000, 19999);
        assertEquals(20000, state.questCalendar().day());
        assertEquals(20000, state.getPlayerData(id).getLastRewardDay());
        assertEquals(24000, state.questCalendar().remainingTicks(false));
    }
}
