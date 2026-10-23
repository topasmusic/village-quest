package de.quest.quest.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.config.VillageQuestServerConfig;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoryCooldownConfigTest {
    @TempDir Path temporary;
    private VillageQuestServerConfig config(String text) throws Exception {
        var path=temporary.resolve("server.properties"); Files.writeString(path,text);
        var load=VillageQuestServerConfig.class.getDeclaredMethod("load",Path.class); load.setAccessible(true);
        return (VillageQuestServerConfig)load.invoke(null,path);
    }
    @Test void defaultAndInvalidValuesKeepTheExistingTimerEnabled() throws Exception {
        assertTrue(config("").storyCooldownEnabled());
        assertTrue(config("story_cooldown_enabled=true").storyCooldownEnabled());
        assertTrue(config("story_cooldown_enabled=invalid").storyCooldownEnabled());
        assertFalse(config("story_cooldown_enabled=false").storyCooldownEnabled());
    }
    @Test void disablingIgnoresButDoesNotEraseASavedDeadlineAndReenablingRestoresIt() throws Exception {
        var enabled=config(""); var disabled=config("story_cooldown_enabled=false");
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var state=mock(QuestState.class);
        var actor=UUID.randomUUID(); var data=new PlayerQuestData(); long deadline=System.currentTimeMillis()+3600000;
        data.setStoryCooldownUntil(deadline); when(world.getServer()).thenReturn(server); when(state.getPlayerData(actor)).thenReturn(data);
        try(var configs=mockStatic(VillageQuestServerConfig.class); var states=mockStatic(QuestState.class)) {
            states.when(() -> QuestState.get(server)).thenReturn(state);
            configs.when(VillageQuestServerConfig::get).thenReturn(disabled);
            assertEquals(0,StoryQuestService.getStoryCooldownUntil(world,actor)); assertFalse(StoryQuestService.isStoryCooldownActive(world,actor));
            var arm=StoryQuestService.class.getDeclaredMethod("armStoryCooldownIfNeeded",ServerLevel.class,UUID.class); arm.setAccessible(true); arm.invoke(null,world,actor);
            assertEquals(deadline,data.getStoryCooldownUntil());
            configs.when(VillageQuestServerConfig::get).thenReturn(enabled);
            assertEquals(deadline,StoryQuestService.getStoryCooldownUntil(world,actor)); assertTrue(StoryQuestService.isStoryCooldownActive(world,actor));
        }
    }
}
