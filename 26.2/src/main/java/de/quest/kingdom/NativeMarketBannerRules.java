package de.quest.kingdom;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.storage.loot.LootTable;

/** Exact native standing/wall banner variants; no placement, block entity, menu or world execution. */
public final class NativeMarketBannerRules {
    private static final Field WALL=wallField();
    private NativeMarketBannerRules() {}
    /** Read the actual pinned field, not a guessed colour/name alias. Unavailable access stays unknown. */
    static Optional<Block> wall(Item source) {
        try {
            if(source==null || source.getClass()!=BannerItem.class || WALL==null) return Optional.empty();
            var standing=((BannerItem)source).getBlock();
            var wall=(Block)WALL.get(source);
            if(standing.getClass()!=BannerBlock.class || wall==null || wall.getClass()!=WallBannerBlock.class
                    || standing.asItem()!=source || wall.asItem()!=source
                    || ((BannerBlock)standing).getColor()!=((WallBannerBlock)wall).getColor()
                    || !BuiltInRegistries.BLOCK.getKey(standing).getNamespace().equals("minecraft")
                    || !BuiltInRegistries.BLOCK.getKey(wall).getNamespace().equals("minecraft")) return Optional.empty();
            return Optional.of(wall);
        } catch(IllegalAccessException | RuntimeException unavailable) { return Optional.empty(); }
    }
    /** Standing loot/transformers are already mandatory; add the actual wall variant's independent paths. */
    static NativeMarketRecipeRules.Reading read(Identifier item,Item source,HolderLookup.Provider lookup,
            NativeMarketTransformerCatalog.Reading catalog) {
        try {
            var wall=wall(source);
            if(wall.isEmpty() || lookup==null || catalog==null || !catalog.supported()) return unknown();
            var transforms=NativeMarketBlockTransformRules.readVariant(item,wall.get(),catalog.definitions(),lookup);
            if(!transforms.supported()) return unknown();
            var rules=new ArrayList<>(transforms.rules());
            var key=wall.get().getLootTable();
            if(key.isPresent()) {
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key.get());
                if(table.isEmpty()) return unknown();
                if(table.get().value()!=LootTable.EMPTY) {
                    var loot=NativeMarketDropRules.read(item,table.get().value(),lookup);
                    if(!loot.supported()) return unknown();
                    rules.addAll(loot.rules());
                }
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException unproved) { return unknown(); }
    }
    private static Field wallField() {
        try {
            var field=StandingAndWallBlockItem.class.getDeclaredField("wallBlock");
            return field.getType()==Block.class && field.trySetAccessible()?field:null;
        } catch(ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,java.util.List.of()); }
}
