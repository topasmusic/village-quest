package de.quest.kingdom;

import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** Current paid material conversions. Ordinary world production is not an instant purchase conversion. */
final class NativeRegionalBuybackSources {
    private NativeRegionalBuybackSources() {}
    static final List<String> TARGETS=List.of("coal","iron_ingot","copper_ingot","leather","ink_sac","paper","white_wool",
            "wheat","bread","hay_block","honey_bottle","honeycomb");
    static boolean grain(String target) { return Set.of("wheat","bread","hay_block").contains(target); }
    static Map<Identifier,Integer> family(String target) {
        var weights=new TreeMap<Identifier,Integer>();
        if(target.equals("coal")) { put(weights,1,"coal","coal_ore","deepslate_coal_ore"); put(weights,9,"coal_block"); }
        else if(target.equals("iron_ingot") || target.equals("copper_ingot")) {
            String metal=target.substring(0,target.indexOf('_'));
            put(weights,9,metal+"_ingot","raw_"+metal,metal+"_ore","deepslate_"+metal+"_ore");
            put(weights,81,metal+"_block","raw_"+metal+"_block"); put(weights,1,metal+"_nugget");
            for(String gear:List.of("helmet","chestplate","leggings","boots","sword","pickaxe","axe","shovel","hoe","horse_armor","nautilus_armor","spear"))
                put(weights,1,metal+"_"+gear);
            if(metal.equals("iron")) put(weights,1,"chainmail_helmet","chainmail_chestplate","chainmail_leggings","chainmail_boots");
            else put(weights,81,"waxed_copper_block","exposed_copper","weathered_copper","oxidized_copper",
                    "waxed_exposed_copper","waxed_weathered_copper","waxed_oxidized_copper");
        } else if(target.equals("leather")) { put(weights,4,"leather"); put(weights,1,"rabbit_hide"); }
        else if(target.equals("ink_sac")) put(weights,1,"ink_sac");
        else if(target.equals("paper")) { put(weights,1,"paper","sugar_cane"); }
        else if(target.equals("white_wool")) {
            put(weights,1,"string");
            for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"))
                put(weights,4,color+"_wool");
        }
        else if(grain(target)) { put(weights,1,"wheat"); put(weights,3,"bread"); put(weights,9,"hay_block"); }
        else if(target.equals("honey_bottle")) { put(weights,1,"honey_bottle"); put(weights,4,"honey_block"); }
        else if(target.equals("honeycomb")) { put(weights,1,"honeycomb"); put(weights,4,"honeycomb_block"); }
        return Map.copyOf(weights);
    }
    private static void put(Map<Identifier,Integer> weights,int value,String... items) {
        for(var item:items) weights.put(Identifier.withDefaultNamespace(item),value);
    }
    static OptionalLong credit(String target,List<MarketAcquisitionBounds.Acquisition> purchases,
            NativeMarketRecipeRules.Reading recipes,Function<Identifier,NativeMarketRecipeRules.Reading> uses) {
        var weights=family(target); var id=Identifier.withDefaultNamespace(target);
        if(weights.isEmpty() || recipes==null || !recipes.supported()) return OptionalLong.empty();
        // Every current recipe into this material must conserve it, including alternative ingredients.
        for(var rule:recipes.rules()) if(!conserves(rule,weights)) return OptionalLong.empty();
        long unit=Long.MAX_VALUE; var paid=new HashSet<Identifier>();
        for(var source:purchases) {
            long mass=source.outputs().stream().mapToLong(g -> (long)weights.getOrDefault(g.item(),0)*g.quantity()).sum();
            if(mass>0) {
                // A mixed paid pack must not promise its entire price independently to each SKU.
                long families=TARGETS.stream()
                        .map(NativeRegionalBuybackSources::family).distinct()
                        .filter(f -> source.outputs().stream().anyMatch(g -> f.containsKey(g.item()))).count();
                unit=Math.min(unit,source.price()*1_000_000L/(mass*families));
                source.outputs().stream().map(MarketAcquisitionBounds.Goods::item).filter(weights::containsKey).forEach(paid::add);
            }
        }
        if(unit==Long.MAX_VALUE) return OptionalLong.empty();
        // Inspect every purchasable family member and every recipe-reachable member, once.
        var reachable=new HashSet<>(paid);
        var inspected=new HashSet<Identifier>();
        for(int pass=0;pass<=weights.size();pass++) {
            for(var rule:recipes.rules())
                if(rule.inputs().stream().allMatch(c -> c.choices().stream().anyMatch(g -> !weights.containsKey(g.item()) || reachable.contains(g.item()))))
                    rule.outputs().stream().map(MarketAcquisitionBounds.Goods::item).filter(weights::containsKey).forEach(reachable::add);
            var pending=reachable.stream().filter(item -> !inspected.contains(item)).toList();
            if(pending.isEmpty()) return OptionalLong.of(unit*weights.get(id));
            for(var item:pending) {
                var reading=uses.apply(item);
                if(reading==null || !reading.supported() || reading.rules().stream().anyMatch(r -> !conserves(r,weights))) return OptionalLong.empty();
                for(var rule:reading.rules()) rule.outputs().stream().map(MarketAcquisitionBounds.Goods::item).filter(weights::containsKey).forEach(reachable::add);
                inspected.add(item);
            }
        }
        return OptionalLong.empty();
    }
    static boolean conserves(MarketAcquisitionBounds.RecipeConversion rule,Map<Identifier,Integer> weights) {
        long output=rule.outputs().stream().mapToLong(g -> (long)weights.getOrDefault(g.item(),0)*g.quantity()).sum();
        if(output==0) return true;
        long input=rule.inputs().stream().mapToLong(c -> c.choices().stream()
                .mapToLong(g -> (long)weights.getOrDefault(g.item(),0)*g.quantity()).min().orElse(0)).sum();
        return input>=output;
    }
}
