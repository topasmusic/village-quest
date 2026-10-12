package de.quest.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.content.story.ShrinesBetweenRoadsStoryArc;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;

final class SurveyRequirementLocalizationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void offerProgressLoreAndCompletionUseTheSameSurveyRequirement() throws Exception {
        var chapter = new ShrinesBetweenRoadsStoryArc().chapter(0);
        var world = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        UUID id = UUID.randomUUID();
        when(player.getUUID()).thenReturn(id);
        // Read the real objective argument. The test does not repeat the target number.
        try (var quests = mockStatic(StoryQuestService.class)) {
            var progress = (TranslatableContents) chapter.progressLines(world,id).getFirst().getContents();
            int target = (int) progress.getArgs()[1];
            assertArrayEquals(new Object[]{target}, ((TranslatableContents) chapter.offerParagraph1().getContents()).getArgs());
            var method = ModItems.class.getDeclaredMethod("cartographersLensLore"); method.setAccessible(true);
            var lore = (net.minecraft.world.item.component.ItemLore) method.invoke(null);
            var line = (TranslatableContents) lore.lines().get(2).getContents();
            assertArrayEquals(new Object[]{target},line.getArgs());
            quests.when(() -> StoryQuestService.getQuestInt(world,id,StoryQuestKeys.SHRINES_VILLAGES_INSPECTED)).thenReturn(target-1);
            assertFalse(chapter.isComplete(world,player));
            quests.when(() -> StoryQuestService.getQuestInt(world,id,StoryQuestKeys.SHRINES_VILLAGES_INSPECTED)).thenReturn(target);
            assertTrue(chapter.isComplete(world,player));
            for (String locale: List.of("en_us","de_de","es_es")) {
                try (var stream=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
                    assertNotNull(stream);
                    var catalog=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String key:List.of(line.getKey(), ((TranslatableContents)chapter.offerParagraph1().getContents()).getKey())) {
                        String template = catalog.get(key).getAsString();
                        assertTrue(template.contains("%s"),locale+" must use the server's survey target: "+key);
                        assertFalse(String.format(template,target).contains("%s"));
                    }
                }
            }
        }
    }
}
