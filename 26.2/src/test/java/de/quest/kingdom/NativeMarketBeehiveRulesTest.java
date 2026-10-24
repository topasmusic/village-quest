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
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual native loot codec/registry fixtures; no hive, bee, server, world or harvest execution. */
final class NativeMarketBeehiveRulesTest {
    private static HolderLookup.Provider base;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
    }
    @Test void boundedProductionLootDoesNotTurnAnEmptyPurchasedHiveIntoAnImmediateItemSource() {
        var reading=NativeMarketBeehiveRules.read(id("beehive"),lookup(table("""
            {"type":"minecraft:block_interact","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:diamond","modifier":{"type":"minecraft:set_count","count":7}}]}]}
            """)));
        assertTrue(reading.supported());
        assertTrue(reading.rules().isEmpty());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("beehive",1))),
                new MarketAcquisitionBounds.Acquisition(3,List.of(g("glass_bottle",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id("diamond"),4096));
        assertTrue(bounds.maximumPayout(id("honey_bottle"),1)<=3);
    }
    @Test void absentWrongContextAndUnboundedHarvestHoldButExplicitEmptyIsKnown() {
        assertFalse(NativeMarketBeehiveRules.read(id("beehive"),lookup(null)).supported());
        assertFalse(NativeMarketBeehiveRules.read(id("beehive"),lookup(table("""
            {"type":"minecraft:block","pools":[]}
            """))).supported());
        assertFalse(NativeMarketBeehiveRules.read(id("beehive"),lookup(table("""
            {"type":"minecraft:block_interact","pools":[{"rolls":1,"entries":[{"type":"minecraft:loot_table","value":"minecraft:chests/simple_dungeon"}]}]}
            """))).supported());
        assertTrue(NativeMarketBeehiveRules.read(id("bee_nest"),lookup(LootTable.EMPTY)).supported());
        assertFalse(NativeMarketBeehiveRules.read(id("barrel"),base).supported());
    }
    @Test void onlyPinnedHiveHoneyLevelsCanUseTheExistingPlacementStateProof() {
        assertTrue(NativeMarketBeehiveRules.safePlacement(id("beehive"),
                new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("honey_level","0"))));
        for(int level=1;level<=5;level++) assertFalse(NativeMarketBeehiveRules.safePlacement(id("beehive"),
                new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("honey_level",Integer.toString(level)))));
        assertFalse(NativeMarketBeehiveRules.safePlacement(id("beehive"),new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("honey_level","6"))));
        assertFalse(NativeMarketBeehiveRules.safePlacement(id("beehive"),new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("honey_level","0","part","head"))));
        assertFalse(NativeMarketBeehiveRules.safePlacement(id("black_bed"),new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("part","head"))));
    }
    private static LootTable table(String json) {
        return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),NativeMarketTestData.nativeLoot(JsonParser.parseString(json))).getOrThrow();
    }
    private static HolderLookup.Provider lookup(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        if(table!=null) registry.register(BuiltInLootTables.HARVEST_BEEHIVE,table,RegistrationInfo.BUILT_IN);
        registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
}
