package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native memory containers only; no entity, world, AI tick or sleep simulation. */
final class NativeNpcRestTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeRestRegistersRequiredMemoriesAndRetainsWakeCooldownAcrossReattachment() {
        Brain<?> brain=new Brain<>(); var rest=new NativeNpcRest(); rest.attach(brain);
        assertTrue(brain.checkMemory(MemoryModuleType.LAST_WOKEN,MemoryStatus.REGISTERED));
        assertTrue(brain.checkMemory(MemoryModuleType.LAST_SLEPT,MemoryStatus.REGISTERED));
        assertTrue(brain.checkMemory(MemoryModuleType.WALK_TARGET,MemoryStatus.REGISTERED));
        brain.setMemory(MemoryModuleType.LAST_WOKEN,123L);
        rest.attach(brain);
        assertEquals(123L,brain.getMemory(MemoryModuleType.LAST_WOKEN).orElseThrow());
    }
    @Test void onlyTheCertifiedHomeIsSuppliedToNativeRestAndDaytimeChangesActivity() {
        Brain<?> brain=new Brain<>(); var rest=new NativeNpcRest(); rest.attach(brain);
        assertFalse(brain.isActive(Activity.REST));
        BlockPos bed=new BlockPos(7,80,-2);
        rest.prepare(brain,Level.OVERWORLD,bed);
        assertEquals(GlobalPos.of(Level.OVERWORLD,bed),brain.getMemory(MemoryModuleType.HOME).orElseThrow());
        assertTrue(brain.isActive(Activity.REST));
        rest.prepareWake(brain);
        assertFalse(brain.isActive(Activity.REST));
        assertTrue(brain.isActive(Activity.IDLE));
    }
    @Test void nativePackedBrainRestoresSleepHistoryInsteadOfUsingTheEmptyMobDefault() {
        Brain<?> source=new Brain<>(); var rest=new NativeNpcRest(); rest.attach(source);
        source.setMemory(MemoryModuleType.LAST_WOKEN,350L);
        source.setMemory(MemoryModuleType.LAST_SLEPT,200L);
        source.setMemory(MemoryModuleType.HOME,GlobalPos.of(Level.OVERWORLD,new BlockPos(2,64,4)));
        var restored=NativeNpcRest.makeBrain(source.pack()); rest.attach(restored);
        assertEquals(350L,restored.getMemory(MemoryModuleType.LAST_WOKEN).orElseThrow());
        assertEquals(200L,restored.getMemory(MemoryModuleType.LAST_SLEPT).orElseThrow());
        assertEquals(source.getMemory(MemoryModuleType.HOME),restored.getMemory(MemoryModuleType.HOME));
        assertFalse(restored.isActive(Activity.REST));
    }
}
