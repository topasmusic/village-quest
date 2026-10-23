package de.quest.kingdom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.storage.loot.LootTable;

/** Consumed candle -> its native cake variant; cake availability is ignored conservatively, never created. */
final class NativeMarketCandleRules {
    private NativeMarketCandleRules() {}
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup,NativeMarketTransformerCatalog.Reading catalog) {
        try {
            if(item==null || !item.getNamespace().equals("minecraft") || lookup==null || catalog==null || !catalog.supported()) return unknown();
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(source.getClass()!=BlockItem.class || ((BlockItem)source).getBlock().getClass()!=CandleBlock.class) return unknown();
            var candle=(CandleBlock)((BlockItem)source).getBlock();
            if(candle.asItem()!=source || !item.equals(BuiltInRegistries.BLOCK.getKey(candle))) return unknown();
            var cake=CandleCakeBlock.byCandle(candle).getBlock();
            if(cake.getClass()!=CandleCakeBlock.class || !BuiltInRegistries.BLOCK.getKey(cake).getNamespace().equals("minecraft")) return unknown();
            var transformed=NativeMarketBlockTransformRules.readVariant(item,cake,catalog.definitions(),lookup);
            if(!transformed.supported()) return unknown();
            var rules=new ArrayList<>(transformed.rules());
            var key=cake.getLootTable();
            if(key.isPresent()) {
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key.get());
                if(table.isEmpty()) return unknown();
                if(table.get().value()!=LootTable.EMPTY) {
                    // CandleCakeBlock.useWithoutItem eats one cake slice, then invokes this same loot.
                    // It removes the candle cake first; no second independent eating/normal-break payout.
                    var drops=NativeMarketDropRules.read(item,table.get().value(),lookup);
                    if(!drops.supported()) return unknown();
                    rules.addAll(drops.rules());
                }
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException unproved) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
