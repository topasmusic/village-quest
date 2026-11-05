package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native definitions and bounded policies only; no placement, loot context or world execution. */
final class NativeMarketSlabRulesTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void nativeSinglesAndMergedDoublesRetainTheSamePerItemCredit() throws Exception {
        for(String name:List.of("oak_slab","stone_slab","cut_copper_slab","exposed_cut_copper_slab",
                "weathered_cut_copper_slab","oxidized_cut_copper_slab","waxed_cut_copper_slab","waxed_exposed_cut_copper_slab",
                "waxed_weathered_cut_copper_slab","waxed_oxidized_cut_copper_slab")) {
            com.google.gson.JsonElement data;
            try(var stream=getClass().getResourceAsStream("/data/minecraft/loot_table/blocks/"+name+".json")) {
                assertNotNull(stream); data=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
            }
            var read=NativeMarketSlabRules.read(id(name),data); assertTrue(read.supported(),name);
            assertEquals(2,read.rules().size());
            assertTrue(read.rules().stream().anyMatch(r -> r.inputs().size()==1 && r.outputs().equals(List.of(g(name,1)))));
            assertTrue(read.rules().stream().anyMatch(r -> r.inputs().size()==2 && r.outputs().equals(List.of(g(name,2)))));
            var result=bounds(read); assertTrue(result.complete()); assertEquals(12,result.maximumPayout(id(name),1));
        }
    }
    @Test void changedTopWaterAndDoubleLootAreAllChargedToTheirActualInputs() {
        for(String name:List.of("oak_slab","oxidized_cut_copper_slab","waxed_weathered_cut_copper_slab"))
        for(String[] variant:List.of(new String[]{"type","top","3","4"},new String[]{"waterlogged","true","4","3"},
                new String[]{"type","double","6","4"})) {
            var read=NativeMarketSlabRules.read(id(name),JsonParser.parseString("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:diamond",
                "condition":{"type":"minecraft:match_block","blocks":"minecraft:%s","state":{"%s":"%s"}},
                "modifier":{"type":"minecraft:set_count","count":%s}}]}]}
                """.formatted(name,variant[0],variant[1],variant[2])));
            assertTrue(read.supported()); assertEquals(Long.parseLong(variant[3]),bounds(read).maximumPayout(id("diamond"),1));
        }
    }
    @Test void unknownEffectsOnNonDefaultStatesAndCopiedDoublePayloadRemainHeld() {
        var unknown=NativeMarketSlabRules.read(id("oak_slab"),JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:diamond",
            "condition":{"type":"minecraft:match_block","blocks":"minecraft:oak_slab","state":{"type":"top"}},
            "modifier":{"type":"foreign:future_yield"}}]}]}
            """));
        assertFalse(unknown.supported()); assertTrue(unknown.rules().isEmpty());
        var copied=NativeMarketSlabRules.read(id("oak_slab"),JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:oak_slab",
            "modifier":{"type":"minecraft:copy_state","block":"minecraft:oak_slab","properties":["type"]}}]}]}
            """));
        assertFalse(copied.supported()); assertTrue(copied.rules().isEmpty());
        assertFalse(NativeMarketSlabRules.read(id("stone"),JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[]}")).supported());
    }
    private static MarketAcquisitionBounds.Result bounds(NativeMarketRecipeRules.Reading read) {
        // Each fixture uses one purchased slab type; derive it from the actual consumed root.
        var item=read.rules().getFirst().inputs().getFirst().choices().getFirst().item();
        return MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,
                List.of(new MarketAcquisitionBounds.Goods(item,1)))),List.of(),read.rules());
    }
    private static Identifier id(String name) { return Identifier.withDefaultNamespace(name); }
    private static MarketAcquisitionBounds.Goods g(String name,int count) { return new MarketAcquisitionBounds.Goods(id(name),count); }
}
