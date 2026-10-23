package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Registry and inline item-component definitions only; no transformer/predicate/world execution. */
final class NativeMarketTransformerCatalogTest {
    private static HolderLookup.Provider lookup;
    private static DataComponentMap prototype;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(prototype);
        lookup=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
    }
    @Test void unregisteredInlineToolDefinitionIsNotLostBehindTheRegistryCatalog() {
        var inline=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(BlockStateProvider.of(Blocks.DIAMOND_BLOCK)).build()));
        var tool=DataComponentMap.builder().addAll(prototype).set(DataComponents.BLOCK_TRANSFORMER,Holder.direct(inline)).build();
        var reading=NativeMarketTransformerCatalog.read(lookup,List.of(id("iron_axe")),item -> tool);
        assertTrue(reading.supported()); assertTrue(reading.definitions().stream().anyMatch(definition -> definition==inline));
        var use=NativeMarketDirectUseRules.read(id("iron_axe"),tool,lookup,reading);
        assertTrue(use.supported()); assertTrue(use.rules().isEmpty());
        assertFalse(NativeMarketDirectUseRules.read(id("iron_axe"),tool,lookup,
                NativeMarketTransformerCatalog.read(lookup,List.of(id("leather")),item -> prototype)).supported());
    }
    @Test void missingPrototypeNeverPublishesAPartialTransformerInventory() {
        var reading=NativeMarketTransformerCatalog.read(lookup,List.of(id("iron_axe")),item -> null);
        assertFalse(reading.supported()); assertTrue(reading.definitions().isEmpty());
    }
    @Test void excessiveDistinctInlineDefinitionsAreHeldWithoutPublishingThePrefix() {
        var items=java.util.stream.IntStream.range(0,257).mapToObj(i -> Identifier.parse("test:tool_"+i)).toList();
        var reading=NativeMarketTransformerCatalog.read(lookup,items,item -> DataComponentMap.builder().addAll(prototype)
                .set(DataComponents.BLOCK_TRANSFORMER,Holder.direct(new BlockTransformer(List.of(
                        BlockTransformer.BlockTransformData.builder(BlockStateProvider.of(Blocks.STONE)).build())))).build());
        assertFalse(reading.supported()); assertTrue(reading.definitions().isEmpty());
    }
    @Test void excessiveRegistryRowsCannotHideAnUnseenDefinitionBehindRepeatedIdentities() {
        HolderLookup.RegistryLookup<BlockTransformer> registry=org.mockito.Mockito.mock(HolderLookup.RegistryLookup.class);
        Holder.Reference<BlockTransformer> repeated=org.mockito.Mockito.mock(Holder.Reference.class);
        var value=new BlockTransformer(List.of(BlockTransformer.BlockTransformData.builder(BlockStateProvider.of(Blocks.STONE)).build()));
        org.mockito.Mockito.when(repeated.value()).thenReturn(value);
        org.mockito.Mockito.doReturn(net.minecraft.core.registries.Registries.BLOCK_TRANSFORMER).when(registry).key();
        org.mockito.Mockito.when(registry.listElements()).thenAnswer(invocation -> java.util.stream.IntStream.range(0,300).mapToObj(i -> repeated));
        var oversized=HolderLookup.Provider.create(java.util.stream.Stream.concat(lookup.listRegistries()
                .filter(r -> !r.key().equals(net.minecraft.core.registries.Registries.BLOCK_TRANSFORMER)),java.util.stream.Stream.of(registry)));
        var reading=NativeMarketTransformerCatalog.read(oversized,List.of(),item -> prototype);
        assertFalse(reading.supported()); assertTrue(reading.definitions().isEmpty());
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
}
