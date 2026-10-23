package de.quest.kingdom;

import java.util.List;

/** Integer lot prices with a strict global buyback bound, including the maximum permanent discount. */
public final class MarketPricePolicy {
    private static final long MAX_PRICE=1_000_000_000;
    private MarketPricePolicy() {}
    /** Server-computed explanation; personal trust benefits both sides of a trade. */
    public record Adjustment(int availability,int need,int trust,int buying,int selling,int reputation) {
        public Adjustment(int availability,int need,int trust,int buying,int selling) { this(availability,need,trust,buying,selling,0); }
        public Adjustment {
            if((availability!=0 && availability!=3) || (need!=0 && need!=3)
                    || reputation< -6 || reputation>12 || reputation%3!=0 || trust<0 || trust>6 || trust%3!=0 || buying< -15 || buying>15 || selling< -15 || selling>15)
                throw new IllegalArgumentException("Invalid market adjustment");
        }
    }
    public static Adjustment adjust(int personality,int stock,int initialStock,int demand,int bondLevel) {
        return adjust(personality,stock,initialStock,demand,bondLevel,0);
    }
    /** Local social trust has twice the weight of guild standing; permanent bond remains separate. */
    public static int reputation(int guild,int local) {
        int score=(de.quest.reputation.SocialReputationRules.clamp(guild)+2*de.quest.reputation.SocialReputationRules.clamp(local))/3;
        return score<=-60?12:score<=-20?6:score>=60?-6:score>=20?-3:0;
    }
    public static Adjustment adjust(int personality,int stock,int initialStock,int demand,int bondLevel,int reputation) {
        if(reputation< -6 || reputation>12 || reputation%3!=0) throw new IllegalArgumentException("Invalid social price adjustment");
        if(personality< -15 || personality>15 || initialStock<1 || initialStock>128 || stock<0 || stock>initialStock
                || demand<0 || demand>128 || bondLevel<0 || bondLevel>2)
            throw new IllegalArgumentException("Invalid market pricing context");
        int availability=stock*2<=initialStock?3:0;
        int need=demand>0?3:0,trust=bondLevel*3;
        return new Adjustment(availability,need,trust,
                Math.clamp(personality+availability+need-trust+reputation,-15,15),
                Math.clamp(personality+need+trust-reputation,-15,15),reputation);
    }
    public record Source(long price,int quantity) {
        public Source { if(price<0 || price>MAX_PRICE || quantity<1 || quantity>4096) throw new IllegalArgumentException("Invalid acquisition source"); }
    }
    public static long sell(long base,int factor,int discount) {
        if(base<1 || base>MAX_PRICE || factor<-15 || factor>15 || discount<0 || discount>15) throw new IllegalArgumentException("Invalid market price");
        return Math.max(1,(base*(100+factor)*(100-discount)+9999)/10000);
    }
    public static long buy(long base,int quantity,int factor,long saleBase,List<Source> sources) {
        if(base<0 || base>MAX_PRICE || quantity<1 || quantity>128 || factor<-15 || factor>15 || sources==null || sources.size()>64)
            throw new IllegalArgumentException("Invalid buyback price");
        long result=(base*(100+factor)+99)/100;
        result=Math.min(result,sell(saleBase,-15,15)-1);
        for(var source:sources) result=Math.min(result,source.price()==0?0:(source.price()*quantity-1)/source.quantity());
        return Math.max(0,result);
    }
}
