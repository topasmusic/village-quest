package de.quest.kingdom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

/** Read the native 26.2 brewing tables without brewing items or running a world. */
final class NativeMarketBrewingRules {
    private NativeMarketBrewingRules() {}
    static NativeMarketRecipeRules.Reading read(PotionBrewing brewing) {
        try {
            if(brewing==null || brewing.getClass()!=PotionBrewing.class) return unknown();
            var containers=new ArrayList<Item>();
            for(var entry:rows(brewing,"containers")) {
                if(!(entry instanceof Ingredient ingredient)) return unknown();
                for(var holder:ingredient.items().limit(257).toList()) containers.add(holder.value());
            }
            if(containers.isEmpty() || containers.size()>256) return unknown();
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            for(var row:rows(brewing,"containerMixes")) {
                var from=(Holder<?>)capture(row,"from"); var to=(Holder<?>)capture(row,"to");
                if(!(from.value() instanceof Item input) || !(to.value() instanceof Item output)
                        || !containers.contains(input) || !containers.contains(output)) return unknown();
                append(rules,input,output,(Ingredient)capture(row,"ingredient"));
            }
            for(var row:rows(brewing,"potionMixes")) {
                if(!(((Holder<?>)capture(row,"from")).value() instanceof net.minecraft.world.item.alchemy.Potion)
                        || !(((Holder<?>)capture(row,"to")).value() instanceof net.minecraft.world.item.alchemy.Potion)) return unknown();
                for(var container:containers) append(rules,container,container,(Ingredient)capture(row,"ingredient"));
            }
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(ReflectiveOperationException | RuntimeException invalid) { return unknown(); }
    }
    private static List<?> rows(PotionBrewing brewing,String name) throws ReflectiveOperationException {
        var value=capture(brewing,name);
        if(!(value instanceof List<?> list) || list.size()>256) throw new IllegalArgumentException("Unbounded brewing table");
        return List.copyOf(list);
    }
    private static Object capture(Object value,String name) throws ReflectiveOperationException {
        if(value==null || value.getClass()!=PotionBrewing.class && !value.getClass().getName().equals(PotionBrewing.class.getName()+"$Mix"))
            throw new IllegalArgumentException("Foreign brewing definition");
        var field=value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static void append(List<MarketAcquisitionBounds.RecipeConversion> rules,Item input,Item output,Ingredient reagent) {
        var choices=NativeMarketRecipeRules.choice(reagent);
        // The stand consumes one reagent for one to three occupied bottle slots.
        for(int count=1;count<=3;count++) {
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();
            for(int i=0;i<count;i++) inputs.add(new MarketAcquisitionBounds.InputChoice(List.of(goods(input,1))));
            inputs.add(choices);
            var outputs=new java.util.TreeMap<Identifier,Integer>();
            for(var choice:choices.choices()) {
                var remainder=BuiltInRegistries.ITEM.get(choice.item()).orElseThrow().value().getCraftingRemainder();
                if(remainder!=null && remainder.item().value()!=Items.AIR)
                    outputs.merge(goods(remainder.item().value(),remainder.count()).item(),remainder.count(),Math::max);
            }
            outputs.merge(goods(output,count).item(),count,Integer::sum);
            rules.add(new MarketAcquisitionBounds.RecipeConversion(inputs,outputs.entrySet().stream()
                    .map(e -> new MarketAcquisitionBounds.Goods(e.getKey(),e.getValue())).toList()));
            if(rules.size()>8192) throw new IllegalArgumentException("Unbounded brewing graph");
        }
    }
    private static MarketAcquisitionBounds.Goods goods(Item item,int count) {
        var id=BuiltInRegistries.ITEM.getKey(item);
        if(id==null || !id.getNamespace().equals("minecraft") || item==Items.AIR) throw new IllegalArgumentException("Unknown brewing item");
        return new MarketAcquisitionBounds.Goods(id,count);
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
