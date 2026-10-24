package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Current registry/codec/solver tests; never creates an entity, world, or shearing interaction. */
final class NativeMarketShearingRulesTest {
    private static HolderLookup.Provider base;
    private static final Identifier SHEARS=id("shears");
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
    }
    @Test void allCurrentShearingVariantsKeepTheToolAndCannotCreatePositiveOutputCredit() {
        var reading=read(base); assertTrue(reading.supported());
        var outputs=reading.rules().stream().flatMap(r -> r.outputs().stream()).map(g -> g.item()).collect(Collectors.toSet());
        for(var color:DyeColor.values()) assertTrue(outputs.contains(id(color.getName()+"_wool")),color.toString());
        assertTrue(outputs.containsAll(Set.of(id("carved_pumpkin"),id("red_mushroom"),id("brown_mushroom"))));
        var bounds=solve(reading); assertTrue(bounds.complete()); assertEquals(12,bounds.maximumPayout(SHEARS,1));
        for(var output:outputs) if(!output.equals(SHEARS)) assertEquals(0,bounds.maximumPayout(output,4096),output.toString());
    }
    @Test void aChangedCurrentReferencedVariantIsReadRatherThanReplacedByHardcodedWool() {
        var key=BuiltInLootTables.SHEAR_DYED_SHEEP.pick(DyeColor.RED);
        var reading=read(lookup(Map.of(key,table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":64,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}]}")),Set.of()));
        assertTrue(reading.supported()); assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().contains(g("diamond",1))));
        assertEquals(0,solve(reading).maximumPayout(id("diamond"),4096));
    }
    @Test void missingRootsOrNestedVariantsCannotFallBackToAnEmptyTable() {
        for(var key:List.of(BuiltInLootTables.SHEAR_SHEEP,BuiltInLootTables.SHEAR_MOOSHROOM,
                BuiltInLootTables.SHEAR_SNOW_GOLEM,BuiltInLootTables.BOGGED_SHEAR,
                BuiltInLootTables.SHEAR_DYED_SHEEP.pick(DyeColor.RED),BuiltInLootTables.SHEAR_RED_MOOSHROOM))
            assertFalse(read(lookup(Map.of(),Set.of(key))).supported(),key.toString());
    }
    @Test void cyclicReferencesAndForeignChildContextsHoldTheWholeReading() {
        var cyclic=table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:shearing/sheep\"}]}]}");
        assertFalse(read(lookup(Map.of(BuiltInLootTables.SHEAR_SHEEP,cyclic),Set.of())).supported());
        assertFalse(read(lookup(Map.of(BuiltInLootTables.SHEAR_RED_MOOSHROOM,
                table("{\"type\":\"minecraft:entity_interact\",\"pools\":[]}")),Set.of())).supported());
    }
    @Test void parentModifiersRemainValidatedEvenWhenTheReferencedChildIsEmpty() {
        var root=table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:shearing/mooshroom/red\",\"modifier\":{\"type\":\"minecraft:furnace_smelt\"}}]}]}");
        assertFalse(read(lookup(Map.of(BuiltInLootTables.SHEAR_MOOSHROOM,root,
                BuiltInLootTables.SHEAR_RED_MOOSHROOM,LootTable.EMPTY),Set.of())).supported());
    }
    @Test void explicitEmptyIsKnownButAChangedShearsOutputStillCannotDuplicateTheToolCredit() {
        var outputs=table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:shears\"}]}]}");
        var reading=read(lookup(Map.of(BuiltInLootTables.SHEAR_SHEEP,LootTable.EMPTY,
                BuiltInLootTables.SHEAR_SNOW_GOLEM,outputs),Set.of()));
        assertTrue(reading.supported()); assertEquals(0,solve(reading).maximumPayout(SHEARS,4096));
    }
    @Test void inlineTablesAndExcessivelyDeepEntryTreesAreNotSilentlyApproved() {
        var inline=table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":{\"type\":\"minecraft:shearing\",\"pools\":[]}}]}]}");
        assertFalse(read(lookup(Map.of(BuiltInLootTables.SHEAR_SHEEP,inline),Set.of())).supported());
        String entry="{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}";
        for(int i=0;i<17;i++) entry="{\"type\":\"minecraft:alternatives\",\"children\":["+entry+"]}";
        var deep=table("{\"type\":\"minecraft:shearing\",\"pools\":[{\"rolls\":1,\"entries\":["+entry+"]}]}");
        assertFalse(read(lookup(Map.of(BuiltInLootTables.SHEAR_SHEEP,deep),Set.of())).supported());
    }
    @Test void aForeignGrowingPlantCallbackCannotInheritTheNativeAgeOnlyProof() {
        var foreign=org.mockito.Mockito.mock(ForeignHead.class);
        var blocks=new MappedRegistry<net.minecraft.world.level.block.Block>(Registries.BLOCK,Lifecycle.stable());
        blocks.register(ResourceKey.create(Registries.BLOCK,id("unknown_shear_head")),foreign,RegistrationInfo.BUILT_IN);
        blocks.freeze();
        var changed=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
        assertFalse(read(changed).supported());
        org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).getMaxAgeState(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).isMaxAge(org.mockito.ArgumentMatchers.any());
    }
    private static NativeMarketRecipeRules.Reading read(HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(SHEARS,DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    private static MarketAcquisitionBounds.Result solve(NativeMarketRecipeRules.Reading reading) {
        return MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("shears",1)))),List.of(),reading.rules());
    }
    private static LootTable table(String json) {
        return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),NativeMarketTestData.nativeLoot(JsonParser.parseString(json))).getOrThrow();
    }
    private static HolderLookup.Provider lookup(Map<ResourceKey<LootTable>,LootTable> replacements,Set<ResourceKey<LootTable>> removed) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        replacements.forEach((key,value) -> registry.register(key,value,RegistrationInfo.BUILT_IN));
        registry.freeze();
        // Keep native nested holders owned by their original registry. This delegate changes
        // actual key reads while preserving codec ownership; copying tables to a new owner
        // would fail encoding before a missing/changed-reference regression reached its target.
        var current=new HolderLookup.RegistryLookup.Delegate<LootTable>() {
            public HolderLookup.RegistryLookup<LootTable> parent() { return base.lookupOrThrow(Registries.LOOT_TABLE); }
            @Override public java.util.Optional<net.minecraft.core.Holder.Reference<LootTable>> get(ResourceKey<LootTable> key) {
                if(removed.contains(key)) return java.util.Optional.empty();
                return replacements.containsKey(key)?registry.get(key):parent().get(key);
            }
        };
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(current)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
    private abstract static class ForeignHead extends net.minecraft.world.level.block.GrowingPlantHeadBlock {
        private ForeignHead() {
            super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of(),net.minecraft.core.Direction.UP,
                    net.minecraft.world.phys.shapes.Shapes.empty(),false,0);
        }
        @Override public net.minecraft.world.level.block.state.BlockState getMaxAgeState(net.minecraft.world.level.block.state.BlockState state) {
            throw new AssertionError("Foreign callback must never execute during a price proof");
        }
    }
}
