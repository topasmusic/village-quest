package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class MarketRecipeChoicesTest {
    private static final Identifier A=id("oak_planks"),B=id("spruce_planks"),C=id("birch_planks"),OUT=id("stick");
    @Test void recipesMustUseTheCheapestPermittedIngredientEvenWhenOtherVariantsAreExpensive() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(2,A),acquire(20,B)),List.of(),List.of(
                new MarketAcquisitionBounds.RecipeConversion(List.of(choice(A,B)),List.of(goods(OUT,4)))));
        assertTrue(result.complete());
        assertTrue(MarketPricePolicy.buy(10,4,15,20,List.of(result.source(OUT)))<2);
    }
    @Test void downstreamAlternativesAreRecheckedWhenAnotherConversionLowersAnInput() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(20,A),acquire(30,B),acquire(2,C)),List.of(
                new MarketAcquisitionBounds.Conversion(List.of(goods(C,1)),List.of(goods(A,1)))),List.of(
                new MarketAcquisitionBounds.RecipeConversion(List.of(choice(A,B)),List.of(goods(OUT,4)))));
        assertTrue(result.complete()); assertTrue(result.maximumPayout(OUT,4)<=2);
    }
    @Test void repeatedWideIngredientTagsDoNotRequireAnExponentialCartesianProduct() {
        var variants=java.util.stream.IntStream.range(0,128).mapToObj(i -> goods(id("variant_"+i),1)).toList();
        var tagged=new MarketAcquisitionBounds.InputChoice(variants);
        var inputs=java.util.Collections.nCopies(9,tagged);
        var result=MarketAcquisitionBounds.solve(List.of(acquire(2,variants.getLast().item())),List.of(),List.of(
                new MarketAcquisitionBounds.RecipeConversion(inputs,List.of(goods(OUT,4)))));
        assertTrue(result.complete()); assertTrue(result.maximumPayout(OUT,4)<=18);
        assertEquals(0,result.maximumPayout(id("not_in_any_source"),64));
    }
    @Test void invalidPublicCreditSnapshotsCannotBypassThePriceDomain() {
        assertThrows(IllegalArgumentException.class,() -> new MarketAcquisitionBounds.Result(true,Map.of(A,-1L)));
        assertThrows(IllegalArgumentException.class,() -> new MarketAcquisitionBounds.Result(true,Map.of(A,1_000_000_000_000_001L)));
        assertThrows(IllegalArgumentException.class,() -> new MarketAcquisitionBounds.InputChoice(List.of()));
    }
    private static MarketAcquisitionBounds.InputChoice choice(Identifier... ids) {
        return new MarketAcquisitionBounds.InputChoice(java.util.Arrays.stream(ids).map(id -> goods(id,1)).toList());
    }
    private static MarketAcquisitionBounds.Goods goods(Identifier id,int count) { return new MarketAcquisitionBounds.Goods(id,count); }
    private static MarketAcquisitionBounds.Acquisition acquire(long price,Identifier id) { return new MarketAcquisitionBounds.Acquisition(price,List.of(goods(id,1))); }
    private static Identifier id(String value) { return Identifier.withDefaultNamespace(value); }
}
