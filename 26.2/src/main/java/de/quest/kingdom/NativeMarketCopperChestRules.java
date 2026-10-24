package de.quest.kingdom;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Exact native chest family; never executes placement, connection, weathering or inventory callbacks. */
final class NativeMarketCopperChestRules {
    static final List<String> NAMES=List.of("copper_chest","exposed_copper_chest","weathered_copper_chest","oxidized_copper_chest",
            "waxed_copper_chest","waxed_exposed_copper_chest","waxed_weathered_copper_chest","waxed_oxidized_copper_chest");
    private NativeMarketCopperChestRules() {}
    static boolean member(Block block) {
        if(block==null || block.getClass()!=CopperChestBlock.class && block.getClass()!=WeatheringCopperChestBlock.class) return false;
        var id=BuiltInRegistries.BLOCK.getKey(block);
        return id!=null && id.getNamespace().equals("minecraft") && NAMES.contains(id.getPath());
    }
    static boolean boundedFamily(HolderLookup.Provider lookup) {
        try {
            if(lookup==null) return false;
            var family=NAMES.stream().map(name -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name)))
                    .collect(Collectors.toSet());
            var tag=lookup.lookupOrThrow(Registries.BLOCK).get(BlockTags.COPPER_CHESTS);
            if(family.size()!=8 || tag.isEmpty() || !tag.get().stream().map(holder -> holder.value()).collect(Collectors.toSet()).equals(family)) return false;
            for(var block:family) {
                if(!member(block) || block.asItem()==Items.AIR
                        || block.defaultBlockState().getValue(ChestBlock.TYPE)!=ChestType.SINGLE
                        || block.defaultBlockState().getValue(ChestBlock.WATERLOGGED)) return false;
                // Connections choose an existing family's least-oxidized state and may unwax
                // both halves. Weathering/lightning and waxing must not escape that same family.
                for(var next:List.of(WeatheringCopper.getNext(block),WeatheringCopper.getPrevious(block)))
                    if(next.isPresent() && !family.contains(next.get())) return false;
                for(var map:List.of(HoneycombItem.WAXABLES.get(),HoneycombItem.WAX_OFF_BY_BLOCK.get()))
                    if(map.containsKey(block) && !family.contains(map.get(block))) return false;
            }
            return true;
        } catch(RuntimeException invalid) { return false; }
    }
    static List<MarketAcquisitionBounds.RecipeConversion> conversions(Identifier item) {
        // One consumed placement owns one cell. A connected half remains a separate paid/external
        // cell with its own 27-slot inventory. Preserve all eight possible cell SKUs conservatively,
        // even when reaching one needs time, another chest or wax. Never return two cells for one.
        return NAMES.stream().map(name -> new MarketAcquisitionBounds.RecipeConversion(
                List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)))),
                List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace(name),1)))).toList();
    }
}
