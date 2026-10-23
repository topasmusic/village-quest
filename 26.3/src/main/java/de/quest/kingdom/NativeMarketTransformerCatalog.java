package de.quest.kingdom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

/** Derived native transformer inventory, including inline definitions on every supplied current item prototype. */
public final class NativeMarketTransformerCatalog {
    private NativeMarketTransformerCatalog() {}
    public record Reading(boolean supported,List<BlockTransformer> definitions) {
        public Reading {
            if(definitions==null || definitions.size()>256 || !supported && !definitions.isEmpty()) throw new IllegalArgumentException("Partial transformer proof");
            definitions=List.copyOf(definitions);
        }
    }
    public static Reading read(HolderLookup.Provider lookup,Collection<Identifier> items,Function<Identifier,DataComponentMap> prototypes) {
        try {
            if(lookup==null || items==null || items.size()>4096 || prototypes==null) return unknown();
            var definitions=new ArrayList<BlockTransformer>(); var seen=new IdentityHashMap<BlockTransformer,Boolean>();
            var rows=lookup.lookupOrThrow(Registries.BLOCK_TRANSFORMER).listElements().limit(257).toList();
            // An exhausted row budget is unknown even if its prefix contains repeated value identities.
            if(rows.size()>256) return unknown();
            for(var holder:rows) add(holder.value(),definitions,seen);
            for(var item:List.copyOf(items)) {
                var prototype=prototypes.apply(item); if(prototype==null) return unknown();
                var transformer=prototype.get(DataComponents.BLOCK_TRANSFORMER);
                if(transformer!=null) add(transformer.value(),definitions,seen);
            }
            return new Reading(true,definitions);
        } catch(RuntimeException unavailable) { return unknown(); }
    }
    private static void add(BlockTransformer value,List<BlockTransformer> definitions,IdentityHashMap<BlockTransformer,Boolean> seen) {
        if(value==null) throw new IllegalArgumentException("Missing transformer");
        if(seen.put(value,Boolean.TRUE)==null) definitions.add(value);
        if(definitions.size()>256) throw new IllegalArgumentException("Too many transformers");
    }
    private static Reading unknown() { return new Reading(false,List.of()); }
}
