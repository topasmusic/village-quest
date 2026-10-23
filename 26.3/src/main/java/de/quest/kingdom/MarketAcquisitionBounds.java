package de.quest.kingdom;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * Conservative credit shared by every output of a purchase or conversion.
 * All constraints only lower credit, so arbitrary mixtures/repeated conversions
 * cannot increase the total credit paid for. Derived state, never another wallet.
 */
public final class MarketAcquisitionBounds {
    private static final long SCALE=1_000_000,MAX_PRICE=1_000_000_000,MAX_CREDIT=MAX_PRICE*SCALE,UNKNOWN=Long.MAX_VALUE;
    private static final int MAX_NODES=4096,MAX_RULES=8192,WORK_BUDGET=200_000;
    private MarketAcquisitionBounds() {}
    private static boolean sourceNamespace(Identifier item) {
        return item!=null && (item.getNamespace().equals("minecraft") || item.getNamespace().equals("village-quest"));
    }

    public record Goods(Identifier item,int quantity) {
        public Goods {
            // Own paid plaques are acquisition roots too. Market SKUs remain vanilla;
            // unknown native conversions still hold the complete source graph.
            if(!sourceNamespace(item) || quantity<1 || quantity>4096)
                throw new IllegalArgumentException("Invalid acquisition goods");
        }
    }
    public record Acquisition(long price,List<Goods> outputs) {
        public Acquisition {
            if(price<0 || price>MAX_PRICE) throw new IllegalArgumentException("Invalid acquisition price");
            outputs=canonical(outputs);
        }
    }
    /** Outputs include all returned containers and other co-products. */
    public record Conversion(List<Goods> inputs,List<Goods> outputs) {
        public Conversion { inputs=canonical(inputs); outputs=canonical(outputs); }
    }
    /** One ingredient position may accept several items; only its cheapest attainable choice bounds the result. */
    public record InputChoice(List<Goods> choices) {
        public InputChoice {
            if(choices==null || choices.isEmpty() || choices.size()>256 || choices.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Unbounded ingredient choices");
            choices=choices.stream().distinct().sorted(java.util.Comparator.comparing((Goods g) -> g.item()).thenComparingInt(Goods::quantity)).toList();
        }
    }
    public record RecipeConversion(List<InputChoice> inputs,List<Goods> outputs) {
        public RecipeConversion {
            if(inputs==null || inputs.isEmpty() || inputs.size()>64 || inputs.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Invalid recipe positions");
            inputs=inputs.stream().sorted(java.util.Comparator.comparing(c -> key(c.choices()))).toList(); outputs=canonical(outputs);
        }
    }
    public record Result(boolean complete,Map<Identifier,Long> credits) {
        public Result {
            if(credits==null || credits.size()>MAX_NODES) throw new IllegalArgumentException("Invalid credit snapshot");
            for(var entry:credits.entrySet()) if(!sourceNamespace(entry.getKey())
                    || entry.getValue()==null || entry.getValue()!=UNKNOWN && (entry.getValue()<0 || entry.getValue()>MAX_CREDIT))
                throw new IllegalArgumentException("Invalid credit snapshot");
            credits=Map.copyOf(credits);
        }
        public long maximumPayout(Identifier item,int quantity) {
            if(quantity<1 || quantity>4096) throw new IllegalArgumentException("Invalid payout quantity");
            long credit=credits.getOrDefault(item,UNKNOWN);
            return !complete || credit==UNKNOWN?0:BigInteger.valueOf(credit).multiply(BigInteger.valueOf(quantity))
                    .divide(BigInteger.valueOf(SCALE)).longValueExact();
        }
        /** Rounded down again for the existing exact integer lot policy. Unknown/unfinished data holds buyback at zero. */
        public MarketPricePolicy.Source source(Identifier item) {
            long credit=credits.getOrDefault(item,UNKNOWN);
            if(!complete || credit==UNKNOWN) return new MarketPricePolicy.Source(0,1);
            int denominator=4096;
            while(denominator>1 && credit*denominator/SCALE>MAX_PRICE) denominator/=2;
            return new MarketPricePolicy.Source(credit*denominator/SCALE,denominator);
        }
    }
    public static Result solve(List<Acquisition> acquisitions,List<Conversion> conversions) {
        return solve(acquisitions,conversions,WORK_BUDGET);
    }
    public static Result solve(List<Acquisition> acquisitions,List<Conversion> conversions,int workBudget) {
        return solve(acquisitions,conversions,List.of(),workBudget);
    }
    public static Result solve(List<Acquisition> acquisitions,List<Conversion> conversions,List<RecipeConversion> recipes) {
        return solve(acquisitions,conversions,recipes,WORK_BUDGET);
    }
    private static Result solve(List<Acquisition> acquisitions,List<Conversion> conversions,List<RecipeConversion> recipes,int workBudget) {
        if(acquisitions==null || conversions==null || recipes==null || acquisitions.size()>512 || conversions.size()+recipes.size()>MAX_RULES
                || workBudget<1 || workBudget>1_000_000 || acquisitions.stream().anyMatch(java.util.Objects::isNull)
                || conversions.stream().anyMatch(java.util.Objects::isNull) || recipes.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Unbounded price graph");
        var purchases=acquisitions.stream().sorted(java.util.Comparator.comparing(a -> a.price()+":"+key(a.outputs()))).toList();
        var allRules=new java.util.ArrayList<>(recipes);
        for(var conversion:conversions) allRules.add(new RecipeConversion(conversion.inputs().stream().map(g -> new InputChoice(List.of(g))).toList(),conversion.outputs()));
        var originalRules=allRules.stream().sorted(java.util.Comparator.comparing(c -> c.inputs().toString()+">"+key(c.outputs()))).toList();
        var credits=new TreeMap<Identifier,Long>();
        for(var purchase:purchases) addNodes(credits,purchase.outputs());
        for(var rule:originalRules) { for(var input:rule.inputs()) addNodes(credits,input.choices()); addNodes(credits,rule.outputs()); }
        var rules=originalRules.stream().map(MarketAcquisitionBounds::net).toList();
        int remaining=workBudget;
        for(var purchase:purchases) {
            if(remaining--<=0) return new Result(false,credits);
            constrain(credits,purchase.outputs(),BigInteger.valueOf(purchase.price()).multiply(BigInteger.valueOf(SCALE)));
        }
        boolean changed;
        do {
            changed=false;
            for(var rule:rules) {
                if(remaining--<=0) return new Result(false,credits);
                if(rule.outputs().isEmpty()) continue;
                var inputCredit=recipeBudget(credits,rule.inputs());
                if(inputCredit==null) continue;
                changed|=constrain(credits,rule.outputs(),inputCredit);
            }
        } while(changed);
        return new Result(true,credits);
    }
    private record NetRule(List<InputChoice> inputs,List<Goods> outputs) {}
    /** Cancel only guaranteed identical retained quantities. Alternative ingredients remain actual costs. */
    private static NetRule net(RecipeConversion rule) {
        var outputs=new TreeMap<Identifier,Integer>(); for(var output:rule.outputs()) outputs.put(output.item(),output.quantity());
        var inputs=new java.util.ArrayList<InputChoice>();
        for(var input:rule.inputs()) {
            if(input.choices().size()!=1) { inputs.add(input); continue; }
            var goods=input.choices().getFirst(); int returned=Math.min(goods.quantity(),outputs.getOrDefault(goods.item(),0));
            if(returned>0) outputs.put(goods.item(),outputs.get(goods.item())-returned);
            if(goods.quantity()>returned) inputs.add(new InputChoice(List.of(new Goods(goods.item(),goods.quantity()-returned))));
        }
        return new NetRule(List.copyOf(inputs),outputs.entrySet().stream().filter(e -> e.getValue()>0).map(e -> new Goods(e.getKey(),e.getValue())).toList());
    }
    private static BigInteger recipeBudget(Map<Identifier,Long> credits,List<InputChoice> inputs) {
        BigInteger total=BigInteger.ZERO;
        for(var input:inputs) {
            BigInteger cheapest=null;
            for(var candidate:input.choices()) {
                long credit=credits.get(candidate.item()); if(credit==UNKNOWN) continue;
                var cost=BigInteger.valueOf(credit).multiply(BigInteger.valueOf(candidate.quantity()));
                cheapest=cheapest==null?cost:cheapest.min(cost);
            }
            if(cheapest==null) return null; total=total.add(cheapest);
        }
        return total;
    }
    private static boolean constrain(Map<Identifier,Long> credits,List<Goods> outputs,BigInteger budget) {
        BigInteger known=BigInteger.ZERO; int unknownQuantity=0; boolean changed=false;
        for(var goods:outputs) {
            long credit=credits.get(goods.item());
            if(credit==UNKNOWN) unknownQuantity+=goods.quantity();
            else known=known.add(BigInteger.valueOf(credit).multiply(BigInteger.valueOf(goods.quantity())));
        }
        if(unknownQuantity>0) {
            long share=budget.subtract(known).max(BigInteger.ZERO).divide(BigInteger.valueOf(unknownQuantity))
                    .min(BigInteger.valueOf(MAX_CREDIT)).longValueExact();
            for(var goods:outputs) if(credits.get(goods.item())==UNKNOWN) { credits.put(goods.item(),share); changed=true; }
        }
        var current=total(credits,outputs);
        if(current.compareTo(budget)>0) {
            for(var goods:outputs) {
                long before=credits.get(goods.item());
                long after=BigInteger.valueOf(before).multiply(budget).divide(current).longValueExact();
                if(after<before) { credits.put(goods.item(),after); changed=true; }
            }
        }
        return changed;
    }
    private static BigInteger total(Map<Identifier,Long> credits,List<Goods> goods) {
        BigInteger total=BigInteger.ZERO;
        for(var entry:goods) total=total.add(BigInteger.valueOf(credits.get(entry.item())).multiply(BigInteger.valueOf(entry.quantity())));
        return total;
    }
    private static void addNodes(Map<Identifier,Long> credits,List<Goods> goods) {
        for(var entry:goods) credits.putIfAbsent(entry.item(),UNKNOWN);
        if(credits.size()>MAX_NODES) throw new IllegalArgumentException("Unbounded price nodes");
    }
    private static List<Goods> canonical(List<Goods> goods) {
        if(goods==null || goods.isEmpty() || goods.size()>64 || goods.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Invalid conversion contents");
        var quantities=new TreeMap<Identifier,Integer>(); int total=0;
        for(var entry:goods) { quantities.merge(entry.item(),entry.quantity(),Integer::sum); total+=entry.quantity(); }
        if(total>8192 || quantities.values().stream().anyMatch(q -> q>4096)) throw new IllegalArgumentException("Unbounded contents");
        return quantities.entrySet().stream().map(e -> new Goods(e.getKey(),e.getValue())).toList();
    }
    private static String key(List<Goods> goods) { return goods.toString(); }
}
