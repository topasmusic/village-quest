package de.quest.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.data.PlayerQuestData;
import de.quest.guildtown.GuildTownService;
import de.quest.guildtown.GuildTownStory;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildNoticePostBlockTest {
    private static final BlockPos ROOT = new BlockPos(10, 64, 20);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void virtualFootprintIsExactlyThreeWideAndTwoHigh() {
        Set<BlockPos> north = Set.copyOf(GuildNoticePostBlock.occupiedPositions(ROOT, Direction.NORTH));
        assertEquals(6, north.size());
        assertEquals(Set.of(
                ROOT.west(), ROOT, ROOT.east(),
                ROOT.west().above(), ROOT.above(), ROOT.east().above()), north);

        Set<BlockPos> east = Set.copyOf(GuildNoticePostBlock.occupiedPositions(ROOT, Direction.EAST));
        assertEquals(Set.of(
                ROOT.north(), ROOT, ROOT.south(),
                ROOT.north().above(), ROOT.above(), ROOT.south().above()), east);
    }

    @Test
    void everyOverhangingCellRejectsBlockPlacement() {
        for (BlockPos occupied : GuildNoticePostBlock.occupiedPositions(ROOT, Direction.NORTH)) {
            assertTrue(GuildNoticePostBlock.occupies(ROOT, Direction.NORTH, occupied),
                    occupied.toShortString());
        }
        assertFalse(GuildNoticePostBlock.occupies(ROOT, Direction.NORTH, ROOT.above(2)));
        assertFalse(GuildNoticePostBlock.occupies(ROOT, Direction.NORTH, ROOT.east(2)));
        assertFalse(GuildNoticePostBlock.occupies(ROOT, Direction.NORTH, ROOT.north()));
    }

    @Test
    void targetingFindsTheRootAcrossEveryOverhangingBoardCellAndOrientation() {
        record Case(Direction facing, Vec3 start, Vec3 end, List<Vec3> targets) {}
        List<Case> cases = List.of(
                new Case(Direction.NORTH, new Vec3(10.5, 65.0, 24.0), new Vec3(10.5, 65.0, 18.0), List.of(
                        new Vec3(9.2, 64.5, 24.0), new Vec3(10.5, 64.5, 24.0), new Vec3(11.8, 64.5, 24.0),
                        new Vec3(9.2, 65.5, 24.0), new Vec3(10.5, 65.5, 24.0), new Vec3(11.8, 65.5, 24.0))),
                new Case(Direction.SOUTH, new Vec3(10.5, 65.0, 16.0), new Vec3(10.5, 65.0, 22.0), List.of(
                        new Vec3(9.2, 64.5, 16.0), new Vec3(10.5, 64.5, 16.0), new Vec3(11.8, 64.5, 16.0),
                        new Vec3(9.2, 65.5, 16.0), new Vec3(10.5, 65.5, 16.0), new Vec3(11.8, 65.5, 16.0))),
                new Case(Direction.EAST, new Vec3(6.0, 65.0, 20.5), new Vec3(14.0, 65.0, 20.5), List.of(
                        new Vec3(6.0, 64.5, 19.2), new Vec3(6.0, 64.5, 20.5), new Vec3(6.0, 64.5, 21.8),
                        new Vec3(6.0, 65.5, 19.2), new Vec3(6.0, 65.5, 20.5), new Vec3(6.0, 65.5, 21.8))),
                new Case(Direction.WEST, new Vec3(14.0, 65.0, 20.5), new Vec3(6.0, 65.0, 20.5), List.of(
                        new Vec3(14.0, 64.5, 19.2), new Vec3(14.0, 64.5, 20.5), new Vec3(14.0, 64.5, 21.8),
                        new Vec3(14.0, 65.5, 19.2), new Vec3(14.0, 65.5, 20.5), new Vec3(14.0, 65.5, 21.8))));

        for (Case testCase : cases) {
            for (Vec3 targetStart : testCase.targets()) {
                Vec3 direction = testCase.end().subtract(testCase.start()).normalize();
                Vec3 start = targetStart;
                Vec3 end = start.add(direction.scale(8.0));
                HitResult vanillaBackground = new BlockHitResult(
                        end, testCase.facing().getOpposite(), BlockPos.containing(end), false);
                HitResult corrected = GuildNoticePostTargeting.correctHit(
                        start, end, vanillaBackground,
                        List.of(new GuildNoticePostTargeting.Target(ROOT, testCase.facing())));

                assertTrue(corrected instanceof BlockHitResult,
                        testCase.facing() + " at " + targetStart);
                BlockHitResult blockHit = (BlockHitResult) corrected;
                assertEquals(ROOT, blockHit.getBlockPos(),
                        testCase.facing() + " at " + targetStart);
                assertTrue(blockHit.getLocation().x >= ROOT.getX()
                                && blockHit.getLocation().x <= ROOT.getX() + 1.0
                                && blockHit.getLocation().y >= ROOT.getY()
                                && blockHit.getLocation().y <= ROOT.getY() + 1.0
                                && blockHit.getLocation().z >= ROOT.getZ()
                                && blockHit.getLocation().z <= ROOT.getZ() + 1.0,
                        "server-safe root-local hit for " + testCase.facing() + " at " + targetStart);
            }
        }
    }

    @Test
    void targetingDoesNotReplaceACloserVanillaHit() {
        Vec3 start = new Vec3(9.2, 65.0, 24.0);
        Vec3 end = new Vec3(9.2, 65.0, 18.0);
        BlockHitResult foreground = new BlockHitResult(
                new Vec3(9.2, 65.0, 22.0), Direction.SOUTH, new BlockPos(9, 65, 21), false);

        HitResult corrected = GuildNoticePostTargeting.correctHit(
                start, end, foreground,
                List.of(new GuildNoticePostTargeting.Target(ROOT, Direction.NORTH)));

        assertEquals(foreground, corrected);
    }

    @Test
    void targetingCorrectsTheObservedNorthFacingCornerRay() {
        BlockPos observedRoot = new BlockPos(483, -59, 482);
        Vec3 start = new Vec3(483.367, -57.38, 484.766);
        Vec3 direction = Vec3.directionFromRotation(16.7F, 163.0F);
        Vec3 end = start.add(direction.scale(4.5));
        double backgroundDistance = (start.z - 482.0) / -direction.z;
        Vec3 backgroundLocation = start.add(direction.scale(backgroundDistance));
        BlockHitResult vanillaLeaves = new BlockHitResult(
                backgroundLocation, Direction.SOUTH, new BlockPos(482, -59, 481), false);

        HitResult corrected = GuildNoticePostTargeting.correctHit(
                start, end, vanillaLeaves,
                List.of(new GuildNoticePostTargeting.Target(observedRoot, Direction.NORTH)));

        assertTrue(corrected instanceof BlockHitResult);
        assertEquals(observedRoot, ((BlockHitResult) corrected).getBlockPos());
    }

    @Test
    void clientTargetingHooksTheAuthoritativeCameraRaycastReturn() throws Exception {
        Path mixin = Path.of("src", "client", "java", "de", "quest", "mixin", "LocalPlayerMixin.java");
        String source = Files.readString(mixin);
        String config = Files.readString(Path.of("src", "main", "resources", "village-quest.mixins.json"));

        assertTrue(source.contains("@Mixin(LocalPlayer.class)"));
        assertTrue(source.contains("method = \"raycastHitResult\", at = @At(\"RETURN\"), cancellable = true"));
        assertTrue(source.contains("cameraEntity.getEyePosition(partialTick)"));
        assertTrue(source.contains("cameraEntity.getViewVector(partialTick)"));
        assertTrue(source.contains("cir.setReturnValue(corrected)"));
        assertTrue(config.contains("\"LocalPlayerMixin\""));
        assertFalse(config.contains("\"MinecraftMixin\""));
    }

    @Test
    void frontTextureUsesTheRequestedThreeByTwoAspectRatio() throws Exception {
        Path texture = Path.of("src", "main", "resources", "assets", "village-quest",
                "textures", "block", "notice_board_front.png");
        try (var input = Files.newInputStream(texture)) {
            BufferedImage image = ImageIO.read(input);
            assertEquals(192, image.getWidth());
            assertEquals(128, image.getHeight());
            assertEquals(3 * image.getHeight(), 2 * image.getWidth());
        }
    }

    @Test
    void frontTextureIsFullyOpaque() throws Exception {
        Path texture = Path.of("src", "main", "resources", "assets", "village-quest",
                "textures", "block", "notice_board_front.png");
        try (var input = Files.newInputStream(texture)) {
            BufferedImage image = ImageIO.read(input);
            int translucentPixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0xFF) {
                        translucentPixels++;
                    }
                }
            }
            assertEquals(0, translucentPixels, "notice-board pixels with alpha below 255");
        }
    }

    @Test
    void structuralMaterialsUseVanillaMinecraftTextures() throws Exception {
        JsonObject textures = loadModel().getAsJsonObject("textures");
        assertEquals("minecraft:block/stripped_dark_oak_log", textures.get("log").getAsString());
        assertEquals("minecraft:block/stripped_dark_oak_log_top", textures.get("log_top").getAsString());
        assertEquals("minecraft:block/dark_oak_planks", textures.get("planks").getAsString());
        assertEquals("minecraft:block/stone_bricks", textures.get("base").getAsString());
        assertFalse(textures.has("metal"));
    }

    @Test
    void structuralFacesKeepVanillaOneTexelPerModelUnitDensity() throws Exception {
        JsonArray elements = loadModel().getAsJsonArray("elements");
        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            JsonObject element = elements.get(elementIndex).getAsJsonObject();
            float[] from = coordinates(element.getAsJsonArray("from"));
            float[] to = coordinates(element.getAsJsonArray("to"));
            for (Map.Entry<String, com.google.gson.JsonElement> faceEntry
                    : element.getAsJsonObject("faces").entrySet()) {
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                if ("#board".equals(face.get("texture").getAsString())) {
                    continue;
                }
                float[] uv = coordinates(face.getAsJsonArray("uv"));
                float expectedWidth;
                float expectedHeight;
                switch (faceEntry.getKey()) {
                    case "north", "south" -> {
                        expectedWidth = to[0] - from[0];
                        expectedHeight = to[1] - from[1];
                    }
                    case "east", "west" -> {
                        expectedWidth = to[2] - from[2];
                        expectedHeight = to[1] - from[1];
                    }
                    case "up", "down" -> {
                        expectedWidth = to[0] - from[0];
                        expectedHeight = to[2] - from[2];
                    }
                    default -> throw new AssertionError("Unexpected face " + faceEntry.getKey());
                }
                String label = "element " + elementIndex + " face " + faceEntry.getKey();
                assertEquals(expectedWidth, Math.abs(uv[2] - uv[0]), 0.001F, label + " width");
                assertEquals(expectedHeight, Math.abs(uv[3] - uv[1]), 0.001F, label + " height");
                assertTrue(expectedWidth <= 16.0F && expectedHeight <= 16.0F,
                        label + " exceeds one vanilla texture tile");
            }
        }
    }

    @Test
    void blockItemEntrypointRejectsOverlapBeforeVanillaPlacement() throws Exception {
        Path mixin = Path.of("src", "main", "java", "de", "quest", "mixin", "BlockItemMixin.java");
        String source = Files.readString(mixin);
        assertTrue(source.contains("method = \"place\", at = @At(\"HEAD\"), cancellable = true"));
        assertTrue(source.contains("GuildNoticePostBlock.blocksPlacementAt("));
        assertTrue(source.contains("cir.setReturnValue(InteractionResult.FAIL)"));
    }

    @Test
    void contactOnlyPublicEntrypointReachesStoryWithoutNetworkMutation() {
        PlayerQuestData data = new PlayerQuestData();
        VillageContactService.establish(data, 144, -208, VillageBondType.PASTURE);
        data.setTradeRouteFlag("guild_intro.welcome_completed", true);
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                new ShadowsTradeRoadEncounterService.VillageMarker(
                        "test:contact_village", 144, -208, 120, 168, -232, -184);
        Map<String, Integer> intsBefore = Map.copyOf(data.getTradeRouteIntState());
        Map<String, String> stringsBefore = Map.copyOf(data.getTradeRouteStringState());
        Set<String> flagsBefore = Set.copyOf(data.getTradeRouteFlags());

        GuildTownService.NoticePostStoryResolution resolution =
                GuildNoticePostBlock.useWithoutItem(
                        new GuildTownService.NoticePostStoryContext(data, marker, false, null));

        assertEquals(GuildTownStory.LONG_DRIVE, resolution.story());
        assertEquals(GuildTownService.NoticePostStoryState.AVAILABLE, resolution.state());
        assertFalse(resolution.connectedRoute());
        assertEquals(144, resolution.villageX());
        assertEquals(-208, resolution.villageZ());
        assertEquals(intsBefore, data.getTradeRouteIntState());
        assertEquals(stringsBefore, data.getTradeRouteStringState());
        assertEquals(flagsBefore, data.getTradeRouteFlags());
        assertEquals(0, data.getTradeRouteInt("route_count"));
        assertTrue(data.getTradeRouteIntState().keySet().stream()
                .noneMatch(key -> key.contains("freight") || key.contains("slot")
                        || key.startsWith("network_")));
    }

    private static JsonObject loadModel() throws Exception {
        Path model = Path.of("src", "main", "resources", "assets", "village-quest",
                "models", "block", "guild_notice_post.json");
        try (Reader reader = Files.newBufferedReader(model)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static float[] coordinates(JsonArray values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsFloat();
        }
        return result;
    }
}
