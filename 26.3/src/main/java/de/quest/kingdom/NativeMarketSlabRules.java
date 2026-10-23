package de.quest.kingdom;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/** Exact native slab placements: two consumed items fund one merged DOUBLE cell, never one item. */
final class NativeMarketSlabRules {
    private NativeMarketSlabRules() {}
    static boolean member(net.minecraft.world.level.block.Block block) {
        return block!=null && (block.getClass()==SlabBlock.class
                || block.getClass()==net.minecraft.world.level.block.WeatheringCopperSlabBlock.class);
    }
    static NativeMarketRecipeRules.Reading read(Identifier item,LootTable table,HolderLookup.Provider lookup) {
        if(table==null || table.getClass()!=LootTable.class || lookup==null) return unknown();
        try { return read(item,LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow()); }
        catch(RuntimeException invalid) { return unknown(); }
    }
    static NativeMarketRecipeRules.Reading read(Identifier item,JsonElement data) {
        try {
            var source=BuiltInRegistries.ITEM.getValue(item);
            if(source.getClass()!=BlockItem.class || !member(((BlockItem)source).getBlock())) return unknown();
            var block=((BlockItem)source).getBlock();
            var root=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            for(var type:SlabType.values()) for(boolean water:List.of(false,true)) {
                // Include even the conservative DOUBLE+water combination. Imported item states
                // remain rejected, and state-erasing transforms into slabs are held separately.
                var state=block.defaultBlockState().setValue(SlabBlock.TYPE,type).setValue(SlabBlock.WATERLOGGED,water);
                var reading=NativeMarketDropRules.readAtState(item,data,state);
                if(!reading.supported()) return unknown();
                for(var rule:reading.rules()) rules.add(new MarketAcquisitionBounds.RecipeConversion(
                        type==SlabType.DOUBLE?List.of(root,root):List.of(root),rule.outputs()));
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
