package de.quest.kingdom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.core.HolderLookup;
import de.quest.kingdom.NativeMarketTransformerCatalog.Transform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** Native single-block transforms and carving: resulting block plus every interaction coproduct share one input. */
public final class NativeMarketBlockTransformRules {
    private NativeMarketBlockTransformRules() {}
    public static NativeMarketRecipeRules.Reading read(Identifier item,Collection<Transform> transformers,HolderLookup.Provider lookup) {
        try {
            if(!knownRoot(item) || lookup==null || transformers==null || transformers.size()>2048) return unknown();
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(!(source instanceof BlockItem blockItem)) return neutral();
            return readVariant(item,blockItem.getBlock(),transformers,lookup);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    /** Each proven placement variant consumes the same item; its block predicates can differ. */
    static NativeMarketRecipeRules.Reading readVariant(Identifier item,Block input,Collection<Transform> transformers,HolderLookup.Provider lookup) {
        try {
            boolean candleCake=candleCakeRoot(item,input);
            if(!knownRoot(item) || input==null || lookup==null || transformers==null || transformers.size()>2048
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
            for(var transform:List.copyOf(transformers)) {
                if(transform.input()!=input) continue;
                var block=transform.output();
                if(block instanceof net.minecraft.world.level.block.BeehiveBlock
                        || block instanceof net.minecraft.world.level.block.StemBlock
                        || block instanceof net.minecraft.world.level.block.AttachedStemBlock
                        || block instanceof net.minecraft.world.level.block.CropBlock
                        || block instanceof net.minecraft.world.level.block.ComposterBlock) return unknown();
                if(multipart(input) && !(copperChest && NativeMarketCopperChestRules.member(block))
                        && !(nativeDoor && NativeMarketDoorRules.member(block))) return unknown();
                if(multipart(block) && !(copperChest && NativeMarketCopperChestRules.member(block))
                        && !(nativeDoor && NativeMarketDoorRules.member(block))) return unknown();
                if(block instanceof net.minecraft.world.level.block.SlabBlock
                        && !(NativeMarketSlabRules.member(input) && NativeMarketSlabRules.member(block))) return unknown();
                // Native tool maps preserve shared slab/door/chest properties. Tilling cannot
                // introduce an inventory, harvested crop or stored block-entity payload.
                if(block.asItem()==Items.AIR) return unknown();
                var outputs=new ArrayList<>(transform.drops());
                outputs.add(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(block.asItem()),1));
                rules.add(new MarketAcquisitionBounds.RecipeConversion(List.of(new MarketAcquisitionBounds.InputChoice(
                        List.of(new MarketAcquisitionBounds.Goods(item,1)))),outputs));
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
    private static boolean knownRoot(Identifier item) {
        return NativeMarketProjectPlaques.registered(item) || item!=null && item.getNamespace().equals("minecraft")
                && !item.getPath().equals("air") && BuiltInRegistries.ITEM.containsKey(item);
    }
    private static boolean multipart(Block block) {
        return block instanceof DoorBlock || block instanceof BedBlock || block instanceof DoublePlantBlock || block instanceof CopperChestBlock;
    }
    private enum Truth { YES,NO,MAYBE }
    private static NativeMarketRecipeRules.Reading neutral() { return new NativeMarketRecipeRules.Reading(true,List.of()); }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    private static final class Budget { int remaining=8192; void spend(int depth) { if(depth>24 || --remaining<0) throw new IllegalArgumentException("Unbounded provider graph"); } }
}
