package de.quest.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperGolemStatueBlock;

/** One consumed statue cell, including its native remove-before-revive path; no entity/block entity is created. */
final class NativeMarketGolemStatueRules {
    static final List<String> NAMES=List.of("copper_golem_statue","exposed_copper_golem_statue","weathered_copper_golem_statue","oxidized_copper_golem_statue",
            "waxed_copper_golem_statue","waxed_exposed_copper_golem_statue","waxed_weathered_copper_golem_statue","waxed_oxidized_copper_golem_statue");
    private NativeMarketGolemStatueRules() {}
    static boolean member(Block block) {
        if(block==null || block.getClass()!=CopperGolemStatueBlock.class && block.getClass()!=WeatheringCopperGolemStatueBlock.class) return false;
        var id=BuiltInRegistries.BLOCK.getKey(block);
        return id!=null && id.getNamespace().equals("minecraft") && NAMES.contains(id.getPath());
    }
    static boolean safePlacement(Identifier item,BlockItemStateProperties state) {
        try {
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            return source.getClass()==BlockItem.class && member(((BlockItem)source).getBlock()) && state!=null
                    && state.properties().size()==1 && state.properties().containsKey("copper_golem_pose")
                    && CopperGolemStatueBlock.POSE.getValue(state.properties().get("copper_golem_pose")).isPresent();
        } catch(RuntimeException invalid) { return false; }
    }
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup) {
        try {
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(source.getClass()!=BlockItem.class || !member(((BlockItem)source).getBlock()) || lookup==null) return unknown();
            var family=NAMES.stream().map(name -> BuiltInRegistries.BLOCK.getValue(id(name))).collect(Collectors.toSet());
            var tag=lookup.lookupOrThrow(Registries.BLOCK).get(BlockTags.COPPER_GOLEM_STATUES).orElseThrow();
            if(family.size()!=8 || !tag.stream().limit(257).map(h -> h.value()).collect(Collectors.toSet()).equals(family)) return unknown();
            for(var block:family) {
                if(!member(block) || block.asItem()==Items.AIR || block.asItem().getClass()!=BlockItem.class) return unknown();
                for(var next:List.of(WeatheringCopper.getNext(block),WeatheringCopper.getPrevious(block)))
                    if(next.isPresent() && !family.contains(next.get())) return unknown();
                for(var map:List.of(HoneycombItem.WAXABLES.get(),HoneycombItem.WAX_OFF_BY_BLOCK.get()))
                    if(map.containsKey(block) && !family.contains(map.get(block))) return unknown();
            }
            var key=EntityTypes.COPPER_GOLEM.getDefaultLootTable().orElseThrow();
            var death=NativeMarketDropRules.readEntityDeath(item,lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value(),lookup);
            if(!death.supported()) return unknown();
            var rules=new ArrayList<>(death.rules());
            var input=List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1))));
            // Ignore wax/tools/time and cover every same-cell variant. Reviving removes the
            // original cell (removeBlock false: no ordinary loot) and creates one EMPTY golem.
            // It can die for current loot OR weather back to one statue, never a new chest.
            // createStatue/removeStatue retain only CUSTOM_NAME, not equipment or inventory.
            // Pose/facing/signal/water do not change item count; ordinary current loot and
            // current component transforms remain separately mandatory in the caller.
            for(var name:NAMES) rules.add(new MarketAcquisitionBounds.RecipeConversion(input,List.of(new MarketAcquisitionBounds.Goods(id(name),1))));
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
