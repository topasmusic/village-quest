package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Validates every supplied preset while deliberately accepting a completely absent preset set. */
final class GuildCornerPresetContractTest {
    private static final Set<String> STYLES = Set.of(
            "generic", "plains", "desert", "savanna", "taiga", "snowy", "jungle", "swamp", "cherry");

    @Test
    void sharedReleasePresetSetContainsExactlyGenericAndDesert() throws Exception {
        Path root = Path.of("src", "main", "resources", "data", "village-quest", "structure", "guild_corner");
        assertTrue(Files.isDirectory(root), "Missing Guild Corner preset directory");
        try (var paths = Files.list(root)) {
            Set<String> names = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("generic.nbt", "desert.nbt"), names);
        }
    }

    @Test
    void everyPresentPresetFollowsTheAuthoredContract() throws Exception {
        Path root = Path.of("src", "main", "resources", "data", "village-quest", "structure", "guild_corner");
        if (!Files.isDirectory(root)) return; // Missing presets are a supported passive state.
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".nbt")).toList()) {
                String style = path.getFileName().toString().replaceFirst("\\.nbt$", "");
                assertTrue(STYLES.contains(style), "Unsupported Guild Corner style: " + style);
                try (InputStream input = Files.newInputStream(path)) {
                    validate(style, NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
                }
            }
        }
    }

    @Test
    void missingPresetExitsBeforeAnyLandmarkReservation() throws Exception {
        Path source = Path.of("src", "main", "java", "de", "quest", "village",
                "GuildCornerPlacementService.java");
        String placement = Files.readString(source);
        int automaticLoad = placement.indexOf("TemplateHandle template = loadTemplate");
        int automaticMissingExit = placement.indexOf("if (template == null) return;", automaticLoad);
        int firstReservation = placement.indexOf("state.reserve(", automaticLoad);
        int manualMissingExit = placement.indexOf(
                "if (template == null) return ManualPlacementResult.MISSING_OR_INVALID_PRESET;");
        int manualReservation = placement.indexOf("state.reserve(", manualMissingExit);

        assertTrue(automaticLoad >= 0 && automaticMissingExit > automaticLoad
                && firstReservation > automaticMissingExit);
        assertTrue(manualMissingExit >= 0 && manualReservation > manualMissingExit);
    }

    private static void validate(String style, CompoundTag template) {
        ListTag size = template.getListOrEmpty("size");
        assertEquals(3, size.size(), style + " must declare three dimensions");
        for (int axis = 0; axis < 3; axis++) {
            int value = size.getIntOr(axis, -1);
            assertTrue(value >= 1 && value <= 16, style + " axis outside 1..16: " + value);
        }
        assertEquals(0, template.getListOrEmpty("entities").size(), style + " must contain no entities");

        ListTag palette = template.getListOrEmpty("palette");
        Set<Integer> postStates = new HashSet<>();
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = palette.getCompoundOrEmpty(index);
            if (!"village-quest:guild_notice_post".equals(state.getStringOr("Name", ""))) continue;
            postStates.add(index);
            CompoundTag properties = state.getCompoundOrEmpty("Properties");
            assertEquals("north", properties.getStringOr("facing", ""),
                    style + " Notice Post must be authored facing north");
        }
        int posts = 0;
        String postPosition = null;
        Map<String, String> blocksByPosition = new HashMap<>();
        ListTag blocks = template.getListOrEmpty("blocks");
        boolean lowestLayerHasSolidBlock = false;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            assertTrue(!block.contains("nbt"), style + " must contain no block entities");
            ListTag position = block.getListOrEmpty("pos");
            if (position.getIntOr(1, -1) == 0) {
                int stateIndex = block.getIntOr("state", -1);
                String name = stateIndex >= 0 && stateIndex < palette.size()
                        ? palette.getCompoundOrEmpty(stateIndex).getStringOr("Name", "") : "";
                if (!"minecraft:air".equals(name) && !"minecraft:structure_void".equals(name)) {
                    lowestLayerHasSolidBlock = true;
                }
            }
            int stateIndex = block.getIntOr("state", -1);
            String blockName = stateIndex >= 0 && stateIndex < palette.size()
                    ? palette.getCompoundOrEmpty(stateIndex).getStringOr("Name", "") : "";
            String positionKey = positionKey(position.getIntOr(0, -1), position.getIntOr(1, -1),
                    position.getIntOr(2, -1));
            blocksByPosition.put(positionKey, blockName);
            if (postStates.contains(stateIndex)) {
                posts++;
                postPosition = positionKey;
            }
        }
        assertTrue(lowestLayerHasSolidBlock,
                style + " needs authored foundation blocks in local layer Y=0 for surface replacement");
        assertEquals(1, posts, style + " must contain exactly one Guild Notice Post");
        String[] root = postPosition.split(",");
        int postX = Integer.parseInt(root[0]);
        int postY = Integer.parseInt(root[1]);
        int postZ = Integer.parseInt(root[2]);
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = 0; yOffset <= 1; yOffset++) {
                if (xOffset == 0 && yOffset == 0) continue;
                assertEquals("minecraft:air", blocksByPosition.get(
                                positionKey(postX + xOffset, postY + yOffset, postZ)),
                        style + " must keep the Notice Post 3x2 overhang clear");
            }
        }
    }

    private static String positionKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
