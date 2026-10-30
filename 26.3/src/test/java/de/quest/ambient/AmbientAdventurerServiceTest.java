package de.quest.ambient;

import de.quest.config.VillageQuestServerConfig;
import de.quest.entity.AmbientAdventurerEntity;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class AmbientAdventurerServiceTest {
    @Test void lateTravelersReceiveNextTicksReadBudgetBeforeEarlierEntityTickOrder() {
        var world=mock(ServerLevel.class); when(world.getServer()).thenReturn(mock(MinecraftServer.class));
        when(world.getGameTime()).thenReturn(100L);
        assertTrue(AmbientAdventurerService.takeReads(world,64));
        var a=UUID.randomUUID(); var b=UUID.randomUUID(); var early=UUID.randomUUID();
        assertFalse(AmbientAdventurerService.takeTravelReads(world,a,11));
        assertFalse(AmbientAdventurerService.takeTravelReads(world,b,11));
        when(world.getGameTime()).thenReturn(101L);
        assertFalse(AmbientAdventurerService.takeTravelReads(world,early,11));
        assertTrue(AmbientAdventurerService.takeTravelReads(world,a,11));
        assertTrue(AmbientAdventurerService.takeTravelReads(world,b,11));
        assertTrue(AmbientAdventurerService.takeTravelReads(world,early,11));
    }
    @Test void cancelledTravelReleasesItsPlaceWithoutIncreasingTheGlobalReadBudget() {
        var world=mock(ServerLevel.class); when(world.getServer()).thenReturn(mock(MinecraftServer.class));
        when(world.getGameTime()).thenReturn(100L); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        assertTrue(AmbientAdventurerService.takeReads(world,64));
        assertFalse(AmbientAdventurerService.takeTravelReads(world,a,11));
        assertFalse(AmbientAdventurerService.takeTravelReads(world,b,11));
        AmbientAdventurerService.releaseTravelReads(world,a); when(world.getGameTime()).thenReturn(101L);
        assertTrue(AmbientAdventurerService.takeTravelReads(world,b,11));
        assertTrue(AmbientAdventurerService.takeReads(world,53)); assertFalse(AmbientAdventurerService.takeReads(world,1));
    }
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private AmbientAdventurerEntity npc(boolean hunter) {
        var npc=mock(AmbientAdventurerEntity.class); when(npc.getUUID()).thenReturn(UUID.randomUUID()); when(npc.isNightrunner()).thenReturn(hunter); return npc;
    }
    @Test void capsApplyToEveryNearbyPlayerInClusterAndRemainSharedAcrossDistantGroups() {
        var world=mock(ServerLevel.class); var r=new AmbientAdventurerService.Runtime(0);
        var a=mock(ServerPlayer.class); var b=mock(ServerPlayer.class);
        when(a.isAlive()).thenReturn(true); when(b.isAlive()).thenReturn(true);
        when(a.blockPosition()).thenReturn(new BlockPos(0,64,0)); when(b.blockPosition()).thenReturn(new BlockPos(100,64,0)); when(world.players()).thenReturn(List.of(a,b));
        var n1=npc(false);var n2=npc(false); when(n1.distanceToSqr(a)).thenReturn(10000D);when(n2.distanceToSqr(a)).thenReturn(10000D);
        when(n1.distanceToSqr(b)).thenReturn(1D); when(n2.distanceToSqr(b)).thenReturn(1D); r.live.put(n1.getUUID(),n1);r.live.put(n2.getUUID(),n2);
        assertFalse(AmbientAdventurerService.caps(world,r,new BlockPos(50,64,0),false));
        assertTrue(AmbientAdventurerService.caps(world,r,new BlockPos(-50,64,0),false));
        for(int i=0;i<6;i++) { var n=npc(false); r.live.put(n.getUUID(),n); }
        assertFalse(AmbientAdventurerService.caps(world,r,new BlockPos(1000,64,0),false));
    }
    @Test void twoNightrunnersAreGlobalEvenWhenNoPlayersShareTheEncounters() {
        var world=mock(ServerLevel.class); when(world.players()).thenReturn(List.of()); var r=new AmbientAdventurerService.Runtime(0);
        for(int i=0;i<2;i++) { var n=npc(true);r.live.put(n.getUUID(),n); }
        assertFalse(AmbientAdventurerService.caps(world,r,BlockPos.ZERO,true)); assertTrue(AmbientAdventurerService.caps(world,r,BlockPos.ZERO,false));
    }
    @Test void movingPlayerGroupsTogetherKeepsOnlyTwoClosestSharedEncountersWithoutResettingCooldowns() {
        var world=mock(ServerLevel.class);var r=new AmbientAdventurerService.Runtime(0);var player=mock(ServerPlayer.class);
        when(player.isAlive()).thenReturn(true);when(world.players()).thenReturn(List.of(player));
        for(int i=1;i<=4;i++) {var n=npc(false);when(n.distanceToSqr(player)).thenReturn((double)i);r.live.put(n.getUUID(),n);}
        r.arrivals.add(new AmbientAdventurerService.Arrival(BlockPos.ZERO,false,6000));
        AmbientAdventurerService.reconcile(world,r);assertEquals(2,r.live.size());assertEquals(1,r.arrivals.size());
        assertTrue(r.live.values().stream().allMatch(n -> n.distanceToSqr(player)<=2));
        AmbientAdventurerService.reconcile(world,r);assertEquals(2,r.live.size());
    }
    @Test void intangibleBodyHazardsAreRejectedEvenWhenNativeCollisionShapeIsEmpty() {
        for(var b:List.of(net.minecraft.world.level.block.Blocks.FIRE,net.minecraft.world.level.block.Blocks.SOUL_FIRE,
                net.minecraft.world.level.block.Blocks.WITHER_ROSE,net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH,
                net.minecraft.world.level.block.Blocks.POWDER_SNOW,net.minecraft.world.level.block.Blocks.NETHER_PORTAL))
            assertTrue(de.quest.caravan.AmbientRoadAccess.hazard(b.defaultBlockState()));
        assertFalse(de.quest.caravan.AmbientRoadAccess.hazard(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
    }
    @Test void reservationPrecedesNativeAddAndFailedAddOrExceptionRollsBackWithoutCooldown() {
        var world=mock(ServerLevel.class); when(world.players()).thenReturn(List.of());var r=new AmbientAdventurerService.Runtime(0); var n=npc(true);
        when(world.addFreshEntity(n)).thenAnswer(call -> { assertSame(n,r.live.get(n.getUUID())); return false; });
        assertFalse(AmbientAdventurerService.add(world,r,n,BlockPos.ZERO,true)); assertTrue(r.live.isEmpty()); assertTrue(r.arrivals.isEmpty()); verify(n).discard();
        var other=npc(true);when(world.addFreshEntity(other)).thenThrow(new IllegalStateException("test add failure"));
        assertThrows(IllegalStateException.class,() -> AmbientAdventurerService.add(world,r,other,BlockPos.ZERO,true)); assertTrue(r.live.isEmpty());assertTrue(r.arrivals.isEmpty());
    }
    @Test void arrivalCooldownStartsAtSuccessfulAddAndSurvivesEntityRemoval() {
        var world=mock(ServerLevel.class); when(world.players()).thenReturn(List.of());var r=new AmbientAdventurerService.Runtime(0);r.begin(100);var n=npc(true);
        when(world.addFreshEntity(n)).thenReturn(true);assertTrue(AmbientAdventurerService.add(world,r,n,BlockPos.ZERO,true));
        assertEquals(12100,r.arrivals.getFirst().until());r.live.remove(n.getUUID());assertEquals(1,r.arrivals.size());
    }
    @Test void ownReadsAreLimitedTo64AndNeverResetTwiceInOneTick() {
        var r=new AmbientAdventurerService.Runtime(0); r.begin(10); assertEquals(64,r.reads);r.reads=0;r.begin(10);assertEquals(0,r.reads);r.begin(11);assertEquals(64,r.reads);
    }
    @Test void configCannotRaiseSafetyCapsAndMayDisableAllEncounters() {
        assertEquals(8,new VillageQuestServerConfig.AmbientConfig(true,999,999).total());
        assertEquals(2,new VillageQuestServerConfig.AmbientConfig(true,999,999).nightrunners());
        assertEquals(0,new VillageQuestServerConfig.AmbientConfig(false,-1,2).nightrunners());
    }
}
