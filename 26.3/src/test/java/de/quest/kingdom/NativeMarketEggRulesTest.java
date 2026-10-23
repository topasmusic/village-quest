package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Registry/codec/credit proofs only; no animal, world, feeding or projectile execution. */
final class NativeMarketEggRulesTest {
    private static HolderLookup.Provider base;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
    }
    @Test void fourAdultLootOutcomesShareOneConsumedEggEvenWhenFeedingCostsAreIgnored() {
        var changed=lookup(table("""
            {"type":"minecraft:entity","pools":[
              {"rolls":2,"entries":[{"type":"minecraft:item","name":"minecraft:diamond"}]},
              {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:emerald"}]}]}
            """));
        for(String name:List.of("egg","brown_egg","blue_egg")) {
            var reading=read(name,changed); assertTrue(reading.supported(),name);
            assertEquals(1,reading.rules().size());
            assertEquals(List.of(new MarketAcquisitionBounds.InputChoice(List.of(g(name,1)))),reading.rules().getFirst().inputs());
            assertEquals(List.of(g("diamond",8),g("emerald",4)),reading.rules().getFirst().outputs());
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g(name,1)))),List.of(),reading.rules());
            assertTrue(bounds.complete());
            assertTrue(bounds.maximumPayout(id("diamond"),8)+bounds.maximumPayout(id("emerald"),4)<=12);
        }
    }
    @Test void currentNativeDeathLootIncludesFourAdultOutcomesAndTheMaximumEncodedLootingLevel() {
        for(String name:List.of("egg","brown_egg","blue_egg")) {
            var reading=read(name,base); assertTrue(reading.supported(),name);
            assertEquals(1,reading.rules().size());
            assertTrue(reading.rules().getFirst().outputs().contains(g("feather",1028)),"Four times (two plus255)");
            assertTrue(reading.rules().getFirst().outputs().contains(g("chicken",1024)),"Raw intermediate requires the full current smelting graph");
        }
    }
    @Test void missingForeignContextAndReferencedLootCannotMasqueradeAsEmptyBabyLoot() {
        assertFalse(read("egg",lookup(null)).supported());
        assertFalse(read("egg",lookup(table("{\"type\":\"minecraft:entity_interact\",\"pools\":[]}"))).supported());
        assertFalse(read("egg",lookup(table("{\"type\":\"minecraft:entity\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:chests/simple_dungeon\"}]}]}"))).supported());
        assertTrue(read("egg",lookup(LootTable.EMPTY)).supported());
        assertTrue(read("egg",lookup(LootTable.EMPTY)).rules().isEmpty());
    }
    @Test void changedEggReturnsAndExcessiveHatchOutputsRemainConservative() {
        var returned=read("egg",lookup(table("{\"type\":\"minecraft:entity\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:egg\"}]}]}")));
        assertTrue(returned.supported());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("egg",1)))),List.of(),returned.rules());
        assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id("egg"),4096));
        assertFalse(read("egg",lookup(table("{\"type\":\"minecraft:entity\",\"pools\":[{\"rolls\":1025,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}]}"))).supported());
    }
    @Test void smeltingCountResetAndLaterCountFunctionsStayConservativeInTheDeathRecipeGraph() {
        var changed=table("""
            {"type":"minecraft:entity","pools":[{"rolls":1,"entries":[
              {"type":"minecraft:item","name":"minecraft:chicken","modifier":[
                {"type":"minecraft:set_count","count":7},
                {"type":"minecraft:furnace_smelt","use_input_count":false},
                {"type":"minecraft:set_count","count":5}]}]}]}
            """);
        var reading=read("egg",lookup(changed)); assertTrue(reading.supported());
        assertEquals(List.of(g("chicken",20)),reading.rules().getFirst().outputs(),
                "All four raw intermediates keep the later count upper bound; current recipes still supply output identity/multiplicity");
    }
    private static NativeMarketRecipeRules.Reading read(String name,HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(id(name),DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,16).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    private static LootTable table(String json) {
        return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),JsonParser.parseString(json)).getOrThrow();
    }
    private static HolderLookup.Provider lookup(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        if(table!=null) registry.register(EntityTypes.CHICKEN.getDefaultLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN);
        registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
}
