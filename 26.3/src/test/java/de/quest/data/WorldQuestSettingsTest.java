package de.quest.data;

import de.quest.config.*;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WorldQuestSettingsTest {
    @AfterEach void clearWorld() { VillageQuestServerConfig.bindWorld(null); }
    @Test void oldWorldsInheritExistingConfigAndNewWorldOptionsSurviveReload() {
        var state = QuestState.fromNbt(new CompoundTag());
        assertNull(state.worldQuestSettings());
        var options = new WorldQuestSettings(true, VillageQuestServerConfig.AdventureProfile.RELAXED,
                false, VillageQuestServerConfig.CaravanVisualMode.MAP_ONLY);
        UUID player = UUID.randomUUID();
        state.getPlayerData(player).setMilestoneFlag("kept", true);
        state.getPlayerData(player).setMilestoneFlag(QuestSetupService.COMPLETED, true);
        state.setWorldQuestSettings(options);
        var restored = QuestState.fromNbt(QuestState.toNbt(state));
        assertEquals(options, restored.worldQuestSettings());
        assertTrue(restored.getPlayerData(player).hasMilestoneFlag("kept"));
        assertTrue(restored.getPlayerData(player).hasMilestoneFlag(QuestSetupService.COMPLETED));
        VillageQuestServerConfig.bindWorld(restored.worldQuestSettings());
        assertTrue(VillageQuestServerConfig.get().minecraftQuestCalendar());
        assertFalse(VillageQuestServerConfig.get().storyCooldownEnabled());
        assertEquals(options.profile(), VillageQuestServerConfig.get().adventureProfile());
        assertEquals(options.caravans(), VillageQuestServerConfig.get().caravanVisualMode());
        VillageQuestServerConfig.bindWorld(null);
        assertEquals(WorldQuestSettings.recommended(), VillageQuestServerConfig.get().worldSettings());
    }
    @Test void unknownWorldOptionsArePreservedAndCannotBeOverwritten() {
        var root = new CompoundTag(); var future = new CompoundTag();
        future.putInt("schema", 99); future.putString("new_option", "preserve"); root.put("worldQuestSettings26", future);
        var state = QuestState.fromNbt(root);
        assertTrue(state.unknownWorldQuestSettings());
        assertThrows(IllegalStateException.class, () -> state.setWorldQuestSettings(WorldQuestSettings.recommended()));
        assertEquals(future, QuestState.toNbt(state).getCompoundOrEmpty("worldQuestSettings26"));
    }
}
