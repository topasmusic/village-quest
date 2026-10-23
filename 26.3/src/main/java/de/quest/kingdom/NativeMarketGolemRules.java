package de.quest.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** Pinned carved-pumpkin callbacks; registry/loot data only, never creates an entity or invokes placement. */
final class NativeMarketGolemRules {
    private static final Set<String> COPPER=Set.of("copper_block","exposed_copper","weathered_copper","oxidized_copper",
            "waxed_copper_block","waxed_exposed_copper","waxed_weathered_copper","waxed_oxidized_copper");
    private NativeMarketGolemRules() {}
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup) {
        try {
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(source!=Items.CARVED_PUMPKIN && source!=Items.JACK_O_LANTERN || lookup==null) return unknown();
            var tag=lookup.lookupOrThrow(Registries.BLOCK).get(BlockTags.COPPER).orElseThrow();
            var copper=tag.stream().limit(257).map(holder -> holder.value()).collect(Collectors.toSet());
            var expected=COPPER.stream().map(name -> BuiltInRegistries.BLOCK.getValue(id(name))).collect(Collectors.toSet());
            if(expected.size()!=8 || !copper.equals(expected) || !NativeMarketCopperChestRules.boundedFamily(lookup)) return unknown();
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            var input=List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1))));
            for(var entity:List.of(EntityTypes.SNOW_GOLEM,EntityTypes.IRON_GOLEM,EntityTypes.COPPER_GOLEM)) {
                var key=entity.getDefaultLootTable().orElseThrow();
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value();
                var death=NativeMarketDropRules.readEntityDeath(item,table,lookup);
                if(!death.supported()) return unknown();
                var extra=new ArrayList<MarketAcquisitionBounds.Goods>();
                if(entity==EntityTypes.SNOW_GOLEM) {
                    // Shearing removes the head flag before CURRENT shearing loot. Ignore the
                    // two consumed snow blocks and tool cost; even retain the original pumpkin
                    // when a changed table no longer returns it. Death coproducts become zero.
                    extra.add(new MarketAcquisitionBounds.Goods(item,1));
                    var shear=NativeMarketShearingRules.read(item,lookup,List.of(BuiltInLootTables.SHEAR_SNOW_GOLEM));
                    if(!shear.supported()) return unknown();
                    rules.addAll(shear.rules());
                }
                if(entity==EntityTypes.COPPER_GOLEM) {
                    // Ignore the consumed copper block and weather/tool/wax costs. Overbound
                    // the one new chest by ALL eight family SKUs together. Connections only
                    // alter the existing neighbor, whose own closure covers its cell/inventory.
                    for(var name:NativeMarketCopperChestRules.NAMES) extra.add(new MarketAcquisitionBounds.Goods(id(name),1));
                    // Weathering can replace/discard the golem with one oxidized statue. Count
                    // it even alongside death loot; statue callbacks/loot must be proved by the
                    // ordinary reachable-item closure. Never silently omit this further path.
                    extra.add(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(
                            ((net.minecraft.world.level.block.Block)Blocks.COPPER_GOLEM_STATUE.weathering().oxidized()).asItem()),1));
                }
                if(death.rules().isEmpty()) {
                    if(!extra.isEmpty()) rules.add(new MarketAcquisitionBounds.RecipeConversion(input,extra));
                } else for(var outcome:death.rules()) {
                    var outputs=new ArrayList<>(outcome.outputs()); outputs.addAll(extra);
                    rules.add(new MarketAcquisitionBounds.RecipeConversion(input,outputs));
                }
            }
            // ClearPatternBlocks removes each consumed pattern cell without ordinary loot.
            // Snow trail and breeding/AI production require later ticks, not a placement payout.
            // Equipment/container imports remain held by the source builder. Healing and
            // antenna insertion consume external items; empty new golems have no such stock.
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
