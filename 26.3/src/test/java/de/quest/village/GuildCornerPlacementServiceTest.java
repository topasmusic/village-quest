package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GuildCornerPlacementServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void biomeStylesPreferSpecificPaletteFamilies() {
        assertEquals(GuildCornerLandmarkState.CornerStyle.CHERRY,
                GuildCornerPlacementService.classifyStyleName("minecraft:cherry_grove"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.SWAMP,
                GuildCornerPlacementService.classifyStyleName("minecraft:mangrove_swamp"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.DESERT,
                GuildCornerPlacementService.classifyStyleName("modded:painted_badlands"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.SNOWY,
                GuildCornerPlacementService.classifyStyleName("minecraft:snowy_plains"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.SNOWY,
                GuildCornerPlacementService.classifyStyleName("minecraft:snowy_taiga"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.PLAINS,
                GuildCornerPlacementService.classifyStyleName("minecraft:forest"));
        assertEquals(GuildCornerLandmarkState.CornerStyle.GENERIC,
                GuildCornerPlacementService.classifyStyleName("modded:highlands"));
    }

    @Test
    void desertUsesItsOwnPresetAndEveryOtherStyleUsesGeneric() {
        for (GuildCornerLandmarkState.CornerStyle style : GuildCornerLandmarkState.CornerStyle.values()) {
            GuildCornerLandmarkState.CornerStyle expected =
                    style == GuildCornerLandmarkState.CornerStyle.DESERT
                            ? GuildCornerLandmarkState.CornerStyle.DESERT
                            : GuildCornerLandmarkState.CornerStyle.GENERIC;
            assertEquals(expected, GuildCornerPlacementService.templateResourceStyle(style), style.name());
        }
    }

    @Test
    void lowestTemplateLayerSeatsAtTheLowestSurfaceBlockOnAnAllowedSlope() {
        assertEquals(63, GuildCornerPlacementService.surfaceReplacementOriginY(63, 0));
        assertEquals(60, GuildCornerPlacementService.surfaceReplacementOriginY(63, 3));
        assertEquals(-60, GuildCornerPlacementService.surfaceReplacementOriginY(-60, 0));
    }

    @Test
    void replaceableSnowAndPlantsAreNotTreatedAsSupportingGround() {
        assertTrue(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.SNOW.defaultBlockState()));
        assertTrue(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.SHORT_GRASS.defaultBlockState()));
        assertTrue(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.AIR.defaultBlockState()));
        assertFalse(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertFalse(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.SNOW_BLOCK.defaultBlockState()));
        assertFalse(GuildCornerPlacementService.isNonSupportingSurfaceCover(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void flatSiteBeatsAnEarlierOneBlockSlopeWithinTheSameSearchTier() {
        assertTrue(GuildCornerPlacementService.isBetterSurfaceFit(0, 1));
        assertFalse(GuildCornerPlacementService.isBetterSurfaceFit(1, 0));
        assertFalse(GuildCornerPlacementService.isBetterSurfaceFit(1, 1));
    }

    @Test
    void fullVerticalFootprintMustFitTheBuildRange() {
        assertTrue(GuildCornerPlacementService.footprintWithinBuildHeight(-64, 319, -64, 320));
        assertFalse(GuildCornerPlacementService.footprintWithinBuildHeight(-65, 10, -64, 320));
        assertFalse(GuildCornerPlacementService.footprintWithinBuildHeight(10, 320, -64, 320));
    }

    @Test
    void failedPlacementOrInvalidPostAlwaysRunsRollback() {
        AtomicBoolean rolledBack = new AtomicBoolean();
        assertFalse(GuildCornerPlacementService.finalizePlacement(false, true, () -> rolledBack.set(true)));
        assertTrue(rolledBack.get());
        rolledBack.set(false);
        assertFalse(GuildCornerPlacementService.finalizePlacement(true, false, () -> rolledBack.set(true)));
        assertTrue(rolledBack.get());
        rolledBack.set(false);
        assertTrue(GuildCornerPlacementService.finalizePlacement(true, true, () -> rolledBack.set(true)));
        assertFalse(rolledBack.get());
    }

    @Test
    void structureVoidMayPreserveAPathButNeverAHiddenBlockEntity() {
        assertTrue(GuildCornerPlacementService.surfaceCellAllowed(true, false, false, true));
        assertFalse(GuildCornerPlacementService.surfaceCellAllowed(false, false, false, true));
        assertFalse(GuildCornerPlacementService.surfaceCellAllowed(true, false, true, true));
        assertTrue(GuildCornerPlacementService.surfaceCellAllowed(false, true, false, false));
    }

    @Test
    void structureVoidPreservesDirtPathOnOneBlockSlopeByLocalColumn() {
        Set<BlockPos> authoredVoids = Set.of(new BlockPos(10, 65, 10));
        assertTrue(GuildCornerPlacementService.preservesLocalSurface(authoredVoids, 10, 64, 10));
        assertTrue(GuildCornerPlacementService.surfaceCellAllowed(
                GuildCornerPlacementService.preservesLocalSurface(authoredVoids, 10, 64, 10),
                false, false, true));
        assertFalse(GuildCornerPlacementService.preservesLocalSurface(authoredVoids, 10, 63, 10));
    }
}
