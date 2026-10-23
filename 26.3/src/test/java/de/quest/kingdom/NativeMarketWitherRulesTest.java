package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Registry/codec arithmetic only; no skull, pattern, boss or world is instantiated/executed. */
final class NativeMarketWitherRulesTest {
    private static HolderLookup.Provider base,tagged;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
        var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        BuiltInRegistries.BLOCK.listElements().forEach(h -> blocks.register(h.key(),h.value(),RegistrationInfo.BUILT_IN)); blocks.freeze();
        blocks.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,Map.of(BlockTags.WITHER_SUMMON_BASE_BLOCKS,
                List.<Holder<Block>>of(blocks.get(id("soul_sand")).orElseThrow(),blocks.get(id("soul_soil")).orElseThrow())))).apply();
        tagged=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
    }
    @Test void bothNativeSkullPlacementsChargeTheHardcodedStarEvenWithEmptyCurrentLoot() {
        assertSame(Blocks.WITHER_SKELETON_WALL_SKULL,NativeMarketDecorativeWallRules.wall(Items.WITHER_SKELETON_SKULL).orElseThrow());
        for(var lookup:List.of(tagged,loot(LootTable.EMPTY))) {
            var reading=read(lookup); assertTrue(reading.supported());
            var spawn=reading.rules().stream().filter(r -> r.outputs().contains(g("nether_star",1))).findFirst().orElseThrow();
            assertEquals(List.of(new MarketAcquisitionBounds.InputChoice(List.of(g("wither_skeleton_skull",1)))),spawn.inputs());
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("wither_skeleton_skull",1)))),List.of(),reading.rules());
            assertTrue(bounds.complete()); assertTrue(bounds.maximumPayout(id("nether_star"),1)<=12);
        }
    }
    @Test void changedCurrentDeathLootAndTheHardcodedStarShareCredit() {
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,tagged),JsonParser.parseString("""
            {"type":"minecraft:entity","pools":[
              {"rolls":3,"entries":[{"type":"minecraft:item","name":"minecraft:diamond"}]},
              {"rolls":2,"entries":[{"type":"minecraft:item","name":"minecraft:nether_star"}]}]}
            """)).getOrThrow();
        var reading=read(loot(table)); assertTrue(reading.supported());
        var spawn=reading.rules().stream().filter(r -> r.outputs().contains(g("diamond",3))).findFirst().orElseThrow();
        assertTrue(spawn.outputs().contains(g("nether_star",3)),"Two from current loot plus the hardcoded extra");
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("wither_skeleton_skull",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete());
        assertTrue(bounds.maximumPayout(id("diamond"),3)+bounds.maximumPayout(id("nether_star"),3)<=12);
    }
    @Test void missingCurrentTagOrDeathTableCannotBecomeAnEmptyBossProof() {
        assertFalse(read(base).supported()); assertFalse(read(loot(null)).supported());
        var foreign=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,tagged),JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[]}")).getOrThrow();
        assertFalse(read(loot(foreign)).supported());
    }
    private static NativeMarketRecipeRules.Reading read(HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(id("wither_skeleton_skull"),DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    private static HolderLookup.Provider loot(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        var key=EntityTypes.WITHER.getDefaultLootTable().orElseThrow();
        for(var holder:base.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList()) if(!holder.key().equals(key))
            registry.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN);
        if(table!=null) registry.register(key,table,RegistrationInfo.BUILT_IN); registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(tagged.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Identifier id(String name) { return Identifier.withDefaultNamespace(name); }
    private static MarketAcquisitionBounds.Goods g(String name,int count) { return new MarketAcquisitionBounds.Goods(id(name),count); }
}
