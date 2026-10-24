package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual vanilla registry definitions. No predicate/provider execution, block use or world. */
final class NativeMarketBlockTransformRulesTest {
    private static HolderLookup.Provider lookup;
    private static HolderLookup.Provider original;
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var initial=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(initial);
        var base=NativeMarketTestData.lookup();
        original=base;
        lookup=base;
    }
    @Test void actualAxeStrippingKeepsSourceAndResultCoupled() {
        var axe=transforms();
        var reading=NativeMarketBlockTransformRules.read(id("oak_log"),axe,lookup);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertEquals(List.of(g("oak_log",1)),reading.rules().getFirst().inputs().getFirst().choices());
        assertEquals(List.of(g("stripped_oak_log",1)),reading.rules().getFirst().outputs());
        assertTrue(NativeMarketBlockTransformRules.read(id("paper"),axe,lookup).rules().isEmpty());
    }
    @Test void cropTargetTransformsCannotEraseInjectedMaturityIntoFreshItemCredit() {
        for(var target:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES)) {
            var reading=NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(target),lookup);
            assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
        }
    }
    @Test void stemAndAttachedStemTransformsCannotImportMaturityOrFruitAdjacency() {
        for(var target:List.of(Blocks.PUMPKIN_STEM,Blocks.MELON_STEM,Blocks.ATTACHED_PUMPKIN_STEM,Blocks.ATTACHED_MELON_STEM)) {
            var reading=NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(target),lookup);
            assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
        }
    }
    @Test void exactSlabCallbacksPreserveTheSeparatelyChargedPlacementLoot() {
        for(String name:List.of("oak_slab","stone_slab","cut_copper_slab"))
            assertTrue(NativeMarketDirectUseRules.read(id(name),DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),lookup).supported(),name);
        assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(Blocks.OAK_SLAB),lookup).supported());
    }
    @Test void exactStairsHatchesGatesAndWallsHaveOnlyStateChangesBeyondMandatoryLoot() {
        for(String name:List.of("oak_stairs","oak_trapdoor","iron_trapdoor","oak_fence_gate","cobblestone_wall"))
            assertTrue(NativeMarketDirectUseRules.read(id(name),DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build(),lookup).supported(),name);
    }
    @Test void nativeSlabUnwaxingKeepsItsOneExistingSlabAndForeignSlabInsertionIsHeld() {
        var reading=NativeMarketBlockTransformRules.read(id("waxed_cut_copper_slab"),transforms(),lookup);
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("cut_copper_slab",1)))));
        assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("cut_copper_slab"))),lookup).supported());
    }
    @Test void emptyComposterNeedsConsumedCompostAndCannotImportReadyOutputThroughTransforms() {
        for(var target:List.of(Blocks.COMPOSTER)) {
            var reading=NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(target),lookup);
            assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
        }
    }
    @Test void actualHoeProducesDirtAndHangingRootsInTheSameConsumedRootBudget() {
        var hoe=transforms();
        var reading=NativeMarketBlockTransformRules.read(id("rooted_dirt"),hoe,lookup);
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().contains(g("dirt",1)) && r.outputs().contains(g("hanging_roots",1))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(8,List.of(g("rooted_dirt",1)))),List.of(),reading.rules());
        assertTrue(bounds.maximumPayout(id("dirt"),1)+bounds.maximumPayout(id("hanging_roots"),1)<=8);
    }
    @Test void actualPumpkinCarvingIncludesTheFollowingBlockAndFourSeeds() {
        var reading=NativeMarketBlockTransformRules.carving(id("pumpkin"),lookup);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertTrue(reading.rules().getFirst().outputs().contains(g("carved_pumpkin",1)));
        assertTrue(reading.rules().getFirst().outputs().contains(g("pumpkin_seeds",4)));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(5,List.of(g("pumpkin",1)))),List.of(),reading.rules());
        assertTrue(bounds.maximumPayout(id("carved_pumpkin"),1)+bounds.maximumPayout(id("pumpkin_seeds"),4)<=5);
    }
    @Test void missingInteractionLootIsUnknownAndUnrelatedCarvingIsNeutral() {
        var empty=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable()); empty.freeze();
        var incomplete=HolderLookup.Provider.create(Stream.concat(lookup.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(empty)));
        assertFalse(NativeMarketBlockTransformRules.carving(id("pumpkin"),incomplete).supported());
        var neutral=NativeMarketBlockTransformRules.carving(id("stone"),lookup);
        assertTrue(neutral.supported()); assertTrue(neutral.rules().isEmpty());
    }
    @Test void nonItemIntermediateWithDropsIsHeldRatherThanPublishingPartialGoods() {
        var reading=NativeMarketBlockTransformRules.read(id("rooted_dirt"),List.of(new NativeMarketTransformerCatalog.Transform(
                Blocks.ROOTED_DIRT,Blocks.AIR,List.of(g("hanging_roots",1)))),lookup);
        assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void familyDoorTransformIsBoundedAndNonItemIntermediateRemainsExplicitlyHeld() {
        assertTrue(NativeMarketBlockTransformRules.read(id("waxed_copper_door"),transforms(),lookup).supported());
        assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),NativeMarketTestData.transforms(Blocks.WATER),lookup).supported());
    }
    @Test void derivedCandleCakeRetainsOnlyItsOwnCandleAndChargesInteractionCoproducts() {
        var cake=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("black_candle_cake"));
        var kept=List.of(new NativeMarketTransformerCatalog.Transform(cake,BuiltInRegistries.BLOCK.getValue(id("black_candle")),List.of(g("hanging_roots",1))));
        var reading=NativeMarketBlockTransformRules.readVariant(id("black_candle"),cake,kept,lookup);
        assertTrue(reading.supported()); assertTrue(reading.rules().getFirst().outputs().contains(g("hanging_roots",1)));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("black_candle",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id("hanging_roots"),4096));
        assertFalse(NativeMarketBlockTransformRules.readVariant(id("paper"),cake,kept,lookup).supported());
        assertFalse(NativeMarketBlockTransformRules.readVariant(id("black_candle"),BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_candle_cake")),List.of(),lookup).supported());
    }
    @Test void provenBaseBlockCallbacksComposeCurrentTransformersAndCarvingWithoutAnEmptyShortcut() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        var bookshelf=NativeMarketDirectUseRules.read(id("bookshelf"),prototype,lookup);
        assertTrue(bookshelf.supported()); assertTrue(bookshelf.rules().isEmpty());
        var pumpkin=NativeMarketDirectUseRules.read(id("pumpkin"),prototype,lookup);
        assertTrue(pumpkin.supported()); assertTrue(pumpkin.rules().stream().anyMatch(rule -> rule.outputs().contains(g("pumpkin_seeds",4))));
        assertTrue(NativeMarketDirectUseRules.read(id("copper_chest"),prototype,lookup).supported(),
                "The plain chest proof must not approve copper/weathering subclasses");
    }
    @Test void nativeRailStateChangesDoNotCreateAdditionalGoodsOutsideTheOrdinaryLootProof() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(String name:List.of("rail","powered_rail","activator_rail","detector_rail")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototype,lookup);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
        }
    }
    @Test void nativeAnvilWearReturnsOneWornBlockAndDoesNotCreateRepairMaterials() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        var rules=new java.util.ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        for(String name:List.of("anvil","chipped_anvil","damaged_anvil")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototype,lookup);
            assertTrue(reading.supported(),name); rules.addAll(reading.rules());
        }
        assertEquals(2,rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.inputs().getFirst().choices().equals(List.of(g("anvil",1)))
                && r.outputs().equals(List.of(g("chipped_anvil",1)))));
        assertTrue(rules.stream().anyMatch(r -> r.inputs().getFirst().choices().equals(List.of(g("chipped_anvil",1)))
                && r.outputs().equals(List.of(g("damaged_anvil",1)))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(27,List.of(g("anvil",1)))),List.of(),rules);
        assertEquals(27,bounds.maximumPayout(id("chipped_anvil"),1));
        assertEquals(27,bounds.maximumPayout(id("damaged_anvil"),1));
        assertEquals(0,bounds.maximumPayout(id("iron_ingot"),1));
    }
    @Test void exactSmallFlowerCallbacksUseTheSameVerifiedTransformAndCompostPaths() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(String name:List.of("dandelion","poppy","allium","azure_bluet","oxeye_daisy")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototype,lookup);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().contains(g("bone_meal",1))),name);
        }
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static List<NativeMarketTransformerCatalog.Transform> transforms() {
        var reading=NativeMarketTransformerCatalog.read(lookup,List.of(),item -> DataComponentMap.EMPTY);
        assertTrue(reading.supported()); return reading.definitions();
    }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
