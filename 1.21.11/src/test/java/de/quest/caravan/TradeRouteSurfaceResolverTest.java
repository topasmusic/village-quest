package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

final class TradeRouteSurfaceResolverTest {
    @Test
    void yAwareSearchNeverEscapesItsRecordedElevationBand() {
        assertEquals(List.of(35, 36, 34, 37, 33, 38, 32),
                TradeRouteSurfaceResolver.verticalCandidates(35, 3));
    }

    @Test
    void zeroToleranceChecksOnlyTheRecordedTunnelOrBridgeLevel() {
        assertEquals(List.of(-22), TradeRouteSurfaceResolver.verticalCandidates(-22, 0));
    }

    @Test
    void slabAndStairHeightSupportCaravanFootingButFenceDoesNot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(true, TradeRouteSurfaceResolver.supportsFooting(
                Shapes.box(0, 0, 0, 1, 0.5, 1)));
        assertEquals(true, TradeRouteSurfaceResolver.supportsFooting(
                Shapes.box(0, 0, 0, 1, 1, 1)));
        assertEquals(false, TradeRouteSurfaceResolver.supportsFooting(
                Shapes.box(0.4, 0, 0.4, 0.6, 1.5, 0.6)));
        assertEquals(false, TradeRouteSurfaceResolver.supportsFooting(Shapes.empty()));
        assertEquals(true, TradeRouteSurfaceResolver.supportsFooting(
                Blocks.STONE_STAIRS.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
        assertEquals(true, TradeRouteSurfaceResolver.supportsFooting(
                Blocks.STONE_SLAB.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
        assertEquals(false, TradeRouteSurfaceResolver.supportsFooting(
                Blocks.OAK_FENCE.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
        assertEquals(false, TradeRouteSurfaceResolver.supportsFooting(
                Blocks.END_ROD.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
    }
}
