package de.quest.reputation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReputationDamageAttributionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onlyAcceptedHealthAndAbsorptionLossCounts() {
        assertEquals(0, ReputationDamageAdapter.acceptedDamage(20, 4, 20, 4, true));
        assertEquals(0, ReputationDamageAdapter.acceptedDamage(20, 4, 18, 2, false));
        assertEquals(4, ReputationDamageAdapter.acceptedDamage(20, 4, 18, 2, true));
        assertEquals(0, ReputationDamageAdapter.acceptedDamage(Float.NaN, 0, 0, 0, true));
    }
    @Test void currentProjectilePetAndTntOwnersAreAttributedWithoutStaleAttackerFallback() {
        ServerPlayer first = mock(ServerPlayer.class), second = mock(ServerPlayer.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(first.getUUID()).thenReturn(firstId); when(second.getUUID()).thenReturn(secondId);
        DamageSource source = mock(DamageSource.class); Projectile projectile = mock(Projectile.class);
        when(source.getDirectEntity()).thenReturn(projectile); when(projectile.getOwner()).thenReturn(first);
        assertEquals(firstId, ReputationDamageAdapter.attacker(source));
        when(source.getEntity()).thenReturn(second); assertEquals(secondId, ReputationDamageAdapter.attacker(source));
        TamableAnimal pet = mock(TamableAnimal.class); when(pet.getOwner()).thenReturn(first);
        when(source.getEntity()).thenReturn(pet); assertEquals(firstId, ReputationDamageAdapter.attacker(source));
        PrimedTnt tnt = mock(PrimedTnt.class); when(tnt.getOwner()).thenReturn(second);
        when(source.getEntity()).thenReturn(tnt); assertEquals(secondId, ReputationDamageAdapter.attacker(source));
        when(source.getEntity()).thenReturn(null); when(source.getDirectEntity()).thenReturn(null);
        assertNull(ReputationDamageAdapter.attacker(source));
    }
    @Test void spatialIndexIsDimensionalVerticalAndDeterministicAfterReload() {
        var index = new ProtectedVillageIndex();
        VillageKey left = VillageKey.overworld(-20, 0), right = VillageKey.overworld(20, 0);
        index.upsert(right, 64); index.upsert(left, 64);
        index.upsert(new VillageKey("minecraft:the_nether", 0, 0), 64);
        assertEquals(left, index.resolve("minecraft:overworld", new BlockPos(0, 64, 0)).orElseThrow());
        assertTrue(index.resolve("minecraft:overworld", new BlockPos(-20, 97, 0)).isEmpty());
        assertTrue(index.resolve("minecraft:overworld", new BlockPos(85, 64, 0)).isEmpty());
        for (int i = 0; i < 10_000; i++) index.upsert(VillageKey.overworld(10_000 + i * 128, 10_000), 64);
        index = ProtectedVillageIndex.fromNbt(index.toNbt());
        assertEquals(left, index.resolve("minecraft:overworld", new BlockPos(0, 32, 0)).orElseThrow());
        assertTrue(index.lastCandidatesExamined() <= 2);
    }
}
