package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class JournalButtonTextureTest {
    @Test void generatedItemSpriteBelongsToTheItemAtlasAndKeepsTheInventoryImage() throws Exception {
        var loader=getClass().getClassLoader();
        try(var model=loader.getResourceAsStream("assets/village-quest/models/item/journal_inventory_button.json")) {
            assertNotNull(model);
            var json=JsonParser.parseReader(new java.io.InputStreamReader(model,StandardCharsets.UTF_8)).getAsJsonObject();
            String sprite=json.getAsJsonObject("textures").get("layer0").getAsString();
            // GUI textures are outside the default item atlas even when their PNG exists.
            assertTrue(sprite.startsWith("village-quest:item/"),sprite);
            String texture="assets/village-quest/textures/"+sprite.split(":",2)[1]+".png";
            try(var item=loader.getResourceAsStream(texture);var gui=loader.getResourceAsStream("assets/village-quest/textures/gui/journal_inventory_button.png")) {
                assertNotNull(item); assertNotNull(gui); assertArrayEquals(gui.readAllBytes(),item.readAllBytes());
            }
        }
    }
}
