package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Current native component snapshots and pinned callback classes; never consumes or places anything. */
final class NativeMarketDirectUseRulesTest {
    private static Map<Identifier,DataComponentMap> prototypes;
    private static net.minecraft.core.HolderLookup.Provider lookup;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var initial=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(initial);
        lookup=NativeMarketTestData.lookup();
        var maps=new HashMap<Identifier,DataComponentMap>();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)) if(pending.key().equals(Registries.ITEM))
            pending.forEach((holder,components) -> maps.put(holder.key().identifier(),components));
        prototypes=Map.copyOf(maps);
    }
    @Test void freshNativeCropsKeepCompostButDoNotInventImmediateMatureHarvest() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("wheat_seeds","carrot","potato","pumpkin_seeds","melon_seeds")) {
            var crop=id(name);
            var reading=NativeMarketDirectUseRules.read(crop,prototypes.get(crop),lookup,catalog);
            assertTrue(reading.supported(),name);
            assertEquals(List.of(new MarketAcquisitionBounds.RecipeConversion(
                    List.of(new MarketAcquisitionBounds.InputChoice(List.of(g(name,1)))),List.of(g("bone_meal",1)))),reading.rules(),
                    "Planting begins at age0; compost consumes the plant item and remains a real immediate input");
        }
        for(String name:List.of("bone_meal","beetroot_seeds","torchflower_seeds"))
            assertFalse(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog).supported(),
                    name+" needs its own production/loot proof; no subclass or fertilizer approval");
    }
    @Test void exactPassiveCopperWeatheringKeepsEachCurrentCellAndItsTransformsCoupled() {
        var classes=java.util.Set.of(
                net.minecraft.world.level.block.WeatheringCopperFullBlock.class,
                net.minecraft.world.level.block.WeatheringCopperBarsBlock.class,
                net.minecraft.world.level.block.WeatheringCopperChainBlock.class,
                net.minecraft.world.level.block.WeatheringCopperGrateBlock.class,
                net.minecraft.world.level.block.WeatheringCopperBulbBlock.class,
                net.minecraft.world.level.block.WeatheringLanternBlock.class,
                net.minecraft.world.level.block.WeatheringCopperSlabBlock.class,
                net.minecraft.world.level.block.WeatheringCopperStairBlock.class,
                net.minecraft.world.level.block.WeatheringCopperTrapDoorBlock.class,
                net.minecraft.world.level.block.WeatheringLightningRodBlock.class);
        int checked=0;
        var changed=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        for(var block:BuiltInRegistries.BLOCK) if(classes.contains(block.getClass())) {
            checked++;
            var item=BuiltInRegistries.ITEM.getKey(block.asItem());
            var reading=NativeMarketDirectUseRules.read(item,prototypes.get(item),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,changed));
            assertTrue(reading.supported(),item.toString());
            assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().equals(List.of(g("diamond_block",1)))),
                    "Current component transforms remain mandatory for "+item);
            for(var next:List.of(net.minecraft.world.level.block.WeatheringCopper.getNext(block),
                    net.minecraft.world.level.block.WeatheringCopper.getPrevious(block))) if(next.isPresent()) {
                var target=BuiltInRegistries.ITEM.getKey(next.get().asItem());
                assertTrue(reading.rules().stream().anyMatch(rule -> rule.inputs().getFirst().choices()
                        .equals(List.of(new MarketAcquisitionBounds.Goods(item,1)))
                        && rule.outputs().equals(List.of(new MarketAcquisitionBounds.Goods(target,1)))),item+" -> "+target);
            }
            assertTrue(reading.rules().stream().allMatch(rule -> rule.outputs().stream().allMatch(goods -> goods.quantity()==1)));
        }
        assertEquals(48,checked,"Twelve copper families, each with four oxidation states; slab quantities checked separately");
        for(String name:List.of("copper_golem_statue"))
            assertTrue(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of())).supported(),"Independent subclass: "+name);
    }
    @Test void cropCallbacksStillCarryChangedCurrentTransformsAndHoldMatureStateInjection() {
        var diamond=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var grown=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.CARROTS);
        for(String name:List.of("wheat_seeds","carrot","potato")) {
            var changed=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,diamond));
            assertTrue(changed.supported(),name);
            assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),name);
            assertFalse(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,grown)).supported(),
                    "State-erasing transform reading must never turn an age7 crop into a fresh age0 input");
        }
    }
    @Test void smallMushroomsKeepCurrentTransformsAndConsumedCompostInsteadOfFreeGiantGrowth() {
        var diamond=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        for(String name:List.of("brown_mushroom","red_mushroom")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,diamond));
            assertTrue(reading.supported(),name);
            assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().contains(g("bone_meal",1))),
                    "Consumed compost must keep the independent fertilizer boundary reachable");
            assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),
                    "Current transformer output remains mandatory for the consumed mushroom cell");
        }
    }
    @Test void emptyBucketTransfersOneExistingContainerIntoEachNativeFluidOrMilkResult() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        var reading=NativeMarketDirectUseRules.read(id("bucket"),prototypes.get(id("bucket")),lookup,catalog);
        assertTrue(reading.supported(),() -> lookup.lookupOrThrow(Registries.BLOCK).listElements()
                .filter(h -> h.value() instanceof net.minecraft.world.level.block.BucketPickup).map(h -> {
                    try { return h.key()+" -> "+h.value().getClass().getMethod("pickupBlock",net.minecraft.world.entity.LivingEntity.class,
                        net.minecraft.world.level.LevelAccessor.class,net.minecraft.core.BlockPos.class,net.minecraft.world.level.block.state.BlockState.class).getDeclaringClass().getSimpleName(); }
                    catch(Exception invalid) { return invalid.toString(); }
                }).distinct().toList().toString());
        assertEquals(java.util.Set.of("water_bucket","lava_bucket","powder_snow_bucket","milk_bucket"),
                reading.rules().stream().flatMap(r -> r.outputs().stream()).map(g -> g.item().getPath()).collect(java.util.stream.Collectors.toSet()));
        for(var rule:reading.rules()) {
            assertEquals(List.of(new MarketAcquisitionBounds.InputChoice(List.of(g("bucket",1)))),rule.inputs());
            assertEquals(1,rule.outputs().getFirst().quantity());
        }
        assertFalse(NativeMarketDirectUseRules.read(id("water_bucket"),prototypes.get(id("water_bucket")),lookup,catalog).supported(),
                "Fluid placement/reactions and external animal capture remain separate proofs");
        assertFalse(net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.containsKey(net.minecraft.world.item.Items.BUCKET),
                "26.2 compost comes from the native item table, not an injectable component");
    }
    @Test void foreignCurrentBucketPickupCannotInheritTheFourNativeContainerOutputs() {
        var foreign=org.mockito.Mockito.mock(net.minecraft.world.level.block.Block.class,
                org.mockito.Mockito.withSettings().extraInterfaces(net.minecraft.world.level.block.BucketPickup.class));
        var blocks=new net.minecraft.core.MappedRegistry<net.minecraft.world.level.block.Block>(Registries.BLOCK,
                com.mojang.serialization.Lifecycle.stable());
        blocks.register(net.minecraft.resources.ResourceKey.create(Registries.BLOCK,id("unknown_pickup")),foreign,
                net.minecraft.core.RegistrationInfo.BUILT_IN); blocks.freeze();
        var changed=net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.concat(
                lookup.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),java.util.stream.Stream.of(blocks)));
        assertFalse(NativeMarketDirectUseRules.read(id("bucket"),prototypes.get(id("bucket")),changed,
                new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
        org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).defaultBlockState();
    }
    @Test void currentNonProducingBlockCallbacksRetainOrdinaryLootAndTransformRequirements() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("oak_fence","grindstone","lectern","waxed_lightning_rod","redstone","smithing_table","target","string","heavy_weighted_pressure_plate")) {
            assertTrue(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog).supported(),name);
            var diamond=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
            var changed=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,diamond));
            assertTrue(changed.supported(),name);
            assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),name+": keep current transforms");
        }
        for(String name:List.of("compass","name_tag","written_book")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
        }
        var book=NativeMarketDirectUseRules.read(id("writable_book"),prototypes.get(id("writable_book")),lookup,catalog);
        assertTrue(book.supported()); assertTrue(book.rules().stream().anyMatch(r -> r.outputs().contains(g("written_book",1))),
                "Signing replaces a writable book one-for-one; the server packet path is part of the source bound");
        for(String unknown:List.of("crossbow","armor_stand"))
            assertFalse(NativeMarketDirectUseRules.read(id(unknown),prototypes.get(id(unknown)),lookup,catalog).supported(),unknown);
    }
    @Test void unavailableFutureItemsCannotAcquireATargetProof() {
        for(String name:List.of("straw_bed","black_cushion","poplar_sign","black_wool_stairs")) {
            assertFalse(BuiltInRegistries.ITEM.containsKey(id(name)),name);
            assertFalse(NativeMarketDirectUseRules.read(id(name),DataComponentMap.EMPTY,lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
        }
    }
    @Test void exactStorageMinecartsRecoverOneConsumedVehicleAndOnlyMoveExistingCargo() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("minecart","chest_minecart","hopper_minecart")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),
                    "Vehicle destruction kills before returning one matching cart; empty cargo is not a goods source");
        }
        for(String name:List.of("tnt_minecart","furnace_minecart","command_block_minecart"))
            assertFalse(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog).supported(),
                    "Exact storage/rideable proof must not approve other cart behavior: "+name);
    }
    @Test void emptyMapConsumesOneItemAndFilledMapOnlyUpdatesItsExistingMetadata() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        var empty=NativeMarketDirectUseRules.read(id("map"),prototypes.get(id("map")),lookup,catalog);
        assertTrue(empty.supported());
        assertEquals(List.of(new MarketAcquisitionBounds.RecipeConversion(
                List.of(new MarketAcquisitionBounds.InputChoice(List.of(g("map",1)))),List.of(g("filled_map",1)))),empty.rules());
        var filled=NativeMarketDirectUseRules.read(id("filled_map"),prototypes.get(id("filled_map")),lookup,catalog);
        assertTrue(filled.supported()); assertTrue(filled.rules().isEmpty(),
                "Exploration, banners, scaling and locking update map data without creating an extra item");
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("map",8)))),List.of(),empty.rules());
        assertTrue(bounds.complete()); assertTrue(bounds.maximumPayout(id("filled_map"),8)<=12);
        assertFalse(NativeMarketDirectUseRules.read(id("crossbow"),prototypes.get(id("crossbow")),lookup,catalog).supported());
    }
    @Test void exactChiseledBookshelfTransfersConsumedBooksAndKeepsCurrentTransforms() {
        var shelf=id("chiseled_bookshelf");
        var empty=NativeMarketDirectUseRules.read(shelf,prototypes.get(shelf),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
        assertTrue(empty.supported()); assertTrue(empty.rules().isEmpty(),
                "Six initially empty slots cannot manufacture books; removal clears the stored slot first");
        var diamond=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var changed=NativeMarketDirectUseRules.read(shelf,prototypes.get(shelf),lookup,
                new NativeMarketTransformerCatalog.Reading(true,diamond));
        assertTrue(changed.supported());
        assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),
                "The storage proof must retain current component transformer outputs");
        for(String other:List.of("cauldron"))
            assertFalse(NativeMarketDirectUseRules.read(id(other),prototypes.get(id(other)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of())).supported(),other);
    }
    @Test void nativeHangingDisplaysRecoverOnlyTheirConsumedFrameAndExistingExternalDisplay() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("painting","item_frame","glow_item_frame")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),
                    "One consumed display recovers its same SKU; inserted contents are separately consumed external stock");
        }
    }
    @Test void exactFloorAndWallTorchesAndDecorativeHeadsRequireBothCurrentPlacementVariants() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("torch","soul_torch","copper_torch","redstone_torch",
                "skeleton_skull","zombie_head","creeper_head","dragon_head","piglin_head","player_head")) {
            var item=id(name);
            assertTrue(NativeMarketDirectUseRules.read(item,prototypes.get(item),lookup,catalog).supported(),
                    "The same consumed decorative item must prove its floor and wall callbacks/loot: "+name);
        }
        for(String other:List.of("wither_skeleton_skull"))
            assertTrue(NativeMarketDirectUseRules.read(id(other),prototypes.get(id(other)),lookup,catalog).supported(),
                    "Separate spawn/multipart callbacks cannot inherit the decorative proof: "+other);
    }
    @Test void plainNativeSignsStartWithBlankTextAndProveBothWoodMatchedPlacements() {
        for(boolean filtered:List.of(false,true))
            for(var line:new net.minecraft.world.level.block.entity.SignText().getMessages(filtered)) {
                assertTrue(line.getString().isEmpty()); assertNull(line.getStyle().getClickEvent());
            }
        for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","bamboo","crimson","warped")) {
            var item=id(wood+"_sign");
            assertTrue(NativeMarketDirectUseRules.read(item,prototypes.get(item),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of())).supported(),wood);
        }
        var sign=id("oak_sign");
        var wall=NativeMarketDecorativeWallRules.wall(net.minecraft.world.item.Items.OAK_SIGN).orElseThrow();
        var transform=List.of(new NativeMarketTransformerCatalog.Transform(wall,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK,List.of()));
        assertTrue(NativeMarketBlockTransformRules.read(sign,transform,lookup).rules().isEmpty());
        var changed=NativeMarketDirectUseRules.read(sign,prototypes.get(sign),lookup,new NativeMarketTransformerCatalog.Reading(true,transform));
        assertTrue(changed.supported()); assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))));
    }
    @Test void hangingSignsKeepActualCeilingWallIdentitiesAndIndependentCurrentWallTransforms() {
        int count=0;
        for(var nativeItem:BuiltInRegistries.ITEM) if(nativeItem.getClass()==net.minecraft.world.item.HangingSignItem.class) {
            count++;
            var item=BuiltInRegistries.ITEM.getKey(nativeItem);
            var wall=NativeMarketDecorativeWallRules.wall(nativeItem).orElseThrow();
            assertEquals(net.minecraft.world.level.block.WallHangingSignBlock.class,wall.getClass());
            var reading=NativeMarketDirectUseRules.read(item,prototypes.get(item),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of()));
            assertTrue(reading.supported(),item.toString());
            assertTrue(reading.rules().stream().allMatch(rule -> rule.outputs().equals(
                    List.of(new MarketAcquisitionBounds.Goods(item,1)))),"Current wall loot returns only the consumed sign");
            var transform=List.of(new NativeMarketTransformerCatalog.Transform(wall,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK,List.of()));
            assertTrue(NativeMarketBlockTransformRules.read(item,transform,lookup).rules().isEmpty());
            var changed=NativeMarketDirectUseRules.read(item,prototypes.get(item),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,transform));
            assertTrue(changed.supported());
            assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))));
        }
        assertTrue(count>=12,"Every registered hanging-sign wood, including additions in the pinned line, must be checked");
    }
    @Test void aWallOnlyTransformerCannotDisappearBehindTheFloorPlacementProof() {
        var torch=id("torch");
        var wall=NativeMarketDecorativeWallRules.wall(net.minecraft.world.item.Items.TORCH).orElseThrow();
        var changed=List.of(new NativeMarketTransformerCatalog.Transform(wall,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK,List.of()));
        var floor=NativeMarketBlockTransformRules.read(torch,changed,lookup);
        assertTrue(floor.supported()); assertTrue(floor.rules().isEmpty(),"This changed rule matches only the actual wall block");
        var combined=NativeMarketDirectUseRules.read(torch,prototypes.get(torch),lookup,
                new NativeMarketTransformerCatalog.Reading(true,changed));
        assertTrue(combined.supported());
        assertTrue(combined.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("torch",1)))),List.of(),combined.rules());
        assertTrue(bounds.complete()); assertTrue(bounds.maximumPayout(id("diamond_block"),1)<=12);
        var missing=new net.minecraft.core.MappedRegistry<net.minecraft.world.level.storage.loot.LootTable>(Registries.LOOT_TABLE,
                com.mojang.serialization.Lifecycle.stable()); missing.freeze();
        var incomplete=net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.concat(lookup.listRegistries()
                .filter(registry -> !registry.key().equals(Registries.LOOT_TABLE)),java.util.stream.Stream.of(missing)));
        assertFalse(NativeMarketDirectUseRules.read(torch,prototypes.get(torch),incomplete,
                new NativeMarketTransformerCatalog.Reading(true,List.of())).supported(),"Missing wall loot is never EMPTY");
    }
    @Test void leadAttachmentConsumesOneLineAndReparentingDoesNotManufactureAdditionalLines() {
        var reading=NativeMarketDirectUseRules.read(id("lead"),prototypes.get(id("lead")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty(),
                "Recovery replaces an already consumed line; existing world leashes are external stock");
    }
    @Test void plainBowConsumesExternalAmmoWithoutCreatingAReusableArrowSource() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        var bow=NativeMarketDirectUseRules.read(id("bow"),prototypes.get(id("bow")),lookup,catalog);
        assertTrue(bow.supported()); assertTrue(bow.rules().isEmpty());
        for(String other:List.of("crossbow","arrow","spectral_arrow","tipped_arrow"))
            assertFalse(NativeMarketDirectUseRules.read(id(other),prototypes.get(id(other)),lookup,catalog).supported(),other);
        var enchanted=new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchanted.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY),1);
        var changed=DataComponentMap.builder().addAll(prototypes.get(id("bow"))).set(DataComponents.ENCHANTMENTS,enchanted.toImmutable()).build();
        assertFalse(NativeMarketDirectUseRules.read(id("bow"),changed,lookup,catalog).supported());
        var charged=DataComponentMap.builder().addAll(prototypes.get(id("bow"))).set(DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW)))).build();
        assertFalse(NativeMarketDirectUseRules.read(id("bow"),charged,lookup,catalog).supported());
    }
    @Test void foodSteeringToolsChargeTheirOneReturnedRodAndFishingRequiresSubsequentWorldWork() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("carrot_on_a_stick","warped_fungus_on_a_stick")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog);
            assertTrue(reading.supported(),name);
            assertEquals(List.of(new MarketAcquisitionBounds.RecipeConversion(
                    List.of(new MarketAcquisitionBounds.InputChoice(List.of(g(name,1)))),List.of(g("fishing_rod",1)))),reading.rules(),
                    "On break the steering stack is replaced by one rod, not retained alongside it");
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g(name,8)))),List.of(),reading.rules());
            assertTrue(bounds.complete());
            assertTrue(bounds.maximumPayout(id("fishing_rod"),8)<=12,"All returned rods share the real pack cost");
        }
        var rod=NativeMarketDirectUseRules.read(id("fishing_rod"),prototypes.get(id("fishing_rod")),lookup,catalog);
        assertTrue(rod.supported()); assertTrue(rod.rules().isEmpty(),
                "Casting/retrieving without a server-side timed bite only moves existing entities; no immediate fishing loot");
        var enchanted=new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchanted.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).listElements().findFirst().orElseThrow(),1);
        var changed=DataComponentMap.builder().addAll(prototypes.get(id("fishing_rod"))).set(DataComponents.ENCHANTMENTS,enchanted.toImmutable()).build();
        assertFalse(NativeMarketDirectUseRules.read(id("fishing_rod"),changed,lookup,catalog).supported(),
                "Changed enchantment behavior needs its own timed-production proof");
    }
    @Test void nativeBreadCompostHasAConservativeOneBoneMealOutputWithoutResolvingChance() {
        var data=prototypes.get(id("bread")); assertTrue(net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.containsKey(net.minecraft.world.item.Items.BREAD));
        var reading=NativeMarketDirectUseRules.read(id("bread"),data);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().size());
        assertEquals(List.of(g("bread",1)),reading.rules().getFirst().inputs().getFirst().choices());
        assertEquals(List.of(g("bone_meal",1)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(4,List.of(g("bread",32)))),List.of(),reading.rules());
        assertEquals(4,bounds.maximumPayout(id("bone_meal"),32));
    }
    @Test void ExactNativeBaseItemWithoutExtraComponentsHasNoDirectOutput() {
        var reading=NativeMarketDirectUseRules.read(id("leather"),prototypes.get(id("leather")));
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void nativeBlockTransformersAndUnprovenBucketCallbacksAreHeld() {
        assertTrue(NativeMarketTransformerCatalog.read(lookup,prototypes.keySet(),prototypes::get).supported());
        assertFalse(NativeMarketDirectUseRules.read(id("iron_axe"),prototypes.get(id("iron_axe"))).supported());
        for(String item:List.of("cod_bucket","powder_snow_bucket","bookshelf","potion"))
            assertFalse(NativeMarketDirectUseRules.read(id(item),prototypes.get(id(item))).supported(),item);
    }
    @Test void missingPrototypeAndNonNativeItemNeverGetAnEmptySuccessProof() {
        assertFalse(NativeMarketDirectUseRules.read(id("bread"),null).supported());
        assertFalse(NativeMarketDirectUseRules.read(Identifier.parse("villagequest:bread"),prototypes.get(id("bread"))).supported());
    }
    @Test void exactNativeInkApplicatorsOnlyChangeSignTextAndDoNotCreateTradeGoods() {
        var catalog=NativeMarketTransformerCatalog.read(lookup,List.of(id("ink_sac"),id("glow_ink_sac")),prototypes::get);
        for(String name:List.of("ink_sac","glow_ink_sac")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog);
            assertTrue(reading.supported(),name); assertTrue(reading.rules().isEmpty(),name);
        }
    }
    @Test void nativeBedsRetainTheirOneConsumedItemAndRejectOtherEntityPlacements() {
        for(var color:net.minecraft.world.item.DyeColor.values()) {
            var bed=id(color.getName()+"_bed");
            assertTrue(NativeMarketDirectUseRules.read(bed,prototypes.get(bed),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
        }
    }
    @Test void otherEntityPlacingItemsRemainUnproven() {
        assertFalse(NativeMarketDirectUseRules.read(id("armor_stand"),prototypes.get(id("armor_stand")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
    }
    @Test void exactPassiveNativeBlocksStillIncludeEveryCurrentTransformerOutput() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(String name:List.of("hay_block","redstone_block","glass","black_stained_glass",
                "waxed_copper_bulb","waxed_exposed_copper_bulb","waxed_weathered_copper_bulb","waxed_oxidized_copper_bulb"))
            assertTrue(NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,catalog).supported(),name);
        var transform=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var changed=NativeMarketDirectUseRules.read(id("glass"),prototypes.get(id("glass")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,transform));
        assertTrue(changed.supported()); assertTrue(changed.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))));
        for(String name:List.of("copper_bulb","waxed_copper_bulb")) {
            var bulb=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,
                    new NativeMarketTransformerCatalog.Reading(true,transform));
            assertTrue(bulb.supported());
            assertTrue(bulb.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),
                    "Powered/lit state changes cannot skip the current transform output: "+name);
        }
    }
    @Test void exactSpyglassOnlyReturnsItsOriginalStackAndAddsNoTradeGoods() {
        var reading=NativeMarketDirectUseRules.read(id("spyglass"),prototypes.get(id("spyglass")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of()));
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void exactDyesConsumeColoringMaterialWithoutManufacturingWoolOrReplacingAnimalProduction() {
        var catalog=new NativeMarketTransformerCatalog.Reading(true,List.of());
        for(var color:net.minecraft.world.item.DyeColor.values()) {
            var dye=id(color.getName()+"_dye");
            var reading=NativeMarketDirectUseRules.read(dye,prototypes.get(dye),lookup,catalog);
            assertTrue(reading.supported(),dye.toString());
            assertTrue(reading.rules().isEmpty(),"Dyeing a sign/sheep emits no immediate trade goods");
        }
        assertFalse(NativeMarketDirectUseRules.read(id("bone_meal"),prototypes.get(id("bone_meal")),lookup,catalog).supported(),
                "The exact DyeItem proof must not approve growth callbacks");
    }
    @Test void honeyBlockMovementCallbacksKeepCurrentTransformsAndDoNotApproveOtherStickyBlocks() {
        var transform=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var reading=NativeMarketDirectUseRules.read(id("honey_block"),prototypes.get(id("honey_block")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,transform));
        assertTrue(reading.supported());
        assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))));
        assertFalse(NativeMarketDirectUseRules.read(id("slime_block"),prototypes.get(id("slime_block")),lookup,
                new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
    }
    @Test void exactWaterloggedStructuralCallbacksKeepEveryCurrentTransformIncludingProvenWeathering() {
        var transform=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
        var changed=new NativeMarketTransformerCatalog.Reading(true,transform);
        for(String name:List.of("iron_bars","black_stained_glass_pane","iron_chain","lantern","ladder","waxed_copper_grate",
                "copper_bars","copper_chain","copper_lantern","copper_grate")) {
            var reading=NativeMarketDirectUseRules.read(id(name),prototypes.get(id(name)),lookup,changed);
            assertTrue(reading.supported(),name);
            assertTrue(reading.rules().stream().anyMatch(rule -> rule.outputs().contains(g("diamond_block",1))),name);
        }
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
