package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pinned data and adversarial paired quantities only; never places, breaks or sleeps in a bed. */
final class NativeMarketPairedLootRulesTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void everyNativeBedReturnsOneItemAcrossItsFootAndHeadTogether() throws Exception {
        for(var color:net.minecraft.world.item.DyeColor.values()) {
            var item=id(color.getName()+"_bed");
            try(var stream=getClass().getResourceAsStream("/data/minecraft/loot_table/blocks/"+item.getPath()+".json")) {
                assertNotNull(stream);
                var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
                var reading=NativeMarketPairedLootRules.readBed(item,data);
                assertTrue(reading.supported(),item.toString());
                assertEquals(1,reading.rules().size());
                assertEquals(List.of(new MarketAcquisitionBounds.Goods(item,1)),reading.rules().getFirst().outputs());
                assertEquals(List.of(new MarketAcquisitionBounds.Goods(item,1)),reading.rules().getFirst().inputs().getFirst().choices());
            }
        }
    }
    @Test void strawBedLootAlsoChargesBothCellsToOnePurchasedRoot() throws Exception {
        var item=id("straw_bed");
        try(var stream=getClass().getResourceAsStream("/data/minecraft/loot_table/blocks/straw_bed.json")) {
            assertNotNull(stream);
            var reading=NativeMarketPairedLootRules.readBed(item,JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)));
            assertTrue(reading.supported());
            assertEquals(List.of(g("straw_bed",1)),reading.rules().getFirst().outputs());
        }
        var changed=NativeMarketPairedLootRules.readBed(item,JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:book","modifier":{"type":"minecraft:set_count","count":2}}]}]}
            """));
        assertTrue(changed.supported());
        assertEquals(List.of(g("book",4)),changed.rules().getFirst().outputs(),"Two physical cells share one purchased straw bed");
    }
    @Test void changedUnconditionalLootMustCountBothPhysicalCells() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:book","modifier":{"type":"minecraft:set_count","count":2}}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",4)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("black_bed",1)))),List.of(),reading.rules());
        assertEquals(12,bounds.maximumPayout(id("book"),4));
    }
    @Test void distinctFootAndHeadCoproductsShareTheSameConsumedItemBudget() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:group","children":[
             {"type":"minecraft:item","name":"minecraft:book","condition":{"type":"minecraft:match_block","blocks":"minecraft:black_bed","state":{"part":"foot"}},"modifier":{"type":"minecraft:set_count","count":2}},
             {"type":"minecraft:item","name":"minecraft:paper","condition":{"type":"minecraft:match_block","blocks":"minecraft:black_bed","state":{"part":"head"}},"modifier":{"type":"minecraft:set_count","count":3}}]}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",2),g("paper",3)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("black_bed",1)))),List.of(),reading.rules());
        assertTrue(bounds.maximumPayout(id("book"),2)+bounds.maximumPayout(id("paper"),3)<=12);
    }
    @Test void unknownRestrictionsRemainPossibleEvenInsideInvertedPredicates() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"condition":{"type":"minecraft:inverted","term":
             {"type":"minecraft:match_block","blocks":"minecraft:black_bed","state":{"part":"head"},"nbt":"{CustomName:'x'}"}},
             "entries":[{"type":"minecraft:item","name":"minecraft:book"}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",2)),reading.rules().getFirst().outputs());
    }
    @Test void pruningNeverConcealsUnsupportedLootOrUnboundedConditionTrees() {
        assertFalse(read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:dynamic","name":"minecraft:sherds",
             "condition":{"type":"minecraft:match_block","blocks":"minecraft:black_bed","state":{"part":"never"}}}]}]}
            """).supported());
        var data=JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:book"}]}]}
            """).getAsJsonObject();
        com.google.gson.JsonElement condition=JsonParser.parseString("{\"type\":\"minecraft:match_block\",\"state\":{\"part\":\"head\"}}");
        for(int index=0;index<64;index++) {
            var wrapper=new com.google.gson.JsonObject(); wrapper.addProperty("type","minecraft:inverted"); wrapper.add("term",condition); condition=wrapper;
        }
        data.getAsJsonArray("pools").get(0).getAsJsonObject().add("condition",condition);
        assertFalse(NativeMarketPairedLootRules.readBed(id("black_bed"),data).supported());
        assertFalse(NativeMarketPairedLootRules.readBed(id("barrel"),data).supported());
    }
    @Test void explicitNativeEmptyTableIsKnownWithoutAContextOrWorld() {
        var lookup=net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty());
        var reading=NativeMarketPairedLootRules.readBed(id("black_bed"),net.minecraft.world.level.storage.loot.LootTable.EMPTY,lookup);
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void nullableUnknownInvertedTermNeverProvesThatBothHalvesCannotDrop() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"condition":{"type":"minecraft:inverted","term":null},
             "entries":[{"type":"minecraft:item","name":"minecraft:book"}]}]}
            """);
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().equals(List.of(g("book",2)))),
                "An unknown/null condition must stay possible, not become TRUE inside an inversion");
    }
    private static NativeMarketRecipeRules.Reading read(String json) { return NativeMarketPairedLootRules.readBed(id("black_bed"),JsonParser.parseString(json)); }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
}
