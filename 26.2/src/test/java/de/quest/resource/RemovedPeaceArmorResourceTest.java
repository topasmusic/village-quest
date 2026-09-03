package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RemovedPeaceArmorResourceTest {
    @Test
    void obsoletePeaceArmorRecipesAndPrivateFlowerTagStayAbsent() {
        ClassLoader resources = RemovedPeaceArmorResourceTest.class.getClassLoader();
        for (String recipe : List.of("friedens_haube", "friedens_brustplatte",
                "friedens_beinschiene", "friedens_stiefel")) {
            assertNull(resources.getResource("data/village-quest/recipe/" + recipe + ".json"),
                    () -> "Obsolete Peace Armor recipe returned: " + recipe);
        }
        assertNull(resources.getResource("data/village-quest/tags/item/white_flowers.json"),
                "Obsolete Peace Armor-only flower tag returned");
    }
}
