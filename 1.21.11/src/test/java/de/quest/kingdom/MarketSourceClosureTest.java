package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Pure closure/reload-source contract; no registry, entity, world or extra wallet. */
final class MarketSourceClosureTest {
    @Test void newlyFoundDropsUnlockDownstreamRecipesAndAreInspectedExactlyOnce() {
        var purchases=List.of(a(12,"bookshelf",1),a(2,"feather",1),a(2,"ink_sac",1));
        var recipes=reading(r(List.of(c("book"),c("feather"),c("ink_sac")),g("writable_book",1)));
        var calls=new HashMap<Identifier,Integer>();
        var snapshot=MarketSourceClosure.build(purchases,recipes,item -> {
            calls.merge(item,1,Integer::sum);
            return item.equals(id("bookshelf")) ? reading(r(List.of(c("bookshelf")),g("book",3))) : reading();
        });
        assertTrue(snapshot.complete()); assertTrue(snapshot.reachable().contains(id("writable_book")));
        assertEquals(5,calls.size()); assertTrue(calls.values().stream().allMatch(count -> count==1));
        assertTrue(snapshot.bounds().maximumPayout(id("book"),3)<=12);
        assertTrue(snapshot.bounds().maximumPayout(id("writable_book"),3)<=24);
    }
    @Test void knownZeroPriceIsReachableAndStillNeedsItsConsumptionProof() {
        var snapshot=MarketSourceClosure.build(List.of(a(0,"milk_bucket",1)),reading(),item ->
                item.equals(id("milk_bucket")) ? reading(r(List.of(c("milk_bucket")),g("bucket",1))) : reading());
        assertTrue(snapshot.complete()); assertTrue(snapshot.reachable().contains(id("bucket")));
        assertEquals(0,snapshot.bounds().maximumPayout(id("bucket"),1));
    }
    @Test void ingredientAlternativesRequireEveryPositionButOnlyOneReachableChoiceEach() {
        var alternative=new MarketAcquisitionBounds.InputChoice(List.of(g("coal",1),g("diamond",1)));
        var recipes=reading(r(List.of(alternative),g("charcoal",1)),r(List.of(alternative,c("paper")),g("book",1)));
        var snapshot=MarketSourceClosure.build(List.of(a(4,"coal",1)),recipes,item -> {
            assertNotEquals(id("diamond"),item); assertNotEquals(id("paper"),item); assertNotEquals(id("book"),item);
            return reading();
        });
        assertTrue(snapshot.complete()); assertTrue(snapshot.reachable().contains(id("charcoal")));
        assertFalse(snapshot.reachable().contains(id("book")));
    }
    @Test void anUnknownReachableSourceHoldsAllPayoutsWithoutAPartialProof() {
        var snapshot=MarketSourceClosure.build(List.of(a(5,"bookshelf",1)),reading(),item -> new NativeMarketRecipeRules.Reading(false,List.of()));
        assertFalse(snapshot.complete()); assertEquals(0,snapshot.bounds().maximumPayout(id("bookshelf"),1));
        assertFalse(snapshot.bounds().complete());
        assertFalse(MarketSourceClosure.build(List.of(a(5,"bookshelf",1)),reading(),item -> null).complete());
        assertFalse(MarketSourceClosure.build(List.of(a(5,"bookshelf",1)),reading(),item -> { throw new IllegalArgumentException("Missing live data"); }).complete());
    }
    @Test void anUnprovenItemIsDiagnosableWithoutPublishingItsPartialCredit() {
        for(boolean throwsMissingData:List.of(false,true)) {
            var snapshot=MarketSourceClosure.build(List.of(a(5,"bookshelf",1),a(2,"paper",1)),reading(),item -> {
                if(throwsMissingData) throw new IllegalArgumentException("Missing current prototype");
                return new NativeMarketRecipeRules.Reading(false,List.of());
            });
            assertFalse(snapshot.complete()); assertEquals(java.util.Optional.of(id("bookshelf")),snapshot.unprovenItem());
            assertTrue(snapshot.bounds().credits().isEmpty());
        }
        var complete=MarketSourceClosure.build(List.of(a(5,"bookshelf",1)),reading(),item -> reading());
        assertTrue(complete.complete()); assertTrue(complete.unprovenItem().isEmpty());
        var missingRecipe=MarketSourceClosure.build(List.of(a(5,"bookshelf",1)),new NativeMarketRecipeRules.Reading(false,List.of()),item -> reading());
        assertFalse(missingRecipe.complete()); assertTrue(missingRecipe.unprovenItem().isEmpty());
    }
    @Test void exhaustedClosureBudgetCannotPublishStaleOrPartialPrices() {
        var snapshot=MarketSourceClosure.build(List.of(a(12,"bookshelf",1)),reading(),item ->
                reading(r(List.of(c("bookshelf")),g("book",3))),1);
        assertFalse(snapshot.complete()); assertEquals(0,snapshot.bounds().maximumPayout(id("bookshelf"),1));
    }
    @Test void aSourceReaderCannotAttachAConversionForTheWrongInput() {
        var snapshot=MarketSourceClosure.build(List.of(a(12,"bookshelf",1)),reading(),item ->
                reading(r(List.of(c("stone")),g("book",3))));
        assertFalse(snapshot.complete());
    }
    @Test void consumedAdditionalMaterialIsRequiredRatherThanInventedAsAFreeEnvironmentInput() {
        var conversion=new MarketAcquisitionBounds.RecipeConversion(List.of(c("potion"),c("dirt")),List.of(g("glass_bottle",1),g("mud",1)));
        var missing=MarketSourceClosure.build(List.of(a(4,"potion",1)),reading(),item -> item.equals(id("potion"))?reading(conversion):reading());
        assertTrue(missing.complete()); assertFalse(missing.reachable().contains(id("mud")));
        assertEquals(4,missing.bounds().maximumPayout(id("potion"),1));
        var complete=MarketSourceClosure.build(List.of(a(4,"potion",1),a(2,"dirt",1)),reading(),
                item -> item.equals(id("potion"))?reading(conversion):reading());
        assertTrue(complete.complete()); assertTrue(complete.reachable().contains(id("mud")));
        assertTrue(complete.bounds().maximumPayout(id("glass_bottle"),1)+complete.bounds().maximumPayout(id("mud"),1)<=6);
    }
    @Test void aReusedBottleDoesNotBecomeFreeWhenTheDirtInputPaysForTheMud() {
        var mud=new MarketAcquisitionBounds.RecipeConversion(List.of(c("potion"),c("dirt")),List.of(g("glass_bottle",1),g("mud",1)));
        var recipes=reading(r(List.of(c("glass_bottle")),g("potion",1)),r(List.of(c("potion")),g("glass_bottle",1)));
        var snapshot=MarketSourceClosure.build(List.of(a(4,"glass_bottle",1),a(2,"dirt",1)),recipes,
                item -> item.equals(id("potion"))?reading(mud):reading());
        assertTrue(snapshot.complete()); assertEquals(4,snapshot.bounds().maximumPayout(id("glass_bottle"),1));
        assertEquals(2,snapshot.bounds().maximumPayout(id("mud"),1));
    }
    @Test void sourceCollectionsAreFrozenAndPublishedSetsAreDefensive() {
        var purchases=new ArrayList<>(List.of(a(12,"bookshelf",1)));
        var snapshot=MarketSourceClosure.build(purchases,reading(),item -> { purchases.clear(); return reading(); });
        assertTrue(snapshot.complete()); assertEquals(java.util.Set.of(id("bookshelf")),snapshot.reachable());
        assertThrows(UnsupportedOperationException.class,() -> snapshot.reachable().clear());
        assertEquals(12,snapshot.bounds().maximumPayout(id("bookshelf"),1));
    }
    @Test void catalogSourcesIncludeAllActualShopPacksAndAllPotentialMarketProfiles() {
        var sources=MarketSourceClosure.catalogAcquisitions();
        assertTrue(sources.containsAll(KingdomMarketCatalog.acquisitions()));
        de.quest.shop.ShopAcquisitionCatalog.all().forEach((key,pack) -> assertTrue(sources.contains(
                new MarketAcquisitionBounds.Acquisition(de.quest.shop.ShopAcquisitionCatalog.minimumPrice(key),pack.goods().stream()
                        .map(goods -> g(goods.item(),goods.count())).toList()))));
        assertThrows(UnsupportedOperationException.class,sources::clear);
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
    private static MarketAcquisitionBounds.Acquisition a(long price,String path,int count) { return new MarketAcquisitionBounds.Acquisition(price,List.of(g(path,count))); }
    private static MarketAcquisitionBounds.InputChoice c(String path) { return new MarketAcquisitionBounds.InputChoice(List.of(g(path,1))); }
    private static MarketAcquisitionBounds.RecipeConversion r(List<MarketAcquisitionBounds.InputChoice> inputs,MarketAcquisitionBounds.Goods output) { return new MarketAcquisitionBounds.RecipeConversion(inputs,List.of(output)); }
    private static NativeMarketRecipeRules.Reading reading(MarketAcquisitionBounds.RecipeConversion... rules) { return new NativeMarketRecipeRules.Reading(true,List.of(rules)); }
}
