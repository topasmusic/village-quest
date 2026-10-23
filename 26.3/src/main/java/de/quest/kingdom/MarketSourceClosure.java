package de.quest.kingdom;

import de.quest.shop.ShopAcquisitionCatalog;
import de.quest.shop.ShopSupplementalCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** Immutable derived source closure; every reachable item gets its additional live conversion proof once. */
public final class MarketSourceClosure {
    private static final int MAX_NODES=4096,MAX_RULES=8192,WORK=200_000;
    private MarketSourceClosure() {}
    public static final class Snapshot {
        private final MarketAcquisitionBounds.Result bounds;
        private final Set<Identifier> reachable;
        private final Identifier unprovenItem;
        private Snapshot(MarketAcquisitionBounds.Result bounds,Set<Identifier> reachable,Identifier unprovenItem) {
            this.bounds=bounds; this.reachable=Set.copyOf(reachable); this.unprovenItem=unprovenItem;
        }
        public boolean complete() { return bounds.complete(); }
        public MarketAcquisitionBounds.Result bounds() { return bounds; }
        public Set<Identifier> reachable() { return reachable; }
        /** Diagnostic only, never a partial price proof; absent for failures before item inspection. */
        public java.util.Optional<Identifier> unprovenItem() { return java.util.Optional.ofNullable(unprovenItem); }
    }
    public static List<MarketAcquisitionBounds.Acquisition> catalogAcquisitions() {
        var sources=new ArrayList<MarketAcquisitionBounds.Acquisition>();
        ShopAcquisitionCatalog.all().forEach((key,pack) -> sources.add(new MarketAcquisitionBounds.Acquisition(
                ShopAcquisitionCatalog.minimumPrice(key),pack.goods().stream().map(goods -> new MarketAcquisitionBounds.Goods(
                        Identifier.withDefaultNamespace(goods.item()),goods.count())).toList())));
        ShopSupplementalCatalog.all().values().forEach(offer -> sources.add(offer.acquisition()));
        sources.addAll(KingdomMarketCatalog.acquisitions()); return List.copyOf(sources);
    }
    /** Independent reviewed SKU proofs; a held SKU contributes no credit to the combined cache. */
    static Snapshot reviewed(java.util.Map<Identifier,Long> credits) {
        return new Snapshot(new MarketAcquisitionBounds.Result(true,credits),credits.keySet(),null);
    }
    public static Snapshot build(List<MarketAcquisitionBounds.Acquisition> acquisitions,NativeMarketRecipeRules.Reading recipes,
            Function<Identifier,NativeMarketRecipeRules.Reading> extraSources) { return build(acquisitions,recipes,extraSources,64); }
    public static Snapshot build(List<MarketAcquisitionBounds.Acquisition> acquisitions,NativeMarketRecipeRules.Reading recipes,
            Function<Identifier,NativeMarketRecipeRules.Reading> extraSources,int rounds) {
        var reachable=new TreeSet<Identifier>();
        try {
            if(acquisitions==null || acquisitions.isEmpty() || acquisitions.size()>512 || recipes==null || !recipes.supported()
                    || extraSources==null || rounds<1 || rounds>256) return held(reachable);
            var purchases=List.copyOf(acquisitions); var rules=new ArrayList<>(recipes.rules());
            for(var purchase:purchases) for(var goods:purchase.outputs()) add(reachable,goods.item());
            var inspected=new HashSet<Identifier>(); var budget=new Budget();
            for(int round=0;round<rounds;round++) {
                spread(reachable,rules,budget);
                var pending=reachable.stream().filter(item -> !inspected.contains(item)).toList();
                if(pending.isEmpty()) {
                    var result=MarketAcquisitionBounds.solve(purchases,List.of(),rules);
                    return result.complete() ? new Snapshot(result,reachable,null) : held(reachable);
                }
                for(var item:pending) {
                    budget.spend(); NativeMarketRecipeRules.Reading reading;
                    try { reading=extraSources.apply(item); }
                    catch(RuntimeException missingCurrentData) { return held(reachable,item); }
                    if(reading==null || !reading.supported() || rules.size()+reading.rules().size()>MAX_RULES) return held(reachable,item);
                    for(var rule:reading.rules()) {
                        // The callback identifies one consumed root. Other consumed materials remain real
                        // positive inputs; they must become reachable and contribute credit independently.
                        var root=List.of(new MarketAcquisitionBounds.Goods(item,1));
                        if(rule.inputs().stream().noneMatch(position -> position.choices().equals(root))) return held(reachable,item);
                    }
                    inspected.add(item); rules.addAll(reading.rules());
                }
            }
            return held(reachable);
        } catch(RuntimeException malformed) { return held(reachable); }
    }
    private static void spread(Set<Identifier> reachable,List<MarketAcquisitionBounds.RecipeConversion> rules,Budget budget) {
        boolean changed;
        do {
            changed=false;
            for(var rule:rules) {
                budget.spend();
                if(rule.inputs().stream().allMatch(position -> position.choices().stream().anyMatch(choice -> reachable.contains(choice.item()))))
                    for(var output:rule.outputs()) changed|=add(reachable,output.item());
            }
        } while(changed);
    }
    private static boolean add(Set<Identifier> reachable,Identifier item) {
        if(!reachable.contains(item) && reachable.size()>=MAX_NODES) throw new IllegalArgumentException("Source closure too large");
        return reachable.add(item);
    }
    private static Snapshot held(Set<Identifier> reachable) {
        return held(reachable,null);
    }
    private static Snapshot held(Set<Identifier> reachable,Identifier item) {
        // Never publish partial numeric credits that a caller could mistake for a current acquisition bound.
        return new Snapshot(new MarketAcquisitionBounds.Result(false,Map.of()),reachable,item);
    }
    private static final class Budget {
        private int remaining=WORK;
        void spend() { if(--remaining<0) throw new IllegalArgumentException("Source closure work exhausted"); }
    }
}
