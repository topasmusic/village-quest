package de.quest.kingdom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;

/** Conservative bounded native block-loot outcomes; never executes a loot context or assumes a Fortune level. */
public final class NativeMarketDropRules {
    private static final int MAX_OUTCOMES=128,MAX_STACKS=64;
    private static final long UNBOUNDED=Long.MAX_VALUE;
    private NativeMarketDropRules() {}
    static NativeMarketRecipeRules.Reading readAtState(Identifier blockItem,LootTable table,HolderLookup.Provider lookup,
            net.minecraft.world.level.block.state.BlockState state) {
        if(table==null || table.getClass()!=LootTable.class || lookup==null) return unknown();
        try { return readAtState(blockItem,LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow(),state); }
        catch(RuntimeException invalid) { return unknown(); }
    }
    /** Explicit state proof only; callers must separately cover every reachable placement/transform state. */
    static NativeMarketRecipeRules.Reading readAtState(Identifier blockItem,JsonElement json,
            net.minecraft.world.level.block.state.BlockState state) {
        try {
            if(state==null || blockItem==null || !(BuiltInRegistries.ITEM.getValue(blockItem) instanceof net.minecraft.world.item.BlockItem item)
                    || item.getBlock()!=state.getBlock()) return unknown();
            var root=json.getAsJsonObject().deepCopy(); var budget=new Budget();
            filterModifiers(root,state,budget);
            if(root.has("pools")) {
                var kept=new com.google.gson.JsonArray();
                for(var value:root.getAsJsonArray("pools")) {
                    spend(budget,0); var pool=value.getAsJsonObject();
                    if(falseCondition(pool,state)) continue;
                    filterModifiers(pool,state,budget);
                    var entries=new com.google.gson.JsonArray();
                    for(var entry:pool.getAsJsonArray("entries")) entries.add(filterEntry(entry.getAsJsonObject(),state,0,budget));
                    pool.add("entries",entries); kept.add(pool);
                }
                root.add("pools",kept);
            }
            return read(blockItem,root);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static JsonObject filterEntry(JsonObject entry,net.minecraft.world.level.block.state.BlockState state,int depth,Budget budget) {
        spend(budget,depth);
        if(falseCondition(entry,state)) { var empty=new JsonObject(); empty.addProperty("type","minecraft:empty"); return empty; }
        filterModifiers(entry,state,budget);
        if(entry.has("children")) {
            var children=new com.google.gson.JsonArray();
            for(var child:entry.getAsJsonArray("children")) children.add(filterEntry(child.getAsJsonObject(),state,depth+1,budget));
            entry.add("children",children);
        }
        return entry;
    }
    private static void filterModifiers(JsonObject object,net.minecraft.world.level.block.state.BlockState state,Budget budget) {
        if(!object.has("modifier")) return;
        var value=object.get("modifier"); var functions=value.isJsonArray()?value.getAsJsonArray().asList():List.of(value);
        var kept=new com.google.gson.JsonArray();
        for(var function:functions) { spend(budget,0); if(!falseCondition(function.getAsJsonObject(),state)) kept.add(function); }
        object.add("modifier",kept);
    }
    /** Only the exact current native concrete block + literal property predicate is decidable here.
     * Unknown conditions, tags, ranges and metadata never justify removing a possible output. */
    private static boolean falseCondition(JsonObject object,net.minecraft.world.level.block.state.BlockState state) {
        var condition=object.get("condition");
        if(condition==null || !condition.isJsonObject()) return false;
        var predicate=condition.getAsJsonObject();
        if(!predicate.has("type") || !predicate.get("type").isJsonPrimitive()
                || !predicate.get("type").getAsString().equals("minecraft:match_block")
                || predicate.keySet().stream().anyMatch(k -> !Set.of("type","blocks","state").contains(k))) return false;
        var blocks=predicate.get("blocks");
        if(blocks==null || !blocks.isJsonPrimitive() || !blocks.getAsJsonPrimitive().isString()) return false;
        var id=Identifier.tryParse(blocks.getAsString());
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
        var block=BuiltInRegistries.BLOCK.getValue(id);
        if(block!=state.getBlock()) return true;
        if(!predicate.has("state")) return false;
        if(!predicate.get("state").isJsonObject()) return false;
        for(var expected:predicate.getAsJsonObject("state").entrySet()) {
            var property=block.getStateDefinition().getProperty(expected.getKey());
            if(property==null || !expected.getValue().isJsonPrimitive() || !expected.getValue().getAsJsonPrimitive().isString()) continue;
            var parsed=property.getValue(expected.getValue().getAsString());
            if(parsed.isPresent() && !state.getValue(property).equals(parsed.get())) return true;
        }
        return false;
    }
    private static void spend(Budget budget,int depth) {
        if(depth>16 || --budget.remaining<0) throw new IllegalArgumentException("Unbounded state-filtered loot tree");
    }
    public static NativeMarketRecipeRules.Reading read(Identifier blockItem,LootTable table,HolderLookup.Provider lookup) {
        if(table==null || table.getClass()!=LootTable.class || lookup==null) return unknown();
        try { return read(blockItem,LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow()); }
        catch(RuntimeException invalid) { return unknown(); }
    }
    /** Same bounded item-yield proof for a native block interaction context; callers must prove key existence. */
    public static NativeMarketRecipeRules.Reading readInteraction(Identifier blockItem,LootTable table,HolderLookup.Provider lookup) {
        return readInteraction(blockItem,table,lookup,"minecraft:block_interact");
    }
    static NativeMarketRecipeRules.Reading readEntityInteraction(Identifier item,LootTable table,HolderLookup.Provider lookup) {
        return readInteraction(item,table,lookup,"minecraft:entity_interact");
    }
    /** Death consumes the newly created animal; age/feeding costs may be ignored conservatively.
     * Furnace outputs depend on the complete current recipe graph, required before callbacks by
     * NativeMarketSourceBuilder. This reading is not a standalone quote/recipe snapshot. */
    static NativeMarketRecipeRules.Reading readEntityDeath(Identifier item,LootTable table,HolderLookup.Provider lookup) {
        return readInteraction(item,table,lookup,"minecraft:entity",true);
    }
    private static NativeMarketRecipeRules.Reading readInteraction(Identifier blockItem,LootTable table,HolderLookup.Provider lookup,String context) {
        return readInteraction(blockItem,table,lookup,context,false);
    }
    private static NativeMarketRecipeRules.Reading readInteraction(Identifier blockItem,LootTable table,HolderLookup.Provider lookup,String context,
            boolean recipeSmelting) {
        if(table==null || table.getClass()!=LootTable.class || lookup==null) return unknown();
        try {
            if(table==LootTable.EMPTY) return new NativeMarketRecipeRules.Reading(true,List.of());
            var data=LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow().getAsJsonObject();
            if(!data.has("type") || !data.get("type").getAsString().equals(context)) return unknown();
            var bounded=data.deepCopy(); bounded.addProperty("type","minecraft:block");
            if(recipeSmelting) smeltThroughCurrentRecipes(bounded,0,new Budget());
            return read(blockItem,bounded);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static void smeltThroughCurrentRecipes(JsonObject object,int depth,Budget budget) {
        spend(budget,depth);
        if(object.has("modifier")) {
            var modifiers=object.get("modifier");
            var functions=modifiers.isJsonArray()?modifiers.getAsJsonArray().asList():List.of(modifiers);
            var kept=new com.google.gson.JsonArray();
            for(var value:functions) {
                spend(budget,depth); var function=value.getAsJsonObject();
                if(function.get("type").getAsString().equals("minecraft:furnace_smelt")) {
                    keys(function,"type","use_input_count","condition");
                    if(function.has("use_input_count") && (!function.get("use_input_count").isJsonPrimitive()
                            || !function.get("use_input_count").getAsJsonPrimitive().isBoolean()))
                        throw new IllegalArgumentException("Unproved smelt count mode");
                    // Retain the raw SKU as a conservative intermediate. Every current smelting
                    // recipe is already in the source graph, including changed outputs/counts.
                    // Its nonempty result count is >=1. Moving nonnegative count additions,
                    // set/limit operations before that multiplier can only overstate the yield;
                    // ignoring use_input_count=false also overstates every nonempty input.
                    // No recipe match leaves the original stack, also covered by this raw rule.
                } else kept.add(value);
            }
            object.add("modifier",kept);
        }
        for(String children:List.of("pools","entries","children")) if(object.has(children))
            for(var child:object.getAsJsonArray(children)) smeltThroughCurrentRecipes(child.getAsJsonObject(),depth+1,budget);
    }
    /** Native 26.3 JSON representation, also used for independent pinned-resource fixtures. */
    public static NativeMarketRecipeRules.Reading read(Identifier blockItem,JsonElement json) {
        try {
            var source=goods(blockItem.toString(),1); var root=json.getAsJsonObject();
            keys(root,"type","pools","modifier","random_sequence");
            if(!root.has("type") || !root.get("type").getAsString().equals("minecraft:block")) return unknown();
            modify(List.of(),root.get("modifier")); // Validate root metadata even when no pool emits a stack.
            var outcomes=new ArrayList<List<MarketAcquisitionBounds.Goods>>(); outcomes.add(List.of());
            if(root.has("pools")) {
                var pools=root.getAsJsonArray("pools"); if(pools.size()>64) return unknown();
                var budget=new Budget();
                for(var value:pools) {
                    var pool=value.getAsJsonObject(); keys(pool,"entries","rolls","bonus_rolls","condition","conditions","modifier");
                    if(pool.has("bonus_rolls") && maximum(pool.get("bonus_rolls"))!=0) return unknown();
                    int rolls=(int)maximum(pool.get("rolls"));
                    var alternatives=new ArrayList<List<MarketAcquisitionBounds.Goods>>();
                    var entries=pool.getAsJsonArray("entries"); if(entries.size()>128) return unknown();
                    for(var entry:entries) alternatives.addAll(entry(entry.getAsJsonObject(),0,budget));
                    if(alternatives.isEmpty() || rolls==0) alternatives=new ArrayList<>(List.of(List.of()));
                    bounded(alternatives);
                    var expanded=new ArrayList<List<MarketAcquisitionBounds.Goods>>();
                    for(var alternative:alternatives) {
                        var stacks=modify(alternative,pool.get("modifier")); stacks=modify(stacks,root.get("modifier"));
                        var multiplied=new ArrayList<MarketAcquisitionBounds.Goods>();
                        if(rolls>0) for(var stack:stacks) multiplied.add(goods(stack.item().toString(),Math.multiplyExact(stack.quantity(),rolls)));
                        expanded.add(multiplied);
                    }
                    // Each homogeneous roll outcome is an extreme point; mixtures are bounded by their convex combinations.
                    outcomes=combine(outcomes,expanded);
                }
            }
            var input=new MarketAcquisitionBounds.InputChoice(List.of(source));
            var rules=outcomes.stream().filter(o -> !o.isEmpty()).map(o -> new MarketAcquisitionBounds.RecipeConversion(List.of(input),o)).distinct().toList();
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException malformed) { return unknown(); }
    }
    private static List<List<MarketAcquisitionBounds.Goods>> entry(JsonObject entry,int depth,Budget budget) {
        if(depth>16 || --budget.remaining<0) throw new IllegalArgumentException("Unbounded loot tree");
        keys(entry,"type","name","children","modifier","condition","weight","quality");
        List<List<MarketAcquisitionBounds.Goods>> outcomes;
        switch(entry.get("type").getAsString()) {
            case "minecraft:item" -> outcomes=List.of(List.of(goods(entry.get("name").getAsString(),1)));
            case "minecraft:empty" -> outcomes=List.of(List.of());
            case "minecraft:alternatives","minecraft:group","minecraft:sequence" -> {
                boolean alternatives=entry.get("type").getAsString().equals("minecraft:alternatives");
                var children=entry.getAsJsonArray("children"); if(children.size()>128) throw new IllegalArgumentException("Wide loot tree");
                var collected=new ArrayList<List<MarketAcquisitionBounds.Goods>>(); if(!alternatives) collected.add(List.of());
                for(var child:children) {
                    var next=entry(child.getAsJsonObject(),depth+1,budget);
                    if(alternatives) collected.addAll(next); else collected=combine(collected,next);
                    bounded(collected);
                }
                outcomes=collected;
            }
            default -> throw new IllegalArgumentException("Unknown dynamic/reference/tag entry");
        }
        var modified=new ArrayList<List<MarketAcquisitionBounds.Goods>>();
        for(var outcome:outcomes) modified.add(modify(outcome,entry.get("modifier")));
        bounded(modified); return modified;
    }
    private static List<MarketAcquisitionBounds.Goods> modify(List<MarketAcquisitionBounds.Goods> stacks,JsonElement modifiers) {
        if(modifiers==null) return stacks;
        var functions=modifiers.isJsonArray() ? modifiers.getAsJsonArray().asList() : List.of(modifiers);
        if(functions.size()>64) throw new IllegalArgumentException("Too many loot modifiers");
        modifiedCount(1,functions); // Schema proof must not depend on a nonempty outcome.
        var result=new ArrayList<MarketAcquisitionBounds.Goods>();
        for(var stack:stacks) {
            long count=modifiedCount(stack.quantity(),functions);
            if(count>0) result.add(goods(stack.item().toString(),(int)count));
        }
        if(result.size()>MAX_STACKS) throw new IllegalArgumentException("Unbounded output stacks");
        return List.copyOf(result);
    }
    private static long modifiedCount(long count,List<JsonElement> functions) {
        for(var value:functions) {
            var function=value.getAsJsonObject(); String type=function.get("type").getAsString(); long changed=count;
            switch(type) {
                case "minecraft:set_count" -> {
                    keys(function,"type","count","add","condition"); long added=maximum(function.get("count"));
                    changed=function.has("add") && function.get("add").getAsBoolean() ? plus(count,added) : added;
                }
                case "minecraft:limit_count" -> {
                    keys(function,"type","limit","condition"); var limit=function.get("limit");
                    if(limit.isJsonPrimitive()) changed=maximum(limit);
                    else {
                        var range=limit.getAsJsonObject(); keys(range,"min","max");
                        long min=range.has("min") ? maximum(range.get("min")) : 0;
                        long max=range.has("max") ? maximum(range.get("max")) : UNBOUNDED;
                        if(min>max) throw new IllegalArgumentException("Invalid count limits");
                        changed=Math.max(min,Math.min(count,max));
                    }
                }
                case "minecraft:apply_bonus" -> {
                    keys(function,"type","enchantment","formula","parameters","condition");
                    if(!Set.of("minecraft:ore_drops","minecraft:uniform_bonus_count","minecraft:binomial_with_bonus_count")
                            .contains(function.get("formula").getAsString())) throw new IllegalArgumentException("Unknown bonus formula");
                    changed=UNBOUNDED; // A following unconditional limit_count can prove a finite bound.
                }
                case "minecraft:enchanted_count_increase" -> {
                    keys(function,"type","enchantment","count","limit","condition");
                    if(!function.has("enchantment") || !function.get("enchantment").isJsonPrimitive()
                            || !function.get("enchantment").getAsJsonPrimitive().isString()
                            || Identifier.tryParse(function.get("enchantment").getAsString())==null)
                        throw new IllegalArgumentException("Missing/unproved count enchantment");
                    // ItemEnchantments validates every level in [0,255]; EnchantmentHelper takes
                    // the maximum equipped level, not a sum. Ceil before multiplying also bounds
                    // float multiplication/Math.round without executing a context provider.
                    changed=plus(count,Math.multiplyExact(255,maximum(function.get("count"))));
                    if(function.has("limit")) {
                        long limit=maximum(function.get("limit"));
                        // Native limit applies only when a living attacker has level>0. Level0
                        // leaves the original count unchanged, even when limit is smaller.
                        if(limit>0) changed=Math.max(count,Math.min(changed,limit));
                    }
                }
                case "minecraft:explosion_decay" -> keys(function,"type","condition");
                case "minecraft:copy_state" -> {
                    keys(function,"type","block","properties","condition");
                    if(!function.has("block") || !function.has("properties") || !function.get("properties").isJsonArray())
                        throw new IllegalArgumentException("Unproven copied placement state");
                    var copiedBlock=Identifier.tryParse(function.get("block").getAsString());
                    boolean statue=copiedBlock!=null && BuiltInRegistries.BLOCK.containsKey(copiedBlock)
                            && NativeMarketGolemStatueRules.member(BuiltInRegistries.BLOCK.getValue(copiedBlock));
                    // Copied placement states can create extra cells/stack drops (bed part or slab
                    // type). Only pinned hive honey level is allowed: the source builder requires
                    // empty placement, transforms into hives are held, and production after a
                    // refill is outside immediate purchase conversions. Breaking the original
                    // empty cell cannot import stored honey or increase the block item count.
                    // Exact native statue pose changes display/comparator strength only. All
                    // poses share remove-before-revive and the same complete current loot.
                    if(!statue && !Set.of("minecraft:beehive","minecraft:bee_nest").contains(function.get("block").getAsString()))
                        throw new IllegalArgumentException("Unproven copied placement state");
                    for(var property:function.getAsJsonArray("properties")) if(!property.isJsonPrimitive()
                            || !property.getAsString().equals(statue?"copper_golem_pose":"honey_level")) throw new IllegalArgumentException("Unproven copied placement property");
                }
                case "minecraft:copy_components" -> {
                    keys(function,"type","source","include","condition");
                    if(!function.has("include")) throw new IllegalArgumentException("Unbounded copied components");
                    // Native names/tooltip visibility/rarity and banner texture layers are cosmetic;
                    // Skull profiles select only display/name/skin; note-block sound selects an
                    // audio asset. Neither carries item stacks or a use conversion. Stored
                    // inventories and arbitrary block/entity data remain unknown.
                    for(var component:function.getAsJsonArray("include")) if(!Set.of("minecraft:custom_name","minecraft:bees",
                            "minecraft:item_name","minecraft:tooltip_display","minecraft:banner_patterns","minecraft:rarity",
                            "minecraft:profile","minecraft:note_block_sound")
                            .contains(component.getAsString())) throw new IllegalArgumentException("Unknown copied payload");
                }
                default -> throw new IllegalArgumentException("Unknown loot modifier");
            }
            count=function.has("condition") ? Math.max(count,changed) : changed;
        }
        if(count>4096) throw new IllegalArgumentException("Unbounded output count");
        return count;
    }
    private static long maximum(JsonElement value) { return maximum(value,0); }
    private static long maximum(JsonElement value,int depth) {
        if(value==null || depth>16) throw new IllegalArgumentException("Missing/unbounded count provider");
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number=value.getAsDouble(); if(!Double.isFinite(number) || number<0 || number>4096) throw new IllegalArgumentException("Invalid count bound");
            return (long)Math.ceil(number);
        }
        var provider=value.getAsJsonObject();
        return switch(provider.get("type").getAsString()) {
            case "minecraft:constant" -> { keys(provider,"type","value"); yield maximum(provider.get("value"),depth+1); }
            case "minecraft:uniform" -> { keys(provider,"type","min","max"); long min=maximum(provider.get("min"),depth+1),max=maximum(provider.get("max"),depth+1);
                if(min>max) throw new IllegalArgumentException("Invalid uniform bound"); yield max; }
            case "minecraft:binomial" -> { keys(provider,"type","n","p"); double p=provider.get("p").getAsDouble();
                if(!Double.isFinite(p) || p<0 || p>1) throw new IllegalArgumentException("Invalid probability"); yield maximum(provider.get("n"),depth+1); }
            default -> throw new IllegalArgumentException("Unknown/context-dependent count provider");
        };
    }
    private static ArrayList<List<MarketAcquisitionBounds.Goods>> combine(List<List<MarketAcquisitionBounds.Goods>> left,List<List<MarketAcquisitionBounds.Goods>> right) {
        if((long)left.size()*right.size()>MAX_OUTCOMES) throw new IllegalArgumentException("Unbounded loot combinations");
        var result=new ArrayList<List<MarketAcquisitionBounds.Goods>>();
        for(var a:left) for(var b:right) { var joined=new ArrayList<>(a); joined.addAll(b); if(joined.size()>MAX_STACKS) throw new IllegalArgumentException("Unbounded loot stacks"); result.add(List.copyOf(joined)); }
        return result;
    }
    private static void bounded(List<?> outcomes) { if(outcomes.size()>MAX_OUTCOMES) throw new IllegalArgumentException("Unbounded loot outcomes"); }
    private static long plus(long count,long amount) { return count==UNBOUNDED ? UNBOUNDED : Math.addExact(count,amount); }
    private static void keys(JsonObject object,String... allowed) {
        var accepted=Set.of(allowed); if(object.keySet().stream().anyMatch(k -> !accepted.contains(k))) throw new IllegalArgumentException("Unknown loot metadata");
    }
    private static MarketAcquisitionBounds.Goods goods(String value,int count) {
        var id=Identifier.parse(value);
        if(!NativeMarketProjectPlaques.id(id) && (!id.getNamespace().equals("minecraft") || id.getPath().equals("air")
                || !BuiltInRegistries.ITEM.containsKey(id))) throw new IllegalArgumentException("Unknown loot item");
        return new MarketAcquisitionBounds.Goods(id,count);
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    private static final class Budget { int remaining=4096; }
}
