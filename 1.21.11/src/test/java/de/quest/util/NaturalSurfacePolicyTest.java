package de.quest.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalSurfacePolicyTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsGenericSolidSupportWithoutVanillaAllowlist() {
        assertTrue(NaturalSurfacePolicy.isStructurallyEligible(Blocks.STONE.defaultBlockState()));
        assertTrue(NaturalSurfacePolicy.isStructurallyEligible(Blocks.CALCITE.defaultBlockState()));
    }

    @Test
    void rejectsLeavesFluidsAirAndHazards() {
        assertFalse(NaturalSurfacePolicy.isStructurallyEligible(Blocks.OAK_LEAVES.defaultBlockState()));
        assertFalse(NaturalSurfacePolicy.isStructurallyEligible(Blocks.WATER.defaultBlockState()));
        assertFalse(NaturalSurfacePolicy.isStructurallyEligible(Blocks.AIR.defaultBlockState()));
        assertFalse(NaturalSurfacePolicy.isStructurallyEligible(Blocks.MAGMA_BLOCK.defaultBlockState()));
    }
}
