package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MarketPricePolicyTest {
    @Test void socialStandingChangesBothDirectionsWithoutRemovingPermanentBondBenefits() {
        var ordinary=MarketPricePolicy.adjust(0,4,4,0,0,0);
        var bad=MarketPricePolicy.adjust(0,4,4,0,0,MarketPricePolicy.reputation(0,-60));
        var good=MarketPricePolicy.adjust(0,4,4,0,0,MarketPricePolicy.reputation(60,60));
        assertTrue(bad.buying()>ordinary.buying()); assertTrue(bad.selling()<ordinary.selling());
        assertTrue(good.buying()<ordinary.buying()); assertTrue(good.selling()>ordinary.selling());
        assertEquals(0,MarketPricePolicy.reputation(0,0)); assertEquals(12,MarketPricePolicy.reputation(-60,-60));
        for(int reputation:new int[]{-6,-3,0,6,12}) for(int bond=0;bond<=2;bond++) for(int personality=-15;personality<=15;personality++) {
            var a=MarketPricePolicy.adjust(personality,2,4,2,bond,reputation);
            assertTrue(MarketPricePolicy.buy(20,32,a.selling(),20,List.of(new MarketPricePolicy.Source(14,32)))
                    <MarketPricePolicy.sell(20,a.buying(),15));
        }
    }
    @Test void realStockAndRemainingHomeDemandAffectOffersWhileTrustBenefitsBothSides() {
        var neutral=MarketPricePolicy.adjust(0,4,4,0,0);
        var scarce=MarketPricePolicy.adjust(0,2,4,0,0);
        var wanted=MarketPricePolicy.adjust(0,4,4,2,0);
        var trusted=MarketPricePolicy.adjust(0,4,4,2,1);
        var allied=MarketPricePolicy.adjust(0,4,4,2,2);
        assertEquals(0,neutral.buying()); assertEquals(0,neutral.selling());
        assertEquals(3,scarce.buying()); assertEquals(0,scarce.selling());
        assertEquals(3,wanted.buying()); assertEquals(3,wanted.selling());
        assertTrue(trusted.buying()<wanted.buying()); assertTrue(trusted.selling()>wanted.selling());
        assertTrue(allied.buying()<trusted.buying()); assertTrue(allied.selling()>trusted.selling());
        assertEquals(3,MarketPricePolicy.adjust(0,0,4,0,0).availability());
    }
    @Test void allContextCombinationsKeepTheExistingGlobalAcquisitionAndDiscountBound() {
        for(int base=-15;base<=15;base++) for(int stock=0;stock<=4;stock++) for(int need=0;need<=1;need++) for(int bond=0;bond<=2;bond++) {
            var a=MarketPricePolicy.adjust(base,stock,4,need,bond);
            assertTrue(a.buying()>=-15 && a.buying()<=15); assertTrue(a.selling()>=-15 && a.selling()<=15);
            for(int discount:List.of(0,5,10,15)) {
                long buying=MarketPricePolicy.sell(20,a.buying(),discount);
                long selling=MarketPricePolicy.buy(20,32,a.selling(),20,List.of(new MarketPricePolicy.Source(14,32)));
                assertTrue(selling<buying);
            }
        }
        assertThrows(IllegalArgumentException.class,() -> MarketPricePolicy.adjust(0,5,4,0,0));
        assertThrows(IllegalArgumentException.class,() -> MarketPricePolicy.adjust(0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> MarketPricePolicy.adjust(0,1,4,0,3));
        assertThrows(IllegalArgumentException.class,() -> new MarketPricePolicy.Adjustment(1,0,0,0,0));
    }
    @Test void everyDynamicFactorAndProsperityDiscountRemainsAboveTheGlobalBuybackCeiling() {
        for(int factor=-15;factor<=15;factor++) for(int discount=0;discount<=15;discount++) {
            long sell=MarketPricePolicy.sell(4,factor,discount);
            long buy=MarketPricePolicy.buy(4,32,factor,4,List.of(new MarketPricePolicy.Source(3,32)));
            assertTrue(buy<sell,"Buyback must be below every attainable sale price");
        }
    }
    @Test void cheaperPilgrimBundlesConstrainBuybackAcrossDifferentLotSizes() {
        assertEquals(4,MarketPricePolicy.buy(8,32,15,20,List.of(new MarketPricePolicy.Source(10,64))));
        assertEquals(0,MarketPricePolicy.buy(2,32,0,4,List.of(new MarketPricePolicy.Source(0,64))));
        assertEquals(9,MarketPricePolicy.buy(10,16,0,20,List.of(new MarketPricePolicy.Source(28,48))));
    }
    @Test void priceCalculationUsesIntegerRoundingAndRejectsUnboundedInputs() {
        assertEquals(3,MarketPricePolicy.sell(4,-15,15));
        assertEquals(5,MarketPricePolicy.sell(4,15,0));
        assertThrows(IllegalArgumentException.class,() -> MarketPricePolicy.sell(Long.MAX_VALUE,0,0));
        assertThrows(IllegalArgumentException.class,() -> new MarketPricePolicy.Source(4,0));
    }
}
