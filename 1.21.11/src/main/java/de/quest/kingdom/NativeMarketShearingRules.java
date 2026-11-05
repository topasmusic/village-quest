package de.quest.kingdom;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** Current shearing item identities only; no loot execution or finite lifetime assumed for the tool. */
final class NativeMarketShearingRules {
    private NativeMarketShearingRules() {}
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup) {
        return read(item,lookup,List.of(BuiltInLootTables.SHEAR_SHEEP,BuiltInLootTables.SHEAR_MOOSHROOM,
                BuiltInLootTables.SHEAR_SNOW_GOLEM,BuiltInLootTables.BOGGED_SHEAR));
    }
    /** Retain the charged source and overbound repeated access to these current shearing roots. */
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup,List<ResourceKey<LootTable>> roots) {
        try {
            var outputs=new LinkedHashSet<Identifier>(); var budget=new Budget();
            for(var root:roots)
                table(item,root,lookup,outputs,new HashSet<>(),0,budget);
            // Ignore tool wear and external animal availability. Returning the same tool makes
            // every possible shearing output zero-credit, also after arbitrary mixtures/repeats.
            // Only identities matter in this retained-tool proof: reference/parent count
            // multipliers cannot change zero credit. Unknown identity-changing functions hold.
            var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
            var rules=outputs.stream().map(output -> new MarketAcquisitionBounds.RecipeConversion(List.of(input),
                    List.of(new MarketAcquisitionBounds.Goods(item,1),new MarketAcquisitionBounds.Goods(output,1)))).toList();
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    }
    private static void table(Identifier item,ResourceKey<LootTable> key,HolderLookup.Provider lookup,
            Set<Identifier> outputs,Set<ResourceKey<LootTable>> path,int depth,Budget budget) {
        budget.spend(depth);
        if(!path.add(key)) throw new IllegalArgumentException("Cyclic shearing loot");
        try {
            var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value();
            if(table.getClass()!=LootTable.class) throw new IllegalArgumentException("Foreign shearing loot");
            if(table==LootTable.EMPTY) return;
            var data=NativeMarketLootData.normalize(LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow()).getAsJsonObject();
            if(!data.has("type") || !data.get("type").getAsString().equals("minecraft:shearing"))
                throw new IllegalArgumentException("Wrong shearing context");
            var bounded=data.deepCopy();
            if(bounded.has("pools")) for(var pool:bounded.getAsJsonArray("pools"))
                for(var entry:pool.getAsJsonObject().getAsJsonArray("entries"))
                    references(item,entry.getAsJsonObject(),lookup,outputs,path,depth+1,budget);
            bounded.addProperty("type","minecraft:block");
            var reading=NativeMarketDropRules.read(item,bounded);
            if(!reading.supported()) throw new IllegalArgumentException("Unproved shearing metadata");
            for(var rule:reading.rules()) for(var goods:rule.outputs()) outputs.add(goods.item());
            if(outputs.size()>256) throw new IllegalArgumentException("Too many shearing outputs");
        } finally { path.remove(key); }
    }
    private static void references(Identifier item,JsonObject entry,HolderLookup.Provider lookup,
            Set<Identifier> outputs,Set<ResourceKey<LootTable>> path,int depth,Budget budget) {
        budget.spend(depth);
        if(entry.get("type").getAsString().equals("minecraft:loot_table")) {
            if(entry.keySet().stream().anyMatch(k -> !Set.of("type","value","modifier","condition","weight","quality").contains(k))
                    || !entry.has("value") || !entry.get("value").isJsonPrimitive()
                    || !entry.get("value").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Unproved inline/reference metadata");
            var id=Identifier.parse(entry.get("value").getAsString());
            table(item,ResourceKey.create(Registries.LOOT_TABLE,id),lookup,outputs,path,depth+1,budget);
            // Validate parent/pool/root metadata with the strict ordinary reader even if this
            // reference emits nothing. Child identities were read from the current registry.
            entry.remove("value"); entry.addProperty("type","minecraft:empty");
        } else if(entry.has("children")) for(var child:entry.getAsJsonArray("children"))
            references(item,child.getAsJsonObject(),lookup,outputs,path,depth+1,budget);
    }
    private static final class Budget {
        int remaining=4096;
        void spend(int depth) {
            if(depth>16 || --remaining<0) throw new IllegalArgumentException("Unbounded shearing references");
        }
    }
}
