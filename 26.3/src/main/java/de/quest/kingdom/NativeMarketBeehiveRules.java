package de.quest.kingdom;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** Validates the pinned hive production boundary without executing a harvest or entity callback. */
public final class NativeMarketBeehiveRules {
    private NativeMarketBeehiveRules() {}
    /** Only empty placement is covered: nonzero honey is stored production, not a plain purchased hive. */
    static boolean safePlacement(Identifier item,BlockItemStateProperties state) {
        try {
            if(item==null || state==null || !List.of(Identifier.withDefaultNamespace("beehive"),Identifier.withDefaultNamespace("bee_nest")).contains(item)) return false;
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            if(source.getClass()!=BlockItem.class) return false;
            var block=((BlockItem)source).getBlock();
            if(block.getClass()!=BeehiveBlock.class || block!=Blocks.BEEHIVE && block!=Blocks.BEE_NEST) return false;
            return state.properties().size()==1 && state.properties().containsKey("honey_level")
                    && "0".equals(state.properties().get("honey_level"));
        } catch(RuntimeException invalid) { return false; }
    }
    /** Caller proves the exact native block; the current interaction loot table is independently mandatory. */
    public static NativeMarketRecipeRules.Reading read(Identifier hive,HolderLookup.Provider lookup) {
        try {
            if(hive==null || !List.of(Identifier.withDefaultNamespace("beehive"),Identifier.withDefaultNamespace("bee_nest")).contains(hive)
                    || lookup==null) return unknown();
            var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(BuiltInLootTables.HARVEST_BEEHIVE);
            if(table.isEmpty()) return unknown();
            var harvest=NativeMarketDropRules.readInteraction(hive,table.get().value(),lookup);
            if(!harvest.supported()) return unknown();
            // Both pinned useItemOn paths require honey_level >= 5 and reset it to 0.
            // Default placement starts at 0 with no bees. Refilling requires external bee
            // production (or an earned, limited VQ tool action), so it is not a repeatable
            // purchase conversion. Do not pretend the retained hive was consumed. Ordinary
            // loot, current transforms, empty payloads and recipe patches are checked separately.
            // Still require the actual bounded harvest definition above; missing/foreign loot
            // must not silently inherit the production proof.
            return new NativeMarketRecipeRules.Reading(true,List.of());
        } catch(RuntimeException unproved) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
