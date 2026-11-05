package de.quest.ambient;

import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Entry-point guards only. Never calls native search, navigation.tick or entity/world ticks. */
final class AmbientAdventurerNavigationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private AmbientAdventurerNavigation nav(Mob mob,ServerLevel world) {
        when(mob.blockPosition()).thenReturn(new BlockPos(-1,64,-1));
        when(mob.getAttributeValue(Attributes.FOLLOW_RANGE)).thenReturn(24D);
        when(mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE)).thenReturn(24D);
        when(world.getServer()).thenReturn(mock(net.minecraft.server.MinecraftServer.class));
        when(world.getGameTime()).thenReturn(100L); return new AmbientAdventurerNavigation(mob,world);
    }
    @Test void automaticPathEntryPointsAndInjectedPathCannotStartUnbudgetedTerrainSearch() {
        var mob=mock(Mob.class);var world=mock(ServerLevel.class); var nav=nav(mob,world);var target=new BlockPos(2,64,2);
        assertNull(nav.createPath(target,0));assertNull(nav.createPath(target,0,24));assertNull(nav.createPath(mob,0));
        assertNull(nav.createPath(Stream.of(target),0));assertNull(nav.createPath(Set.of(target),0));
        assertNull(nav.createPath(Set.of(target),8,false,0,24));
        assertNull(nav.createPath(2,64,2,0));assertFalse(nav.moveTo(mock(Path.class),1));nav.recomputePath();
        assertFalse(nav.canCutCorner(net.minecraft.world.level.pathfinder.PathType.WALKABLE));
        verify(world,never()).getBlockState(any());verify(world,never()).getChunkSource();
    }
    @Test void unloadedEdgeAndNonTickingColumnsRejectControllerRequestBeforeTerrainRead() {
        var mob=mock(Mob.class);var world=mock(ServerLevel.class); var nav=nav(mob,world);
        when(world.isPositionEntityTicking(any())).thenReturn(true);when(world.hasChunkAt(any())).thenReturn(true);
        when(world.hasChunkAt(new BlockPos(16,64,16))).thenReturn(false);
        assertFalse(nav.request(new BlockPos(3,64,3),1));
        when(world.hasChunkAt(any())).thenReturn(true);when(world.isPositionEntityTicking(new BlockPos(16,64,16))).thenReturn(false);
        assertFalse(nav.request(new BlockPos(3,64,3),1));verify(world,never()).getBlockState(any());
    }
    @Test void fullyLoadedRequestOnlyQueuesOneBoundedCorridorAndCannotBeReplacedOrExpanded() {
        var mob=mock(Mob.class);var world=mock(ServerLevel.class); var nav=nav(mob,world);
        when(world.isPositionEntityTicking(any())).thenReturn(true);when(world.hasChunkAt(any())).thenReturn(true);
        assertFalse(nav.request(new BlockPos(30,64,0),1));
        assertTrue(nav.request(new BlockPos(3,64,3),1));assertTrue(nav.busy());
        assertFalse(nav.request(new BlockPos(4,64,4),1));assertNull(nav.createPath(new BlockPos(3,64,3),0));
        nav.cancel();assertFalse(nav.busy());assertFalse(nav.request(new BlockPos(3,64,3),1));
        when(world.getGameTime()).thenReturn(140L);assertTrue(nav.request(new BlockPos(3,64,3),1));
        verify(world,never()).getBlockState(any());
    }
}
