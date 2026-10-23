package de.quest.kingdom;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.FireworkStarFadeRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;

/** Exact native special classes only; component-dependent eligibility is conservatively widened. */
final class NativeMarketSpecialRecipeRules {
    private static final Set<Class<?>> KNOWN=Set.of(SmithingTransformRecipe.class,SmithingTrimRecipe.class,
            BannerDuplicateRecipe.class,BookCloningRecipe.class,DecoratedPotRecipe.class,MapExtendingRecipe.class,
            ShieldDecorationRecipe.class,RepairItemRecipe.class,FireworkRocketRecipe.class,FireworkStarRecipe.class,FireworkStarFadeRecipe.class);
    private NativeMarketSpecialRecipeRules() {}
    static boolean handles(Recipe<?> recipe) { return KNOWN.contains(recipe.getClass()); }
    static NativeMarketRecipeRules.Reading read(Recipe<?> recipe,HolderLookup.Provider lookup) {
        if(recipe.getClass()==SmithingTrimRecipe.class || recipe.getClass()==RepairItemRecipe.class) {
            // Native trim: base.copyWithCount(1), only TRIM changes. Repair: two identical items -> one same item.
            // Neither creates another item identity or extra units, so no tighter commodity constraint is needed.
            return new NativeMarketRecipeRules.Reading(true,List.of());
        }
        var ops=RegistryOps.create(JsonOps.INSTANCE,lookup);
        JsonObject data=Recipe.DIRECT_CODEC.encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
        var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        if(recipe.getClass()==FireworkRocketRecipe.class) {
            var shell=ingredient(data,"shell",ops); var fuel=ingredient(data,"fuel",ops); var star=ingredient(data,"star",ops);
            var result=output(template(data,ops),0);
            for(int fuels=1;fuels<=3;fuels++) for(int stars=0;stars<=8-fuels;stars++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(shell);
                for(int i=0;i<fuels;i++) inputs.add(fuel); for(int i=0;i<stars;i++) inputs.add(star);
                rules.add(rule(inputs,result,true));
            }
        } else if(recipe.getClass()==FireworkStarRecipe.class) {
            var fuel=ingredient(data,"fuel",ops); var dye=ingredient(data,"dye",ops);
            var trail=ingredient(data,"trail",ops); var twinkle=ingredient(data,"twinkle",ops);
            var shapes=new ArrayList<MarketAcquisitionBounds.Goods>();
            if(data.has("shapes")) for(var shape:data.getAsJsonObject("shapes").entrySet())
                shapes.addAll(NativeMarketRecipeRules.choice(Ingredient.CODEC.parse(ops,shape.getValue()).getOrThrow()).choices());
            var shape=shapes.isEmpty() ? null : new MarketAcquisitionBounds.InputChoice(shapes.stream().distinct().toList());
            var result=output(template(data,ops),0);
            for(int mask=0;mask<8;mask++) {
                if((mask&1)!=0 && shape==null) continue;
                for(int dyes=1;dyes<=8-Integer.bitCount(mask);dyes++) {
                    var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(fuel);
                    for(int i=0;i<dyes;i++) inputs.add(dye);
                    if((mask&1)!=0) inputs.add(shape); if((mask&2)!=0) inputs.add(trail); if((mask&4)!=0) inputs.add(twinkle);
                    rules.add(rule(inputs,result,true));
                }
            }
        } else if(recipe.getClass()==FireworkStarFadeRecipe.class) {
            var target=ingredient(data,"target",ops); var dye=ingredient(data,"dye",ops); var result=output(template(data,ops),0);
            for(int count=1;count<=8;count++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(target);
                for(int i=0;i<count;i++) inputs.add(dye);
                rules.add(rule(inputs,result,true));
            }
        } else if(recipe.getClass()==SmithingTransformRecipe.class) {
            var smithing=(SmithingTransformRecipe)recipe;
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();
            smithing.templateIngredient().ifPresent(i -> inputs.add(NativeMarketRecipeRules.choice(i)));
            inputs.add(NativeMarketRecipeRules.choice(smithing.baseIngredient()));
            smithing.additionIngredient().ifPresent(i -> inputs.add(NativeMarketRecipeRules.choice(i)));
            // SmithingMenu shrinks each occupied slot once; it does not return crafting remainders.
            rules.add(rule(inputs,output(template(data,ops),0),false));
        } else if(recipe.getClass()==DecoratedPotRecipe.class) {
            var inputs=List.of(ingredient(data,"back",ops),ingredient(data,"front",ops),
                    ingredient(data,"left",ops),ingredient(data,"right",ops));
            rules.add(rule(inputs,output(template(data,ops),0),true));
        } else if(recipe.getClass()==ShieldDecorationRecipe.class) {
            rules.add(rule(List.of(ingredient(data,"banner",ops),ingredient(data,"target",ops)),
                    output(template(data,ops),0),true));
        } else if(recipe.getClass()==MapExtendingRecipe.class) {
            var input=ingredient(data,"map",ops); var material=ingredient(data,"material",ops);
            var result=TransmuteResult.CODEC.parse(ops,data.get("result")).getOrThrow();
            for(var candidate:input.choices()) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();
                inputs.add(single(candidate)); for(int i=0;i<8;i++) inputs.add(material);
                var holder=BuiltInRegistries.ITEM.get(candidate.item()).orElseThrow();
                rules.add(rule(inputs,output(result.resolve(holder),0),true));
            }
        } else {
            boolean books=recipe.getClass()==BookCloningRecipe.class;
            var source=ingredient(data,books ? "source" : "banner",ops);
            var material=books ? ingredient(data,"material",ops) : source;
            var result=template(data,ops);
            for(int count=1;count<=(books ? 8 : 1);count++) {
                for(var candidate:source.choices()) {
                    var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(single(candidate));
                    for(int i=0;i<count;i++) inputs.add(books ? material : single(candidate));
                    var outputs=new ArrayList<MarketAcquisitionBounds.Goods>();
                    outputs.add(output(result,books ? count-1 : 0));
                    outputs.add(candidate); // Patterned banner/written-book source is returned once, within this budget.
                    rules.add(rule(inputs,outputs,true));
                }
            }
        }
        return new NativeMarketRecipeRules.Reading(true,rules);
    }
    private static MarketAcquisitionBounds.InputChoice ingredient(JsonObject data,String key,RegistryOps<com.google.gson.JsonElement> ops) {
        return NativeMarketRecipeRules.choice(Ingredient.CODEC.parse(ops,data.get(key)).getOrThrow());
    }
    private static ItemStackTemplate template(JsonObject data,RegistryOps<com.google.gson.JsonElement> ops) {
        return ItemStackTemplate.CODEC.parse(ops,data.get("result")).getOrThrow();
    }
    private static MarketAcquisitionBounds.Goods output(ItemStackTemplate template,int extraCount) {
        return new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(template.item().value()),Math.addExact(template.count(),extraCount));
    }
    private static MarketAcquisitionBounds.InputChoice single(MarketAcquisitionBounds.Goods candidate) {
        return new MarketAcquisitionBounds.InputChoice(List.of(candidate));
    }
    private static MarketAcquisitionBounds.RecipeConversion rule(List<MarketAcquisitionBounds.InputChoice> inputs,
            MarketAcquisitionBounds.Goods result,boolean remainders) { return rule(inputs,List.of(result),remainders); }
    private static MarketAcquisitionBounds.RecipeConversion rule(List<MarketAcquisitionBounds.InputChoice> inputs,
            List<MarketAcquisitionBounds.Goods> results,boolean remainders) {
        var outputs=new TreeMap<Identifier,Integer>(); results.forEach(g -> outputs.merge(g.item(),g.quantity(),Math::addExact));
        if(remainders) for(var input:inputs) {
            var perPosition=new TreeMap<Identifier,Integer>();
            for(var candidate:input.choices()) {
                var remainder=BuiltInRegistries.ITEM.get(candidate.item()).orElseThrow().value().getCraftingRemainder();
                if(remainder!=null && remainder.item().value()!=Items.AIR && remainder.count()>0) {
                    var goods=output(remainder,0); perPosition.merge(goods.item(),goods.quantity(),Math::max);
                }
            }
            perPosition.forEach((id,count) -> outputs.merge(id,count,Math::addExact));
        }
        return new MarketAcquisitionBounds.RecipeConversion(inputs,outputs.entrySet().stream()
                .map(e -> new MarketAcquisitionBounds.Goods(e.getKey(),e.getValue())).toList());
    }
}
