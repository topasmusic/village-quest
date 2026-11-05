package de.quest.kingdom;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperDoorBlock;

/** Native mixed-partner doors, conservatively zero-credit; no placement, neighbor update or destruction runs. */
final class NativeMarketDoorRules {
    private NativeMarketDoorRules() {}
    static boolean member(Item item) {
        return item!=null && item.getClass()==DoubleHighBlockItem.class
                && member(((DoubleHighBlockItem)item).getBlock());
    }
    static boolean member(Block block) {
        if(block==null || block.getClass()!=DoorBlock.class && block.getClass()!=WeatheringCopperDoorBlock.class) return false;
        var id=BuiltInRegistries.BLOCK.getKey(block);
        return id!=null && id.getNamespace().equals("minecraft") && block.asItem().getClass()==DoubleHighBlockItem.class
                && ((DoubleHighBlockItem)block.asItem()).getBlock()==block;
    }
    static Set<Block> family(HolderLookup.Provider lookup) {
        var family=new LinkedHashSet<Block>();
        for(var holder:lookup.lookupOrThrow(Registries.BLOCK).listElements().toList()) if(holder.value() instanceof DoorBlock) {
            if(!member(holder.value()) || family.size()>=64) throw new IllegalArgumentException("Unproved native door partner");
            family.add(holder.value());
        }
        // updateShape accepts ANY DoorBlock partner, not just the clicked SKU. A fixture or
        // missing registry must not omit an actual registered partner (including foreign code).
        var actual=new LinkedHashSet<Block>();
        for(var block:BuiltInRegistries.BLOCK) if(block instanceof DoorBlock) actual.add(block);
        if(family.isEmpty() || !family.equals(actual)) throw new IllegalArgumentException("Incomplete door family");
        for(var block:family) {
            for(var next:List.of(WeatheringCopper.getNext(block),WeatheringCopper.getPrevious(block)))
                if(next.isPresent() && !family.contains(next.get())) throw new IllegalArgumentException("Unproved door weathering");
            for(var map:List.of(HoneycombItem.WAXABLES.get(),HoneycombItem.WAX_OFF_BY_BLOCK.get()))
                if(map.containsKey(block) && !family.contains(map.get(block))) throw new IllegalArgumentException("Unproved door wax target");
        }
        return Set.copyOf(family);
    }
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup) {
        try {
            if(item==null || !member(BuiltInRegistries.ITEM.get(item).orElseThrow().value()) || lookup==null) return unknown();
            var input=List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1))));
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            for(var block:family(lookup)) {
                // DoubleHighBlockItem clears the replaceable upper cell; DoorBlock adds UPPER
                // after the consumed LOWER placement. Vertical updateShape can replace a cell
                // by another DoorBlock's state, with the original HALF. Ignore that external
                // partner's cost AND retain the charged origin as an upper envelope. This is
                // NOT a claim that placement duplicates an item. It forces ALL possible native
                // door SKUs to zero credit, hence covers two-cell/mixed-partner loot together
                // without treating either cell as an independently paid item. Current strict
                // ordinary loot and family-only transforms are still required for every SKU.
                rules.add(new MarketAcquisitionBounds.RecipeConversion(input,List.of(new MarketAcquisitionBounds.Goods(item,1),
                        new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(block.asItem()),1))));
            }
            // Toggle/redstone/wind only change properties/sounds/events. Survival/support
            // removal uses current ordinary loot. Weathering runs on LOWER; upper copies it.
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
