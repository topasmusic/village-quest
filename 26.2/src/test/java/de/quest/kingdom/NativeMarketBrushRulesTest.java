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
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native definitions only; no animal, block, world or interaction execution. */
final class NativeMarketBrushRulesTest {
    private static HolderLookup.Provider base;
    private static final Identifier BRUSH=Identifier.withDefaultNamespace("brush");
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
    }
    @Test void repeatedNativeBrushingCannotCreatePositiveScuteCreditOrConsumeTheRetainedTool() {
        var reading=read(base); assertTrue(reading.supported());
        assertTrue(reading.rules().stream().allMatch(r -> r.outputs().contains(g("brush",1))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("brush",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete()); assertEquals(12,bounds.maximumPayout(BRUSH,1));
        assertEquals(0,bounds.maximumPayout(id("armadillo_scute"),4096));
    }
    @Test void changedCurrentInteractionOutputsAreZeroCreditIncludingReturnedBrushes() {
        for(String output:List.of("diamond","brush")) {
            var reading=read(lookup(table("{\"type\":\"minecraft:entity_interact\",\"pools\":[{\"rolls\":2,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:"+output+"\"}]}]}")));
            assertTrue(reading.supported());
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("brush",1)))),List.of(),reading.rules());
            assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id(output),4096));
        }
    }
    @Test void missingForeignContextAndDynamicLootRemainHeldButExplicitEmptyIsKnown() {
        assertFalse(read(lookup(null)).supported());
        assertFalse(read(lookup(table("{\"type\":\"minecraft:block_interact\",\"pools\":[]}"))).supported());
        assertFalse(read(lookup(table("{\"type\":\"minecraft:entity_interact\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:chests/simple_dungeon\"}]}]}"))).supported());
        assertTrue(read(lookup(LootTable.EMPTY)).supported());
    }
    private static NativeMarketRecipeRules.Reading read(HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(BRUSH,DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    private static LootTable table(String json) {
        return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),NativeMarketTestData.nativeLoot(JsonParser.parseString(json))).getOrThrow();
    }
    private static HolderLookup.Provider lookup(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        if(table!=null) registry.register(BuiltInLootTables.ARMADILLO_BRUSH,table,RegistrationInfo.BUILT_IN);
        registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
}
