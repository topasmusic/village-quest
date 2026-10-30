package de.quest.ambient;

import de.quest.entity.AmbientAdventurerEntity;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native collection/callback regression only; no server, world or game loop is started. */
final class AmbientAdventurerLifecycleTest {
    MinecraftServer server;
    ServerLevel world;
    @BeforeAll static void register() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        AmbientAdventurerService.register();
    }
    @SuppressWarnings("unchecked") private Map<MinecraftServer,AmbientAdventurerService.Runtime> runtimes() throws Exception {
        var field=AmbientAdventurerService.class.getDeclaredField("RUNTIMES");field.setAccessible(true);
        return (Map<MinecraftServer,AmbientAdventurerService.Runtime>)field.get(null);
    }
    @BeforeEach void setup() throws Exception {
        runtimes().clear();server=mock(MinecraftServer.class);world=mock(ServerLevel.class);
        when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);
        when(world.dimension()).thenReturn(Level.OVERWORLD);when(world.players()).thenReturn(List.of());
    }
    @AfterEach void clear() throws Exception { runtimes().clear(); }
    private AmbientAdventurerEntity npc() {
        var npc=mock(AmbientAdventurerEntity.class);when(npc.getUUID()).thenReturn(UUID.randomUUID());
        when(npc.isAlive()).thenReturn(true);return npc;
    }
    private void load(Entity npc) { ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(npc,world); }
    private void unload(Entity npc) { ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(npc,world); }
    private void tick() throws Exception {
        var tick=AmbientAdventurerService.class.getDeclaredMethod("tick",MinecraftServer.class);
        tick.setAccessible(true);tick.invoke(null,server);
    }
    @Test void chunkVisibilityIterationSurvivesUnloadAndRetiresNpcOnlyAfterIteration() throws Exception {
        var npc=npc();load(npc);
        var section=new EntitySection<Entity>(Entity.class,Visibility.TRACKED);
        section.add(npc);section.add(mock(Entity.class));section.add(mock(Entity.class));
        var removed=new AtomicBoolean();when(npc.isRemoved()).thenAnswer(c->removed.get());
        // Native discard -> level callback removes the entity from this same section.
        doAnswer(c->{removed.set(true);section.remove(npc);unload(npc);return null;}).when(npc).discard();
        assertDoesNotThrow(()->section.getEntities().forEach(this::unload));
        assertEquals(3,section.size());assertFalse(removed.get());
        assertFalse(runtimes().get(server).live.containsKey(npc.getUUID()));
        tick();assertTrue(removed.get());assertEquals(2,section.size());
        tick();verify(npc,times(1)).discard();
    }
    @Test void duplicateUnloadRetiresOnceAndPreservesArrivalCooldown() throws Exception {
        var npc=npc();load(npc);var runtime=runtimes().get(server);
        runtime.arrivals.add(new AmbientAdventurerService.Arrival(net.minecraft.core.BlockPos.ZERO,false,6000));
        unload(npc);unload(npc);verify(npc,never()).discard();
        tick();verify(npc,times(1)).discard();assertEquals(1,runtime.arrivals.size());
        tick();verify(npc,times(1)).discard();
    }
    @Test void alreadyRemovedNpcIsNotDiscardedAgain() throws Exception {
        var npc=npc();load(npc);when(npc.isRemoved()).thenReturn(true);unload(npc);tick();
        verify(npc,never()).discard();assertTrue(runtimes().get(server).live.isEmpty());
    }
    @Test void ordinaryEntitiesAreNotRetired() throws Exception {
        var entity=mock(Entity.class);unload(entity);tick();verify(entity,never()).discard();
    }
    @Test void serverStopClearsQueuedRetirementWithoutLeavingBodies() throws Exception {
        var npc=npc();load(npc);unload(npc);verify(npc,never()).discard();
        ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
        verify(npc,times(1)).discard();assertFalse(runtimes().containsKey(server));
    }
}
