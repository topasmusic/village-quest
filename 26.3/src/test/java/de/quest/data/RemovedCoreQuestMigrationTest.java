package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.RelicQuestStage;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

final class RemovedCoreQuestMigrationTest {
    @Test void oldTestFlagsSurviveReloadWithoutMaskingNormalSpecialQuests() {
        UUID id = UUID.randomUUID(); var state = QuestState.fromNbt(new CompoundTag());
        state.getPlayerData(id).setMilestoneFlag("admin_core_test.active", true);
        state.getPlayerData(id).setMilestoneFlag("admin_core_test.complete", true);
        var restored = QuestState.fromNbt(QuestState.toNbt(state));
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class); when(world.getServer()).thenReturn(server);
        try (var states = mockStatic(QuestState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(restored);
            assertEquals(0, SpecialQuestService.activeCount(world, id));
            assertNull(SpecialQuestService.openStatus(world, id));
            restored.getPlayerData(id).setShepherdFluteQuestStage(RelicQuestStage.ACTIVE);
            assertEquals(1, SpecialQuestService.activeCount(world, id));
            assertNotNull(SpecialQuestService.openStatus(world, id));
            assertTrue(restored.getPlayerData(id).hasMilestoneFlag("admin_core_test.active"));
            assertTrue(restored.getPlayerData(id).hasMilestoneFlag("admin_core_test.complete"));
        }
    }
}
