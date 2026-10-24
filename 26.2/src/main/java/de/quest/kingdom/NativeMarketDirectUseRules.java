package de.quest.kingdom;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.InkSacItem;
import net.minecraft.world.item.GlowInkSacItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.Blocks;

/** Direct callbacks of the exact pinned base Item; subclasses and block transformations require their own proof. */
public final class NativeMarketDirectUseRules {
    private NativeMarketDirectUseRules() {}
    /** Additional callbacks only: prototype payload, consumption and ordinary block loot are proved by the builder. */
    public static NativeMarketRecipeRules.Reading read(Identifier item,DataComponentMap prototype,HolderLookup.Provider lookup) {
        var catalog=NativeMarketTransformerCatalog.read(lookup,BuiltInRegistries.ITEM.keySet(),
                id -> BuiltInRegistries.ITEM.get(id).orElseThrow().components());
        return read(item,prototype,lookup,catalog);
    }
    /** The production builder supplies its single complete current registry/item-definition snapshot. */
    public static NativeMarketRecipeRules.Reading read(Identifier item,DataComponentMap prototype,HolderLookup.Provider lookup,
            NativeMarketTransformerCatalog.Reading catalog) {
        if(catalog==null || !catalog.supported()) return unknown();
        var basic=read(item,prototype); if(basic.supported()) return basic;
        try {
            if(item==null || (!item.getNamespace().equals("minecraft") && !NativeMarketProjectPlaques.registered(item)) || lookup==null || prototype==null
                    || item.getPath().equals("air")) return unknown();
            var max=prototype.get(DataComponents.MAX_STACK_SIZE); if(max==null || max<1 || max>99) return unknown();
            var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
            boolean baseCallbacks=Set.of(Item.class,InkSacItem.class,GlowInkSacItem.class,
                    net.minecraft.world.item.DyeItem.class).contains(source.getClass());
            // 26.2 tool transformations are native static tables, captured by the catalog.
            if(Set.of(net.minecraft.world.item.AxeItem.class,net.minecraft.world.item.ShovelItem.class,
                    net.minecraft.world.item.HoeItem.class).contains(source.getClass()))
                return new NativeMarketRecipeRules.Reading(true,List.of());
            if(source==Items.BONE_MEAL && source.getClass()==net.minecraft.world.item.BoneMealItem.class) {
                var growth=NativeMarketBoneMealRules.read(item,lookup);
                if(!growth.supported()) return unknown();
                var rules=new ArrayList<>(growth.rules());
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if((source==Items.EGG || source==Items.BROWN_EGG || source==Items.BLUE_EGG)
                    && source.getClass()==net.minecraft.world.item.EggItem.class) {
                if(prototype.has(DataComponents.ENTITY_DATA)) return unknown();
                var key=net.minecraft.world.entity.EntityTypes.CHICKEN.getDefaultLootTable();
                if(key.isEmpty()) return unknown();
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key.get());
                if(table.isEmpty()) return unknown();
                var death=NativeMarketDropRules.readEntityDeath(item,table.get().value(),lookup);
                if(!death.supported()) return unknown();
                // EggItem consumes one egg; ThrownEgg creates at most four age -24000 chickens
                // and discards itself. Babies have no death loot, but feeding can mature them.
                // Charge FOUR complete current adult outcomes to that same single egg, ignoring
                // food costs. Egg laying still needs later aiStep eggTime ticks (initial 6000+).
                // Breeding consumes external food/another animal and needs subsequent AI work.
                // Never infer an empty adult table from the initial baby age or a missing key.
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                for(var rule:death.rules()) rules.add(single(item,rule.outputs().stream()
                        .map(goods -> new MarketAcquisitionBounds.Goods(goods.item(),Math.multiplyExact(goods.quantity(),4))).toList()));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.COMPASS && source.getClass()==net.minecraft.world.item.CompassItem.class
                    || source==Items.NAME_TAG && source.getClass()==net.minecraft.world.item.NameTagItem.class
                    || source==Items.WRITTEN_BOOK && source.getClass()==net.minecraft.world.item.WrittenBookItem.class
                    || source==Items.WRITABLE_BOOK && source.getClass()==net.minecraft.world.item.WritableBookItem.class) {
                // Lodestone binding consumes one compass before returning one bound compass.
                // Name tags consume a tag; book reading/editing only changes content. Signing
                // in ServerGamePacketListenerImpl replaces one writable book with one written
                // book; include that distinct SKU rather than treating the GUI as the whole path.
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                if(source==Items.WRITABLE_BOOK) rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(
                        BuiltInRegistries.ITEM.getKey(Items.WRITTEN_BOOK),1))));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if((source==Items.MINECART || source==Items.CHEST_MINECART || source==Items.HOPPER_MINECART)
                    && source.getClass()==net.minecraft.world.item.MinecartItem.class) {
                // MinecartItem shrinks one stack per placed vehicle. VehicleEntity.destroy kills
                // it before dropping one same-SKU cart (custom name only), not extra iron/storage.
                // Container carts start EMPTY without a loot key; slot removal and destruction
                // consume existing stacks. Hopper carts use the already proved HopperBlockEntity
                // transfer/entity-collection path. Imported entity/container/loot data stays held.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source==Items.MAP && source.getClass()==net.minecraft.world.item.EmptyMapItem.class
                    || source==Items.FILLED_MAP && source.getClass()==net.minecraft.world.item.MapItem.class) {
                // EmptyMapItem consumes one blank before creating one filled map; it returns that
                // single result in hand, inventory or as a drop. MapItem exploration/banner markers
                // alter saved data only. Crafted scaling/locking replaces MAP_ID on the same stack,
                // without adding items. Current recipes still charge every copying/material input.
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                if(source==Items.MAP) rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(
                        BuiltInRegistries.ITEM.getKey(Items.FILLED_MAP),1))));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.PAINTING && source.getClass()==net.minecraft.world.item.HangingEntityItem.class
                    || (source==Items.ITEM_FRAME || source==Items.GLOW_ITEM_FRAME)
                        && source.getClass()==net.minecraft.world.item.ItemFrameItem.class) {
                // HangingEntityItem consumes one item per placed entity. The removal path checks
                // isRemoved, kills it and returns one matching painting/frame SKU. Frame insertion
                // consumes one external displayed item; dropItem empties that stored slot before
                // returning it. The frame itself carries only its custom name. No reusable source
                // of displayed goods; imported ENTITY_DATA/CONTAINER remains held by the builder.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source==Items.LEAD && source.getClass()==net.minecraft.world.item.LeadItem.class) {
                // Entity.interact shrinks one lead when creating a new attachment; replacing an
                // old attachment returns its pre-existing external line. LeadItem fence binding
                // and secondary-use reparenting move existing leash connections only. Leashable
                // clears its stored LeashData before dropping the one recovered line. Recovered
                // delayed lines also clear their stored data; no retained-item production source.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source==Items.BUCKET && source.getClass()==net.minecraft.world.item.BucketItem.class) {
                if(!nativeBucketPickups(lookup)) return unknown();
                // Empty-bucket use replaces one consumed container with one native fluid bucket;
                // cow/goat milking likewise uses createFilledResult. No free duplicate container.
                // Waterlogged support loss removes its external block through ordinary current
                // loot, which is independently required for every purchased/reachable block.
                // Capturing a live animal consumes an external entity, not a purchased bucket's
                // item production. Filled-bucket placement/reactions need independent proofs.
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                for(var output:List.of("water_bucket","lava_bucket","powder_snow_bucket","milk_bucket"))
                    rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace(output),1))));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.BRUSH && source.getClass()==net.minecraft.world.item.BrushItem.class) {
                // Archaeology removes the external brushable block's stored loot and replaces
                // that block. Armadillo brushing is repeatable and uses the CURRENT loot table.
                // Conservatively retain the brush: ignoring wear/repair costs makes every such
                // output zero-credit, without inventing a consumed brush or a positive scute price.
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(
                        net.minecraft.world.level.storage.loot.BuiltInLootTables.ARMADILLO_BRUSH);
                if(table.isEmpty()) return unknown();
                var loot=NativeMarketDropRules.readEntityInteraction(item,table.get().value(),lookup);
                if(!loot.supported()) return unknown();
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                for(var rule:loot.rules()) {
                    var outputs=new ArrayList<>(rule.outputs());
                    outputs.add(new MarketAcquisitionBounds.Goods(item,1));
                    rules.add(new MarketAcquisitionBounds.RecipeConversion(rule.inputs(),outputs));
                }
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.SHEARS && source.getClass()==net.minecraft.world.item.ShearsItem.class) {
                var enchantments=prototype.get(DataComponents.ENCHANTMENTS);
                if(enchantments!=null && !enchantments.isEmpty()) return unknown();
                // mineBlock only wears the tool; current ordinary block loot is independently
                // charged to each consumed block. useOn stops head growth by changing AGE in
                // the same cell; prove the two virtual head methods without invoking callbacks.
                for(var holder:lookup.lookupOrThrow(Registries.BLOCK).listElements().toList()) {
                    var block=holder.value();
                    if(block instanceof net.minecraft.world.level.block.GrowingPlantHeadBlock) {
                        for(String method:List.of("isMaxAge","getMaxAgeState"))
                            if(block.getClass().getMethod(method,net.minecraft.world.level.block.state.BlockState.class)
                                    .getDeclaringClass()!=net.minecraft.world.level.block.GrowingPlantHeadBlock.class) return unknown();
                    }
                }
                var shear=NativeMarketShearingRules.read(item,lookup);
                if(!shear.supported()) return unknown();
                var rules=new ArrayList<>(shear.rules());
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if((source==Items.CARROT_ON_A_STICK || source==Items.WARPED_FUNGUS_ON_A_STICK)
                    && source.getClass()==net.minecraft.world.item.FoodOnAStickItem.class
                    || source==Items.FISHING_ROD && source.getClass()==net.minecraft.world.item.FishingRodItem.class) {
                var enchantments=prototype.get(DataComponents.ENCHANTMENTS);
                if(enchantments!=null && !enchantments.isEmpty()) return unknown();
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                if(source!=Items.FISHING_ROD) {
                    // FoodOnAStickItem uses hurtAndConvertOnBreak: the consumed steering tool is
                    // replaced with a rod. Ignore required riding/boosting/wear costs conservatively.
                    rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(Items.FISHING_ROD),1))));
                }
                // A plain FishingHook starts with zero bite/lure/hook timers. New fishing loot
                // requires server ticks in water through catchingFish and nibble>0 in retrieve;
                // pulling an existing hooked entity only moves that external entity. Retrieval
                // discards the hook. Timed fishing work is subsequent production, like crop growth,
                // not an immediate retained-rod conversion. Enchanted behavior is not covered.
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.BOW && source.getClass()==net.minecraft.world.item.BowItem.class) {
                var enchantments=prototype.get(DataComponents.ENCHANTMENTS);
                var charged=prototype.get(DataComponents.CHARGED_PROJECTILES);
                if(enchantments!=null && !enchantments.isEmpty() || charged!=null && !charged.isEmpty()) return unknown();
                // Pinned BowItem/ProjectileWeaponItem splits external ammunition. Additional or
                // free shots carry INTANGIBLE_PROJECTILE; AbstractArrow converts that marker to
                // CREATIVE_ONLY pickup, which adds nothing to a survival inventory. A paid arrow
                // returns only its copied ammunition stack and discards itself on pickup.
                // No reusable bow -> arrow source; ammunition classes need independent proofs.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source.getClass()==net.minecraft.world.item.SpyglassItem.class) {
                // Exact pinned use/finish/release only play sounds, award a stat and return the
                // original stack. No item duplication, conversion or entity placement callback.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source.getClass()==BundleItem.class) {
                // Pinned BundleItem transfers existing slots only: Mutable.tryInsert shrinks/splits
                // the incoming stack, removeOne removes its stored entry, and both dropContent
                // and onDestroyed clear the stored contents before exposing the removed items.
                // The builder separately requires BundleContents.EMPTY and rejects recipe payloads.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            // Exact ink/dye applicators update sign text/glow. DyeItem additionally consumes one
            // dye to recolor an existing alive, unshorn sheep, without an item drop. Subsequent
            // shearing consumes external animal wool; regrowth requires eating. It is not a
            // conversion of the purchased dye into endlessly reusable wool. Crafting recolors,
            // current component transformers and consumption/compost remain independently bounded.
            if(baseCallbacks) return new NativeMarketRecipeRules.Reading(true,
                    compostable(item)?List.of(compost(item)):List.of());
            if(source==Items.HONEYCOMB && source.getClass()==HoneycombItem.class) {
                // Native sign waxing only changes an existing sign flag. useOn consumes one comb
                // and replaces a block via the current native WAXABLES map; block-side readers
                // independently charge each replaced block, conservatively ignoring the comb cost.
                return new NativeMarketRecipeRules.Reading(true,
                        compostable(item)?List.of(compost(item)):List.of());
            }
            if(source==Items.GLASS_BOTTLE && source.getClass()==BottleItem.class) {
                // BottleItem.use immediately gathers water / dragon breath. Honey filling is
                // a different block callback: it consumes stored level-5 hive production and
                // resets it to 0. An empty purchased bottle cannot produce that resource.
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                for(String output:List.of("potion","dragon_breath")) rules.add(single(item,
                        List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace(output),1))));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            if(source==Items.POTION && source.getClass()==PotionItem.class) {
                var dirt=lookup.lookupOrThrow(Registries.BLOCK).get(BlockTags.CONVERTABLE_TO_MUD);
                if(dirt.isEmpty()) return unknown();
                var choices=new ArrayList<MarketAcquisitionBounds.Goods>();
                for(var holder:dirt.get().stream().limit(257).toList()) {
                    if(holder.value().asItem()==Items.AIR) return unknown();
                    choices.add(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(holder.value().asItem()),1));
                }
                if(choices.size()>256) return unknown();
                var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
                if(!choices.isEmpty()) rules.add(new MarketAcquisitionBounds.RecipeConversion(List.of(
                        new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1))),
                        new MarketAcquisitionBounds.InputChoice(choices)),List.of(
                        new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("glass_bottle"),1),
                        new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("mud"),1))));
                if(compostable(item)) rules.add(compost(item));
                return new NativeMarketRecipeRules.Reading(true,rules);
            }
            boolean nativeBanner=NativeMarketBannerRules.wall(source).isPresent();
            boolean nativeDecorativeWall=NativeMarketDecorativeWallRules.wall(source).isPresent();
            boolean nativeDoor=NativeMarketDoorRules.member(source);
            if(source.getClass()!=BlockItem.class && source.getClass()!=net.minecraft.world.item.BedItem.class && !nativeBanner && !nativeDecorativeWall && !nativeDoor) return unknown();
            var block=((BlockItem)source).getBlock();
            boolean copperChest=NativeMarketCopperChestRules.member(block);
            boolean golemStatue=NativeMarketGolemStatueRules.member(block);
            boolean golemPumpkin=(source==Items.CARVED_PUMPKIN || source==Items.JACK_O_LANTERN)
                    && block.getClass()==net.minecraft.world.level.block.CarvedPumpkinBlock.class;
            if(copperChest && !NativeMarketCopperChestRules.boundedFamily(lookup)) return unknown();
            boolean passiveWeathering=Set.of(
                    net.minecraft.world.level.block.WeatheringCopperFullBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperBarsBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperChainBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperGrateBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperBulbBlock.class,
                    net.minecraft.world.level.block.WeatheringLanternBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperSlabBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperStairBlock.class,
                    net.minecraft.world.level.block.WeatheringCopperTrapDoorBlock.class,
                    net.minecraft.world.level.block.WeatheringLightningRodBlock.class).contains(block.getClass());
            // CopperBulbBlock.checkAndFlip changes only POWERED/LIT on the same cell, plays a
            // sound and exposes a comparator signal. Its four states remain covered by unfiltered
            // current loot/transforms; the weathering subclass additionally uses the maps below.
            // Native rail subclasses alter shape/power only. Support loss uses the same ordinary
            // loot table and removes the rail; it does not add an independent item-producing callback.
            if(!Set.of(Block.class,RotatedPillarBlock.class,PumpkinBlock.class,FlowerBlock.class,
                    PoweredRailBlock.class,RailBlock.class,DetectorRailBlock.class,WoolCarpetBlock.class,
                    net.minecraft.world.level.block.HayBlock.class,net.minecraft.world.level.block.HoneyBlock.class,net.minecraft.world.level.block.PoweredBlock.class,
                    net.minecraft.world.level.block.TransparentBlock.class,net.minecraft.world.level.block.StainedGlassBlock.class,
                    net.minecraft.world.level.block.IronBarsBlock.class,net.minecraft.world.level.block.StainedGlassPaneBlock.class,
                    net.minecraft.world.level.block.ChainBlock.class,net.minecraft.world.level.block.LanternBlock.class,
                    net.minecraft.world.level.block.CopperBulbBlock.class,
                    net.minecraft.world.level.block.LadderBlock.class,net.minecraft.world.level.block.WaterloggedTransparentBlock.class,
                    net.minecraft.world.level.block.SlabBlock.class,
                    net.minecraft.world.level.block.StairBlock.class,net.minecraft.world.level.block.TrapDoorBlock.class,
                    net.minecraft.world.level.block.FenceGateBlock.class,net.minecraft.world.level.block.WallBlock.class,
                    net.minecraft.world.level.block.FenceBlock.class,net.minecraft.world.level.block.GrindstoneBlock.class,
                    net.minecraft.world.level.block.LecternBlock.class,net.minecraft.world.level.block.LightningRodBlock.class,
                    net.minecraft.world.level.block.RedStoneWireBlock.class,net.minecraft.world.level.block.SmithingTableBlock.class,
                    net.minecraft.world.level.block.TargetBlock.class,net.minecraft.world.level.block.TripWireBlock.class,
                    net.minecraft.world.level.block.WeightedPressurePlateBlock.class,
                    de.quest.content.block.WallPlaqueBlock.class).contains(block.getClass())
                    && !(block.getClass()==AnvilBlock.class && Set.of(Blocks.ANVIL,Blocks.CHIPPED_ANVIL,Blocks.DAMAGED_ANVIL).contains(block))
                    && !(block==Blocks.BARREL && block.getClass()==BarrelBlock.class)
                    && !(block.getClass()==net.minecraft.world.level.block.CampfireBlock.class
                        && (block==Blocks.CAMPFIRE || block==Blocks.SOUL_CAMPFIRE))
                    && !(block==Blocks.CHEST && block.getClass()==net.minecraft.world.level.block.ChestBlock.class)
                    && !copperChest
                    && !golemPumpkin
                    && !golemStatue
                    && !nativeDoor
                    && !passiveWeathering
                    && !nativeDecorativeWall
                    && !(block==Blocks.HOPPER && block.getClass()==net.minecraft.world.level.block.HopperBlock.class)
                    && !(block==Blocks.BELL && block.getClass()==BellBlock.class)
                    && !(block==Blocks.COMPOSTER && block.getClass()==net.minecraft.world.level.block.ComposterBlock.class
                        && block.defaultBlockState().getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL)==0)
                    // Native small mushrooms spread only in later random ticks. Giant growth
                    // requires the separate consumed-fertilizer callback; compost remains below.
                    && !(block.getClass()==net.minecraft.world.level.block.MushroomBlock.class
                        && (block==Blocks.BROWN_MUSHROOM || block==Blocks.RED_MUSHROOM))
                    && !((block==Blocks.WHEAT && block.getClass()==net.minecraft.world.level.block.CropBlock.class
                            || block==Blocks.CARROTS && block.getClass()==net.minecraft.world.level.block.CarrotBlock.class
                            || block==Blocks.POTATOES && block.getClass()==net.minecraft.world.level.block.PotatoBlock.class)
                        && block.defaultBlockState().getValue(net.minecraft.world.level.block.CropBlock.AGE)==0)
                    && !(block.getClass()==net.minecraft.world.level.block.StemBlock.class
                        && (block==Blocks.PUMPKIN_STEM || block==Blocks.MELON_STEM)
                        && block.defaultBlockState().getValue(net.minecraft.world.level.block.StemBlock.AGE)==0)
                    && !(nativeBanner && block.getClass()==BannerBlock.class)
                    && block.getClass()!=net.minecraft.world.level.block.CandleBlock.class
                    && !(block==Blocks.CHISELED_BOOKSHELF
                        && block.getClass()==net.minecraft.world.level.block.ChiseledBookShelfBlock.class)
                    && block.getClass()!=BedBlock.class
                    && !(block.getClass()==BeehiveBlock.class && (block==Blocks.BEEHIVE || block==Blocks.BEE_NEST))) return unknown();
            // The exact native barrel starts with 27 EMPTY slots and a null loot-table key. Opening
            // only opens ChestMenu; transfer/removal splits or consumes existing contents. Loot can
            // only be imported through stored block/entity/container data, all rejected by the source
            // builder. Ordinary block loot and current transformer outputs remain mandatory below.
            // Exact native campfires start with four EMPTY slots. placeFood consumes one external
            // ingredient; cookTick uses the current CAMPFIRE_COOKING recipe (already charged by
            // the complete recipe reader), emits its result and clears that slot. Missing recipes
            // only return the inserted stack. Cooling/lighting adds no goods; removal drops only
            // existing slots. CONTAINER/BLOCK_ENTITY_DATA imports remain rejected by the builder.
            // Exact chest/hopper inventories likewise begin EMPTY with no seeded loot. A double
            // chest joins two existing inventories, not two copies of either inventory. Hopper
            // extraction removes a source slot before insertion, restores failed transfers, and
            // item-entity collection replaces/discards the consumed entity. Merging shrinks the
            // incoming stack by exactly the amount added. No goods arise from the container alone.
            // The exact chiseled bookshelf starts with six EMPTY slots. addBook consumes one
            // external book with consumeAndReturn; removeItem empties that slot before returning
            // its existing stack. Hopper transfer uses that same container boundary. Occupancy
            // flags and redstone only report contents; imported container/block data stay held.
            // Both cells' current ordinary loot is jointly charged by the paired reader; current
            // transforms to or from either bed kind remain held rather than erasing a half.
            // Exact native BellBlock/BellBlockEntity only ring, update memories and apply glow;
            // they contain no inventory or item-production callback. Support loss uses normal loot.
            // Exact WoolCarpetBlock adds only its color. CarpetBlock.updateShape returns AIR on
            // support loss; Block.updateOrDestroy destroys that one block through ordinary loot.
            // No separate payout is added. Other carpet subclasses still need their own proof.
            // Exact hay only reduces fall damage; powered blocks only expose a signal; plain and
            // stained glass add rendering/light/color properties. Their ordinary current loot and
            // transforms remain mandatory; random-weathering and container subclasses stay held.
            // Exact HoneyBlock adds fall/sliding movement, sound, particles and an advancement;
            // neither it nor the pinned HalfTransparent base emits goods from an interaction.
            // Exact bars/panes/chains/lanterns/ladders/grates add shapes, connections and water
            // state only. Support loss/pickup destruction uses the same mandatory ordinary loot.
            // SimpleWaterloggedBlock drains existing external water to an independently consumed
            // bucket; it does not manufacture fluid from a purchased dry block. Imported placement
            // states remain held by the source builder. Exact weathering subclasses below only
            // replace this same cell; their current next/previous SKUs enter the full closure.
            // Exact SlabBlock adds geometry and merging of two separately consumed placements.
            // Mandatory NativeMarketSlabRules charges both items for DOUBLE loot and checks all
            // top/bottom/double/water states, also for the exact copper weathering subclass.
            // Only property-copying slab transforms preserve that one/two-item boundary.
            // Exact stairs/walls only change geometry and external water state. Exact trapdoors
            // and gates toggle open/powered/facing state, sound and game events. Wind-triggered
            // explosion callbacks also delegate normal destruction to the base block loot path.
            // Current loot and transforms below remain mandatory, including each weathering SKU.
            // The exact empty native composter has no output stock. Insertion consumes compost
            // material, extraction requires level8 and resets to0, including the one-slot hopper
            // output. The existing per-material compost conversion already overbounds its yield
            // at one bone meal per consumed item. Never add a retained-composter-only output.
            // Imported fill states and all state-erasing transforms into composters stay held.
            // These three exact crop identities start at age0. Random growth requires further
            // world time; ravager/support destruction delegates the ordinary current loot path.
            // Carrot/Potato only override clone seed and shape. Fertilizer is a separate consumed
            // item whose still-held callbacks must be proved independently; compost below keeps
            // it reachable. No mature yield is credited to a newly bought planting item. The
            // builder rejects imported ages and transforms into any crop remain held.
            // Exact pumpkin/melon stems likewise begin at age0. StemBlock.randomTick requires
            // brightness, a later random growth tick and age7 before producing fruit; fresh
            // support loss/destruction uses its current age0 loot, including any extra seeds.
            // BoneMeal requires a separately consumed external input and remains independently
            // held. No retained fresh-seed source is credited for later world-grown fruit.
            // Transforms into StemBlock/AttachedStemBlock are held rather than erasing maturity.
            var transformed=NativeMarketBlockTransformRules.read(item,catalog.definitions(),lookup);
            var carved=NativeMarketBlockTransformRules.carving(item,lookup);
            if(!transformed.supported() || !carved.supported()) return unknown();
            var rules=new ArrayList<>(transformed.rules()); rules.addAll(carved.rules());
            if(golemPumpkin) {
                var golems=NativeMarketGolemRules.read(item,lookup);
                if(!golems.supported()) return unknown();
                rules.addAll(golems.rules());
            }
            if(golemStatue) {
                var statue=NativeMarketGolemStatueRules.read(item,lookup);
                if(!statue.supported()) return unknown();
                rules.addAll(statue.rules());
            }
            if(nativeDoor) {
                var door=NativeMarketDoorRules.read(item,lookup);
                if(!door.supported()) return unknown();
                rules.addAll(door.rules());
            }
            // Exact copper chests reuse the proved EMPTY ChestBlockEntity inventory. Joining,
            // unwaxing and weathering replace each existing cell; they do not duplicate stock.
            // All resulting SKUs enter the ordinary current loot/recipe/callback closure too.
            if(copperChest) rules.addAll(NativeMarketCopperChestRules.conversions(item));
            if(passiveWeathering) {
                // Pinned subclasses only add randomTick -> ChangeOverTimeBlock, whose only
                // mutation is setBlockAndUpdate(next.withPropertiesOf(state)). Lightning/axe
                // reduction uses the inverse map; repeated previous edges also cover getFirst.
                // Ignore elapsed time and tool/wax costs conservatively, never the original cell.
                if(net.minecraft.world.level.block.WeatheringCopper.NEXT_BY_BLOCK.get().size()>256
                        || net.minecraft.world.level.block.WeatheringCopper.PREVIOUS_BY_BLOCK.get().size()>256) return unknown();
                for(var target:List.of(net.minecraft.world.level.block.WeatheringCopper.getNext(block),
                        net.minecraft.world.level.block.WeatheringCopper.getPrevious(block))) {
                    if(target.isEmpty()) continue;
                    var next=target.get();
                    if(next.getClass()!=block.getClass() || next.asItem()==Items.AIR
                            || next.asItem().getClass()!=BlockItem.class
                            || ((BlockItem)next.asItem()).getBlock()!=next) return unknown();
                    rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(next.asItem()),1))));
                }
            }
            if(block.getClass()==net.minecraft.world.level.block.CandleBlock.class) {
                // Pinned stack placement consumes each added candle. Extinguishing/lighting changes
                // only state/particles; ordinary bounded loot is still charged by the source builder.
                var cake=NativeMarketCandleRules.read(item,lookup,catalog);
                if(!cake.supported()) return unknown();
                rules.addAll(cake.rules());
            }
            if(nativeBanner) {
                var wall=NativeMarketBannerRules.read(item,source,lookup,catalog);
                if(!wall.supported()) return unknown();
                rules.addAll(wall.rules());
            }
            if(nativeDecorativeWall) {
                var wall=NativeMarketDecorativeWallRules.read(item,source,lookup,catalog);
                if(!wall.supported()) return unknown();
                rules.addAll(wall.rules());
            }
            if(block.getClass()==BeehiveBlock.class) {
                var harvest=NativeMarketBeehiveRules.read(item,lookup);
                if(!harvest.supported()) return unknown();
                rules.addAll(harvest.rules());
            }
            if(block.getClass()==AnvilBlock.class) {
                // Repair/rename consumes other item stacks without increasing their item counts.
                // Falling/menu wear replaces this one anvil with its next worn variant; the last
                // variant breaks without a returned block. Read the pure native state mapping only.
                var worn=AnvilBlock.damage(block.defaultBlockState());
                if(worn!=null) rules.add(single(item,List.of(new MarketAcquisitionBounds.Goods(
                        BuiltInRegistries.ITEM.getKey(worn.getBlock().asItem()),1))));
            }
            if(compostable(item)) rules.add(compost(item));
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException | ReflectiveOperationException unproved) { return unknown(); }
    }
    public static NativeMarketRecipeRules.Reading read(Identifier item,DataComponentMap prototype) {
        try {
            if(item==null || !item.getNamespace().equals("minecraft") || !BuiltInRegistries.ITEM.containsKey(item)
                    || item.getPath().equals("air") || prototype==null || !prototype.has(DataComponents.MAX_STACK_SIZE)
                    || BuiltInRegistries.ITEM.get(item).orElseThrow().value().getClass()!=Item.class) return unknown();
            // Item.use delegates to Consumable/equipment/blocking, useOn only to BlockTransformer, and
            // interactLivingEntity returns PASS. Consumable output is proved by its separate native reader.
            if(!compostable(item)) return new NativeMarketRecipeRules.Reading(true,List.of());
            // Native Composter.insertItem shrinks one unit; extractProduce always emits one BoneMeal
            // then empties the composter. Ignore required layers/chance as a conservative upper yield.
            // No resolution of live ResolvableInt/LootContext or execution of either callback is needed.
            return new NativeMarketRecipeRules.Reading(true,List.of(compost(item)));
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static boolean compostable(Identifier item) {
        var source=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
        return net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.getFloat(source)>0;
    }
    private static MarketAcquisitionBounds.RecipeConversion compost(Identifier item) {
        return single(item,List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("bone_meal"),1)));
    }
    /** Inspect declarations/data only; never invoke pickupBlock or create a world. */
    private static boolean nativeBucketPickups(HolderLookup.Provider lookup) {
        try {
            for(var holder:lookup.lookupOrThrow(Registries.BLOCK).listElements().toList()) {
                var block=holder.value();
                if(!(block instanceof net.minecraft.world.level.block.BucketPickup)) continue;
                var owner=block.getClass().getMethod("pickupBlock",net.minecraft.world.entity.LivingEntity.class,
                        net.minecraft.world.level.LevelAccessor.class,net.minecraft.core.BlockPos.class,
                        net.minecraft.world.level.block.state.BlockState.class).getDeclaringClass();
                if(owner==net.minecraft.world.level.block.SimpleWaterloggedBlock.class) continue;
                if(owner==net.minecraft.world.level.block.BarrierBlock.class && block==Blocks.BARRIER
                        && block.getClass()==net.minecraft.world.level.block.BarrierBlock.class) continue; // Survival returns EMPTY.
                if(owner==net.minecraft.world.level.block.BubbleColumnBlock.class && block==Blocks.BUBBLE_COLUMN
                        && block.getClass()==net.minecraft.world.level.block.BubbleColumnBlock.class) continue; // One WATER_BUCKET.
                if(owner==net.minecraft.world.level.block.PowderSnowBlock.class && block==Blocks.POWDER_SNOW
                        && block.getClass()==net.minecraft.world.level.block.PowderSnowBlock.class) continue;
                if(owner==net.minecraft.world.level.block.LiquidBlock.class
                        && block.getClass()==net.minecraft.world.level.block.LiquidBlock.class) {
                    var fluid=block.defaultBlockState().getFluidState().getType();
                    if(fluid==net.minecraft.world.level.material.Fluids.WATER || fluid==net.minecraft.world.level.material.Fluids.LAVA) continue;
                }
                return false;
            }
            return true;
        } catch(ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    private static MarketAcquisitionBounds.RecipeConversion single(Identifier item,List<MarketAcquisitionBounds.Goods> outputs) {
        var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
        return new MarketAcquisitionBounds.RecipeConversion(List.of(input),outputs);
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
