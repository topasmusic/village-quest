package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class BrokenHeartstoneStructureTest {
    @Test
    void authoredRuinTemplateHasExpectedBoundsAndMilestone() throws Exception {
        String path = "data/village-quest/structure/broken_heartstone_ruin.nbt";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing authored Heartstone ruin template");
            CompoundTag template = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());

            ListTag size = template.getListOrEmpty("size");
            assertEquals(7, size.getIntOr(0, -1));
            assertEquals(3, size.getIntOr(1, -1));
            assertEquals(7, size.getIntOr(2, -1));
            assertEquals(147, template.getListOrEmpty("blocks").size());
            assertEquals(0, template.getListOrEmpty("entities").size());

            ListTag palette = template.getListOrEmpty("palette");
            boolean containsMilestone = false;
            for (int index = 0; index < palette.size(); index++) {
                CompoundTag entry = palette.getCompoundOrEmpty(index);
                if ("village-quest:guild_milestone".equals(entry.getStringOr("Name", ""))) {
                    containsMilestone = true;
                    break;
                }
            }
            assertTrue(containsMilestone, "Heartstone ruin must contain its quest milestone");
        }
    }
}
