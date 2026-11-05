package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class RegionalSourcePolicyTest {
    @Test void independentMaterialProofsRejectForeignInputsAndMultiplicationButBoundCheapBundles() {
        for(String item:NativeRegionalBuybackSources.TARGETS) {
            var id=Identifier.withDefaultNamespace(item); var goods=new MarketAcquisitionBounds.Goods(id,32);
            var purchases=List.of(new MarketAcquisitionBounds.Acquisition(1,List.of(goods)));
            var neutral=new NativeMarketRecipeRules.Reading(true,List.of());
            var credit=NativeRegionalBuybackSources.credit(item,purchases,neutral,next -> neutral).orElseThrow();
            var proof=MarketSourceClosure.reviewed(Map.of(id,credit));
            assertEquals(0,MarketPricePolicy.buy(1,32,15,6,List.of(proof.bounds().source(id))),item);
            var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(id,1)));
            var doubled=new MarketAcquisitionBounds.RecipeConversion(List.of(input),List.of(new MarketAcquisitionBounds.Goods(id,2)));
            assertTrue(NativeRegionalBuybackSources.credit(item,purchases,new NativeMarketRecipeRules.Reading(true,List.of(doubled)),next -> neutral).isEmpty());
            var foreign=new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(
                    List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("barrel"),1)))),List.of(new MarketAcquisitionBounds.Goods(id,1)));
            assertTrue(NativeRegionalBuybackSources.credit(item,purchases,new NativeMarketRecipeRules.Reading(true,List.of(foreign)),next -> neutral).isEmpty());
            assertTrue(NativeRegionalBuybackSources.credit(item,purchases,neutral,next -> new NativeMarketRecipeRules.Reading(false,List.of())).isEmpty());
        }
    }
    @Test void renewableCandidatesDoNotAccidentallyAcquireAFiniteMaterialProof() {
        for(String item:List.of("wheat_seeds","potato","dandelion","feather")) {
            var paid=List.of(new MarketAcquisitionBounds.Acquisition(10,List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace(item),32))));
            assertTrue(NativeRegionalBuybackSources.credit(item,paid,new NativeMarketRecipeRules.Reading(true,List.of()),
                    next -> new NativeMarketRecipeRules.Reading(true,List.of())).isEmpty(),item);
        }
    }
    @Test void mixedPurchaseOutputsCannotEachClaimTheEntirePaidBudget() {
        var coal=Identifier.withDefaultNamespace("coal"); var ink=Identifier.withDefaultNamespace("ink_sac");
        var purchases=List.of(new MarketAcquisitionBounds.Acquisition(4,List.of(new MarketAcquisitionBounds.Goods(coal,32),new MarketAcquisitionBounds.Goods(ink,32))));
        var neutral=new NativeMarketRecipeRules.Reading(true,List.of());
        long a=NativeRegionalBuybackSources.credit("coal",purchases,neutral,next -> neutral).orElseThrow();
        long b=NativeRegionalBuybackSources.credit("ink_sac",purchases,neutral,next -> neutral).orElseThrow();
        var proof=MarketSourceClosure.reviewed(Map.of(coal,a,ink,b));
        assertTrue(proof.bounds().maximumPayout(coal,32)+proof.bounds().maximumPayout(ink,32)<=4);
    }
    @Test void compressedGrainAndHoneyCannotBeBoughtAndUnpackedForProfit() {
        for(var pair:List.of(List.of("hay_block","wheat","9"),List.of("honey_block","honey_bottle","4"),List.of("honeycomb_block","honeycomb","4"))) {
            var block=Identifier.withDefaultNamespace(pair.get(0)); var item=Identifier.withDefaultNamespace(pair.get(1));
            var unpack=new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(
                    List.of(new MarketAcquisitionBounds.Goods(block,1)))),List.of(new MarketAcquisitionBounds.Goods(item,Integer.parseInt(pair.get(2)))));
            var reading=new NativeMarketRecipeRules.Reading(true,List.of(unpack));
            var purchases=List.of(new MarketAcquisitionBounds.Acquisition(1,List.of(new MarketAcquisitionBounds.Goods(block,64))));
            long credit=NativeRegionalBuybackSources.credit(pair.get(1),purchases,reading,next -> new NativeMarketRecipeRules.Reading(true,List.of())).orElseThrow();
            var proof=MarketSourceClosure.reviewed(Map.of(item,credit));
            for(int factor:List.of(-15,0,15))
                assertEquals(0,MarketPricePolicy.buy(1,32,factor,6,List.of(proof.bounds().source(item))),pair.get(0));
        }
    }
}
