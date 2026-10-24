package de.quest.client.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuestTrackerConfigTest {
    @TempDir Path directory;
    @Test void oldDefaultsUpgradeOnceAndCanThenBeCustomized() throws Exception {
        Path path=directory.resolve("client.properties");
        Files.writeString(path,"quest_tracker_scale=1.0\nquest_tracker_background_opacity=0.55\nminimap_scale=1.25\n");
        var config=VillageQuestClientConfig.load(path);
        assertEquals(.85f,config.questTrackerScale());assertEquals(.5f,config.questTrackerBackgroundOpacity());
        assertEquals(1.25f,config.minimapScale());
        Files.writeString(path,Files.readString(path).replace("quest_tracker_scale=0.85","quest_tracker_scale=1.0"));
        assertEquals(1f,VillageQuestClientConfig.load(path).questTrackerScale(),"An explicit post-upgrade customization must survive");
    }
    @Test void customAppearanceAndUnrelatedSettingsSurviveSideChangesAndReload() throws Exception {
        Path path=directory.resolve("village-quest/client.properties");Files.createDirectories(path.getParent());
        Files.writeString(path,"quest_tracker_scale=1.25\nquest_tracker_background_opacity=0.3\nquest_tracker_position=BOTTOM_LEFT\nspeech_bubbles_enabled=false\nprivate_extra=keep\n");
        var config=VillageQuestClientConfig.load(path);
        try(var loader=mockStatic(FabricLoader.class)) {
            var api=mock(FabricLoader.class);loader.when(FabricLoader::getInstance).thenReturn(api);when(api.getConfigDir()).thenReturn(directory);
            assertTrue(config.setQuestTrackerSide(true));
            var reload=VillageQuestClientConfig.load(path);
            assertEquals(VillageQuestClientConfig.HudPosition.BOTTOM_RIGHT,reload.questTrackerPosition());
            assertEquals(1.25f,reload.questTrackerScale());assertEquals(.3f,reload.questTrackerBackgroundOpacity());
            assertFalse(reload.speechBubblesEnabled());assertTrue(Files.readString(path).contains("private_extra=keep"));
            assertTrue(reload.setQuestTrackerSide(false));
            assertEquals(VillageQuestClientConfig.HudPosition.BOTTOM_LEFT,VillageQuestClientConfig.load(path).questTrackerPosition());
        }
    }
    @Test void failedSaveLeavesTheActivePositionUnchanged() throws Exception {
        Path path=directory.resolve("client.properties");Files.writeString(path,"quest_tracker_position=TOP_LEFT\n");
        var config=VillageQuestClientConfig.load(path);
        Path blocked=directory.resolve("blocked");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"data");
        assertThrows(java.io.IOException.class,()->config.saveQuestTrackerPosition(blocked,VillageQuestClientConfig.HudPosition.TOP_RIGHT));
        assertEquals(VillageQuestClientConfig.HudPosition.TOP_LEFT,config.questTrackerPosition());
        assertEquals("data",Files.readString(blocked.resolve("keep")));
    }
}
