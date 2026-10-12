package de.quest.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaqueHandPoseTest {
    @Test void everyPlaqueKeepsItsFlatFaceAtTheHandInsteadOfFloatingAway() throws Exception {
        var resources = getClass().getClassLoader();
        for (String plaque : new String[]{"apiary_charter", "forge_charter", "market_charter", "pasture_charter", "village_ledger"}) {
            String name = plaque + "_plaque";
            JsonObject item = read("assets/village-quest/items/" + name + ".json");
            assertEquals("village-quest:block/" + name, item.getAsJsonObject("model").get("model").getAsString());
            JsonObject block = read("assets/village-quest/models/block/" + name + ".json");
            String parent = block.get("parent").getAsString().replace("village-quest:", "assets/village-quest/models/") + ".json";
            JsonObject base = read(parent);
            assertTrue(base.has("display"), name + " needs an inherited hand pose");
            for (String hand : new String[]{"thirdperson_righthand", "thirdperson_lefthand"}) {
                JsonObject pose = base.getAsJsonObject("display").getAsJsonObject(hand);
                assertNotNull(pose, name + " / " + hand);
                // Native plaque faces are centred at z=0.6, while item transforms rotate/scale about z=8.
                // In the unrotated vertical pose the face must stay within one model unit of the grip.
                double distance = (0.6 - 8) * pose.getAsJsonArray("scale").get(2).getAsDouble()
                        + pose.getAsJsonArray("translation").get(2).getAsDouble();
                assertTrue(Math.abs(distance) < 1, name + " floats " + distance + " units from the grip");
            }
        }
    }
    private JsonObject read(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
