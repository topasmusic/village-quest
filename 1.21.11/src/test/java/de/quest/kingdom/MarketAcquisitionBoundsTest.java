package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Independent portfolio/conversion expectations; no world, inventory or recipe-manager runtime. */
final class MarketAcquisitionBoundsTest {
    private static final Identifier WHEAT=item("wheat"),HAY=item("hay_block"),WOOL=item("white_wool"),
            IRON=item("iron_ingot"),NUGGET=item("iron_nugget"),PAPER=item("paper"),LEATHER=item("leather"),BOOK=item("book"),
            HONEY=item("honey_bottle"),BOTTLE=item("glass_bottle"),SUGAR=item("sugar");

    @Test void aMixedBundleCannotRepayItsFullPurchasePriceOncePerCommodity() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(14,goods(WHEAT,128),goods(LEATHER,48))),List.of());
        assertTrue(result.complete());
        assertTrue(payout(result,WHEAT,128)+payout(result,LEATHER,48)<14);
        assertTrue(result.maximumPayout(WHEAT,128)+result.maximumPayout(LEATHER,48)<=14);
    }
    @Test void unpackingHayAndSellingTheOtherBundleContentsStillCannotGenerateProfit() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(11,goods(HAY,16),goods(WOOL,64))),List.of(
                convert(List.of(goods(HAY,1)),List.of(goods(WHEAT,9))),
                convert(List.of(goods(WHEAT,9)),List.of(goods(HAY,1)))));
        assertTrue(result.complete());
        // Four full wheat lots, one partial lot, and the rest of the actual bundle.
        assertTrue(4*payout(result,WHEAT,32)+payout(result,WHEAT,16)+2*payout(result,WOOL,32)<11);
        assertTrue(result.maximumPayout(WHEAT,144)+result.maximumPayout(WOOL,64)<=11);
    }
    @Test void aReturnedContainerSharesTheInputCreditWithTheCraftedOutput() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(20,goods(HONEY,4),goods(BOTTLE,8))),List.of(
                convert(List.of(goods(HONEY,1)),List.of(goods(SUGAR,3),goods(BOTTLE,1)))));
        assertTrue(result.complete());
        assertTrue(result.maximumPayout(SUGAR,12)+result.maximumPayout(BOTTLE,12)<=20);
        assertTrue(payout(result,SUGAR,12)+payout(result,BOTTLE,12)<20);
    }
    @Test void reversibleNuggetConversionsConvergeWithoutRoundingUpOrRenewingCredit() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(3,goods(IRON,1))),List.of(
                convert(List.of(goods(IRON,1)),List.of(goods(NUGGET,9))),
                convert(List.of(goods(NUGGET,9)),List.of(goods(IRON,1)))));
        assertTrue(result.complete());
        assertTrue(result.maximumPayout(NUGGET,9)<=3);
        assertTrue(result.maximumPayout(IRON,1)<=3);
        assertTrue(payout(result,NUGGET,9)<3);
    }
    @Test void ingredientsFromDifferentPurchasesBoundTheCombinedCraftedSale() {
        var result=MarketAcquisitionBounds.solve(List.of(acquire(2,goods(PAPER,3)),acquire(4,goods(LEATHER,1))),List.of(
                convert(List.of(goods(PAPER,3),goods(LEATHER,1)),List.of(goods(BOOK,1)))));
        assertTrue(result.complete()); assertTrue(payout(result,BOOK,1)<6);
        assertTrue(result.maximumPayout(BOOK,1)<=6);
        assertEquals(0,result.maximumPayout(item("unknown_material"),64));
    }
    @Test void freeSourcesAndExhaustedComputationBudgetCannotPayAnything() {
        var free=MarketAcquisitionBounds.solve(List.of(acquire(0,goods(IRON,16))),List.of(
                convert(List.of(goods(IRON,1)),List.of(goods(NUGGET,9)))));
        assertTrue(free.complete()); assertEquals(0,payout(free,NUGGET,64));
        var held=MarketAcquisitionBounds.solve(List.of(acquire(20,goods(HAY,16))),List.of(
                convert(List.of(goods(HAY,1)),List.of(goods(WHEAT,9)))),1);
        assertFalse(held.complete()); assertEquals(0,payout(held,WHEAT,32)); assertEquals(0,payout(held,HAY,16));
    }
    @Test void inputOrderDoesNotChangeTheServerPriceAndRecordsDefendTheirInputs() {
        var contents=new ArrayList<>(List.of(goods(HAY,16),goods(WOOL,64)));
        var bundle=new MarketAcquisitionBounds.Acquisition(11,contents); contents.clear();
        var expensive=acquire(40,goods(WOOL,64));
        var unpack=convert(List.of(goods(HAY,1)),List.of(goods(WHEAT,9)));
        var pack=convert(List.of(goods(WHEAT,9)),List.of(goods(HAY,1)));
        var first=MarketAcquisitionBounds.solve(List.of(bundle,expensive),List.of(unpack,pack));
        var second=MarketAcquisitionBounds.solve(List.of(expensive,bundle),List.of(pack,unpack));
        assertEquals(first.source(WHEAT),second.source(WHEAT));
        assertEquals(first.source(WOOL),second.source(WOOL));
        assertThrows(IllegalArgumentException.class,() -> goods(WHEAT,0));
        assertThrows(IllegalArgumentException.class,() -> acquire(-1,goods(WHEAT,32)));
        assertThrows(IllegalArgumentException.class,() -> goods(Identifier.parse("other:copy"),1));
        var plaque=Identifier.parse("village-quest:apiary_charter_plaque");
        var own=MarketAcquisitionBounds.solve(List.of(acquire(30,goods(plaque,1))),List.of());
        assertTrue(own.complete()); assertEquals(30,own.maximumPayout(plaque,1));
    }
    @Test void retainedInputsCannotLoseTheirCreditToRepeatedFreeCoproducts() {
        var root=item("beehive");
        var result=MarketAcquisitionBounds.solve(List.of(acquire(12,goods(root,1)),acquire(4,goods(PAPER,1))),List.of(
                convert(List.of(goods(root,1)),List.of(goods(root,1),goods(PAPER,1)))));
        assertTrue(result.complete());
        assertEquals(12,result.maximumPayout(root,1),"The unchanged input is not a consumed production cost");
        assertEquals(0,result.maximumPayout(PAPER,4096),"Repeated extra goods cannot inherit a reusable root budget");
    }
    @Test void onlyRetainedQuantitiesCancelAndRealConsumablesStillPayForOutputs() {
        var root=item("beehive");
        var partial=MarketAcquisitionBounds.solve(List.of(acquire(12,goods(root,1))),List.of(
                convert(List.of(goods(root,2)),List.of(goods(root,1),goods(PAPER,1)))));
        assertTrue(partial.complete()); assertEquals(12,partial.maximumPayout(root,1));
        assertEquals(12,partial.maximumPayout(PAPER,1));
        var supplied=MarketAcquisitionBounds.solve(List.of(acquire(12,goods(root,1)),acquire(3,goods(PAPER,1))),List.of(
                convert(List.of(goods(root,1),goods(PAPER,1)),List.of(goods(root,1),goods(BOOK,1)))));
        assertTrue(supplied.complete()); assertEquals(12,supplied.maximumPayout(root,1));
        assertEquals(3,supplied.maximumPayout(BOOK,1));
    }
    @Test void anAlternativeIngredientIsNotGuaranteedToReturnTheSameRoot() {
        var root=item("beehive");
        var rule=new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(List.of(
                goods(root,1),goods(PAPER,1)))),List.of(goods(root,1),goods(BOOK,1)));
        var result=MarketAcquisitionBounds.solve(List.of(acquire(12,goods(root,1)),acquire(3,goods(PAPER,1))),List.of(),List.of(rule));
        assertTrue(result.complete());
        assertTrue(result.maximumPayout(root,1)+result.maximumPayout(BOOK,1)<=3);
    }
    private static long payout(MarketAcquisitionBounds.Result result,Identifier item,int quantity) {
        return MarketPricePolicy.buy(1_000_000_000,quantity,15,1_000_000_000,List.of(result.source(item)));
    }
    private static MarketAcquisitionBounds.Goods goods(Identifier id,int quantity) { return new MarketAcquisitionBounds.Goods(id,quantity); }
    private static MarketAcquisitionBounds.Acquisition acquire(long price,MarketAcquisitionBounds.Goods... goods) { return new MarketAcquisitionBounds.Acquisition(price,List.of(goods)); }
    private static MarketAcquisitionBounds.Conversion convert(List<MarketAcquisitionBounds.Goods> in,List<MarketAcquisitionBounds.Goods> out) { return new MarketAcquisitionBounds.Conversion(in,out); }
    private static Identifier item(String id) { return Identifier.withDefaultNamespace(id); }
}
