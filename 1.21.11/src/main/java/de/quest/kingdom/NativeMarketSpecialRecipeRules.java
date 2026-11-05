package de.quest.kingdom;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Exact native 1.21.11 special recipes. Component requirements are conservatively widened,
 * while every produced unit and returned source remains in the same input budget. */
final class NativeMarketSpecialRecipeRules {
    private static final Set<Class<?>> KNOWN=Set.of(SmithingTransformRecipe.class,SmithingTrimRecipe.class,
            BannerDuplicateRecipe.class,BookCloningRecipe.class,MapCloningRecipe.class,DecoratedPotRecipe.class,
            MapExtendingRecipe.class,ShieldDecorationRecipe.class,RepairItemRecipe.class,ArmorDyeRecipe.class,
            TippedArrowRecipe.class,FireworkRocketRecipe.class,FireworkStarRecipe.class,FireworkStarFadeRecipe.class);
    private NativeMarketSpecialRecipeRules() {}
    static boolean handles(Recipe<?> recipe) { return KNOWN.contains(recipe.getClass()); }
    static NativeMarketRecipeRules.Reading read(Recipe<?> recipe,HolderLookup.Provider lookup) {
        var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        var type=recipe.getClass();
        if(type==SmithingTrimRecipe.class || type==RepairItemRecipe.class || type==ArmorDyeRecipe.class)
            return new NativeMarketRecipeRules.Reading(true,List.of()); // Same item identity, no extra units.
        if(type==SmithingTransformRecipe.class) {
            var smithing=(SmithingTransformRecipe)recipe;
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();
            smithing.templateIngredient().ifPresent(i -> inputs.add(NativeMarketRecipeRules.choice(i)));
            inputs.add(NativeMarketRecipeRules.choice(smithing.baseIngredient()));
            smithing.additionIngredient().ifPresent(i -> inputs.add(NativeMarketRecipeRules.choice(i)));
            var ops=RegistryOps.create(JsonOps.INSTANCE,lookup);
            var json=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
            var result=TransmuteResult.CODEC.parse(ops,json.get("result")).getOrThrow();
            rules.add(rule(inputs,List.of(goods(result.item().value(),result.count())),false));
        } else if(type==FireworkRocketRecipe.class) {
            for(int fuel=1;fuel<=3;fuel++) for(int stars=0;stars<=8-fuel;stars++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(Items.PAPER));
                repeat(inputs,choice(Items.GUNPOWDER),fuel);repeat(inputs,choice(Items.FIREWORK_STAR),stars);
                rules.add(rule(inputs,List.of(goods(Items.FIREWORK_ROCKET,3)),true));
            }
        } else if(type==FireworkStarRecipe.class) {
            var dyes=dyes(lookup);
            var shapes=choice(Items.FIRE_CHARGE,Items.FEATHER,Items.GOLD_NUGGET,Items.SKELETON_SKULL,
                    Items.WITHER_SKELETON_SKULL,Items.CREEPER_HEAD,Items.PLAYER_HEAD,Items.DRAGON_HEAD,Items.ZOMBIE_HEAD,Items.PIGLIN_HEAD);
            for(int mask=0;mask<8;mask++) for(int count=1;count<=8-Integer.bitCount(mask);count++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(Items.GUNPOWDER));repeat(inputs,dyes,count);
                if((mask&1)!=0)inputs.add(shapes);if((mask&2)!=0)inputs.add(choice(Items.DIAMOND));if((mask&4)!=0)inputs.add(choice(Items.GLOWSTONE_DUST));
                rules.add(rule(inputs,List.of(goods(Items.FIREWORK_STAR,1)),true));
            }
        } else if(type==FireworkStarFadeRecipe.class) {
            for(int count=1;count<=8;count++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(Items.FIREWORK_STAR));repeat(inputs,dyes(lookup),count);
                rules.add(rule(inputs,List.of(goods(Items.FIREWORK_STAR,1)),true));
            }
        } else if(type==TippedArrowRecipe.class) {
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(Items.LINGERING_POTION));repeat(inputs,choice(Items.ARROW),8);
            rules.add(rule(inputs,List.of(goods(Items.TIPPED_ARROW,8)),true));
        } else if(type==DecoratedPotRecipe.class) {
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();repeat(inputs,tag(lookup,ItemTags.DECORATED_POT_INGREDIENTS),4);
            rules.add(rule(inputs,List.of(goods(Items.DECORATED_POT,1)),true));
        } else if(type==ShieldDecorationRecipe.class) {
            rules.add(rule(List.of(banners(lookup),choice(Items.SHIELD)),List.of(goods(Items.SHIELD,1)),true));
        } else if(type==MapExtendingRecipe.class) {
            var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(Items.FILLED_MAP));repeat(inputs,choice(Items.PAPER),8);
            rules.add(rule(inputs,List.of(goods(Items.FILLED_MAP,1)),true));
        } else if(type==BannerDuplicateRecipe.class) {
            for(var banner:banners(lookup).choices())
                rules.add(rule(List.of(single(banner),single(banner)),List.of(new MarketAcquisitionBounds.Goods(banner.item(),2)),true));
        } else if(type==BookCloningRecipe.class || type==MapCloningRecipe.class) {
            boolean books=type==BookCloningRecipe.class;
            for(int count=1;count<=8;count++) {
                var inputs=new ArrayList<MarketAcquisitionBounds.InputChoice>();inputs.add(choice(books?Items.WRITTEN_BOOK:Items.FILLED_MAP));
                repeat(inputs,choice(books?Items.WRITABLE_BOOK:Items.MAP),count);
                rules.add(rule(inputs,List.of(goods(books?Items.WRITTEN_BOOK:Items.FILLED_MAP,count+1)),true));
            }
        } else throw new IllegalArgumentException("Unproved native special recipe");
        return new NativeMarketRecipeRules.Reading(true,rules);
    }
    private static MarketAcquisitionBounds.InputChoice dyes(HolderLookup.Provider lookup) {
        return matching(lookup,item -> item instanceof DyeItem);
    }
    private static MarketAcquisitionBounds.InputChoice banners(HolderLookup.Provider lookup) {
        return matching(lookup,item -> item instanceof BannerItem);
    }
    private static MarketAcquisitionBounds.InputChoice matching(HolderLookup.Provider lookup,java.util.function.Predicate<Item> predicate) {
        var items=lookup.lookupOrThrow(Registries.ITEM).listElements().filter(h -> predicate.test(h.value())).limit(257).map(h -> goods(h.value(),1)).toList();
        return new MarketAcquisitionBounds.InputChoice(items);
    }
    private static MarketAcquisitionBounds.InputChoice tag(HolderLookup.Provider lookup,TagKey<Item> key) {
        return new MarketAcquisitionBounds.InputChoice(lookup.lookupOrThrow(Registries.ITEM).getOrThrow(key).stream().limit(257).map(h -> goods(h.value(),1)).toList());
    }
    private static void repeat(List<MarketAcquisitionBounds.InputChoice> inputs,MarketAcquisitionBounds.InputChoice choice,int count) {
        for(int i=0;i<count;i++)inputs.add(choice);
    }
    private static MarketAcquisitionBounds.InputChoice choice(Item... items) {
        return new MarketAcquisitionBounds.InputChoice(java.util.Arrays.stream(items).map(i -> goods(i,1)).toList());
    }
    private static MarketAcquisitionBounds.InputChoice single(MarketAcquisitionBounds.Goods goods) { return new MarketAcquisitionBounds.InputChoice(List.of(goods)); }
    private static MarketAcquisitionBounds.Goods goods(Item item,int count) {
        var id=BuiltInRegistries.ITEM.getKey(item);
        if(item==Items.AIR || !id.getNamespace().equals("minecraft"))throw new IllegalArgumentException("Unknown native recipe item");
        return new MarketAcquisitionBounds.Goods(id,count);
    }
    private static MarketAcquisitionBounds.RecipeConversion rule(List<MarketAcquisitionBounds.InputChoice> inputs,List<MarketAcquisitionBounds.Goods> results,boolean remainders) {
        var outputs=new TreeMap<Identifier,Integer>();results.forEach(g -> outputs.merge(g.item(),g.quantity(),Math::addExact));
        if(remainders)for(var input:inputs) {
            var perPosition=new TreeMap<Identifier,Integer>();
            for(var candidate:input.choices()) {
                var remainder=BuiltInRegistries.ITEM.get(candidate.item()).orElseThrow().value().getCraftingRemainder();
                if(remainder!=null && !remainder.isEmpty())perPosition.merge(goods(remainder.getItem(),remainder.getCount()).item(),remainder.getCount(),Math::max);
            }
            perPosition.forEach((id,count) -> outputs.merge(id,count,Math::addExact));
        }
        return new MarketAcquisitionBounds.RecipeConversion(inputs,outputs.entrySet().stream().map(e -> new MarketAcquisitionBounds.Goods(e.getKey(),e.getValue())).toList());
    }
}
