package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.quest.daily.FirstDailyChoiceService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class QuestStateGuildIntroductionTest {
    private static final UUID PLAYER = UUID.fromString("25d78e10-5168-4215-b677-ad6a61c38722");

    @Test
    void firstDailyAndWelcomeProgressSurviveSaveReload() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = state.getPlayerData(PLAYER);
        data.setTradeRouteFlag("guild_intro.first_daily_completed", true);
        data.setTradeRouteFlag("guild_intro.welcome_active", true);
        data.setTradeRouteFlag("guild_intro.welcome_greeted_" + PLAYER, true);
        data.setTradeRouteInt("guild_intro.welcome_village", 3);
        data.setTradeRouteInt("guild_intro.welcome_greetings", 1);

        PlayerQuestData restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);

        assertTrue(restored.hasTradeRouteFlag("guild_intro.first_daily_completed"));
        assertTrue(restored.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertTrue(restored.hasTradeRouteFlag("guild_intro.welcome_greeted_" + PLAYER));
        assertEquals(3, restored.getTradeRouteInt("guild_intro.welcome_village"));
        assertEquals(1, restored.getTradeRouteInt("guild_intro.welcome_greetings"));
        assertEquals(0, restored.getTradeRouteInt("route_count"));
    }

    @Test
    void legacyDailyProfileMigratesDuringLoadWithoutCompletingWelcomeOrCreatingAQuest() {
        QuestState legacy = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData data = legacy.getPlayerData(PLAYER);
        data.setDailyDiscovered(true);

        QuestState firstLoad = QuestState.fromNbt(QuestState.toNbt(legacy));
        PlayerQuestData migrated = firstLoad.getPlayerData(PLAYER);

        assertTrue(migrated.hasTradeRouteFlag("guild_intro.first_daily_completed"));
        assertFalse(migrated.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertFalse(migrated.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(PlayerQuestData.UNSET_DAY, migrated.getAcceptedDay());
        assertEquals(0L, migrated.getCurrencyBalance());

        PlayerQuestData secondLoad = QuestState.fromNbt(QuestState.toNbt(firstLoad)).getPlayerData(PLAYER);
        assertTrue(secondLoad.hasTradeRouteFlag("guild_intro.first_daily_completed"));
        assertFalse(secondLoad.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(PlayerQuestData.UNSET_DAY, secondLoad.getAcceptedDay());
    }

    @Test
    void freshMigrationMarkerSurvivesReloadAndLaterMilestoneCannotSkipFirstDaily() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData fresh = state.getPlayerData(PLAYER);
        assertTrue(FirstDailyChoiceService.migratePre24Progress(fresh));
        fresh.setMilestoneFlag("guild_corner.discovered", true);

        PlayerQuestData restored = QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(PLAYER);

        assertFalse(FirstDailyChoiceService.migratePre24Progress(restored));
        assertFalse(FirstDailyChoiceService.isCompleted(restored));
        assertTrue(FirstDailyChoiceService.canChoose(restored));
        assertFalse(restored.hasTradeRouteFlag("guild_intro.welcome_completed"));
        assertEquals(PlayerQuestData.UNSET_DAY, restored.getAcceptedDay());
    }
}
