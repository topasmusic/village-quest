package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.*;

final class EncounterEntityLifecycleTest {
    final LivingEntity entity=mock(LivingEntity.class);
    final ServerLevel world=mock(ServerLevel.class);
    final UUID id=UUID.randomUUID();
    @BeforeEach void setup(){EncounterEntityLifecycle.reset();when(entity.getUUID()).thenReturn(id);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(entity.entityTags()).thenReturn(Set.of("vq_empty_caravan_attacker"));}
    @AfterEach void clear(){EncounterEntityLifecycle.reset();}
    @Test void unloadAndReloadAreNeverDeathOrReplacementPermission(){
        EncounterEntityLifecycle.expect(entity);when(entity.getRemovalReason()).thenReturn(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        EncounterEntityLifecycle.onUnload(entity,world);
        assertFalse(EncounterEntityLifecycle.defeated(id,null));assertFalse(EncounterEntityLifecycle.terminated(id));
        EncounterEntityLifecycle.onLoad(entity,world);verify(entity,never()).discard();
    }
    @Test void confirmedDeathRemainsKnownAfterCorpseLeavesLoadedWorld(){
        EncounterEntityLifecycle.expect(entity);EncounterEntityLifecycle.onDeath(entity);
        assertTrue(EncounterEntityLifecycle.defeated(id,null));EncounterEntityLifecycle.forget(id);
        assertFalse(EncounterEntityLifecycle.defeated(id,null));
    }
    @Test void discardAllowsRetryButDoesNotAwardCombatVictory(){
        EncounterEntityLifecycle.expect(entity);when(entity.getRemovalReason()).thenReturn(Entity.RemovalReason.DISCARDED);
        EncounterEntityLifecycle.onUnload(entity,world);
        assertTrue(EncounterEntityLifecycle.terminated(id));assertFalse(EncounterEntityLifecycle.defeated(id,null));
    }
    @Test void retiredOrPreRestartTaggedBodiesAreRemovedWhenTheyEventuallyLoad(){
        EncounterEntityLifecycle.expect(entity);EncounterEntityLifecycle.reset();EncounterEntityLifecycle.onLoad(entity,world);
        verify(entity).discard();
    }
    @Test void freshSpawnIsRegisteredBeforeSynchronousFabricLoadCallback(){
        when(world.noCollision(entity)).thenReturn(true);
        when(world.addFreshEntity(entity)).thenAnswer(inv->{EncounterEntityLifecycle.onLoad(entity,world);return true;});
        assertTrue(EncounterEntityLifecycle.spawn(world,entity));verify(entity,never()).discard();
    }
    @Test void failedSpawnDoesNotLeaveAnAcceptedOrphanIdentity(){
        when(world.noCollision(entity)).thenReturn(true);when(world.addFreshEntity(entity)).thenReturn(false);
        assertFalse(EncounterEntityLifecycle.spawn(world,entity));EncounterEntityLifecycle.onLoad(entity,world);verify(entity).discard();
    }
    @Test void managedEnemyCannotRemainUnresolvedInAnotherDimension(){
        EncounterEntityLifecycle.expect(entity);when(world.dimension()).thenReturn(net.minecraft.world.level.Level.NETHER);
        EncounterEntityLifecycle.onLoad(entity,world);verify(entity).discard();
    }
}
