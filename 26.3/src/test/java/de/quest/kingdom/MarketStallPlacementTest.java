package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.*;
import de.quest.content.block.MarketStallBlock;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/** Stateless placement and resource contracts only; no level, entity or game runtime is started. */
final class MarketStallPlacementTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void placementUsesWorldRandomAndCanReturnEachOfFiveSavedCloths() {
        var block=mock(MarketStallBlock.class);var base=mock(BlockState.class);
        var context=mock(BlockPlaceContext.class);var level=mock(Level.class);
        var random=mock(net.minecraft.util.RandomSource.class);
        when(context.getLevel()).thenReturn(level);when(level.getRandom()).thenReturn(random);
        when(block.defaultBlockState()).thenReturn(base);when(block.getStateForPlacement(context)).thenCallRealMethod();
        assertEquals(Set.of(0,1,2,3,4),Set.copyOf(MarketStallBlock.CLOTH.getPossibleValues()));
        for(int i=0;i<5;i++) {
            var colored=mock(BlockState.class);when(random.nextInt(5)).thenReturn(i);
            when(base.setValue(MarketStallBlock.CLOTH,i)).thenReturn(colored);
            assertSame(colored,block.getStateForPlacement(context));
        }
        verify(random,times(5)).nextInt(5);
    }
    @Test void everySavedClothHasACompleteModelAndRetainsOriginalWoodAndCanopyBounds() throws Exception {
        var assets=Path.of("src/main/resources/assets/village-quest");
        var variants=json(assets.resolve("blockstates/market_stall.json")).getAsJsonObject("variants");
        assertEquals(5,variants.size());
        var original=json(assets.resolve("models/block/market_stall.json")).getAsJsonArray("elements");
        var wood=new ArrayList<JsonElement>();for(var element:original) if(element.getAsJsonObject().getAsJsonArray("from").get(1).getAsInt()!=15) wood.add(element);
        for(int i=0;i<5;i++) {
            var model=variants.getAsJsonObject("cloth="+i).get("model").getAsString().replace("village-quest:","");
            var elements=json(assets.resolve("models/"+model+".json")).getAsJsonArray("elements");
            var actualWood=new ArrayList<JsonElement>();var canopy=new ArrayList<JsonObject>();
            for(var element:elements) {
                var e=element.getAsJsonObject();if(e.getAsJsonArray("from").get(1).getAsInt()==15) canopy.add(e);else actualWood.add(e);
            }
            assertEquals(wood,actualWood,"Wood geometry/materials must not change between colors");
            int nextX=0;
            canopy.sort(Comparator.comparingInt(e -> e.getAsJsonArray("from").get(0).getAsInt()));
            for(var e:canopy) {
                var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
                assertEquals(nextX,from.get(0).getAsInt());assertEquals(15,from.get(1).getAsInt());assertEquals(0,from.get(2).getAsInt());
                assertEquals(16,to.get(1).getAsInt());assertEquals(16,to.get(2).getAsInt());nextX=to.get(0).getAsInt();
            }
            assertEquals(16,nextX,"Cloth covers exactly the original canopy");
        }
        assertEquals("village-quest:block/market_stall",json(assets.resolve("models/item/market_stall.json")).get("parent").getAsString());
        var loot=json(Path.of("src/main/resources/data/village-quest/loot_table/blocks/market_stall.json"));
        assertEquals("village-quest:market_stall",loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
    }
    private static JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
}
