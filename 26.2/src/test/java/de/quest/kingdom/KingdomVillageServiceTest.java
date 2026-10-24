package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomVillageServiceTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void confirmedCustomMarkerKeepsLegacyCenterAfterCouncilMoveAndCarriesLocalBounds() {
        var state = new LivingKingdomsState(); UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        var key = VillageKey.overworld(100, 100);
        state.registerCustom(id, owner, key, new BlockPos(100, -20, 100), "Talheim");
        assertNull(KingdomVillageService.current(state, "minecraft:overworld", new BlockPos(100, -20, 100)));
        state.confirmZone(owner, id, 0, new LivingKingdomsState.Zone(92, 108, -24, -16, 92, 108));
        state.moveCouncil(owner, id, 1, new BlockPos(105, -20, 102));
        var marker = KingdomVillageService.current(state, "minecraft:overworld", new BlockPos(105, -20, 102));
        assertNotNull(marker); assertEquals(100, marker.centerX()); assertEquals(100, marker.centerZ());
        assertEquals(92, marker.minX()); assertEquals(108, marker.maxZ());
        assertNull(KingdomVillageService.current(state, "minecraft:overworld", new BlockPos(105, 64, 102)));
        assertEquals(new BlockPos(105, -20, 102), KingdomVillageService.arrival(state, key).orElseThrow());
    }

    @Test void allReadersSeeTheSameRenamedVillageAndFallbackNeverCreatesARecord() {
        var state = new LivingKingdomsState(); UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        var key = VillageKey.overworld(100, 100);
        var fallback = Component.literal("Historical route");
        assertEquals(fallback, KingdomVillageService.name(state, key, fallback));
        assertTrue(state.villages().isEmpty());
        state.registerCustom(id, owner, key, new BlockPos(100, -20, 100), "Talheim");
        assertEquals("Talheim", KingdomVillageService.name(state, key, fallback).getString());
        state.rename(owner, id, 0, "Lindenau");
        assertEquals("Lindenau", KingdomVillageService.name(state, key, Component.literal("Other player's label")).getString());
    }

    @Test void generatedDefaultPositionIsNotAnInventedPhysicalArrival() {
        var state = new LivingKingdomsState(); var key = VillageKey.overworld(100, 100);
        var village = state.registerGenerated(key, new BlockPos(100, 63, 100), "Talheim").orElseThrow();
        assertTrue(KingdomVillageService.arrival(state, key).isEmpty());
        assertTrue(state.recognizeLegacyAlias(village.id(), VillageKey.overworld(105, 100)));
        assertTrue(KingdomVillageService.sameVillage(state, "minecraft:overworld", 100, 100, 105, 100));
        assertFalse(KingdomVillageService.sameVillage(state, "minecraft:overworld", 100, 100, 106, 100));
    }
}
