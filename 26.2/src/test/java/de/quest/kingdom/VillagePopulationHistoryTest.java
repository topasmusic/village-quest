package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

final class VillagePopulationHistoryTest {
    private static final UUID TOWN=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    @Test void unknownEvidencePreservesLastSafeCountAndCannotEarnFiveOrTenResidents() {
        var state=state();
        assertTrue(state.observePopulation(TOWN,5,true,100));
        assertFalse(state.observePopulation(TOWN,20,false,300));
        assertFalse(state.observePopulation(TOWN,20,true,50),"Older reports cannot overwrite newer safe evidence");
        var known=state.population(TOWN).orElseThrow();
        assertEquals(5,known.adults()); assertEquals(100,known.tick()); assertTrue(known.reachedFive()); assertFalse(known.reachedTen());
        assertTrue(state.observePopulation(TOWN,2,true,400));
        assertTrue(state.population(TOWN).orElseThrow().reachedFive());
        assertTrue(state.observePopulation(TOWN,10,true,500));
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertEquals(10,restored.population(TOWN).orElseThrow().adults());
        assertTrue(restored.population(TOWN).orElseThrow().reachedTen());
    }
    @Test void missingOrInvalidTargetsAndUnboundedCountsCannotInventAHistory() {
        var state=state(); assertEquals(-1,state.population(TOWN).orElseThrow().adults());
        assertFalse(state.observePopulation(UUID.randomUUID(),12,true,100));
        assertFalse(state.observePopulation(TOWN,65,true,100));
        assertFalse(state.observePopulation(TOWN,-1,true,100));
        assertFalse(state.observePopulation(TOWN,10,true,-1));
        assertFalse(state.population(TOWN).orElseThrow().reachedFive());
    }
    @Test void futureOrMalformedPopulationPayloadsRemainOpaqueAndDoNotEraseVillageIdentity() {
        for(var payload:java.util.List.of(future(),StringTag.valueOf("future population data"))) {
            var saved=LivingKingdomsState.toNbt(state()); var villages=saved.getListOrEmpty("villages");
            villages.getCompoundOrEmpty(0).put("population",payload.copy());
            var state=LivingKingdomsState.fromNbt(saved);
            assertTrue(state.village(TOWN).isPresent()); assertTrue(state.population(TOWN).orElseThrow().readOnly());
            assertFalse(state.observePopulation(TOWN,10,true,100));
            assertEquals(payload,LivingKingdomsState.toNbt(state).getListOrEmpty("villages").getCompoundOrEmpty(0).get("population"));
        }
    }
    private static CompoundTag future() { var tag=new CompoundTag(); tag.putInt("schema",99); tag.putString("opaque","keep"); return tag; }
    private static LivingKingdomsState state() {
        var state=new LivingKingdomsState(); state.registerCustom(TOWN,ACTOR,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow(); return state;
    }
}
