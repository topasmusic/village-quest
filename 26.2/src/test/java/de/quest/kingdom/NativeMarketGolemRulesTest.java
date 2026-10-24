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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Codec/registry/price-graph checks only. No golem, world, spawn or shearing callback runs. */
final class NativeMarketGolemRulesTest {
    private static HolderLookup.Provider base,tagged;
    private static final List<String> COPPER=List.of("copper_block","exposed_copper","weathered_copper","oxidized_copper",
            "waxed_copper_block","waxed_exposed_copper","waxed_weathered_copper","waxed_oxidized_copper");
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
        tagged=tags(COPPER);
    }
    @Test void allThreeCurrentGolemsAndChestStatueCoproductsAreChargedToTheSamePumpkin() {
        for(String name:List.of("carved_pumpkin","jack_o_lantern")) {
            var reading=read(name,tagged); assertTrue(reading.supported(),name);
            assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().contains(g("snowball",15)) && r.outputs().contains(g(name,1))));
            assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().contains(g("iron_ingot",5)) && r.outputs().contains(g("poppy",2))));
            var copper=reading.rules().stream().filter(r -> r.outputs().contains(g("copper_ingot",258))).findFirst().orElseThrow();
            for(String chest:NativeMarketCopperChestRules.NAMES) assertTrue(copper.outputs().contains(g(chest,1)),chest);
            assertTrue(copper.outputs().contains(g("oxidized_copper_golem_statue",1)));
            assertTrue(reading.rules().stream().allMatch(r -> r.inputs().equals(List.of(new MarketAcquisitionBounds.InputChoice(List.of(g(name,1)))))),
                    "Ignoring other consumed pattern inputs is conservative, never extra credit");
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g(name,1)))),List.of(),reading.rules());
            assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id("snowball"),4096));
            assertTrue(copper.outputs().stream().mapToLong(goods -> bounds.maximumPayout(goods.item(),goods.quantity())).sum()<=12);
        }
    }
    @Test void changedCopperDeathLootSharesItsCreditWithEveryChestAndTheStatue() {
        var loot=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        var diamond=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,tagged),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                {"type":"minecraft:entity","pools":[{"rolls":3,"entries":[{"type":"minecraft:item","name":"minecraft:diamond"}]}]}
                """))).getOrThrow();
        for(var holder:base.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList())
            loot.register(holder.key(),holder.key().equals(EntityTypes.COPPER_GOLEM.getDefaultLootTable().orElseThrow())?diamond:holder.value(),RegistrationInfo.BUILT_IN);
        loot.freeze();
        var lookup=HolderLookup.Provider.create(Stream.concat(tagged.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(loot)));
        var reading=read("carved_pumpkin",lookup); assertTrue(reading.supported());
        var outcome=reading.rules().stream().filter(r -> r.outputs().contains(g("diamond",3))).findFirst().orElseThrow();
        assertTrue(outcome.outputs().contains(g("copper_chest",1)));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("carved_pumpkin",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete());
        assertTrue(outcome.outputs().stream().mapToLong(goods -> bounds.maximumPayout(goods.item(),goods.quantity())).sum()<=12);
    }
    @Test void missingCurrentTagsOrLootAndChangedPatternMembersHold() {
        assertFalse(read("carved_pumpkin",NativeMarketTestData.withoutBlockTags(base)).supported());
        assertFalse(read("carved_pumpkin",tags(List.of("diamond_block"))).supported());
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable()); registry.freeze();
        var missing=HolderLookup.Provider.create(Stream.concat(tagged.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
        assertFalse(read("carved_pumpkin",missing).supported());
        assertFalse(NativeMarketGolemRules.read(id("pumpkin"),tagged).supported());
    }
    @Test void everyStatueVariantConsumesOneCellForCurrentDeathLootWithoutAnotherChest() {
        for(String name:NativeMarketGolemStatueRules.NAMES) {
            var reading=read(name,tagged); assertTrue(reading.supported(),name);
            assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("copper_ingot",258)))));
            assertFalse(reading.rules().stream().flatMap(r -> r.outputs().stream()).anyMatch(g -> g.item().getPath().contains("chest")));
            for(String target:NativeMarketGolemStatueRules.NAMES)
                assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g(target,1)))),name+" -> "+target);
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g(name,1)))),List.of(),reading.rules());
            assertTrue(bounds.complete()); assertTrue(bounds.maximumPayout(id("copper_ingot"),258)<=12);
        }
    }
    @Test void onlyExactStatuePosePlacementIsSafeAndCurrentTransformsStillApply() {
        var statue=id("copper_golem_statue");
        for(var pose:net.minecraft.world.level.block.CopperGolemStatueBlock.POSE.getPossibleValues())
            assertTrue(NativeMarketGolemStatueRules.safePlacement(statue,new net.minecraft.world.item.component.BlockItemStateProperties(
                    Map.of("copper_golem_pose",pose.getSerializedName()))));
        for(var properties:List.of(Map.of("copper_golem_pose","invalid"),Map.of("copper_golem_pose","standing","waterlogged","true"),Map.of("waterlogged","true")))
            assertFalse(NativeMarketGolemStatueRules.safePlacement(statue,new net.minecraft.world.item.component.BlockItemStateProperties(properties)));
        assertFalse(NativeMarketGolemStatueRules.safePlacement(id("copper_door"),new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("copper_golem_pose","standing"))));
        var changed=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var reading=NativeMarketDirectUseRules.read(statue,DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),tagged,
                new NativeMarketTransformerCatalog.Reading(true,changed));
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("diamond_block",1)))));
        assertFalse(read("copper_golem_statue",NativeMarketTestData.withoutBlockTags(base)).supported(),"Missing current family tag cannot become a positive callback proof");
    }
    private static NativeMarketRecipeRules.Reading read(String name,HolderLookup.Provider lookup) {
        return NativeMarketDirectUseRules.read(id(name),DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
    }
    private static HolderLookup.Provider tags(List<String> copper) {
        var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        BuiltInRegistries.BLOCK.listElements().forEach(h -> blocks.register(h.key(),h.value(),RegistrationInfo.BUILT_IN)); blocks.freeze();
        blocks.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,Map.of(
                BlockTags.COPPER,copper.stream().map(name -> (Holder<Block>)blocks.get(id(name)).orElseThrow()).toList(),
                BlockTags.COPPER_CHESTS,NativeMarketCopperChestRules.NAMES.stream().map(name -> (Holder<Block>)blocks.get(id(name)).orElseThrow()).toList(),
                BlockTags.COPPER_GOLEM_STATUES,NativeMarketGolemStatueRules.NAMES.stream().map(name -> (Holder<Block>)blocks.get(id(name)).orElseThrow()).toList()))).apply();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int quantity) { return new MarketAcquisitionBounds.Goods(id(path),quantity); }
}
