package de.quest.kingdom;

import de.quest.content.block.WallPlaqueBlock;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/** Closed six project decoration identities. Loot and all current block transforms remain separate proofs. */
final class NativeMarketProjectPlaques {
    private static final Set<String> NAMES=Set.of("apiary_charter_plaque","village_ledger_plaque","forge_charter_plaque",
            "market_charter_plaque","pasture_charter_plaque","watch_bell_reliquary");
    private NativeMarketProjectPlaques() {}
    static boolean id(Identifier id) { return id!=null && id.getNamespace().equals("village-quest") && NAMES.contains(id.getPath()); }
    private static boolean identity(Identifier id,Item source) {
        if(!id(id) || source==null || source.getClass()!=BlockItem.class) return false;
        var block=((BlockItem)source).getBlock();
        return block!=null && block.getClass()==WallPlaqueBlock.class && block.getLootTable()
                .filter(key -> key.identifier().equals(id.withPath(p -> "blocks/"+p))).isPresent();
    }
    static boolean registered(Identifier id) {
        if(!id(id) || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        var source=BuiltInRegistries.ITEM.get(id).orElseThrow().value();
        return identity(id,source) && id.equals(BuiltInRegistries.BLOCK.getKey(((BlockItem)source).getBlock()));
    }
    /** These exact classes have no finishUsingItem override; patched consumable/remainder needs a new proof. */
    static NativeMarketRecipeRules.Reading consumption(Identifier id,Item source,DataComponentMap prototype) {
        if(!identity(id,source) || prototype==null || !Integer.valueOf(1).equals(prototype.get(DataComponents.MAX_STACK_SIZE))
                || prototype.has(DataComponents.CONSUMABLE) || prototype.has(DataComponents.USE_REMAINDER))
            return new NativeMarketRecipeRules.Reading(false,List.of());
        return new NativeMarketRecipeRules.Reading(true,List.of());
    }
}
