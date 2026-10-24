package de.quest.kingdom;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.item.crafting.ImbueRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;

/** Proven native recipe semantics, without matches/Level/player crafting; component variants share conservative credit. */
public final class NativeMarketRecipeRules {
    private static final Set<Class<?>> FIXED=Set.of(ShapedRecipe.class,ShapelessRecipe.class,SmeltingRecipe.class,
            BlastingRecipe.class,SmokingRecipe.class,CampfireCookingRecipe.class,StonecutterRecipe.class);
    private NativeMarketRecipeRules() {}
    public record Reading(boolean supported,List<MarketAcquisitionBounds.RecipeConversion> rules) {
        public Reading {
            if(rules==null || rules.size()>8192 || !supported && !rules.isEmpty()) throw new IllegalArgumentException("Partial recipe proof");
            rules=List.copyOf(rules);
        }
    }
    private static Reading unknown() { return new Reading(false,List.of()); }
    public static Reading readAll(Collection<? extends Recipe<?>> recipes) {
        return readAll(recipes,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    public static Reading readAll(Collection<? extends Recipe<?>> recipes,HolderLookup.Provider lookup) {
        if(recipes==null || recipes.isEmpty() || recipes.size()>4096 || lookup==null) return unknown();
        final java.util.Map<Item,Identifier> items;
        try { items=registeredItems(lookup); } catch(RuntimeException missing) { return unknown(); }
        var rules=new java.util.ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        for(var recipe:recipes) {
            var reading=read(recipe,lookup,items);
            if(!reading.supported() || rules.size()+reading.rules().size()>8192) return unknown();
            rules.addAll(reading.rules());
        }
        return new Reading(true,rules);
    }
    public static Reading read(Recipe<?> recipe) {
        return read(recipe,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    public static Reading read(Recipe<?> recipe,HolderLookup.Provider lookup) {
        if(recipe==null || lookup==null) return unknown();
        try { return read(recipe,lookup,registeredItems(lookup)); } catch(RuntimeException missing) { return unknown(); }
    }
    private static Reading read(Recipe<?> recipe,HolderLookup.Provider lookup,java.util.Map<Item,Identifier> items) {
        if(recipe==null) return unknown();
        try {
            if(NativeMarketSpecialRecipeRules.handles(recipe)) return NativeMarketSpecialRecipeRules.read(recipe,lookup);
            if(recipe.getClass()==TransmuteRecipe.class || recipe.getClass()==DyeRecipe.class || recipe.getClass()==ImbueRecipe.class)
                return dynamic(recipe,RegistryOps.create(JsonOps.INSTANCE,lookup));
            if(!FIXED.contains(recipe.getClass())) return unknown();
            var placement=recipe.placementInfo();
            if(placement.isImpossibleToPlace() || placement.ingredients().isEmpty() || placement.ingredients().size()>9) return unknown();
            var inputs=new java.util.ArrayList<MarketAcquisitionBounds.InputChoice>();
            var returned=new TreeMap<Identifier,Integer>();
            boolean crafting=recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe;
            for(var ingredient:placement.ingredients()) {
                var choice=fixedChoice(ingredient,items); inputs.add(choice);
                if(crafting) {
                    fixedRemainders(choice,returned,lookup,items);
                }
            }
            ItemStack output;
            if(recipe instanceof ShapedRecipe shaped) output=shaped.assemble(CraftingInput.of(1,1,List.of(ItemStack.EMPTY)));
            else if(recipe instanceof ShapelessRecipe shapeless) output=shapeless.assemble(CraftingInput.of(1,1,List.of(ItemStack.EMPTY)));
            else output=((SingleItemRecipe)recipe).assemble(new SingleRecipeInput(ItemStack.EMPTY));
            if(output.isEmpty()) return unknown();
            returned.merge(registered(output.getItem(),items),output.getCount(),Integer::sum);
            var outputs=returned.entrySet().stream().map(e -> new MarketAcquisitionBounds.Goods(e.getKey(),e.getValue())).toList();
            return new Reading(true,List.of(new MarketAcquisitionBounds.RecipeConversion(inputs,outputs)));
        } catch(RuntimeException malformed) { return unknown(); }
    }
    private static java.util.Map<Item,Identifier> registeredItems(HolderLookup.Provider lookup) {
        var result=new java.util.IdentityHashMap<Item,Identifier>();
        var holders=lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).listElements().limit(16385).toList();
        if(holders.isEmpty() || holders.size()>16384) throw new IllegalArgumentException("Unbounded item registry");
        for(var holder:holders) if(result.put(holder.value(),holder.key().identifier())!=null) throw new IllegalArgumentException("Ambiguous item identity");
        return result;
    }
    private static Identifier registered(Item item,java.util.Map<Item,Identifier> items) {
        var id=items.get(item);
        if(id==null || item==Items.AIR || id.equals(Identifier.withDefaultNamespace("air"))) throw new IllegalArgumentException("Unregistered fixed item");
        return id;
    }
    private static MarketAcquisitionBounds.InputChoice fixedChoice(Ingredient ingredient,java.util.Map<Item,Identifier> items) {
        var holders=ingredient.items().limit(257).toList();
        if(holders.isEmpty() || holders.size()>256) throw new IllegalArgumentException("Unbounded native ingredient");
        return new MarketAcquisitionBounds.InputChoice(holders.stream().map(holder ->
                new MarketAcquisitionBounds.Goods(registered(holder.value(),items),1)).toList());
    }
    private static void fixedRemainders(MarketAcquisitionBounds.InputChoice input,TreeMap<Identifier,Integer> outputs,
                                        HolderLookup.Provider lookup,java.util.Map<Item,Identifier> items) {
        var perPosition=new TreeMap<Identifier,Integer>();
        for(var candidate:input.choices()) {
            var item=lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).get(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ITEM,candidate.item())).orElseThrow().value();
            var remainder=item.getCraftingRemainder();
            if(remainder!=null && remainder.count()>0 && remainder.item().value()!=Items.AIR)
                perPosition.merge(registered(remainder.item().value(),items),remainder.count(),Math::max);
        }
        perPosition.forEach((id,count) -> outputs.merge(id,count,Integer::sum));
    }
    private static Reading dynamic(Recipe<?> recipe,RegistryOps<com.google.gson.JsonElement> ops) {
        // Round-trip the exact native serializer with the caller's live tag lookup; no reflective private fields.
        JsonObject data=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
        var rules=new java.util.ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        if(recipe.getClass()==ImbueRecipe.class) {
            var source=choice(Ingredient.CODEC.parse(ops,data.get("source")).getOrThrow());
            var material=choice(Ingredient.CODEC.parse(ops,data.get("material")).getOrThrow());
            var inputs=new java.util.ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(source);
            for(int i=0;i<8;i++) inputs.add(material);
            rules.add(craftingConversion(inputs,ItemStackTemplate.CODEC.parse(ops,data.get("result")).getOrThrow()));
        } else if(recipe.getClass()==DyeRecipe.class) {
            var target=choice(Ingredient.CODEC.parse(ops,data.get("target")).getOrThrow());
            var dye=choice(Ingredient.CODEC.parse(ops,data.get("dye")).getOrThrow());
            var output=ItemStackTemplate.CODEC.parse(ops,data.get("result")).getOrThrow();
            for(int count=1;count<=8;count++) {
                var inputs=new java.util.ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(target);
                for(int i=0;i<count;i++) inputs.add(dye);
                rules.add(craftingConversion(inputs,output));
            }
        } else {
            var input=choice(Ingredient.CODEC.parse(ops,data.get("input")).getOrThrow());
            var material=choice(Ingredient.CODEC.parse(ops,data.get("material")).getOrThrow());
            var counts=data.has("material_count") ? MinMaxBounds.Ints.CODEC.parse(ops,data.get("material_count")).getOrThrow() : MinMaxBounds.Ints.exactly(1);
            var result=ItemStackTemplate.CODEC.parse(ops,data.get("result")).getOrThrow();
            boolean scaled=data.has("add_material_count_to_result") && data.get("add_material_count_to_result").getAsBoolean();
            for(int count=1;count<=8;count++) if(counts.matches(count)) {
                var inputs=new java.util.ArrayList<MarketAcquisitionBounds.InputChoice>(); inputs.add(input);
                for(int i=0;i<count;i++) inputs.add(material);
                rules.add(craftingConversion(inputs,result.withCount(result.count()+(scaled ? count : 0))));
            }
        }
        return rules.isEmpty() ? unknown() : new Reading(true,rules);
    }
    private static MarketAcquisitionBounds.RecipeConversion craftingConversion(List<MarketAcquisitionBounds.InputChoice> inputs,ItemStackTemplate result) {
        var outputs=new TreeMap<Identifier,Integer>();
        inputs.forEach(input -> remainders(input,outputs)); addOutput(result,1,outputs);
        return conversion(inputs,outputs);
    }
    private static void remainders(MarketAcquisitionBounds.InputChoice input,TreeMap<Identifier,Integer> outputs) {
        var perPosition=new TreeMap<Identifier,Integer>();
        for(var candidate:input.choices()) {
            var item=BuiltInRegistries.ITEM.get(candidate.item()).orElseThrow().value();
            var remainder=item.getCraftingRemainder();
            if(remainder!=null && remainder.count()>0 && remainder.item().value()!=Items.AIR)
                perPosition.merge(vanilla(BuiltInRegistries.ITEM.getKey(remainder.item().value())),remainder.count(),Math::max);
        }
        // Union upper bound: all possible returned kinds share the same input credit, never extra credit.
        perPosition.forEach((id,count) -> outputs.merge(id,count,Integer::sum));
    }
    private static void addOutput(ItemStackTemplate result,int multiplier,TreeMap<Identifier,Integer> outputs) {
        if(result.count()<=0) throw new IllegalArgumentException("Empty recipe result");
        outputs.merge(vanilla(BuiltInRegistries.ITEM.getKey(result.item().value())),Math.multiplyExact(result.count(),multiplier),Math::addExact);
    }
    private static MarketAcquisitionBounds.RecipeConversion conversion(List<MarketAcquisitionBounds.InputChoice> inputs,TreeMap<Identifier,Integer> outputs) {
        return new MarketAcquisitionBounds.RecipeConversion(inputs,outputs.entrySet().stream()
                .map(e -> new MarketAcquisitionBounds.Goods(e.getKey(),e.getValue())).toList());
    }
    static MarketAcquisitionBounds.InputChoice choice(Ingredient ingredient) {
        var items=ingredient.items().limit(257).toList();
        if(items.isEmpty() || items.size()>256) throw new IllegalArgumentException("Unbounded native ingredient");
        return new MarketAcquisitionBounds.InputChoice(items.stream().map(holder ->
                new MarketAcquisitionBounds.Goods(vanilla(BuiltInRegistries.ITEM.getKey(holder.value())),1)).toList());
    }
    private static Identifier vanilla(Identifier item) {
        if(item==null || !item.getNamespace().equals("minecraft") || item.getPath().equals("air")) throw new IllegalArgumentException("Unknown ingredient/output");
        return item;
    }
}
