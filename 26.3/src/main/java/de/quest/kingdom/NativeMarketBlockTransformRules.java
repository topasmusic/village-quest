package de.quest.kingdom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** Native single-block transforms and carving: resulting block plus every interaction coproduct share one input. */
public final class NativeMarketBlockTransformRules {
    private NativeMarketBlockTransformRules() {}
    public static NativeMarketRecipeRules.Reading read(Identifier item,Collection<BlockTransformer> transformers,HolderLookup.Provider lookup) {
        try {
            if(!knownRoot(item) || lookup==null || transformers==null || transformers.size()>256) return unknown();
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(!(source instanceof BlockItem blockItem)) return neutral();
            return readVariant(item,blockItem.getBlock(),transformers,lookup);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    /** Each proven placement variant consumes the same item; its block predicates can differ. */
    static NativeMarketRecipeRules.Reading readVariant(Identifier item,Block input,Collection<BlockTransformer> transformers,HolderLookup.Provider lookup) {
        try {
            boolean candleCake=candleCakeRoot(item,input);
            if(!knownRoot(item) || input==null || lookup==null || transformers==null || transformers.size()>256
                    || input.asItem()!=BuiltInRegistries.ITEM.get(item).orElseThrow().value() && !candleCake) return unknown();
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>(); var budget=new Budget();
            boolean copperChest=NativeMarketCopperChestRules.member(input);
            boolean nativeDoor=NativeMarketDoorRules.member(input);
            if(copperChest && !NativeMarketCopperChestRules.boundedFamily(lookup)) return unknown();
            if(nativeDoor) NativeMarketDoorRules.family(lookup);
            // HoneycombItem.useOn uses this native map rather than the component transformer
            // registry. Read its current immutable value, never invoke useOn or a world callback.
            // Every waxed half of a connected chest still replaces one real input half; ignoring
            // the shared comb cost is conservative and does not create another chest item.
            var waxables=HoneycombItem.WAXABLES.get();
            if(waxables.size()>256) return unknown();
            var waxed=waxables.get(input);
            if(waxed!=null) {
                if(waxed.asItem()==Items.AIR) return unknown();
                append(rules,item,new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(waxed.asItem()),1),neutral());
            }
            for(var transformer:List.copyOf(transformers)) {
                if(transformer.transforms().size()>512) return unknown();
                for(var transform:transformer.transforms()) {
                    var blocks=new LinkedHashSet<Block>();
                    var unpreservedSlabs=new LinkedHashSet<Block>();
                    collect(transform.blockStateProvider().value(),input,lookup,blocks,unpreservedSlabs,false,0,budget);
                    if(blocks.isEmpty()) continue;
                    // COPPER_CHEST sets the clicked cell once. Native updateShape aligns the
                    // existing connected half; it is independently charged as a real cell.
                    // Only family-to-family changes preserve that inventory/state proof.
                    if(copperChest) {
                        if(blocks.stream().anyMatch(block -> !NativeMarketCopperChestRules.member(block))) return unknown();
                    } else if(nativeDoor) {
                        // The separate door envelope holds every native partner at zero credit.
                        // Only same-family SINGLE_BLOCK changes fit that two-cell proof; do not
                        // erase a half into a non-door or apply chest-specific partner behavior.
                        if(transform.transformType()!=BlockTransformer.TransformType.SINGLE_BLOCK
                                || blocks.stream().anyMatch(block -> !NativeMarketDoorRules.member(block))) return unknown();
                    } else if(transform.transformType()!=BlockTransformer.TransformType.SINGLE_BLOCK || multipart(input)) return unknown();
                    var drops=neutral();
                    if(transform.loot().isPresent()) {
                        var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(transform.loot().get());
                        if(table.isEmpty()) return unknown();
                        drops=NativeMarketDropRules.readInteraction(item,table.get().value(),lookup);
                        if(!drops.supported()) return unknown();
                    }
                    for(var block:blocks) {
                        // This reader intentionally erases block-state properties. A transform
                        // into a hive could inject honey_level=5, or preserve it via copied
                        // properties, and bypass empty placement. Hold until statefully proved.
                        if(block instanceof net.minecraft.world.level.block.BeehiveBlock
                                || block instanceof net.minecraft.world.level.block.CropBlock
                                || block instanceof net.minecraft.world.level.block.StemBlock
                                || block instanceof net.minecraft.world.level.block.AttachedStemBlock
                                || block instanceof net.minecraft.world.level.block.SlabBlock
                                    && (!NativeMarketSlabRules.member(input) || !NativeMarketSlabRules.member(block)
                                        || unpreservedSlabs.contains(block))
                                || block instanceof net.minecraft.world.level.block.ComposterBlock) return unknown();
                        if(block==Blocks.AIR) { append(rules,item,null,drops); continue; }
                        // A non-item intermediate needs a separate block-state closure; never invent an item ID.
                        boolean retainedCandleCake=candleCake && block==input;
                        if(block.asItem()==Items.AIR && !retainedCandleCake || multipart(block)
                                && !(copperChest && NativeMarketCopperChestRules.member(block))
                                && !(nativeDoor && NativeMarketDoorRules.member(block))) return unknown();
                        var result=new MarketAcquisitionBounds.Goods(retainedCandleCake?item:BuiltInRegistries.ITEM.getKey(block.asItem()),1);
                        append(rules,item,result,drops);
                    }
                }
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static boolean candleCakeRoot(Identifier item,Block input) {
        if(item==null || input==null || input.getClass()!=net.minecraft.world.level.block.CandleCakeBlock.class
                || !item.getNamespace().equals("minecraft")) return false;
        var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
        return source.getClass()==BlockItem.class && ((BlockItem)source).getBlock().getClass()==net.minecraft.world.level.block.CandleBlock.class
                && net.minecraft.world.level.block.CandleCakeBlock.byCandle((net.minecraft.world.level.block.CandleBlock)((BlockItem)source).getBlock()).getBlock()==input;
    }
    public static NativeMarketRecipeRules.Reading carving(Identifier item,HolderLookup.Provider lookup) {
        try {
            if(!knownRoot(item) || lookup==null) return unknown();
            if(BuiltInRegistries.ITEM.get(item).orElseThrow().value()!=Items.PUMPKIN) return neutral();
            var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(BuiltInLootTables.CARVE_PUMPKIN);
            if(table.isEmpty()) return unknown();
            var drops=NativeMarketDropRules.readInteraction(item,table.get().value(),lookup);
            if(!drops.supported()) return unknown();
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            append(rules,item,new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("carved_pumpkin"),1),drops);
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static void append(List<MarketAcquisitionBounds.RecipeConversion> rules,Identifier item,
            MarketAcquisitionBounds.Goods block,NativeMarketRecipeRules.Reading drops) {
        var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
        if(drops.rules().isEmpty()) { if(block!=null) rules.add(new MarketAcquisitionBounds.RecipeConversion(List.of(input),List.of(block))); }
        else for(var drop:drops.rules()) {
            var outputs=new ArrayList<>(drop.outputs()); if(block!=null) outputs.add(block);
            rules.add(new MarketAcquisitionBounds.RecipeConversion(List.of(input),outputs));
        }
        if(rules.size()>8192) throw new IllegalArgumentException("Too many block transforms");
    }
    private static void collect(BlockStateProvider provider,Block input,HolderLookup.Provider lookup,Set<Block> output,
            Set<Block> unpreservedSlabs,boolean copied,int depth,Budget budget) {
        budget.spend(depth);
        if(provider.getClass()==SimpleStateProvider.class) {
            var block=((SimpleStateProvider)provider).state().getBlock(); output.add(block);
            if(block instanceof net.minecraft.world.level.block.SlabBlock && !copied) unpreservedSlabs.add(block);
        }
        else if(provider.getClass()==CopyPropertiesProvider.class)
            collect(((CopyPropertiesProvider)provider).source().value(),input,lookup,output,unpreservedSlabs,true,depth+1,budget);
        else if(provider.getClass()==RuleBasedStateProvider.class) {
            var rules=(RuleBasedStateProvider)provider;
            if(rules.rules().size()>512) throw new IllegalArgumentException("Too many provider rules");
            // Do not execute predicates: keep every branch that could match any state/environment of this block.
            for(var rule:rules.rules()) {
                var condition=BlockPredicate.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),rule.ifTrue()).getOrThrow();
                if(matches(condition,input,lookup,depth+1,budget)!=Truth.NO)
                    collect(rule.then().value(),input,lookup,output,unpreservedSlabs,copied,depth+1,budget);
            }
            if(rules.fallback()!=null) collect(rules.fallback().value(),input,lookup,output,unpreservedSlabs,copied,depth+1,budget);
        } else throw new IllegalArgumentException("Unproven state provider");
        if(output.size()>256) throw new IllegalArgumentException("Too many resulting blocks");
    }
    private static Truth matches(JsonElement value,Block input,HolderLookup.Provider lookup,int depth,Budget budget) {
        budget.spend(depth); JsonObject predicate=value.getAsJsonObject();
        if(predicate.has("offset")) for(var axis:predicate.getAsJsonArray("offset")) if(axis.getAsInt()!=0) return Truth.MAYBE;
        var type=predicate.get("type").getAsString();
        if(type.equals("minecraft:matching_blocks")) return contains(predicate.get("blocks"),input,lookup);
        if(type.equals("minecraft:matching_block_tag")) return tag(predicate.get("tag").getAsString(),input,lookup);
        if(type.equals("minecraft:all_of") || type.equals("minecraft:any_of")) {
            boolean all=type.equals("minecraft:all_of"); var result=all?Truth.YES:Truth.NO;
            for(var child:predicate.getAsJsonArray("predicates")) {
                var found=matches(child,input,lookup,depth+1,budget);
                if(all && found==Truth.NO || !all && found==Truth.YES) return found;
                if(found==Truth.MAYBE) result=Truth.MAYBE;
            }
            return result;
        }
        if(type.equals("minecraft:not")) return switch(matches(predicate.get("predicate"),input,lookup,depth+1,budget)) {
            case YES -> Truth.NO; case NO -> Truth.YES; case MAYBE -> Truth.MAYBE;
        };
        return Truth.MAYBE;
    }
    private static Truth contains(JsonElement value,Block input,HolderLookup.Provider lookup) {
        if(value.isJsonArray()) {
            boolean uncertain=false;
            for(var child:value.getAsJsonArray()) { var found=contains(child,input,lookup); if(found==Truth.YES) return found; uncertain|=found==Truth.MAYBE; }
            return uncertain?Truth.MAYBE:Truth.NO;
        }
        var name=value.getAsString(); if(name.startsWith("#")) return tag(name.substring(1),input,lookup);
        var id=Identifier.tryParse(name); if(id==null || !BuiltInRegistries.BLOCK.containsKey(id)) return Truth.MAYBE;
        return BuiltInRegistries.BLOCK.get(id).orElseThrow().value()==input ? Truth.YES:Truth.NO;
    }
    private static Truth tag(String name,Block input,HolderLookup.Provider lookup) {
        var id=Identifier.tryParse(name); if(id==null) return Truth.MAYBE;
        var members=lookup.lookupOrThrow(Registries.BLOCK).get(TagKey.create(Registries.BLOCK,id));
        if(members.isEmpty()) return Truth.MAYBE;
        return members.get().stream().anyMatch(holder -> holder.value()==input)?Truth.YES:Truth.NO;
    }
    private static boolean knownRoot(Identifier item) {
        return NativeMarketProjectPlaques.registered(item) || item!=null && item.getNamespace().equals("minecraft")
                && !item.getPath().equals("air") && BuiltInRegistries.ITEM.containsKey(item);
    }
    private static boolean multipart(Block block) {
        return block instanceof DoorBlock || block instanceof AbstractBedBlock || block instanceof DoublePlantBlock || block instanceof CopperChestBlock;
    }
    private enum Truth { YES,NO,MAYBE }
    private static NativeMarketRecipeRules.Reading neutral() { return new NativeMarketRecipeRules.Reading(true,List.of()); }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    private static final class Budget { int remaining=8192; void spend(int depth) { if(depth>24 || --remaining<0) throw new IllegalArgumentException("Unbounded provider graph"); } }
}
