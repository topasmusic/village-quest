package de.quest.kingdom;

import com.google.gson.JsonArray;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootTable;

/** One native bed item creates two cells. Their joint bounded loot consumes that one root, not two items. */
public final class NativeMarketPairedLootRules {
    private NativeMarketPairedLootRules() {}
    public static NativeMarketRecipeRules.Reading readBed(Identifier item,LootTable table,HolderLookup.Provider lookup) {
        try {
            if(table==null || table.getClass()!=LootTable.class || lookup==null) return unknown();
            if(table==LootTable.EMPTY) {
                var empty=new JsonObject(); empty.addProperty("type","minecraft:block");
                return readBed(item,empty);
            }
            return readBed(item,NativeMarketLootData.normalize(LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow()));
        } catch(RuntimeException invalid) { return unknown(); }
    }
    /** Same pinned representation for independent data fixtures; no predicate, block or loot execution. */
    public static NativeMarketRecipeRules.Reading readBed(Identifier item,JsonElement data) {
        try {
            if(item==null || !item.getNamespace().equals("minecraft") || !BuiltInRegistries.ITEM.containsKey(item)) return unknown();
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(source.getClass()!=net.minecraft.world.item.BedItem.class) return unknown();
            var block=((BlockItem)source).getBlock();
            if((block.getClass()!=BedBlock.class) || block.asItem()!=source
                    || block.defaultBlockState().getValue(BedBlock.PART)!=BedPart.FOOT) return unknown();
            var blockId=BuiltInRegistries.BLOCK.getKey(block);
            if(blockId==null || !blockId.getNamespace().equals("minecraft")) return unknown();
            data=NativeMarketLootData.normalize(data);
            validateTree(data,0,new Budget());
            // Validate all original loot metadata before pruning. An impossible part condition must
            // never conceal an unknown modifier, reference, entry or unbounded count provider.
            var schema=NativeMarketDropRules.read(item,data);
            if(!schema.supported()) return unknown();
            var foot=NativeMarketDropRules.read(item,part(data,"foot",blockId));
            var head=NativeMarketDropRules.read(item,part(data,"head",blockId));
            if(!foot.supported() || !head.supported()) return unknown();
            var footOut=outcomes(foot); var headOut=outcomes(head);
            if((long)footOut.size()*headOut.size()>128) return unknown();
            var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            for(var first:footOut) for(var second:headOut) {
                var both=new ArrayList<>(first); both.addAll(second);
                if(!both.isEmpty()) rules.add(new MarketAcquisitionBounds.RecipeConversion(List.of(input),both));
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static List<List<MarketAcquisitionBounds.Goods>> outcomes(NativeMarketRecipeRules.Reading reading) {
        return reading.rules().isEmpty()?List.of(List.of()):reading.rules().stream().map(MarketAcquisitionBounds.RecipeConversion::outputs).toList();
    }
    private static JsonElement part(JsonElement data,String part,Identifier block) {
        var root=data.deepCopy().getAsJsonObject();
        if(root.has("pools")) {
            var pools=new JsonArray();
            for(var value:root.getAsJsonArray("pools")) {
                var pool=value.getAsJsonObject();
                if(!possible(pool,part,block)) continue;
                var entries=new JsonArray();
                for(var entry:pool.getAsJsonArray("entries")) entries.add(entry(entry.getAsJsonObject(),part,block));
                pool.add("entries",entries); pools.add(pool);
            }
            root.add("pools",pools);
        }
        return root;
    }
    private static JsonObject entry(JsonObject entry,String part,Identifier block) {
        if(!possible(entry,part,block)) {
            var empty=new JsonObject(); empty.addProperty("type","minecraft:empty"); return empty;
        }
        if(entry.has("children")) {
            var children=new JsonArray();
            for(var child:entry.getAsJsonArray("children")) children.add(entry(child.getAsJsonObject(),part,block));
            entry.add("children",children);
        }
        return entry;
    }
    private static boolean possible(JsonObject value,String part,Identifier block) {
        return (!value.has("condition") || truth(value.get("condition"),part,block)!=Truth.NO)
                && (!value.has("conditions") || truth(value.get("conditions"),part,block)!=Truth.NO);
    }
    private static Truth truth(JsonElement value,String part,Identifier block) {
        if(value==null || value.isJsonNull()) return Truth.MAYBE;
        if(value.isJsonArray()) return aggregate(value.getAsJsonArray(),part,block,true);
        if(!value.isJsonObject()) return Truth.MAYBE; // Named predicates are not executed or guessed.
        var condition=value.getAsJsonObject();
        if(!condition.has("type") || !condition.get("type").isJsonPrimitive()) return Truth.MAYBE;
        return switch(condition.get("type").getAsString()) {
            case "minecraft:match_block" -> match(condition,part,block);
            case "minecraft:all_of","minecraft:any_of" -> {
                if(!only(condition,"type","terms") || !condition.has("terms") || !condition.get("terms").isJsonArray()) yield Truth.MAYBE;
                yield aggregate(condition.getAsJsonArray("terms"),part,block,condition.get("type").getAsString().equals("minecraft:all_of"));
            }
            case "minecraft:inverted" -> {
                if(!only(condition,"type","term") || !condition.has("term")) yield Truth.MAYBE;
                yield switch(truth(condition.get("term"),part,block)) { case YES -> Truth.NO; case NO -> Truth.YES; case MAYBE -> Truth.MAYBE; };
            }
            default -> Truth.MAYBE;
        };
    }
    private static Truth aggregate(JsonArray terms,String part,Identifier block,boolean all) {
        var result=all?Truth.YES:Truth.NO;
        for(var term:terms) {
            var found=truth(term,part,block);
            if(all && found==Truth.NO || !all && found==Truth.YES) return found;
            if(found==Truth.MAYBE) result=Truth.MAYBE;
        }
        return result;
    }
    private static Truth match(JsonObject condition,String part,Identifier block) {
        var result=only(condition,"type","blocks","state")?Truth.YES:Truth.MAYBE;
        if(condition.has("blocks")) {
            var found=blocks(condition.get("blocks"),block);
            if(found==Truth.NO) return Truth.NO;
            if(found==Truth.MAYBE) result=Truth.MAYBE;
        }
        if(condition.has("state")) {
            if(!condition.get("state").isJsonObject()) return Truth.MAYBE;
            var state=condition.getAsJsonObject("state");
            for(var property:state.entrySet()) {
                if(property.getKey().equals("part") && property.getValue().isJsonPrimitive() && property.getValue().getAsJsonPrimitive().isString()) {
                    if(!property.getValue().getAsString().equals(part)) return Truth.NO;
                } else result=Truth.MAYBE;
            }
        }
        return result;
    }
    private static Truth blocks(JsonElement values,Identifier block) {
        if(values.isJsonArray()) return aggregateBlocks(values.getAsJsonArray(),block);
        if(!values.isJsonPrimitive() || !values.getAsJsonPrimitive().isString()) return Truth.MAYBE;
        var name=values.getAsString(); if(name.startsWith("#")) return Truth.MAYBE;
        var id=Identifier.tryParse(name); return id==null?Truth.MAYBE:id.equals(block)?Truth.YES:Truth.NO;
    }
    private static Truth aggregateBlocks(JsonArray values,Identifier block) {
        var result=Truth.NO;
        for(var value:values) { var found=blocks(value,block); if(found==Truth.YES) return found; if(found==Truth.MAYBE) result=found; }
        return result;
    }
    private static boolean only(JsonObject object,String... keys) { return Set.of(keys).containsAll(object.keySet()); }
    private static void validateTree(JsonElement value,int depth,Budget budget) {
        if(value==null || depth>32 || --budget.remaining<0) throw new IllegalArgumentException("Unbounded paired loot");
        if(value.isJsonArray()) for(var child:value.getAsJsonArray()) validateTree(child,depth+1,budget);
        else if(value.isJsonObject()) for(var child:value.getAsJsonObject().entrySet()) validateTree(child.getValue(),depth+1,budget);
    }
    private enum Truth { YES,NO,MAYBE }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    private static final class Budget { int remaining=16_384; }
}
