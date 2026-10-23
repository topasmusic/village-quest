package de.quest.kingdom;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;

/** Fertilizer output evidence only; never runs a growth, placement, feature or loot callback. */
final class NativeMarketBoneMealRules {
    private NativeMarketBoneMealRules() {}
    static NativeMarketRecipeRules.Reading read(Identifier item,HolderLookup.Provider lookup) {
        try {
            if(!root(item) || lookup==null) return unknown();
            var outputs=new LinkedHashSet<Identifier>();
            water(item,lookup,outputs);
            var blocks=lookup.lookupOrThrow(Registries.BLOCK).listElements().limit(4097).toList();
            if(blocks.size()>4096) return unknown();
            for(var holder:blocks) {
                var block=holder.value();
                if(!(block instanceof BonemealableBlock)) continue;
                // These exact virtual implementations only change crop age or replace one
                // seagrass cell with two tall-seagrass halves. Feature/biome-driven plants
                // remain unproved: a positive water branch is never a full fertilizer proof.
                land(item,block,lookup,outputs);
            }
            return retained(item,outputs);
        } catch(ReflectiveOperationException | RuntimeException invalid) { return unknown(); }
    }
    static void land(Identifier item,Block block,HolderLookup.Provider lookup,Set<Identifier> outputs) {
        var type=block.getClass();
        if(Set.of(CropBlock.class,CarrotBlock.class,PotatoBlock.class,BeetrootBlock.class,
                CocoaBlock.class,TallFlowerBlock.class,FlowerBedBlock.class).contains(type)) {
            // Beetroot only slows the age increment; cocoa only increments age. Tall flowers
            // drop their own item directly. Flower beds add a segment OR emit their own item.
            // Include ordinary current loot as well as that direct same-item output.
            loot(item,block,lookup,outputs);
        } else if(block==Blocks.TORCHFLOWER_CROP && type==TorchflowerCropBlock.class) {
            loot(item,block,lookup,outputs);
            loot(item,Blocks.TORCHFLOWER,lookup,outputs); // getStateForAge(max) replaces the crop.
        } else if(block==Blocks.SWEET_BERRY_BUSH && type==SweetBerryBushBlock.class) {
            loot(item,block,lookup,outputs);
            var key=net.minecraft.world.level.storage.loot.BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH;
            var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value();
            var harvest=NativeMarketDropRules.readInteraction(item,table,lookup);
            if(!harvest.supported()) throw new IllegalArgumentException("Unproved berry interaction harvest");
            for(var rule:harvest.rules()) for(var goods:rule.outputs()) outputs.add(goods.item());
        } else if(block==Blocks.SEAGRASS && type==SeagrassBlock.class)
            loot(item,Blocks.TALL_SEAGRASS,lookup,outputs);
        else throw new IllegalArgumentException("Unproved fertilizer target: "+BuiltInRegistries.BLOCK.getKey(block));
    }
    /** Independent branch evidence; callers must still prove ALL BonemealableBlock targets. */
    static NativeMarketRecipeRules.Reading readWater(Identifier item,HolderLookup.Provider lookup) {
        try {
            if(!root(item) || lookup==null) return unknown();
            var outputs=new LinkedHashSet<Identifier>(); water(item,lookup,outputs);
            return retained(item,outputs);
        } catch(ReflectiveOperationException | RuntimeException invalid) { return unknown(); }
    }
    static void water(Identifier item,HolderLookup.Provider lookup,Set<Identifier> outputs)
            throws ReflectiveOperationException {
        var blocks=new LinkedHashSet<Block>(); blocks.add(Blocks.SEAGRASS); blocks.add(Blocks.TALL_SEAGRASS);
        var registry=lookup.lookupOrThrow(Registries.BLOCK);
        // The biome tag only restricts where these two block tags are sampled. Ignore that
        // restriction and read BOTH current sets; never substitute the shipped tag contents.
        for(var key:List.of(BlockTags.WALL_CORALS,BlockTags.UNDERWATER_BONEMEALS)) {
            var tag=registry.get(key).orElseThrow();
            var members=tag.stream().limit(257).toList();
            if(members.size()>256) throw new IllegalArgumentException("Unbounded underwater tag");
            for(var holder:members) blocks.add(holder.value());
        }
        for(var block:List.copyOf(blocks)) {
            var type=block.getClass();
            if(!Set.of(Block.class,SeagrassBlock.class,TallSeagrassBlock.class,CoralPlantBlock.class,
                    CoralFanBlock.class,CoralWallFanBlock.class,BaseCoralPlantBlock.class,BaseCoralPlantTypeBlock.class,
                    BaseCoralFanBlock.class,BaseCoralWallFanBlock.class).contains(type))
                throw new IllegalArgumentException("Unproved underwater block callback");
            var key=BuiltInRegistries.BLOCK.getKey(block);
            if(key==null || !key.getNamespace().equals("minecraft") || registry.get(ResourceKey.create(Registries.BLOCK,key)).orElseThrow().value()!=block)
                throw new IllegalArgumentException("Foreign underwater block identity");
            // Current native coral instances carry their actual dead counterpart. Include its
            // loot too, conservatively ignoring the water-removal and scheduled-tick costs.
            if(Set.of(CoralPlantBlock.class,CoralFanBlock.class,CoralWallFanBlock.class).contains(type)) {
                var field=type.getDeclaredField("deadBlock"); field.setAccessible(true);
                var dead=(Block)field.get(block);
                if(dead==null || !Set.of(BaseCoralPlantBlock.class,BaseCoralPlantTypeBlock.class,BaseCoralFanBlock.class,
                        BaseCoralWallFanBlock.class).contains(dead.getClass()))
                    throw new IllegalArgumentException("Unproved dead coral callback");
                var deadId=BuiltInRegistries.BLOCK.getKey(dead);
                if(deadId==null || !deadId.getNamespace().equals("minecraft") || registry.get(ResourceKey.create(Registries.BLOCK,deadId)).orElseThrow().value()!=dead)
                    throw new IllegalArgumentException("Missing dead coral identity");
                loot(item,dead,lookup,outputs);
            }
            loot(item,block,lookup,outputs);
        }
    }
    private static void loot(Identifier item,Block block,HolderLookup.Provider lookup,Set<Identifier> outputs) {
        var key=block.getLootTable().orElseThrow();
        var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value();
        var reading=new NativeMarketRecipeRules.Reading(true,List.of());
        if(table!=net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
            if(table.getClass()!=net.minecraft.world.level.storage.loot.LootTable.class)
                throw new IllegalArgumentException("Foreign fertilizer loot");
            var json=net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC
                    .encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table).getOrThrow();
            countOnlyBonuses(json,0,new int[]{4096});
            reading=NativeMarketDropRules.read(item,json);
        }
        if(!reading.supported()) throw new IllegalArgumentException("Unproved fertilizer harvest");
        if(block.asItem()!=Items.AIR) outputs.add(BuiltInRegistries.ITEM.getKey(block.asItem()));
        for(var rule:reading.rules()) for(var goods:rule.outputs()) outputs.add(goods.item());
        if(outputs.size()>1024) throw new IllegalArgumentException("Unbounded fertilizer identities");
    }
    static void countOnlyBonuses(JsonElement json,int depth,int[] budget) {
        if(depth>32 || --budget[0]<0) throw new IllegalArgumentException("Unbounded fertilizer loot tree");
        if(json.isJsonArray()) { for(var child:json.getAsJsonArray()) countOnlyBonuses(child,depth+1,budget); }
        else if(json.isJsonObject()) {
            var object=json.getAsJsonObject();
            if(object.has("type") && object.get("type").isJsonPrimitive()
                    && object.get("type").getAsString().equals("minecraft:apply_bonus")) {
                if(!Set.of("type","enchantment","formula","parameters","condition").containsAll(object.keySet())
                        || !object.has("enchantment") || Identifier.tryParse(object.get("enchantment").getAsString())==null
                        || !object.has("formula") || !Set.of("minecraft:ore_drops","minecraft:uniform_bonus_count",
                                "minecraft:binomial_with_bonus_count").contains(object.get("formula").getAsString()))
                    throw new IllegalArgumentException("Unproved count bonus");
                // These three native codec formulas alter stack COUNT only. All quantities are
                // irrelevant to the retained-source zero-credit envelope, but item-transforming
                // functions and every surrounding metadata node still go through the strict reader.
                var condition=object.get("condition");
                object.remove("enchantment"); object.remove("formula"); object.remove("parameters");
                object.addProperty("type","minecraft:set_count"); object.addProperty("count",1);
                if(condition!=null) object.add("condition",condition);
            }
            for(var entry:object.entrySet()) countOnlyBonuses(entry.getValue(),depth+1,budget);
        }
    }
    private static NativeMarketRecipeRules.Reading retained(Identifier item,Set<Identifier> outputs) {
        var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
        // Fertilizer really consumes one unit. Deliberately returning it in this PRICE envelope
        // ignores that cost and gives every observed output zero credit, regardless of output
        // quantity/repeated growth. This is no claim of an actual retained-fertilizer exploit.
        return new NativeMarketRecipeRules.Reading(true,outputs.stream().sorted().map(output ->
                new MarketAcquisitionBounds.RecipeConversion(List.of(input),List.of(
                        new MarketAcquisitionBounds.Goods(item,1),new MarketAcquisitionBounds.Goods(output,1)))).toList());
    }
    private static boolean root(Identifier item) {
        return item!=null && item.equals(BuiltInRegistries.ITEM.getKey(Items.BONE_MEAL))
                && Items.BONE_MEAL.getClass()==BoneMealItem.class;
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
