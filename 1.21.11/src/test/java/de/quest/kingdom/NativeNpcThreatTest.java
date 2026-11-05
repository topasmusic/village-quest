package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Isolated predicate mocks only, never a real mob, world or AI simulation. */
final class NativeNpcThreatTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void aCalmSpiderIsNotAnAttackAndAnActuallyTargetingSpiderIs() {
        Spider spider=mock(Spider.class); when(spider.isAlive()).thenReturn(true);
        assertFalse(NativeNpcActivities.hostile(spider));
        when(spider.getTarget()).thenReturn(mock(LivingEntity.class));
        assertTrue(NativeNpcActivities.hostile(spider));
    }
    @Test void deadRemovedOrDisabledMonstersDoNotTriggerWarnings() {
        Monster enemy=mock(Monster.class); assertFalse(NativeNpcActivities.hostile(enemy));
        when(enemy.isAlive()).thenReturn(true); assertTrue(NativeNpcActivities.hostile(enemy));
        when(enemy.isNoAi()).thenReturn(true); assertFalse(NativeNpcActivities.hostile(enemy));
        when(enemy.isNoAi()).thenReturn(false); when(enemy.isRemoved()).thenReturn(true);
        assertFalse(NativeNpcActivities.hostile(enemy));
    }
}
