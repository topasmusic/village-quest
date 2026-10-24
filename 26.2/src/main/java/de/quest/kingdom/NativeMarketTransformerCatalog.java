package de.quest.kingdom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/** Snapshot the actual 26.2 tool tables; never execute useOn, predicates or world callbacks. */
public final class NativeMarketTransformerCatalog {
    private NativeMarketTransformerCatalog() {}
    public record Transform(Block input,Block output,List<MarketAcquisitionBounds.Goods> drops) {
        public Transform {
            if(input==null || output==null || drops==null || drops.size()>16) throw new IllegalArgumentException("Invalid native transform");
            drops=List.copyOf(drops);
        }
    }
    public record Reading(boolean supported,List<Transform> definitions) {
        public Reading {
            if(definitions==null || definitions.size()>2048 || !supported && !definitions.isEmpty()) throw new IllegalArgumentException("Partial transformer proof");
            definitions=List.copyOf(definitions);
        }
    }
    public static Reading read(HolderLookup.Provider lookup,Collection<Identifier> items,Function<Identifier,DataComponentMap> prototypes) {
        try {
            if(lookup==null || items==null || items.size()>4096 || prototypes==null) return unknown();
            for(var item:items) if(prototypes.apply(item)==null) return unknown();
            var definitions=new ArrayList<Transform>();
            for(var entry:table(AxeItem.class,"STRIPPABLES").entrySet())
                add(definitions,(Block)entry.getKey(),(Block)entry.getValue(),List.of());
            for(var entry:table(ShovelItem.class,"FLATTENABLES").entrySet())
                add(definitions,(Block)entry.getKey(),((BlockState)entry.getValue()).getBlock(),List.of());
            for(var entry:WeatheringCopper.PREVIOUS_BY_BLOCK.get().entrySet())
                add(definitions,entry.getKey(),entry.getValue(),List.of());
            for(var entry:HoneycombItem.WAX_OFF_BY_BLOCK.get().entrySet())
                add(definitions,entry.getKey(),entry.getValue(),List.of());
            // Inspect native hoe closure captures; do not run predicates or world actions.
            var plain=HoeItem.changeIntoState(Blocks.FARMLAND.defaultBlockState()).getClass();
            var dropping=HoeItem.changeIntoStateAndDropItem(Blocks.DIRT.defaultBlockState(),Items.HANGING_ROOTS).getClass();
            for(var entry:table(HoeItem.class,"TILLABLES").entrySet()) {
                var action=((com.mojang.datafixers.util.Pair<?,?>)entry.getValue()).getSecond();
                if(action==null || action.getClass()!=plain && action.getClass()!=dropping) return unknown();
                Block output=null; var drops=new ArrayList<MarketAcquisitionBounds.Goods>();
                for(var capture:action.getClass().getDeclaredFields()) {
                    if(java.lang.reflect.Modifier.isStatic(capture.getModifiers())) continue;
                    capture.setAccessible(true); var value=capture.get(action);
                    if(value instanceof BlockState state && output==null) output=state.getBlock();
                    else if(value instanceof net.minecraft.world.level.ItemLike item && action.getClass()==dropping)
                        drops.add(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(item.asItem()),1));
                    else return unknown();
                }
                if(output==null || action.getClass()==dropping && drops.size()!=1 || action.getClass()==plain && !drops.isEmpty()) return unknown();
                add(definitions,(Block)entry.getKey(),output,drops);
            }
            return new Reading(true,definitions);
        } catch(ReflectiveOperationException | RuntimeException unavailable) { return unknown(); }
    }
    private static Map<?,?> table(Class<?> owner,String name) throws ReflectiveOperationException {
        var field=owner.getDeclaredField(name); field.setAccessible(true);
        var value=field.get(null);
        if(!(value instanceof Map<?,?> map) || map.size()>512) throw new IllegalArgumentException("Unbounded tool table");
        return Map.copyOf(map);
    }
    private static void add(List<Transform> definitions,Block input,Block output,List<MarketAcquisitionBounds.Goods> drops) {
        if(definitions.size()>=2048 || BuiltInRegistries.BLOCK.getKey(input)==null || BuiltInRegistries.BLOCK.getKey(output)==null)
            throw new IllegalArgumentException("Unknown native transform");
        definitions.add(new Transform(input,output,drops));
    }
    private static Reading unknown() { return new Reading(false,List.of()); }
}
