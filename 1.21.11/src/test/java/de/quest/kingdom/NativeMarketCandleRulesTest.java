package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native registry/codec inspection only; no placement, eating, loot execution, server or world. */
final class NativeMarketCandleRulesTest {
    private static HolderLookup.Provider base;
    @BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
    }
    private static NativeMarketRecipeRules.Reading read(HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(id("black_candle"),
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    @Test void candleCakeDropsShareTheConsumedCandleCredit() {
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:diamond",
             "modifier":{"type":"minecraft:set_count","count":7}}]}]}
            """))).getOrThrow();
        var reading=read(lookup(table));
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(rule -> rule.inputs().getFirst().choices().equals(List.of(g("black_candle",1)))
                && rule.outputs().equals(List.of(g("diamond",7)))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("black_candle",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete());
        long payout=bounds.maximumPayout(id("diamond"),7);
        assertTrue(payout>0 && payout<=12,"the existing fixed-point rounding may conservatively reduce the shared credit");
    }
    @Test void missingCakeLootHoldsWhileExplicitEmptyHasNoExtraGoods() {
        assertFalse(read(lookup(null)).supported());
        var empty=read(lookup(LootTable.EMPTY));
        assertTrue(empty.supported()); assertTrue(empty.rules().isEmpty());
    }
    @Test void unprovenCakeLootCannotGetAnEmptySuccessProof() {
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:loot_table","value":"minecraft:chests/simple_dungeon"}]}]}
            """))).getOrThrow();
        assertFalse(read(lookup(table)).supported());
    }
    private static HolderLookup.Provider lookup(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        if(table!=null) registry.register(BuiltInRegistries.BLOCK.getValue(id("black_candle_cake")).getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN);
        registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
