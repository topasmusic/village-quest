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
import net.minecraft.core.component.BlockTransformer;
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
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
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
        var base=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
        original=base;
        // Native generated data lookup has no resource-pack block tags. Bind the two actual required tags
        // to a private registry copy, never to the process-global BuiltIn registry.
        var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        BuiltInRegistries.BLOCK.listElements().forEach(holder -> blocks.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN));
        blocks.freeze();
        var raw=new HashMap<Identifier,List<TagLoader.EntryWithSource>>();
        for(String name:List.of("turns_into_farmland","turns_into_dirt_path","air")) try(var stream=Block.class.getResourceAsStream("/data/minecraft/tags/block/"+name+".json")) {
            assertNotNull(stream);
            var file=TagFile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
            raw.put(id(name),file.entries().stream().map(entry -> new TagLoader.EntryWithSource(entry,"pinned native block tag")).toList());
        }
        var built=new TagLoader<Holder<Block>>((id,required) -> blocks.get(id),"tags/block").build(raw);
        var tags=new HashMap<TagKey<Block>,List<Holder<Block>>>(); built.forEach((id,values) -> tags.put(TagKey.create(Registries.BLOCK,id),values));
        assertEquals(3,built.size()); blocks.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,tags)).apply();
        lookup=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
        var transforms=new MappedRegistry<BlockTransformer>(Registries.BLOCK_TRANSFORMER,Lifecycle.stable());
        for(var holder:original.lookupOrThrow(Registries.BLOCK_TRANSFORMER).listElements().toList())
            transforms.register(holder.key(),transformer(holder.key()),RegistrationInfo.BUILT_IN);
        transforms.freeze();
        lookup=HolderLookup.Provider.create(Stream.concat(lookup.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK_TRANSFORMER)),Stream.of(transforms)));
    }
    @Test void actualAxeStrippingKeepsSourceAndResultCoupled() {
        var axe=transformer(BlockTransformers.AXE);
        var reading=NativeMarketBlockTransformRules.read(id("oak_log"),List.of(axe),lookup);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertEquals(List.of(g("oak_log",1)),reading.rules().getFirst().inputs().getFirst().choices());
        assertEquals(List.of(g("stripped_oak_log",1)),reading.rules().getFirst().outputs());
        assertTrue(NativeMarketBlockTransformRules.read(id("paper"),List.of(axe),lookup).rules().isEmpty());
    }
    @Test void cropTargetTransformsCannotEraseInjectedMaturityIntoFreshItemCredit() {
        for(var target:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES)) {
            for(int age:List.of(0,7)) {
                var transform=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                        net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(
                                target.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,age))).build()));
                var reading=NativeMarketBlockTransformRules.read(id("dirt"),List.of(transform),lookup);
                assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
            }
        }
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(String name:List.of("wheat_seeds","carrot","potato","pumpkin_seeds","melon_seeds"))
            assertTrue(NativeMarketDirectUseRules.read(id(name),prototype,lookup).supported(),"Only exact fresh crop callbacks: "+name);
        for(String name:List.of("bone_meal","beetroot_seeds"))
            assertFalse(NativeMarketDirectUseRules.read(id(name),prototype,lookup).supported(),"Fertilizer and other growth classes remain independent: "+name);
    }
    @Test void stemAndAttachedStemTransformsCannotImportMaturityOrFruitAdjacency() {
        for(var target:List.of(Blocks.PUMPKIN_STEM,Blocks.MELON_STEM,Blocks.ATTACHED_PUMPKIN_STEM,Blocks.ATTACHED_MELON_STEM)) {
            var transform=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(target)).build()));
            for(String source:List.of("dirt","pumpkin_seeds","melon_seeds"))
                assertFalse(NativeMarketBlockTransformRules.read(id(source),List.of(transform),lookup).supported());
        }
        for(var target:List.of(Blocks.PUMPKIN_STEM,Blocks.MELON_STEM)) {
            var mature=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(
                            target.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE,7))).build()));
            assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),List.of(mature),lookup).supported());
        }
    }
    @Test void exactSlabCallbacksPreserveTheSeparatelyChargedPlacementLoot() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(String name:List.of("oak_slab","stone_slab","black_wool_slab")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototype,lookup);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
        }
        for(var type:net.minecraft.world.level.block.state.properties.SlabType.values()) {
            var transform=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(
                            Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,type))).build()));
            assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),List.of(transform),lookup).supported(),"State-erasing slab target remains held: "+type);
        }
    }
    @Test void exactStairsHatchesGatesAndWallsHaveOnlyStateChangesBeyondMandatoryLoot() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(String name:List.of("black_wool_stairs","oak_trapdoor","iron_trapdoor","oak_fence_gate","cobblestone_wall")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototype,lookup);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
            var transformed=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(Blocks.DIAMOND_BLOCK)).build()));
            var current=NativeMarketDirectUseRules.read(id(name),prototype,lookup,new NativeMarketTransformerCatalog.Reading(true,List.of(transformed)));
            assertTrue(current.supported()); assertTrue(current.rules().stream().anyMatch(r -> r.outputs().contains(g("diamond_block",1))));
        }
        assertTrue(NativeMarketDirectUseRules.read(id("copper_door"),prototype,lookup).supported(),"Native mixed-partner doors have their separate zero-credit envelope");
    }
    @Test void slabTransformsRequirePropertyCopyOnEveryReachableBranchAndAnExistingSlab() {
        var source=id("waxed_cut_copper_slab");
        var target=BuiltInRegistries.BLOCK.getValue(id("cut_copper_slab"));
        var copied=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                new CopyPropertiesProvider(target)).build()));
        var reading=NativeMarketBlockTransformRules.read(source,List.of(copied),lookup);
        assertTrue(reading.supported());
        assertEquals(List.of(g("cut_copper_slab",1)),reading.rules().getFirst().outputs());
        assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),List.of(copied),lookup).supported(),
                "Copying properties from dirt cannot prove the slab count property");
        for(var type:net.minecraft.world.level.block.state.properties.SlabType.values()) {
            var injected=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(
                            target.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,type))).build()));
            assertFalse(NativeMarketBlockTransformRules.read(source,List.of(injected),lookup).supported(),
                    "A noncopying branch cannot borrow another branch's preserved slab type: "+type);
            var mixed=new BlockTransformer(List.of(copied.transforms().getFirst(),injected.transforms().getFirst()));
            assertFalse(NativeMarketBlockTransformRules.read(source,List.of(mixed),lookup).supported());
        }
        var current=NativeMarketBlockTransformRules.read(source,List.of(transformer(BlockTransformers.AXE)),lookup);
        assertTrue(current.supported(),"Pinned unwaxing copies all existing slab properties");
        assertTrue(current.rules().stream().anyMatch(rule -> rule.outputs().contains(g("cut_copper_slab",1))));
    }
    @Test void emptyComposterNeedsConsumedCompostAndCannotImportReadyOutputThroughTransforms() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        var reading=NativeMarketDirectUseRules.read(id("composter"),prototype,lookup);
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty(),"Empty retained composter alone cannot produce bone meal");
        for(int level:List.of(0,7,8)) {
            var transform=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(
                            Blocks.COMPOSTER.defaultBlockState().setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL,level))).build()));
            assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),List.of(transform),lookup).supported(),"State-erasing composter transform cannot inject stored compost: "+level);
        }
    }
    @Test void actualHoeProducesDirtAndHangingRootsInTheSameConsumedRootBudget() {
        var hoe=transformer(BlockTransformers.HOE);
        var reading=NativeMarketBlockTransformRules.read(id("rooted_dirt"),List.of(hoe),lookup);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertTrue(reading.rules().getFirst().outputs().contains(g("dirt",1)));
        assertTrue(reading.rules().getFirst().outputs().contains(g("hanging_roots",1)));
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
    @Test void aTransformToAirStillAccountsForAllInteractionDrops() {
        var transform=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(Blocks.AIR))
                .loot(BuiltInLootTables.TILL_ROOTED_DIRT).build()));
        var reading=NativeMarketBlockTransformRules.read(id("rooted_dirt"),List.of(transform),lookup);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertEquals(List.of(g("hanging_roots",1)),reading.rules().getFirst().outputs());
    }
    @Test void familyDoorTransformIsBoundedAndNonItemIntermediateRemainsExplicitlyHeld() {
        var axe=transformer(BlockTransformers.AXE);
        assertTrue(NativeMarketBlockTransformRules.read(id("waxed_copper_door"),List.of(axe),lookup).supported());
        // Farmland actually has a native item in this line; choose a proven non-item water state.
        assertSame(net.minecraft.world.item.Items.AIR,Blocks.WATER.asItem());
        var water=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(Blocks.WATER)).build()));
        assertFalse(NativeMarketBlockTransformRules.read(id("dirt"),List.of(water),lookup).supported());
    }
    @Test void derivedCandleCakeRetainsOnlyItsOwnCandleAndChargesInteractionCoproducts() {
        var cake=BuiltInRegistries.BLOCK.getValue(id("black_candle_cake"));
        var kept=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(cake))
                .loot(BuiltInLootTables.TILL_ROOTED_DIRT).build()));
        var reading=NativeMarketBlockTransformRules.readVariant(id("black_candle"),cake,List.of(kept),lookup);
        assertTrue(reading.supported());
        assertEquals(List.of(g("black_candle",1),g("hanging_roots",1)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("black_candle",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete()); assertEquals(0,bounds.maximumPayout(id("hanging_roots"),4096));
        assertFalse(NativeMarketBlockTransformRules.readVariant(id("paper"),cake,List.of(kept),lookup).supported());
        assertFalse(NativeMarketBlockTransformRules.readVariant(id("black_candle"),BuiltInRegistries.BLOCK.getValue(id("white_candle_cake")),List.of(),lookup).supported());
        var water=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.of(Blocks.WATER)).build()));
        assertFalse(NativeMarketBlockTransformRules.readVariant(id("black_candle"),cake,List.of(water),lookup).supported());
    }
    @Test void provenBaseBlockCallbacksComposeCurrentTransformersAndCarvingWithoutAnEmptyShortcut() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        var bookshelf=NativeMarketDirectUseRules.read(id("bookshelf"),prototype,lookup);
        assertTrue(bookshelf.supported()); assertTrue(bookshelf.rules().isEmpty());
        var pumpkin=NativeMarketDirectUseRules.read(id("pumpkin"),prototype,lookup);
        assertTrue(pumpkin.supported()); assertTrue(pumpkin.rules().stream().anyMatch(rule -> rule.outputs().contains(g("pumpkin_seeds",4))));
        assertFalse(NativeMarketDirectUseRules.read(id("copper_chest"),prototype,lookup).supported(),
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
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
        }
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static BlockTransformer transformer(ResourceKey<BlockTransformer> key) {
        var value=original.lookupOrThrow(Registries.BLOCK_TRANSFORMER).getOrThrow(key).value();
        // Re-decode holders against the private tagged registry, just as the real reload does.
        var json=BlockTransformer.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,original),value).getOrThrow();
        return BlockTransformer.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),json).getOrThrow();
    }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
