package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Isolated native codecs/block states/POI mocks only; no actual Level, Entity, sleep or tick runs. */
final class NativeInnBedsTest {
    private static final BlockPos FOOT=new BlockPos(0,64,0),HEAD=FOOT.east();
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void freeHomeAcquisitionTargetsOnlyTheExactHeadAndNeverQueriesPoiAcrossUnknownChunks() {
        ServerLevel world=mock(ServerLevel.class); PoiManager poi=mock(PoiManager.class); when(world.getPoiManager()).thenReturn(poi);
        assertFalse(NativeInnBeds.takeHome(world,HEAD)); verify(poi,never()).take(any(),any(),any(),anyInt());
        when(world.hasChunkAt(any())).thenReturn(true); when(world.isPositionEntityTicking(any())).thenReturn(true);
        Holder<PoiType> home=BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.HOME);
        when(poi.getDebugPoiInfo(HEAD)).thenReturn(new DebugPoiInfo(HEAD,home,1));
        when(poi.take(any(),any(),eq(HEAD),eq(0))).thenAnswer(invocation -> {
            java.util.function.BiPredicate<Holder<PoiType>,BlockPos> select=invocation.getArgument(1);
            assertTrue(select.test(home,HEAD)); assertFalse(select.test(home,HEAD.north())); return Optional.of(HEAD);
        });
        assertTrue(NativeInnBeds.takeHome(world,HEAD)); verify(world,never()).getBlockState(any());
        verify(poi,never()).ensureLoadedAndValid(any(),any(),anyInt());
    }
    @Test void aClaimedHomePoiIsNeverTakenAwayFromItsExistingOwner() {
        ServerLevel world=mock(ServerLevel.class); PoiManager poi=mock(PoiManager.class); when(world.getPoiManager()).thenReturn(poi);
        when(world.hasChunkAt(any())).thenReturn(true); when(world.isPositionEntityTicking(any())).thenReturn(true);
        Holder<PoiType> home=BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.HOME);
        when(poi.getDebugPoiInfo(HEAD)).thenReturn(new DebugPoiInfo(HEAD,home,0));
        assertFalse(NativeInnBeds.takeHome(world,HEAD)); verify(poi,never()).take(any(),any(),any(),anyInt());
    }
    @Test void nativeSleepRequiresACompleteDryMatchingBedPair() {
        var foot=Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.EAST);
        var head=foot.setValue(BedBlock.PART,BedPart.HEAD);
        assertTrue(NativeInnBeds.complete(foot,head));
        assertFalse(NativeInnBeds.complete(foot,Blocks.AIR.defaultBlockState()));
        assertFalse(NativeInnBeds.complete(foot,head.setValue(BedBlock.FACING,Direction.WEST)));
        assertFalse(NativeInnBeds.complete(foot,Blocks.BED.pick(DyeColor.BLUE).defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.EAST)));
    }
}
