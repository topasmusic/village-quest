package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Registry/codec evidence only; no world, growth, neighbor, feature or loot callback is run. */
final class NativeMarketBoneMealRulesTest {
    private static HolderLookup.Provider base;
    private static Map<TagKey<Block>,List<Identifier>> nativeTags;
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        base=NativeMarketTestData.lookup();
        var resource=Block.class.getResource("/data/minecraft/tags/block/underwater_bonemeals.json");
        assertNotNull(resource);
        var connection=(JarURLConnection)resource.openConnection();
        var raw=new HashMap<Identifier,List<TagLoader.EntryWithSource>>();
        try(var zip=new ZipFile(java.nio.file.Path.of(connection.getJarFileURL().toURI()).toFile())) {
            for(var entry:zip.stream().filter(e -> e.getName().startsWith("data/minecraft/tags/block/") && e.getName().endsWith(".json")).toList()) {
                var name=entry.getName().substring("data/minecraft/tags/block/".length(),entry.getName().length()-5);
                try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)) {
                    var tag=TagFile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(reader)).getOrThrow();
                    raw.put(id(name),tag.entries().stream().map(e -> new TagLoader.EntryWithSource(e,"pinned native jar")).toList());
                }
            }
        }
        var resolved=new TagLoader<Holder<Block>>((id,required) -> BuiltInRegistries.BLOCK.get(id),"tags/block").build(raw);
        var tags=new HashMap<TagKey<Block>,List<Identifier>>();
        for(var key:List.of(BlockTags.WALL_CORALS,BlockTags.UNDERWATER_BONEMEALS))
            tags.put(key,resolved.get(key.location()).stream().map(h -> BuiltInRegistries.BLOCK.getKey(h.value())).toList());
        nativeTags=Map.copyOf(tags);
    }
    @Test void currentWaterTagsIncludeTallSeagrassAliveAndDeadCoralHarvestsWithZeroCredit() throws Exception {
        NativeMarketBoneMealRules.water(id("bone_meal"),blocks(nativeTags,false),new LinkedHashSet<>());
        var reading=NativeMarketBoneMealRules.readWater(id("bone_meal"),blocks(nativeTags,false));
        assertTrue(reading.supported());
        var outputs=outputs(reading);
        assertTrue(outputs.contains(id("seagrass")));
        for(var color:List.of("tube","brain","bubble","fire","horn"))
            for(var suffix:List.of("_coral","_coral_fan")) {
                assertTrue(outputs.contains(id(color+suffix)),color+suffix);
                assertTrue(outputs.contains(id("dead_"+color+suffix)),"dead_"+color+suffix);
            }
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,
                List.of(new MarketAcquisitionBounds.Goods(id("bone_meal"),1)))),List.of(),reading.rules());
        assertTrue(bounds.complete());
        for(var output:outputs) if(!output.equals(id("bone_meal"))) assertEquals(0,bounds.maximumPayout(output,64),output.toString());
        assertFalse(NativeMarketBoneMealRules.read(id("bone_meal"),blocks(nativeTags,false)).supported(),
                "Actual native feature-driven plants remain HELD; a water branch cannot approve the item");
    }
    @Test void changedCurrentTagsAndLootAreBothReadRatherThanUsingShippedOutputNames() {
        var changed=new HashMap<>(nativeTags);
        var members=new ArrayList<>(changed.get(BlockTags.UNDERWATER_BONEMEALS)); members.add(id("diamond_block"));
        changed.put(BlockTags.UNDERWATER_BONEMEALS,members);
        var lookup=blocks(changed,false);
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                {"type":"minecraft:block","pools":[{"rolls":64,"entries":[{"type":"minecraft:item","name":"minecraft:diamond"}]}]}
                """))).getOrThrow();
        var reading=NativeMarketBoneMealRules.readWater(id("bone_meal"),loot(lookup,Blocks.DIAMOND_BLOCK.getLootTable().orElseThrow(),table));
        assertTrue(reading.supported()); assertTrue(outputs(reading).containsAll(Set.of(id("diamond_block"),id("diamond"))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,
                List.of(new MarketAcquisitionBounds.Goods(id("bone_meal"),1)))),List.of(),reading.rules());
        assertEquals(0,bounds.maximumPayout(id("diamond"),64));
    }
    @Test void missingTagsLootOrUnknownWaterCallbacksRemainHeld() {
        assertFalse(NativeMarketBoneMealRules.readWater(id("bone_meal"),NativeMarketTestData.withoutBlockTags(base)).supported());
        var lookup=blocks(nativeTags,false);
        assertFalse(NativeMarketBoneMealRules.readWater(id("bone_meal"),loot(lookup,Blocks.TALL_SEAGRASS.getLootTable().orElseThrow(),null)).supported());
        assertTrue(NativeMarketBoneMealRules.readWater(id("bone_meal"),loot(lookup,Blocks.TALL_SEAGRASS.getLootTable().orElseThrow(),LootTable.EMPTY)).supported(),
                "Explicit current EMPTY is known; missing data is not");
        for(var name:List.of("oak_sapling","chest","carrots")) {
            var changed=new HashMap<>(nativeTags);
            changed.put(BlockTags.UNDERWATER_BONEMEALS,List.of(id(name)));
            assertFalse(NativeMarketBoneMealRules.readWater(id("bone_meal"),blocks(changed,false)).supported(),name);
        }
        assertFalse(NativeMarketBoneMealRules.readWater(id("bread"),lookup).supported());
    }
    @Test void boundedCropRegistryCanCompleteButOneUnknownTargetHoldsTheWholeDispatcher() {
        // Artificial narrow registry proves dispatch composition, NOT full vanilla readiness.
        var lookup=blocks(nativeTags,true);
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        var reading=NativeMarketDirectUseRules.read(id("bone_meal"),prototype,lookup,new NativeMarketTransformerCatalog.Reading(true,List.of()));
        assertTrue(reading.supported());
        assertTrue(outputs(reading).containsAll(Set.of(id("wheat"),id("carrot"),id("potato"),id("poisonous_potato"))));
        assertFalse(NativeMarketDirectUseRules.read(id("bone_meal"),prototype,blocks(nativeTags,false),
                new NativeMarketTransformerCatalog.Reading(true,List.of())).supported());
    }
    @Test void countOnlyEnvelopeCannotApproveAnIdentityChangingLootFunction() {
        var lookup=blocks(nativeTags,false);
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item",
                "name":"minecraft:seagrass","modifier":{"type":"minecraft:furnace_smelt"}}]}]}
                """))).getOrThrow();
        assertFalse(NativeMarketBoneMealRules.readWater(id("bone_meal"),
                loot(lookup,Blocks.SEAGRASS.getLootTable().orElseThrow(),table)).supported());
    }
    @Test void exactSimpleTargetsReadMatureLootDirectFlowersAndCurrentBerryInteraction() {
        var lookup=blocks(nativeTags,false);
        var outputs=new LinkedHashSet<Identifier>();
        for(var block:List.of(Blocks.BEETROOTS,Blocks.COCOA,Blocks.SUNFLOWER,Blocks.PINK_PETALS,
                Blocks.TORCHFLOWER_CROP,Blocks.SWEET_BERRY_BUSH))
            NativeMarketBoneMealRules.land(id("bone_meal"),block,lookup,outputs);
        assertTrue(outputs.containsAll(Set.of(id("beetroot"),id("beetroot_seeds"),id("cocoa_beans"),
                id("sunflower"),id("pink_petals"),id("torchflower"),id("torchflower_seeds"),id("sweet_berries"))));
        assertThrows(IllegalArgumentException.class,() -> NativeMarketBoneMealRules.land(id("bone_meal"),Blocks.OAK_SAPLING,lookup,new LinkedHashSet<>()));
        var key=net.minecraft.world.level.storage.loot.BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH;
        assertThrows(RuntimeException.class,() -> NativeMarketBoneMealRules.land(id("bone_meal"),Blocks.SWEET_BERRY_BUSH,
                loot(lookup,key,null),new LinkedHashSet<>()));
        var changed=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),NativeMarketTestData.nativeLoot(JsonParser.parseString("""
                {"type":"minecraft:block_interact","pools":[{"rolls":64,"entries":[{"type":"minecraft:item","name":"minecraft:diamond"}]}]}
                """))).getOrThrow();
        var changedOutputs=new LinkedHashSet<Identifier>();
        NativeMarketBoneMealRules.land(id("bone_meal"),Blocks.SWEET_BERRY_BUSH,loot(lookup,key,changed),changedOutputs);
        assertTrue(changedOutputs.contains(id("diamond")),"Actual current interaction loot is mandatory alongside block loot");
    }
    private static HolderLookup.Provider blocks(Map<TagKey<Block>,List<Identifier>> tags,boolean cropOnly) {
        var registry=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        for(var holder:BuiltInRegistries.BLOCK.listElements().toList()) {
            var block=holder.value();
            if(cropOnly && block instanceof BonemealableBlock && block!=Blocks.SEAGRASS
                    && !Set.of(CropBlock.class,CarrotBlock.class,PotatoBlock.class).contains(block.getClass())) continue;
            registry.register(holder.key(),block,RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        var resolved=new HashMap<TagKey<Block>,List<Holder<Block>>>();
        tags.forEach((key,ids) -> resolved.put(key,ids.stream().<Holder<Block>>map(id -> registry.get(id).orElseThrow()).toList()));
        registry.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,resolved)).apply();
        var replaced=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(registry)));
        // Native crop loot holds block-state predicates. Rebind those holders to this copied
        // fixture registry rather than encoding stale foreign-owner holders as current data.
        var loot=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        for(var holder:base.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList()) {
            var table=holder.value();
            if(Set.of("blocks/wheat","blocks/carrots","blocks/potatoes","blocks/beetroots","blocks/cocoa",
                    "blocks/sunflower","blocks/pink_petals","blocks/torchflower_crop","blocks/torchflower",
                    "blocks/sweet_berry_bush","harvest/sweet_berry_bush").contains(holder.key().identifier().getPath())
                    && (!cropOnly || Set.of("blocks/wheat","blocks/carrots","blocks/potatoes").contains(holder.key().identifier().getPath())))
                table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,replaced),NativeMarketTestData.nativeLoot(
                        LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,base),table).getOrThrow())).getOrThrow();
            loot.register(holder.key(),table,RegistrationInfo.BUILT_IN);
        }
        loot.freeze();
        return HolderLookup.Provider.create(Stream.concat(replaced.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(loot)));
    }
    private static HolderLookup.Provider loot(HolderLookup.Provider lookup,ResourceKey<LootTable> key,LootTable replacement) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        for(var holder:lookup.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList()) if(!holder.key().equals(key))
            registry.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN);
        if(replacement!=null) registry.register(key,replacement,RegistrationInfo.BUILT_IN); registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(lookup.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Set<Identifier> outputs(NativeMarketRecipeRules.Reading reading) {
        return reading.rules().stream().flatMap(r -> r.outputs().stream()).map(g -> g.item()).collect(java.util.stream.Collectors.toSet());
    }
    private static Identifier id(String name) { return Identifier.withDefaultNamespace(name); }
}
