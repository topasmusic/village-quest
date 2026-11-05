package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;

import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomVillageZonesTest {
    private static final UUID OWNER = UUID.fromString("ec03ece2-cae7-4eac-9604-7e9affd22828");
    private static final UUID TOWN = UUID.fromString("00352efb-12c9-4d9f-bdab-27d923982497");
    private static final BlockPos COUNCIL = new BlockPos(100, -20, 100);

    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void registrationMetadataAloneDoesNotAssertAValidatedVillageArea() {
        var state = state();
        assertTrue(state.resolveAt("minecraft:overworld", COUNCIL).isEmpty());
        assertTrue(state.zones(TOWN).isEmpty());
    }

    @Test void confirmedUndergroundZoneResolvesOnlyInsideItsDimensionAndHeight() {
        var state = state();
        var zone = zone(100, -20, 100);
        assertEquals(LivingKingdomsState.Mutation.APPLIED, state.confirmZone(OWNER, TOWN, 0, zone));
        assertEquals(TOWN, state.resolveAt("minecraft:overworld", COUNCIL).orElseThrow().id());
        assertTrue(state.resolveAt("minecraft:overworld", new BlockPos(100, 64, 100)).isEmpty());
        assertTrue(state.resolveAt("minecraft:the_nether", COUNCIL).isEmpty());
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(state.zones(TOWN), restored.zones(TOWN));
        assertEquals(TOWN, restored.resolveAt("minecraft:overworld", COUNCIL).orElseThrow().id());
    }

    @Test void overlappingNeighborsStayAmbiguousAndAreNeverMergedOrNearestAssigned() {
        var state = state();
        UUID neighbor = UUID.randomUUID();
        state.registerCustom(neighbor, OWNER, VillageKey.overworld(103, 100),
                new BlockPos(103, -20, 100), "Nearbrook").orElseThrow();
        state.confirmZone(OWNER, TOWN, 0, zone(100, -20, 100));
        state.confirmZone(OWNER, neighbor, 0, zone(103, -20, 100));
        assertTrue(state.isAmbiguousAt("minecraft:overworld", COUNCIL));
        assertTrue(state.resolveAt("minecraft:overworld", COUNCIL).isEmpty());
        assertEquals(2, state.villages().size());
        assertEquals(TOWN, state.byLegacy(VillageKey.overworld(100, 100)).orElseThrow().id());
    }

    @Test void foreignStaleHugeAndDisconnectedZoneEvidenceCannotExpandTheVillage() {
        var state = state();
        assertEquals(LivingKingdomsState.Mutation.UNAUTHORIZED,
                state.confirmZone(UUID.randomUUID(), TOWN, 0, zone(100, -20, 100)));
        assertEquals(LivingKingdomsState.Mutation.INVALID,
                state.confirmZone(OWNER, TOWN, 0, new LivingKingdomsState.Zone(0, 200, -100, 100, 0, 200)));
        assertEquals(LivingKingdomsState.Mutation.INVALID,
                state.confirmZone(OWNER, TOWN, 0, zone(200, -20, 100)));
        state.confirmZone(OWNER, TOWN, 0, zone(100, -20, 100));
        assertEquals(LivingKingdomsState.Mutation.STALE,
                state.confirmZone(OWNER, TOWN, 0, zone(116, -20, 100)));
        assertEquals(LivingKingdomsState.Mutation.APPLIED,
                state.confirmZone(OWNER, TOWN, 1, zone(116, -20, 100)));
        assertEquals(LivingKingdomsState.Mutation.UNCHANGED,
                state.confirmZone(OWNER, TOWN, 2, zone(116, -20, 100)));
        assertEquals(2, state.zones(TOWN).size());
    }

    @Test void malformedConfirmedZoneIsQuarantinedWithItsOriginalPayload() {
        var state = state();
        state.confirmZone(OWNER, TOWN, 0, zone(100, -20, 100));
        CompoundTag root = LivingKingdomsState.toNbt(state);
        CompoundTag entry = root.getListOrEmpty("villages").getCompoundOrEmpty(0);
        ListTag broken = new ListTag();
        CompoundTag badZone = new CompoundTag();
        badZone.putString("payload", "do not rewrite this malformed bound");
        broken.add(badZone);
        entry.put("zones", broken);
        var restored = LivingKingdomsState.fromNbt(root);
        assertTrue(restored.village(TOWN).isEmpty());
        assertEquals(root, LivingKingdomsState.toNbt(restored));
        assertTrue(restored.registerCustom(TOWN, OWNER, VillageKey.overworld(100, 100), COUNCIL, "No").isEmpty());
    }

    private static LivingKingdomsState state() {
        var state = new LivingKingdomsState();
        state.registerCustom(TOWN, OWNER, VillageKey.overworld(100, 100), COUNCIL, "Talheim").orElseThrow();
        return state;
    }

    @Test void councilMoveCannotTurnPreviouslyValidZonesIntoQuarantinedDataOnReload() {
        var state = state();
        state.confirmZone(OWNER, TOWN, 0, zone(100, -20, 100));
        assertEquals(LivingKingdomsState.Mutation.INVALID,
                state.moveCouncil(OWNER, TOWN, 1, new BlockPos(1000, -20, 100)));
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(COUNCIL, restored.village(TOWN).orElseThrow().arrival());
    }

    @Test void wrongZoneFieldTypeIsPreservedInsteadOfSilentlyErasingConfirmedArea() {
        var state = state();
        CompoundTag root = LivingKingdomsState.toNbt(state);
        root.getListOrEmpty("villages").getCompoundOrEmpty(0).putString("zones", "original malformed payload");
        var restored = LivingKingdomsState.fromNbt(root);
        assertTrue(restored.village(TOWN).isEmpty());
        assertEquals(root, LivingKingdomsState.toNbt(restored));
    }

    @Test void generatedAliasPersistsAndCustomVillageCannotClaimAToleratedAlias() {
        var state = state();
        assertFalse(state.recognizeLegacyAlias(TOWN, VillageKey.overworld(105, 100)));
        var generated = state.registerGenerated(VillageKey.overworld(500, 500),
                new BlockPos(500, 63, 500), "Lindenau").orElseThrow();
        assertTrue(state.recognizeLegacyAlias(generated.id(), VillageKey.overworld(505, 500)));
        var restored = LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(generated.id(), restored.byLegacy(VillageKey.overworld(505, 500)).orElseThrow().id());
        assertFalse(restored.recognizeLegacyAlias(generated.id(), VillageKey.overworld(509, 500)));
    }

    private static LivingKingdomsState.Zone zone(int x, int y, int z) {
        return new LivingKingdomsState.Zone(x - 8, x + 8, y - 4, y + 4, z - 8, z + 8);
    }
}
