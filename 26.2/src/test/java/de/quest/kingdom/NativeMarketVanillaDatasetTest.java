package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import de.quest.shop.ShopAcquisitionCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

/** Reads the pinned dependency's data only; independent registry copy, no server/world/resource reload launch. */
final class NativeMarketVanillaDatasetTest {
    @Test void allPinnedVanillaRecipesDecodeAndHaveABoundedNativePriceReading() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var prototypes=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(prototypes);
        var resource=Recipe.class.getResource("/data/minecraft/recipe/hay_block.json");
        assertNotNull(resource,"Pinned native recipe resources must be present");
        var jar=(JarURLConnection)resource.openConnection();
        try(var zip=new ZipFile(java.nio.file.Path.of(jar.getJarFileURL().toURI()).toFile())) {
            var lookup=NativeMarketTestData.lookup();
            var ops=RegistryOps.create(JsonOps.INSTANCE,lookup);
            var recipes=new ArrayList<Recipe<?>>();
            var recipeIds=new ArrayList<String>();
            for(var entry:zip.stream().filter(e -> e.getName().startsWith("data/minecraft/recipe/") && e.getName().endsWith(".json")).sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
                var decoded=Recipe.CODEC.parse(ops,json(zip,entry.getName()));
                assertTrue(decoded.result().isPresent(),() -> entry.getName()+": "+decoded.error());
                var recipe=decoded.getOrThrow(); recipes.add(recipe); recipeIds.add(entry.getName());
                assertTrue(NativeMarketRecipeRules.read(recipe,lookup).supported(),entry.getName());
                assertTrue(NativeMarketSourceBuilder.readRecipes(List.of(recipe),lookup).supported(),"Payload gate: "+entry.getName());
            }
            assertEquals(1585,recipes.size(),"The pinned 26.2 dataset changed");
            var reading=NativeMarketRecipeRules.readAll(recipes,lookup);
            assertTrue(reading.supported()); assertTrue(reading.rules().size()>1585 && reading.rules().size()<=8192);
            var acquisitions=new ArrayList<MarketAcquisitionBounds.Acquisition>();
            ShopAcquisitionCatalog.all().forEach((id,pack) -> acquisitions.add(new MarketAcquisitionBounds.Acquisition(
                    ShopAcquisitionCatalog.minimumPrice(id),pack.goods().stream().map(g -> new MarketAcquisitionBounds.Goods(
                            Identifier.withDefaultNamespace(g.item()),g.count())).toList())));
            acquisitions.addAll(KingdomMarketCatalog.acquisitions());
            var bounds=MarketAcquisitionBounds.solve(acquisitions,List.of(),reading.rules());
            assertTrue(bounds.complete(),"The actual catalog/recipe graph must settle inside the bounded solver");
            assertTrue(bounds.maximumPayout(Identifier.withDefaultNamespace("wheat"),32)<=4);

            // Native 26.2 resources are decoded into the same private tagged registry.
            var reloadable=lookup;
            assertTrue(NativeMarketRecipeRules.readAll(recipes,reloadable).supported(),"Current source fixture must retain every native item tag");
            var currentPrototypes=new HashMap<Identifier,DataComponentMap>();
            for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(reloadable)) if(pending.key().equals(Registries.ITEM))
                pending.forEach((holder,components) -> currentPrototypes.put(holder.key().identifier(),components));
            var transformers=NativeMarketTransformerCatalog.read(reloadable,BuiltInRegistries.ITEM.keySet(),currentPrototypes::get);
            assertTrue(transformers.supported());
            assertFalse(NativeMarketSourceBuilder.buildRegional(MarketSourceClosure.catalogAcquisitions(),recipes,reloadable,currentPrototypes::get).complete(),
                    "This vanilla-only fixture has no registered project plaques; missing live goods must stay held");
            var regional=NativeMarketSourceBuilder.buildRegional(MarketSourceClosure.catalogAcquisitions().stream()
                    .filter(a -> a.outputs().stream().allMatch(g -> g.item().getNamespace().equals("minecraft"))).toList(),recipes,reloadable,currentPrototypes::get);
            assertTrue(regional.complete(),"Independent regional proofs must not require global growth closure: "+regional.unprovenItem());
            assertTrue(regional.bounds().source(Identifier.withDefaultNamespace("coal")).price()>0);
            for(String target:List.of("iron_ingot","copper_ingot","leather","ink_sac","paper","white_wool","wheat","bread","hay_block","honey_bottle","honeycomb")) {
                if(regional.bounds().source(Identifier.withDefaultNamespace(target)).price()==0) {
                    var family=NativeRegionalBuybackSources.family(target);
                    for(var rule:reading.rules()) {
                        long output=rule.outputs().stream().mapToLong(g -> (long)family.getOrDefault(g.item(),0)*g.quantity()).sum();
                        long input=rule.inputs().stream().mapToLong(c -> c.choices().stream().mapToLong(g -> (long)family.getOrDefault(g.item(),0)*g.quantity()).min().orElse(0)).sum();
                        if(output>input) System.out.println("Held material recipe "+target+": "+rule);
                    }
                }
                assertTrue(regional.bounds().source(Identifier.withDefaultNamespace(target)).price()>0,"Current reviewed SKU held: "+target+" credits="+regional.bounds().credits());
            }
            assertEquals(0,regional.bounds().source(Identifier.withDefaultNamespace("wheat_seeds")).price());
            verifyNewHomeRequests(regional);
            var seed=Identifier.withDefaultNamespace("wheat_seeds");
            var ripeSeed=DataComponentMap.builder().addAll(currentPrototypes.get(seed)).set(DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("age","7"))).build();
            var ripeProof=NativeMarketSourceBuilder.buildRegional(MarketSourceClosure.catalogAcquisitions().stream()
                    .filter(a -> a.outputs().stream().allMatch(g -> g.item().getNamespace().equals("minecraft"))).toList(),recipes,reloadable,
                    item -> item.equals(seed)?ripeSeed:currentPrototypes.get(item));
            for(String grain:List.of("wheat","bread","hay_block"))
                assertEquals(0,ripeProof.bounds().source(Identifier.withDefaultNamespace(grain)).price(),"A purchased ripe crop must not inherit fresh placement proof");
            assertTrue(ripeProof.bounds().source(Identifier.withDefaultNamespace("honey_bottle")).price()>0,"A held grain source must not suppress the safe honey source");
            var purchasedHive=Identifier.withDefaultNamespace("beehive");
            var filledHive=DataComponentMap.builder().addAll(currentPrototypes.get(purchasedHive)).set(DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("honey_level","5"))).build();
            var filledHiveProof=NativeMarketSourceBuilder.buildRegional(MarketSourceClosure.catalogAcquisitions().stream()
                    .filter(a -> a.outputs().stream().allMatch(g -> g.item().getNamespace().equals("minecraft"))).toList(),recipes,reloadable,
                    item -> item.equals(purchasedHive)?filledHive:currentPrototypes.get(item));
            for(String honey:List.of("honey_bottle","honeycomb"))
                assertEquals(0,filledHiveProof.bounds().source(Identifier.withDefaultNamespace(honey)).price(),"Bought stored honey must not inherit the empty hive proof");
            assertTrue(filledHiveProof.bounds().source(Identifier.withDefaultNamespace("wheat")).price()>0);
            var changedRecipes=new ArrayList<>(recipes);
            changedRecipes.add(Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,reloadable),JsonParser.parseString("""
                {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:barrel"],"result":{"id":"minecraft:coal","count":64}}
                """)).getOrThrow());
            var isolated=NativeMarketSourceBuilder.buildRegional(MarketSourceClosure.catalogAcquisitions().stream()
                    .filter(a -> a.outputs().stream().allMatch(g -> g.item().getNamespace().equals("minecraft"))).toList(),changedRecipes,reloadable,currentPrototypes::get);
            assertTrue(isolated.complete()); assertEquals(0,isolated.bounds().source(Identifier.withDefaultNamespace("coal")).price());
            assertTrue(isolated.bounds().source(Identifier.withDefaultNamespace("iron_ingot")).price()>0,"A held coal recipe must not suppress safe iron");
            for(String name:List.of("painting","item_frame","glow_item_frame")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" current native recovery/recipe closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12);
                var storedItem=new net.minecraft.nbt.CompoundTag(); storedItem.putString("id","minecraft:diamond"); storedItem.putInt("count",64);
                var entityTag=new net.minecraft.nbt.CompoundTag(); entityTag.put("Item",storedItem);
                var entityType=name.equals("painting")?net.minecraft.world.entity.EntityTypes.PAINTING:
                        name.equals("item_frame")?net.minecraft.world.entity.EntityTypes.ITEM_FRAME:net.minecraft.world.entity.EntityTypes.GLOW_ITEM_FRAME;
                var imported=DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.ENTITY_DATA,
                        net.minecraft.world.item.component.TypedEntityData.<net.minecraft.world.entity.EntityType<?>>of(entityType,entityTag)).build();
                var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                        next -> next.equals(item)?imported:currentPrototypes.get(next),
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertFalse(held.complete(),"Stored entity data must not inherit the empty display proof");
                assertTrue(held.bounds().credits().isEmpty());
            }
            for(String name:List.of("carrot_on_a_stick","warped_fungus_on_a_stick","fishing_rod")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" current returned-rod/recipe closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12);
                for(String fish:List.of("cod","salmon","pufferfish","tropical_fish"))
                    assertFalse(proof.reachable().contains(Identifier.withDefaultNamespace(fish)),
                            "Subsequent timed fishing production is not an immediate purchased-tool conversion");
            }
            for(String name:List.of("minecart","chest_minecart","hopper_minecart")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" full native cart closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12);
                for(var payload:List.of(
                        DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.CONTAINER,filledContainerContents()).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.ENTITY_DATA,
                                net.minecraft.world.item.component.TypedEntityData.<net.minecraft.world.entity.EntityType<?>>of(
                                        net.minecraft.world.entity.EntityTypes.MINECART,new net.minecraft.nbt.CompoundTag())).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.CONTAINER_LOOT,
                                new net.minecraft.world.item.component.SeededContainerLoot(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                                        Identifier.withDefaultNamespace("chests/simple_dungeon")),0)).build())) {
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(item)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Imported cart cargo/entity/seeded loot remains held");
                    assertTrue(held.bounds().credits().isEmpty());
                }
            }
            for(String name:List.of("map","filled_map")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,8))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" full native map/recipe closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,8)>0 && proof.bounds().maximumPayout(item,8)<=12);
                assertTrue(proof.bounds().maximumPayout(Identifier.withDefaultNamespace("filled_map"),8)>0);
                assertTrue(proof.bounds().maximumPayout(Identifier.withDefaultNamespace("filled_map"),8)<=12,
                        "Copied or extended maps must retain the paid empty-map/material inputs");
                var payload=DataComponentMap.builder().addAll(currentPrototypes.get(item))
                        .set(DataComponents.CONTAINER,filledContainerContents()).build();
                var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                        next -> next.equals(item)?payload:currentPrototypes.get(next),
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertFalse(held.complete()); assertTrue(held.bounds().credits().isEmpty());
            }
            for(String name:List.of("campfire","soul_campfire","chest","hopper","chiseled_bookshelf")) {
                var campfire=Identifier.withDefaultNamespace(name);
                var direct=NativeMarketDirectUseRules.read(campfire,currentPrototypes.get(campfire),reloadable,transformers);
                assertTrue(direct.supported(),"Exact empty storage/cooking block must not manufacture its contents: "+name);
                assertTrue(direct.rules().stream().noneMatch(rule -> rule.outputs().stream()
                        .anyMatch(goods -> goods.item().getPath().startsWith("cooked_"))),
                        "An empty purchased campfire is not a retained source of free cooked food");
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(campfire,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" native loot/transform closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(campfire,1)>0 && proof.bounds().maximumPayout(campfire,1)<=12);
                for(var payload:List.of(
                        DataComponentMap.builder().addAll(currentPrototypes.get(campfire)).set(DataComponents.CONTAINER,filledContainerContents()).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(campfire)).set(DataComponents.CONTAINER_LOOT,
                                new net.minecraft.world.item.component.SeededContainerLoot(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                                        Identifier.withDefaultNamespace("chests/simple_dungeon")),0)).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(campfire)).set(DataComponents.BLOCK_STATE,
                                new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("waterlogged","true"))).build())) {
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(campfire)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Imported campfire contents/states must not inherit the empty proof");
                    assertTrue(held.bounds().credits().isEmpty());
                }
            }
            for(String rail:List.of("rail","powered_rail","activator_rail","detector_rail")) {
                var railId=Identifier.withDefaultNamespace(rail);
                assertTrue(NativeMarketDirectUseRules.read(railId,currentPrototypes.get(railId),reloadable,transformers).supported(),rail);
            }
            var barrel=Identifier.withDefaultNamespace("barrel");
            assertTrue(NativeMarketDirectUseRules.read(barrel,currentPrototypes.get(barrel),reloadable,transformers).supported(),
                    "Exact native empty barrel callbacks only move existing container contents; current transforms/loot must still be proved");
            var barrelSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(barrel,1))));
            var barrelProof=NativeMarketSourceBuilder.build(barrelSource,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(barrelProof.complete(),() -> "Current barrel loot and every reachable callback must close together; held at "+barrelProof.unprovenItem()
                    +", prototype="+currentPrototypes.get(barrel)+", loot present="+reloadable.lookupOrThrow(Registries.LOOT_TABLE)
                    .get(((net.minecraft.world.item.BlockItem)net.minecraft.world.item.Items.BARREL).getBlock().getLootTable().orElseThrow()).isPresent());
            assertTrue(barrelProof.bounds().maximumPayout(barrel,1)<=12);
            for(String name:List.of("torch","soul_torch","copper_torch","redstone_torch",
                    "skeleton_skull","zombie_head","creeper_head","dragon_head","piglin_head","player_head")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" floor/wall native closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12);
                for(var payload:List.of(
                        DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.CONTAINER,filledContainerContents()).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.BLOCK_STATE,
                                new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("powered","true"))).build())) {
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(item)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Imported placement/stock does not inherit the decorative callbacks: "+name);
                    assertTrue(held.bounds().credits().isEmpty());
                }
            }
            for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","bamboo","crimson","warped")) {
                var sign=Identifier.withDefaultNamespace(wood+"_sign");
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(sign,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> sign+" blank-sign native closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(sign,1)>0 && proof.bounds().maximumPayout(sign,1)<=12);
                for(String side:List.of("front_text","back_text")) {
                    var payload=DataComponentMap.builder().addAll(currentPrototypes.get(sign)).set(DataComponents.BLOCK_ENTITY_DATA,NativeMarketTestData.signPayload(side)).build();
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(sign)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Neither imported sign side may borrow the blank-text proof");
                    assertTrue(held.bounds().credits().isEmpty());
                }
            }
            int hangingSigns=0;
            for(var nativeItem:BuiltInRegistries.ITEM) if(nativeItem.getClass()==net.minecraft.world.item.HangingSignItem.class) {
                hangingSigns++;
                var item=BuiltInRegistries.ITEM.getKey(nativeItem);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> item+" ceiling/wall hanging-sign closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12);
                for(String side:List.of("front_text","back_text")) {
                    var payload=DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.BLOCK_ENTITY_DATA,NativeMarketTestData.signPayload(side)).build();
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(item)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Hanging signs cannot import command authority from either side");
                    assertTrue(held.bounds().credits().isEmpty());
                }
            }
            assertTrue(hangingSigns>=12);
            System.out.println("Hanging-sign full native closures checked: "+hangingSigns);
            for(String name:List.of("copper_bars","copper_chain","copper_grate","copper_lantern","cut_copper_stairs",
                    "copper_trapdoor","lightning_rod","chiseled_copper","cut_copper_slab","cut_copper","copper_bulb","copper_block")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" full weathering closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12,name);
                var block=((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.getValue(item)).getBlock();
                for(int age=0;age<4;age++) {
                    var target=BuiltInRegistries.ITEM.getKey(block.asItem());
                    assertTrue(proof.reachable().contains(target),"Every weathered SKU needs independent current loot: "+target);
                    assertTrue(proof.bounds().maximumPayout(target,1)>0 && proof.bounds().maximumPayout(target,1)<=12);
                    var waxed=net.minecraft.world.item.HoneycombItem.WAXABLES.get().get(block);
                    assertNotNull(waxed);
                    assertTrue(proof.reachable().contains(BuiltInRegistries.ITEM.getKey(waxed.asItem())));
                    var next=net.minecraft.world.level.block.WeatheringCopper.getNext(block);
                    if(next.isPresent()) block=next.get();
                }
                var imported=DataComponentMap.builder().addAll(currentPrototypes.get(item)).set(DataComponents.BLOCK_STATE,
                        new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("type","double","waterlogged","true"))).build();
                var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                        next -> next.equals(item)?imported:currentPrototypes.get(next),
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertFalse(held.complete(),"Imported double/water state must never borrow a consumed placement: "+name);
                assertTrue(held.bounds().credits().isEmpty());
            }
            for(String[] family:List.of(new String[]{"copper_grate","oxidized_copper_grate"},
                    new String[]{"copper_bulb","oxidized_copper_bulb"},
                    new String[]{"cut_copper_slab","oxidized_cut_copper_slab"})) {
                var root=Identifier.withDefaultNamespace(family[0]);
                var sibling=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(family[1])).getLootTable().orElseThrow();
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(root,2))));
                for(boolean changed:List.of(false,true)) {
                    var loot=new MappedRegistry<net.minecraft.world.level.storage.loot.LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
                    for(var holder:reloadable.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList())
                        if(!holder.key().equals(sibling)) loot.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN);
                    if(changed) {
                        String condition=family[0].endsWith("slab")
                                ?",\"condition\":{\"type\":\"minecraft:match_block\",\"blocks\":\"minecraft:"+family[1]+"\",\"state\":{\"type\":\"double\"}}"
                                :family[0].equals("copper_bulb")?",\"condition\":{\"type\":\"minecraft:match_block\",\"blocks\":\"minecraft:"+family[1]+"\",\"state\":{\"lit\":\"true\",\"powered\":\"true\"}}":"";
                        var data=JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\""
                                +condition+",\"modifier\":{\"type\":\"minecraft:set_count\",\"count\":64}}]}]}");
                        loot.register(sibling,net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(
                                RegistryOps.create(JsonOps.INSTANCE,reloadable),NativeMarketTestData.nativeLoot(data)).getOrThrow(),RegistrationInfo.BUILT_IN);
                    }
                    loot.freeze();
                    var current=HolderLookup.Provider.create(Stream.concat(reloadable.listRegistries()
                            .filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(loot)));
                    var proof=NativeMarketSourceBuilder.build(source,recipes,current,currentPrototypes::get,
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),current,transformers));
                    if(changed) {
                        assertTrue(proof.complete(),() -> family[0]+" changed weathered loot held at "+proof.unprovenItem());
                        assertTrue(proof.reachable().contains(Identifier.withDefaultNamespace("diamond")));
                        assertTrue(proof.bounds().maximumPayout(Identifier.withDefaultNamespace("diamond"),64)<=12,
                                "Changed weathered loot, including DOUBLE slabs, shares the actual purchased input budget");
                    } else {
                        assertFalse(proof.complete(),"Every reachable weathering sibling requires its own current loot");
                        assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace(family[1])),proof.unprovenItem());
                        assertTrue(proof.bounds().credits().isEmpty());
                    }
                }
            }
            for(String name:NativeMarketCopperChestRules.NAMES) {
                var chest=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(chest,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" full copper chest closure held at "+proof.unprovenItem());
                for(String variant:NativeMarketCopperChestRules.NAMES) {
                    var target=Identifier.withDefaultNamespace(variant);
                    assertTrue(proof.reachable().contains(target),"Connection/weathering must preserve every reachable SKU: "+variant);
                    assertTrue(proof.bounds().maximumPayout(target,1)>0 && proof.bounds().maximumPayout(target,1)<=12);
                }
                for(var payload:List.of(
                        DataComponentMap.builder().addAll(currentPrototypes.get(chest)).set(DataComponents.CONTAINER,filledContainerContents()).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(chest)).set(DataComponents.BLOCK_STATE,
                                new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("type","left"))).build(),
                        DataComponentMap.builder().addAll(currentPrototypes.get(chest)).set(DataComponents.CONTAINER_LOOT,
                                new net.minecraft.world.item.component.SeededContainerLoot(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                                        Identifier.withDefaultNamespace("chests/simple_dungeon")),0)).build())) {
                    var held=NativeMarketSourceBuilder.build(source,recipes,reloadable,
                            next -> next.equals(chest)?payload:currentPrototypes.get(next),
                            next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                    assertFalse(held.complete(),"Imported copper chest stock/halves/loot remain held");
                    assertTrue(held.bounds().credits().isEmpty());
                }
                var foreign=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
                assertFalse(NativeMarketDirectUseRules.read(chest,currentPrototypes.get(chest),reloadable,
                        new NativeMarketTransformerCatalog.Reading(true,foreign)).supported(),
                        "A changed multipart transformer must not inherit family/inventory preservation");
                var injected=NativeMarketTestData.transforms(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")));
                assertFalse(NativeMarketBlockTransformRules.read(Identifier.withDefaultNamespace("dirt"),injected,reloadable).supported(),
                        "Foreign transform targets cannot inject a connected half or stored inventory");
            }
            var copper=Identifier.withDefaultNamespace("copper_chest");
            var copperSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(copper,1))));
            var siblingKey=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("oxidized_copper_chest")).getLootTable().orElseThrow();
            for(boolean changed:List.of(false,true)) {
                var siblingLoot=new MappedRegistry<net.minecraft.world.level.storage.loot.LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
                for(var holder:reloadable.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList())
                    if(!holder.key().equals(siblingKey)) siblingLoot.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN);
                if(changed) siblingLoot.register(siblingKey,net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(
                        RegistryOps.create(JsonOps.INSTANCE,reloadable),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                        {"type":"minecraft:block","pools":[{"rolls":1,"entries":[
                         {"type":"minecraft:item","name":"minecraft:diamond","modifier":{"type":"minecraft:set_count","count":64}}]}]}
                        """))).getOrThrow(),RegistrationInfo.BUILT_IN);
                siblingLoot.freeze();
                var siblingLookup=HolderLookup.Provider.create(Stream.concat(reloadable.listRegistries()
                        .filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(siblingLoot)));
                var proof=NativeMarketSourceBuilder.build(copperSource,recipes,siblingLookup,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),siblingLookup,transformers));
                if(changed) {
                    assertTrue(proof.complete(),() -> "Changed sibling loot closure held at "+proof.unprovenItem());
                    assertTrue(proof.reachable().contains(Identifier.withDefaultNamespace("diamond")));
                    assertTrue(proof.bounds().maximumPayout(Identifier.withDefaultNamespace("diamond"),64)<=12,
                            "Changed weathered/connected-cell loot must share the original single chest credit");
                } else {
                    assertFalse(proof.complete(),"Missing loot on any reachable weathering sibling must hold the full chest closure");
                    assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("oxidized_copper_chest")),proof.unprovenItem());
                    assertTrue(proof.bounds().credits().isEmpty());
                }
            }
            var filledBarrel=DataComponentMap.builder().addAll(currentPrototypes.get(barrel)).set(DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64)))).build();
            var seededBarrel=DataComponentMap.builder().addAll(currentPrototypes.get(barrel)).set(DataComponents.CONTAINER_LOOT,
                    new net.minecraft.world.item.component.SeededContainerLoot(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                            Identifier.withDefaultNamespace("chests/simple_dungeon")),0)).build();
            for(var payload:List.of(filledBarrel,seededBarrel)) {
                var heldBarrel=NativeMarketSourceBuilder.build(barrelSource,recipes,reloadable,item -> item.equals(barrel)?payload:currentPrototypes.get(item),
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(heldBarrel.complete(),"Stored goods and seeded loot must not inherit the empty barrel proof");
                assertTrue(heldBarrel.bounds().credits().isEmpty());
            }
            for(String hiveName:List.of("beehive","bee_nest")) {
                var hive=Identifier.withDefaultNamespace(hiveName);
                var hiveReading=NativeMarketDirectUseRules.read(hive,currentPrototypes.get(hive),reloadable,transformers);
                assertTrue(hiveReading.supported(),hiveName+" requires current empty placement and harvest proof");
                var comb=Identifier.withDefaultNamespace("honeycomb");
                var honey=Identifier.withDefaultNamespace("honey_bottle");
                assertTrue(hiveReading.rules().stream().noneMatch(rule -> rule.outputs().stream()
                        .anyMatch(g -> g.item().equals(comb) || g.item().equals(honey))),
                        "A purchased empty hive cannot harvest before external bee production");
                var bottle=Identifier.withDefaultNamespace("glass_bottle");
                var bottleReading=NativeMarketDirectUseRules.read(bottle,currentPrototypes.get(bottle),reloadable,transformers);
                assertTrue(bottleReading.supported());
                assertTrue(bottleReading.rules().stream().noneMatch(rule -> rule.outputs().stream().anyMatch(g -> g.item().equals(honey))),
                        "The bottle alone cannot bypass the hive's honey-level production gate");
                var harvestBounds=MarketAcquisitionBounds.solve(List.of(
                        new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(hive,1))),
                        new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(comb,64))),
                        new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(honey,16)))),
                        List.of(),hiveReading.rules());
                assertTrue(harvestBounds.complete());
                assertTrue(MarketPricePolicy.buy(4,64,15,12,List.of(harvestBounds.source(comb)))>0,
                        "Real produced comb must retain a useful positive, purchase-bounded lot price");
                assertTrue(harvestBounds.maximumPayout(comb,64)<=12);
                assertTrue(harvestBounds.maximumPayout(honey,16)>0);
                assertTrue(harvestBounds.maximumPayout(honey,16)<=12);
                var actualLotBounds=MarketAcquisitionBounds.solve(KingdomMarketCatalog.acquisitions(),List.of(),
                        Stream.concat(hiveReading.rules().stream(),bottleReading.rules().stream()).toList());
                assertTrue(actualLotBounds.complete());
                for(var goods:KingdomMarketCatalog.offers(de.quest.shrine.VillageBondType.APIARY,0)) {
                    if(!goods.item().equals(comb) && !goods.item().equals(honey)) continue;
                    for(int factor:List.of(-15,0,15)) {
                        long payout=MarketPricePolicy.buy(goods.buy(),goods.lot(),factor,goods.sell(),List.of(actualLotBounds.source(goods.item())));
                        assertTrue(payout>0,"Real Apiary production lot should have a useful price: "+goods.item());
                        assertTrue(payout<MarketPricePolicy.sell(goods.sell(),-15,15),
                                "Maximum-discount purchase must still cost more than every trader's buyback");
                    }
                }
                var hiveSources=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(hive,1))));
                var hiveProof=NativeMarketSourceBuilder.build(hiveSources,recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(hiveProof.complete(),() -> hiveName+" full native proof held at "+hiveProof.unprovenItem()
                        +", placement="+currentPrototypes.get(hive).get(DataComponents.BLOCK_STATE)
                        +", loot="+net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,reloadable),
                                reloadable.lookupOrThrow(Registries.LOOT_TABLE).get(((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.get(hive).orElseThrow().value())
                                        .getBlock().getLootTable().orElseThrow()).orElseThrow().value()));
                var occupied=DataComponentMap.builder().addAll(currentPrototypes.get(hive)).set(DataComponents.BEES,
                        new net.minecraft.world.item.component.Bees(List.of(net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant.create(0)))).build();
                var heldHive=NativeMarketSourceBuilder.build(hiveSources,recipes,reloadable,item -> item.equals(hive)?occupied:currentPrototypes.get(item),
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(heldHive.complete(),"Stored entity data must not inherit the empty hive prototype proof");
                assertTrue(heldHive.bounds().credits().isEmpty());
                for(int level=1;level<=5;level++) {
                    var filled=DataComponentMap.builder().addAll(currentPrototypes.get(hive)).set(DataComponents.BLOCK_STATE,
                            new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("honey_level",Integer.toString(level)))).build();
                    var heldFilled=NativeMarketSourceBuilder.build(hiveSources,recipes,reloadable,item -> item.equals(hive)?filled:currentPrototypes.get(item),
                            item -> NativeMarketDirectUseRules.read(item,filled,reloadable,transformers));
                    assertFalse(heldFilled.complete(),"Nonempty hive state must not inherit empty placement proof: "+level);
                    assertTrue(heldFilled.bounds().credits().isEmpty());
                }
            }
            var hiveTransform=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.BEEHIVE);
            assertFalse(NativeMarketBlockTransformRules.read(Identifier.withDefaultNamespace("oak_log"),hiveTransform,reloadable).supported(),
                    "A transform that injects filled hive state needs its own stateful proof");
            var apiaryHoneyPurchases=KingdomMarketCatalog.acquisitions().stream().filter(source -> source.outputs().stream()
                    .allMatch(g -> List.of(Identifier.withDefaultNamespace("honeycomb"),Identifier.withDefaultNamespace("honey_bottle")).contains(g.item()))).toList();
            assertEquals(2,apiaryHoneyPurchases.size());
            var apiaryHoneyProof=NativeMarketSourceBuilder.build(apiaryHoneyPurchases,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(apiaryHoneyProof.complete(),() -> "Actual honey recipes, bottle remainders and current native callbacks held at "+apiaryHoneyProof.unprovenItem());
            for(var goods:KingdomMarketCatalog.offers(de.quest.shrine.VillageBondType.APIARY,0)) {
                if(!List.of("honeycomb","honey_bottle").contains(goods.sku())) continue;
                for(int factor:List.of(-15,0,15)) {
                    long payout=MarketPricePolicy.buy(goods.buy(),goods.lot(),factor,goods.sell(),List.of(apiaryHoneyProof.bounds().source(goods.item())));
                    assertTrue(payout>0,"Complete native honey production proof must preserve useful lot prices: "+goods.sku());
                    assertTrue(payout<MarketPricePolicy.sell(goods.sell(),-15,15));
                }
            }
            var waxing=NativeMarketBlockTransformRules.read(Identifier.withDefaultNamespace("copper_block"),transformers.definitions(),reloadable);
            assertTrue(waxing.supported());
            assertTrue(waxing.rules().stream().anyMatch(rule -> rule.outputs().equals(List.of(
                    new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("waxed_copper_block"),1)))),
                    "Native HoneycombItem.WAXABLES must be included even without a component transformer");
            var bell=Identifier.withDefaultNamespace("bell");
            assertTrue(NativeMarketDirectUseRules.read(bell,currentPrototypes.get(bell),reloadable,transformers).supported(),
                    "Exact native bell ringing/resonance must compose ordinary loot and current transforms");
            var bellProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(bell,1)))),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(bellProof.complete()); assertTrue(bellProof.bounds().maximumPayout(bell,1)<=12);
            var blackBanner=Identifier.withDefaultNamespace("black_banner");
            for(var color:net.minecraft.world.item.DyeColor.values()) {
                var banner=Identifier.withDefaultNamespace(color.getName()+"_banner");
                assertTrue(NativeMarketDirectUseRules.read(banner,currentPrototypes.get(banner),reloadable,transformers).supported(),
                        "Both native standing/wall banner variants must be proved: "+banner);
            }
            var wallPredicate=net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.CODEC.parse(
                    RegistryOps.create(JsonOps.INSTANCE,reloadable),JsonParser.parseString("""
                        {"type":"minecraft:matching_blocks","blocks":"minecraft:black_wall_banner"}
                        """)).getOrThrow();
            var wallTransform=List.of(new NativeMarketTransformerCatalog.Transform(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("black_wall_banner")),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK,List.of()));
            var wallReading=NativeMarketDirectUseRules.read(blackBanner,currentPrototypes.get(blackBanner),reloadable,
                    new NativeMarketTransformerCatalog.Reading(true,wallTransform));
            assertTrue(wallReading.supported());
            assertTrue(wallReading.rules().stream().anyMatch(rule -> rule.outputs().contains(
                    new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("diamond_block"),1))),
                    "A wall-only current transformer must not be hidden by the standing banner item block");
            var bannerProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(blackBanner,1)))),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(bannerProof.complete(),() -> "Actual banner source closure held at "+bannerProof.unprovenItem());
            assertTrue(bannerProof.bounds().maximumPayout(blackBanner,1)<=12);
            var missingBannerLoot=new MappedRegistry<net.minecraft.world.level.storage.loot.LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
            missingBannerLoot.freeze();
            var noBannerLoot=HolderLookup.Provider.create(Stream.concat(reloadable.listRegistries()
                    .filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(missingBannerLoot)));
            for(String name:List.of("campfire","soul_campfire","chest","hopper","chiseled_bookshelf")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var missing=NativeMarketSourceBuilder.build(source,recipes,noBannerLoot,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),noBannerLoot,transformers));
                assertFalse(missing.complete(),"Empty inventory proof cannot replace missing current block loot: "+name);
                assertTrue(missing.bounds().credits().isEmpty());
            }
            assertFalse(NativeMarketDirectUseRules.read(blackBanner,currentPrototypes.get(blackBanner),noBannerLoot,transformers).supported(),
                    "A real wall loot key missing from the current lookup must hold even with known placement classes");
            for(var color:net.minecraft.world.item.DyeColor.values()) {
                var bed=Identifier.withDefaultNamespace(color.getName()+"_bed");
                assertTrue(NativeMarketDirectUseRules.read(bed,currentPrototypes.get(bed),reloadable,transformers).supported(),bed.toString());
            }
            var diamondTransform=NativeMarketTestData.transforms(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
            for(String bedName:List.of("black_bed")) {
                var blackBed=Identifier.withDefaultNamespace(bedName);
                var bedSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(blackBed,1))));
                var bedProof=NativeMarketSourceBuilder.build(bedSource,recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                if(bedName.equals("straw_bed")) {
                    assertFalse(bedProof.complete(),"Actual straw-bed compost must retain the independent BoneMeal callback hold");
                    assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("bone_meal")),bedProof.unprovenItem());
                    assertTrue(bedProof.bounds().credits().isEmpty());
                } else {
                assertTrue(bedProof.complete(),() -> "Actual two-cell bed closure held at "+bedProof.unprovenItem()
                        +", placement="+currentPrototypes.get(blackBed).get(DataComponents.BLOCK_STATE)
                        +", loot="+net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,reloadable),
                                reloadable.lookupOrThrow(Registries.LOOT_TABLE).get(((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.get(blackBed).orElseThrow().value())
                                        .getBlock().getLootTable().orElseThrow()).orElseThrow().value()));
                assertEquals(12,bedProof.bounds().maximumPayout(blackBed,1),"Native head-only loot must not invent a second bed or zero the legitimate item credit");
                }
                assertFalse(NativeMarketDirectUseRules.read(blackBed,currentPrototypes.get(blackBed),reloadable,
                        new NativeMarketTransformerCatalog.Reading(true,diamondTransform)).supported(),
                        "A matching bed transformer remains UNKNOWN until its paired outputs have their own proof");
                var patchedBed=DataComponentMap.builder().addAll(currentPrototypes.get(blackBed)).set(DataComponents.BLOCK_STATE,
                        new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("part","head"))).build();
                var heldPatchedBed=NativeMarketSourceBuilder.build(bedSource,recipes,reloadable,
                        item -> item.equals(blackBed)?patchedBed:currentPrototypes.get(item),
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(heldPatchedBed.complete()); assertTrue(heldPatchedBed.bounds().credits().isEmpty());
                var changedBedLoot=new MappedRegistry<net.minecraft.world.level.storage.loot.LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
                var jointTable=net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,reloadable),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                    {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:group","children":[
                     {"type":"minecraft:item","name":"minecraft:book","modifier":{"type":"minecraft:set_count","count":3}},
                     {"type":"minecraft:item","name":"minecraft:paper","modifier":{"type":"minecraft:set_count","count":4}}]}]}]}
                    """))).getOrThrow();
                changedBedLoot.register(((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.get(blackBed).orElseThrow().value()).getBlock().getLootTable().orElseThrow(),jointTable,RegistrationInfo.BUILT_IN);
                changedBedLoot.freeze();
                var jointLookup=HolderLookup.Provider.create(Stream.concat(reloadable.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(changedBedLoot)));
                var jointProof=NativeMarketSourceBuilder.build(bedSource,recipes,jointLookup,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),jointLookup,transformers));
                if(bedName.equals("straw_bed")) {
                    assertFalse(jointProof.complete());
                    assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("bone_block")),jointProof.unprovenItem(),
                            "This replaced-loot fixture omits the reachable bone block table; missing current loot must also hold");
                    assertTrue(jointProof.bounds().credits().isEmpty());
                } else {
                assertTrue(jointProof.complete(),() -> "Changed paired loot proof held at "+jointProof.unprovenItem());
                assertTrue(jointProof.bounds().maximumPayout(Identifier.withDefaultNamespace("book"),6)
                        +jointProof.bounds().maximumPayout(Identifier.withDefaultNamespace("paper"),8)<=12,
                        "Both cells' changed co-products must share one paid bed credit");
                }
                }
            var bundles=new ArrayList<Identifier>(); bundles.add(Identifier.withDefaultNamespace("bundle"));
            for(var color:net.minecraft.world.item.DyeColor.values()) bundles.add(Identifier.withDefaultNamespace(color.getName()+"_bundle"));
            for(var bundle:bundles) {
                assertTrue(NativeMarketDirectUseRules.read(bundle,currentPrototypes.get(bundle),reloadable,transformers).supported(),
                        "Exact native empty bundle must not invent inventory goods: "+bundle);
            }
            var blackBundle=Identifier.withDefaultNamespace("black_bundle");
            var bundleSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(blackBundle,1))));
            var bundleProof=NativeMarketSourceBuilder.build(bundleSource,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(bundleProof.complete(),() -> "Empty bundle proof held at "+bundleProof.unprovenItem());
            assertEquals(12,bundleProof.bounds().maximumPayout(blackBundle,1));
            var filledBundle=DataComponentMap.builder().addAll(currentPrototypes.get(blackBundle)).set(DataComponents.BUNDLE_CONTENTS,
                    new net.minecraft.world.item.component.BundleContents(List.of(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64))))).build();
            var heldBundle=NativeMarketSourceBuilder.build(bundleSource,recipes,reloadable,item -> item.equals(blackBundle)?filledBundle:currentPrototypes.get(item),
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertFalse(heldBundle.complete()); assertTrue(heldBundle.bounds().credits().isEmpty());
            var candleSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(
                    new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("black_candle"),1))));
            var candleProof=NativeMarketSourceBuilder.build(candleSource,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(candleProof.complete(),() -> "Actual candle and candle-cake callback closure held at "+candleProof.unprovenItem());
            assertTrue(candleProof.bounds().maximumPayout(Identifier.withDefaultNamespace("black_candle"),1)<=12,
                    "stacked placement and candle-cake recovery cannot create purchase credit");
            for(var color:net.minecraft.world.item.DyeColor.values()) {
                var carpet=Identifier.withDefaultNamespace(color.getName()+"_carpet");
                assertTrue(NativeMarketDirectUseRules.read(carpet,currentPrototypes.get(carpet),reloadable,transformers).supported(),
                        "Exact native wool carpets must compose support-loss loot and current transforms: "+carpet);
            }
            var blackCarpet=Identifier.withDefaultNamespace("black_carpet");
            var carpetSource=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(blackCarpet,1))));
            var carpetProof=NativeMarketSourceBuilder.build(carpetSource,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(carpetProof.complete(),() -> "Actual wool carpet callback closure held at "+carpetProof.unprovenItem());
            assertTrue(carpetProof.bounds().maximumPayout(blackCarpet,1)>0 && carpetProof.bounds().maximumPayout(blackCarpet,1)<=12);
            var carpetTransform=NativeMarketDirectUseRules.read(blackCarpet,currentPrototypes.get(blackCarpet),reloadable,
                    new NativeMarketTransformerCatalog.Reading(true,diamondTransform));
            assertTrue(carpetTransform.supported());
            assertTrue(carpetTransform.rules().stream().anyMatch(rule -> rule.outputs().contains(
                    new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("diamond_block"),1))),
                    "Approving exact carpet callbacks must not bypass current transformer outputs");
            var missingCarpetLoot=NativeMarketSourceBuilder.build(carpetSource,recipes,noBannerLoot,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),noBannerLoot,transformers));
            assertFalse(missingCarpetLoot.complete()); assertTrue(missingCarpetLoot.bounds().credits().isEmpty());
            var mossCarpet=Identifier.withDefaultNamespace("moss_carpet");
            assertFalse(NativeMarketDirectUseRules.read(mossCarpet,currentPrototypes.get(mossCarpet),reloadable,transformers).supported(),
                    "The wool carpet proof must not approve a different carpet callback class");
            assertFalse(BuiltInRegistries.ITEM.containsKey(Identifier.withDefaultNamespace("black_cushion")),
                    "Cushions do not exist in the pinned target dataset");
            for(String name:List.of("iron_bars","black_stained_glass_pane","iron_chain","lantern","ladder","waxed_copper_grate",
                    "oak_trapdoor","iron_trapdoor","oak_fence_gate","cobblestone_wall")) {
                var structure=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(structure,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" ordinary current loot/callback closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(structure,1)>0 && proof.bounds().maximumPayout(structure,1)<=12,name);
                var missing=NativeMarketSourceBuilder.build(source,recipes,noBannerLoot,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),noBannerLoot,transformers));
                assertFalse(missing.complete(),"Structural callback proof must not bypass current ordinary loot: "+name);
                assertTrue(missing.bounds().credits().isEmpty());
                var wet=DataComponentMap.builder().addAll(currentPrototypes.get(structure)).set(DataComponents.BLOCK_STATE,
                        new net.minecraft.world.item.component.BlockItemStateProperties(Map.of("waterlogged","true"))).build();
                var imported=NativeMarketSourceBuilder.build(source,recipes,reloadable,item -> item.equals(structure)?wet:currentPrototypes.get(item),
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(imported.complete(),"Stored wet placement must not inherit the dry purchased-block proof: "+name);
                assertTrue(imported.bounds().credits().isEmpty());
            }
            for(String name:List.of("oak_slab","stone_slab")) {
                var slab=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(slab,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" current native slab/empty-composter closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(slab,1)>0 && proof.bounds().maximumPayout(slab,1)<=12,name);
                if(name.equals("oak_slab")) {
                    assertTrue(proof.reachable().contains(Identifier.withDefaultNamespace("composter")),"Keep the actual seven-slab recipe");
                    assertFalse(proof.reachable().contains(Identifier.withDefaultNamespace("bone_meal")),"No bone meal without actual consumed compost");
                }
                var missing=NativeMarketSourceBuilder.build(source,recipes,noBannerLoot,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),noBannerLoot,transformers));
                assertFalse(missing.complete()); assertTrue(missing.bounds().credits().isEmpty());
            }
            for(String name:List.of("oak_fence","grindstone","lectern","waxed_lightning_rod","redstone","smithing_table","target","string",
                    "heavy_weighted_pressure_plate","compass","name_tag","writable_book","written_book")) {
                var item=Identifier.withDefaultNamespace(name);
                var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(new MarketAcquisitionBounds.Goods(item,1))));
                var proof=NativeMarketSourceBuilder.build(source,recipes,reloadable,currentPrototypes::get,
                        next -> NativeMarketDirectUseRules.read(next,currentPrototypes.get(next),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" full native source closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(item,1)>0 && proof.bounds().maximumPayout(item,1)<=12,name);
                if(name.equals("writable_book")) assertTrue(proof.reachable().contains(Identifier.withDefaultNamespace("written_book")));
            }
            var brush=Identifier.withDefaultNamespace("brush");
            var brushProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(brush,1)))),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(brushProof.complete(),() -> "Native brush closure held at "+brushProof.unprovenItem());
            assertEquals(0,brushProof.bounds().maximumPayout(Identifier.withDefaultNamespace("armadillo_scute"),4096));
            assertTrue(brushProof.bounds().maximumPayout(brush,1)>0);
            for(String name:List.of("egg","brown_egg","blue_egg")) {
                var egg=Identifier.withDefaultNamespace(name);
                var proof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(egg,1)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(proof.complete(),() -> name+" current adult-loot/smelting closure held at "+proof.unprovenItem());
                assertTrue(proof.bounds().maximumPayout(egg,1)>0 && proof.bounds().maximumPayout(egg,1)<=12);
                assertTrue(proof.reachable().contains(Identifier.withDefaultNamespace("cooked_chicken")));
                assertTrue(proof.bounds().maximumPayout(Identifier.withDefaultNamespace("feather"),1028)
                        +proof.bounds().maximumPayout(Identifier.withDefaultNamespace("cooked_chicken"),1024)<=12,
                        "Four maximum adult co-products and current smelting share one egg budget");
            }
            var changedEggRecipes=new ArrayList<Recipe<?>>(recipes);
            int chickenSmelting=recipeIds.indexOf("data/minecraft/recipe/cooked_chicken.json");
            assertTrue(chickenSmelting>=0);
            var changedSmelting=Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,reloadable),JsonParser.parseString("""
                {"type":"minecraft:smelting","ingredient":"minecraft:chicken","result":{"id":"minecraft:diamond","count":64},
                 "experience":0.0,"cookingtime":200}
                """)).getOrThrow();
            changedEggRecipes.set(chickenSmelting,changedSmelting);
            var changedEggProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(Identifier.withDefaultNamespace("egg"),1)))),changedEggRecipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(changedEggProof.complete(),() -> "Changed current death smelting held at "+changedEggProof.unprovenItem());
            assertTrue(changedEggProof.reachable().contains(Identifier.withDefaultNamespace("diamond")));
            assertTrue(changedEggProof.bounds().maximumPayout(Identifier.withDefaultNamespace("diamond"),4096)<=12,
                    "No hardcoded cooked-chicken assumption may bypass changed current smelting outputs/counts");
            var shears=Identifier.withDefaultNamespace("shears");
            assertTrue(NativeMarketDirectUseRules.read(shears,currentPrototypes.get(shears),reloadable,transformers).supported(),
                    "The actual shearing roots and referenced variants must decode in the current native dataset");
            var shearingProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(shears,1)))),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertFalse(shearingProof.complete(),"Shearing cannot bypass independently held golem/fertilizer callbacks");
            assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("bone_meal")),shearingProof.unprovenItem());
            assertTrue(shearingProof.reachable().contains(Identifier.withDefaultNamespace("oxidized_copper_golem_statue")),
                    "Golem lifecycle must not hide the later statue behind a two-golem proof");
            for(String pumpkin:List.of("carved_pumpkin","jack_o_lantern")) {
                var pumpkinId=Identifier.withDefaultNamespace(pumpkin);
                assertTrue(NativeMarketDirectUseRules.read(pumpkinId,currentPrototypes.get(pumpkinId),reloadable,transformers).supported(),pumpkin);
                var golemProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(pumpkinId,1)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(golemProof.complete(),"Independent fertilizer/door boundaries stay mandatory");
                assertTrue(golemProof.bounds().credits().isEmpty());
                assertTrue(golemProof.reachable().contains(Identifier.withDefaultNamespace("copper_chest")));
                assertTrue(golemProof.reachable().contains(Identifier.withDefaultNamespace("oxidized_copper_golem_statue")));
            }
            assertTrue(shearingProof.bounds().credits().isEmpty());
            System.out.println("Shearing source boundary: "+shearingProof.unprovenItem());
            for(String name:NativeMarketGolemStatueRules.NAMES) {
                var statue=Identifier.withDefaultNamespace(name);
                assertTrue(NativeMarketDirectUseRules.read(statue,currentPrototypes.get(statue),reloadable,transformers).supported(),name);
                var proof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(statue,1)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(proof.complete(),() -> "Current full statue/copper/door graph held at "+proof.unprovenItem());
                for(var door:NativeMarketDoorRules.family(reloadable))
                    assertEquals(0,proof.bounds().maximumPayout(BuiltInRegistries.ITEM.getKey(door.asItem()),4096));
                System.out.println("Statue source boundary: "+name+" -> "+proof.unprovenItem());
            }
            var doorFamily=NativeMarketDoorRules.family(reloadable);
            System.out.println("Native mixed-partner door family: "+doorFamily.size());
            for(var block:doorFamily) {
                var door=BuiltInRegistries.ITEM.getKey(block.asItem());
                var proof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(door,1)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertTrue(proof.complete(),() -> door+" held at "+proof.unprovenItem());
                for(var target:doorFamily) assertEquals(0,proof.bounds().maximumPayout(BuiltInRegistries.ITEM.getKey(target.asItem()),4096));
            }
            var witherSkull=Identifier.withDefaultNamespace("wither_skeleton_skull");
            var skullProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(witherSkull,1)))),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertTrue(skullProof.complete(),() -> "Current floor/wall skull and boss graph held at "+skullProof.unprovenItem());
            assertTrue(skullProof.reachable().contains(Identifier.withDefaultNamespace("nether_star")));
            assertTrue(skullProof.bounds().maximumPayout(Identifier.withDefaultNamespace("nether_star"),1)>0);
            assertTrue(skullProof.bounds().maximumPayout(Identifier.withDefaultNamespace("nether_star"),1)<=12);
            System.out.println("Wither skull full source closure: complete="+skullProof.complete());
            for(String name:List.of("brown_mushroom","red_mushroom")) {
                var mushroom=Identifier.withDefaultNamespace(name);
                var proof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(mushroom,16)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(proof.complete());
                assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("bone_meal")),proof.unprovenItem(),name);
                assertTrue(proof.bounds().credits().isEmpty());
            }
            var snapshot=NativeMarketSourceBuilder.build(MarketSourceClosure.catalogAcquisitions(),recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            assertFalse(snapshot.complete(),"Unproven current callbacks must continue holding all buyback prices");
            assertTrue(snapshot.bounds().credits().isEmpty());
            if(snapshot.reachable().isEmpty()) {
                for(int index=0;index<recipes.size();index++) if(!NativeMarketSourceBuilder.readRecipes(List.of(recipes.get(index)),reloadable).supported()) {
                    System.out.println("Pinned recipe payload blocker: "+recipeIds.get(index)); break;
                }
            }
            System.out.println("Pinned source closure diagnostic: reachable="+snapshot.reachable().size()
                    +", firstUnprovenItem="+snapshot.unprovenItem().map(Identifier::toString).orElse("BEFORE_ITEM_INSPECTION"));
            snapshot.unprovenItem().ifPresent(item -> {
                var prototype=currentPrototypes.get(item);
                var nativeItem=BuiltInRegistries.ITEM.getValue(item);
                boolean loot=true;
                if(nativeItem instanceof net.minecraft.world.item.BlockItem block && block.getBlock().getLootTable().isPresent()) {
                    var table=reloadable.lookupOrThrow(Registries.LOOT_TABLE).get(block.getBlock().getLootTable().orElseThrow());
                    loot=table.isPresent() && NativeMarketDropRules.read(item,table.get().value(),reloadable).supported();
                }
                System.out.println("Pinned item proof diagnostic: item="+item+", nativeClass="+nativeItem.getClass().getSimpleName()
                        +", consumption="+NativeMarketConsumptionRules.read(item,prototype).supported()+", ordinaryLoot="+loot
                        +", directUse="+NativeMarketDirectUseRules.read(item,prototype,reloadable,transformers).supported());
            });
            // Diagnostic only: missing project registrations must not conceal the next native callback blocker.
            // This subset is never published to MarketSourceCache or used for a quote; the full held snapshot above stays authoritative.
            var nativeRoots=MarketSourceClosure.catalogAcquisitions().stream()
                    .filter(source -> source.outputs().stream().allMatch(g -> g.item().getNamespace().equals("minecraft"))).toList();
            var nativeDiagnostic=NativeMarketSourceBuilder.build(nativeRoots,recipes,reloadable,currentPrototypes::get,
                    item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
            System.out.println("DIAGNOSTIC ONLY native callback blocker: "+nativeDiagnostic.unprovenItem().map(Identifier::toString).orElse("NONE")
                    +", complete="+nativeDiagnostic.complete());
            nativeDiagnostic.unprovenItem().ifPresent(item -> System.out.println("DIAGNOSTIC ONLY native class: "
                    +BuiltInRegistries.ITEM.getValue(item).getClass().getSimpleName()));
            // Group only this already-reached graph; no second recipe/data inventory and no price publication.
            // Concrete next production boundary: record only these existing crop roots' current
            // placement/loot definitions. No growth, harvest, callback, world or price publication.
            for(String name:List.of("wheat_seeds","carrot","potato","pumpkin_seeds")) {
                var crop=Identifier.withDefaultNamespace(name);
                assertTrue(nativeDiagnostic.reachable().contains(crop),"Only inspect already reached crop roots");
                var block=((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.getValue(crop)).getBlock();
                var table=reloadable.lookupOrThrow(Registries.LOOT_TABLE).get(block.getLootTable().orElseThrow()).orElseThrow().value();
                System.out.println("PRODUCTION DIAGNOSTIC "+crop+" default="+block.defaultBlockState()+" prototypeState="
                        +currentPrototypes.get(crop).get(DataComponents.BLOCK_STATE)+" loot="
                        +net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,reloadable),table).getOrThrow());
            }
            for(String name:List.of("wheat_seeds","carrot","potato","pumpkin_seeds","melon_seeds")) {
                var crop=Identifier.withDefaultNamespace(name);
                var direct=NativeMarketDirectUseRules.read(crop,currentPrototypes.get(crop),reloadable,transformers);
                assertTrue(direct.supported(),name+" has an exact fresh-placement callback proof");
                assertTrue(direct.rules().stream().anyMatch(rule -> rule.outputs().stream()
                        .anyMatch(goods -> goods.item().equals(Identifier.withDefaultNamespace("bone_meal")))),
                        "Consumed compost is an immediate source; it must not disappear behind natural growth");
                var cropProof=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,
                        List.of(new MarketAcquisitionBounds.Goods(crop,32)))),recipes,reloadable,currentPrototypes::get,
                        item -> NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers));
                assertFalse(cropProof.complete(),"No crop price may bypass the unresolved fertilizer callback");
                assertEquals(java.util.Optional.of(Identifier.withDefaultNamespace("bone_meal")),cropProof.unprovenItem(),name);
                assertTrue(cropProof.bounds().credits().isEmpty(),"Never publish partial crop credit");
            }
            var heldClasses=new java.util.TreeMap<String,List<Identifier>>();
            for(var item:nativeDiagnostic.reachable().stream().sorted().toList()) {
                if(!item.getNamespace().equals("minecraft") || NativeMarketDirectUseRules.read(item,currentPrototypes.get(item),reloadable,transformers).supported()) continue;
                var source=BuiltInRegistries.ITEM.getValue(item);
                String kind=source.getClass().getSimpleName();
                if(source instanceof net.minecraft.world.item.BlockItem block) kind+="/"+block.getBlock().getClass().getSimpleName();
                heldClasses.computeIfAbsent(kind,ignored -> new ArrayList<>()).add(item);
            }
            heldClasses.forEach((kind,items) -> System.out.println("DIAGNOSTIC ONLY reached held class: "+kind+", count="+items.size()+", examples="+items.stream().limit(3).toList()));
        }
    }
    /** Uses the actual native source result above, never injected commodity credits. */
    private static void verifyNewHomeRequests(MarketSourceClosure.Snapshot source) {
        var needs=List.of(de.quest.village.VillageNeed.GRANARY_SEED_RESERVES,de.quest.village.VillageNeed.GRANARY_PANTRY,
                de.quest.village.VillageNeed.APIARY_FORAGE,de.quest.village.VillageNeed.APIARY_WAX_STOCK,de.quest.village.VillageNeed.PASTURE_FODDER);
        var items=List.of("wheat","bread","honey_bottle","honeycomb","hay_block");
        for(int n=0;n<needs.size();n++) {
            var need=needs.get(n); var state=KingdomMarketPlacesTest.state(0); var home=KingdomMarketPlacesTest.TOWN;
            var snapshot=new de.quest.village.LivingVillageNetworkState.VillageSnapshot(0,0,0,need.villageType(),need,55,
                    de.quest.village.VillageCondition.fromSupport(55),0,0,0,0,0,0);
            assertTrue(state.markets().observeHomeNeed(home,KingdomMarketPlacesTest.OWNER,snapshot));
            assertTrue(state.marketSources().rebuild(() -> source));
            var request=state.markets().buybackOffer(home).orElseThrow();
            assertEquals(items.get(n),request.item().getPath()); assertEquals(0,request.stock());
            var plans=new ArrayList<KingdomMarketLedger.TraderPlan>();
            for(int slot=0;slot<4;slot++) {
                var person=state.people().ensure(home,KingdomPersonRegistry.Role.MARKET_TRADER,slot).orElseThrow();
                var plan=KingdomMarketVisitors.plan(state,person,need.villageType(),List.of(),1);
                assertTrue(plan.goods().size()<=8); assertEquals(1,plan.goods().stream().filter(g -> g.buy()>0).count());
                plans.add(new KingdomMarketLedger.TraderPlan(person.id(),home,List.of(request)));
            }
            var visit=state.markets().start(home,plans,0).orElseThrow();
            var actors=List.of(java.util.UUID.randomUUID(),java.util.UUID.randomUUID()); int accepted=0;
            for(var plan:plans) for(int lot=0;lot<request.demand() && accepted<128;lot++) {
                var actor=actors.get((accepted/request.lot())%2);
                var quote=state.markets().quote(actor,visit.id(),plan.person(),request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
                var stale=state.markets().quote(actors.get(1-actors.indexOf(actor)),visit.id(),plan.person(),request.sku(),KingdomMarketLedger.Side.SELL,1,15,15,0).orElseThrow();
                assertTrue(state.markets().sourceAllows(quote)); assertTrue(quote.price()>=1 && quote.price()<=2);
                var receipt=state.markets().prepare(quote,actor,0,request.lot(),true,0).orElseThrow();
                assertTrue(state.markets().prepare(stale,stale.actor(),0,request.lot(),true,0).isEmpty());
                assertTrue(state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems())); accepted+=request.lot();
            }
            assertEquals(128,accepted); var saved=state.markets().visit(home).orElseThrow();
            assertTrue(saved.traders().stream().allMatch(t -> t.budget()>=0 && t.budget()<=16));
            var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
            assertEquals(saved,restored.markets().visit(home).orElseThrow());
            assertTrue(restored.markets().buybackOffer(home).isEmpty());
            restored.marketSources().rebuild(() -> source); assertTrue(restored.markets().observeHomeNeed(home,KingdomMarketPlacesTest.OWNER,snapshot));
            assertTrue(restored.markets().buybackOffer(home).isEmpty(),"Reload cannot renew accepted home demand");
        }
    }
    private static net.minecraft.world.item.component.ItemContainerContents filledContainerContents() {
        return net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64)));
    }
    private static com.google.gson.JsonElement json(ZipFile zip,String name) throws Exception {
        try(var reader=new InputStreamReader(zip.getInputStream(zip.getEntry(name)),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}
