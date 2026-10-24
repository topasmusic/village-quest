package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import de.quest.content.block.WallPlaqueBlock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Closed class/metadata handles and real shipped loot. Frozen native registry is never mutated; no world/use callback. */
final class NativeMarketProjectPlaquesTest {
    static final List<String> NAMES=List.of("apiary_charter_plaque","village_ledger_plaque","forge_charter_plaque","market_charter_plaque","pasture_charter_plaque","watch_bell_reliquary");
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("village-quest",name); }
    private static BlockItem item(Identifier id) {
        // Constructor would mutate the frozen native intrusive-holder registry. Mock only these read-only handles.
        var block=org.mockito.Mockito.mock(WallPlaqueBlock.class);
        org.mockito.Mockito.when(block.getLootTable()).thenReturn(Optional.of(ResourceKey.create(Registries.LOOT_TABLE,id.withPath(p -> "blocks/"+p))));
        var item=org.mockito.Mockito.mock(BlockItem.class); org.mockito.Mockito.when(item.getBlock()).thenReturn(block); return item;
    }
    @Test void sixClosedPlaqueHandlesHaveNoConsumableOutputAndTheirActualLootReturnsOnlyOnePlaque() throws Exception {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build();
        for(var name:NAMES) {
            var id=id(name); var reading=NativeMarketProjectPlaques.consumption(id,item(id),prototype);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty());
            try(var stream=getClass().getResourceAsStream("/data/village-quest/loot_table/blocks/"+name+".json")) {
                assertNotNull(stream); var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
                var drops=NativeMarketDropRules.read(id,json); assertTrue(drops.supported(),name);
                assertEquals(1,drops.rules().size()); assertEquals(List.of(new MarketAcquisitionBounds.Goods(id,1)),drops.rules().getFirst().outputs());
                var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(29,List.of(new MarketAcquisitionBounds.Goods(id,1)))),List.of(),drops.rules());
                assertEquals(29,bounds.maximumPayout(id,1));
            }
        }
    }
    @Test void changedPrototypeForeignClassesAndUnregisteredRuntimeIdentitiesNeverBorrowThePlaqueProof() {
        var id=id(NAMES.getFirst()); var source=item(id); var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build();
        assertFalse(NativeMarketProjectPlaques.registered(id),"Private fixtures are not live registry proof");
        assertFalse(NativeMarketProjectPlaques.consumption(id,Items.STONE,prototype).supported());
        assertFalse(NativeMarketProjectPlaques.consumption(id("foreign_plaque"),source,prototype).supported());
        assertFalse(NativeMarketProjectPlaques.consumption(id,source,DataComponentMap.EMPTY).supported());
        var changed=DataComponentMap.builder().addAll(prototype).set(DataComponents.USE_REMAINDER,new UseRemainder(new ItemStackTemplate(Items.WHEAT))).build();
        assertFalse(NativeMarketProjectPlaques.consumption(id,source,changed).supported());
        assertFalse(NativeMarketProjectPlaques.consumption(id,org.mockito.Mockito.mock(BlockItem.class),prototype).supported());
    }
}
