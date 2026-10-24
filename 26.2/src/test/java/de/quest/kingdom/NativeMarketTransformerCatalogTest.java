package de.quest.kingdom;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
/** 26.2 uses native tool maps rather than 26.3 inline/registry transformers. */
final class NativeMarketTransformerCatalogTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(prototype);
    }
    @Test void realTablesIncludeStrippingFlatteningScrapingUnwaxingAndRootCoproducts() {
        var prototypes=NativeMarketTestData.prototypes();
        var reading=NativeMarketTransformerCatalog.read(NativeMarketTestData.lookup(),prototypes.keySet(),prototypes::get);
        assertTrue(reading.supported()); assertFalse(reading.definitions().isEmpty());
        for(var pair:List.of(List.of(Blocks.OAK_LOG,Blocks.STRIPPED_OAK_LOG),List.of(Blocks.GRASS_BLOCK,Blocks.DIRT_PATH),
                List.of(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("exposed_copper")),BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_block"))),List.of(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("waxed_copper_block")),BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_block")))))
            assertTrue(reading.definitions().stream().anyMatch(t -> t.input()==pair.getFirst() && t.output()==pair.getLast()));
        assertTrue(reading.definitions().stream().anyMatch(t -> t.input()==Blocks.ROOTED_DIRT && t.output()==Blocks.DIRT
                && t.drops().equals(List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("hanging_roots"),1)))));
    }
    @Test void targetItemClassesAndTableCountsAreVisibleInThePortEvidence() {
        for(String name:List.of("black_bed","oak_slab","oak_stairs","black_candle_cake","skeleton_skull","oak_sign","copper_golem_statue")) {
            var id=Identifier.withDefaultNamespace(name); var item=BuiltInRegistries.ITEM.getValue(id); var block=BuiltInRegistries.BLOCK.getValue(id);
            System.out.println(name+" item="+item.getClass()+" block="+block.getClass()+" same="+(item==block.asItem()));
        }
    }
    @Test void missingPrototypeNeverPublishesAPartialTransformerInventory() {
        var reading=NativeMarketTransformerCatalog.read(NativeMarketTestData.lookup(),List.of(Identifier.withDefaultNamespace("iron_axe")),item -> null);
        assertFalse(reading.supported()); assertTrue(reading.definitions().isEmpty());
    }
    @Test void excessiveInventoryAndMissingLookupFailClosedBeforeReadingTables() {
        var items=java.util.Collections.nCopies(4097,Identifier.withDefaultNamespace("iron_axe"));
        assertFalse(NativeMarketTransformerCatalog.read(NativeMarketTestData.lookup(),items,item -> DataComponentMap.EMPTY).supported());
        assertFalse(NativeMarketTransformerCatalog.read(null,List.of(),item -> DataComponentMap.EMPTY).supported());
        assertThrows(IllegalArgumentException.class,() -> new NativeMarketTransformerCatalog.Reading(false,
                List.of(new NativeMarketTransformerCatalog.Transform(Blocks.STONE,Blocks.DIRT,List.of()))));
    }
}
