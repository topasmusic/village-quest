package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Stream;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
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
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native prototypes/tags only; no drinking, bottle filling, hive use or world. */
final class NativeMarketBottleRulesTest {
    private static HolderLookup.Provider base,lookup;
    private static Map<Identifier,DataComponentMap> prototypes;
    private static NativeMarketTransformerCatalog.Reading catalog;
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var initial=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(initial);
        base=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
        var maps=new HashMap<Identifier,DataComponentMap>();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(base)) if(pending.key().equals(Registries.ITEM))
            pending.forEach((holder,components) -> maps.put(holder.key().identifier(),components));
        prototypes=Map.copyOf(maps);
        catalog=NativeMarketTransformerCatalog.read(base,List.of(id("glass_bottle"),id("potion")),prototypes::get);
        var blocks=blocks(); var raw=new HashMap<Identifier,List<TagLoader.EntryWithSource>>();
        try(var stream=Block.class.getResourceAsStream("/data/minecraft/tags/block/convertible_to_mud.json")) {
            assertNotNull(stream); var file=TagFile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(
                    new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
            raw.put(id("convertible_to_mud"),file.entries().stream().map(entry -> new TagLoader.EntryWithSource(entry,"pinned native mud tag")).toList());
        }
        var built=new TagLoader<Holder<Block>>((id,required) -> blocks.get(id),"tags/block").build(raw);
        assertEquals(1,built.size());
        var tags=new HashMap<TagKey<Block>,List<Holder<Block>>>(); built.forEach((id,values) -> tags.put(TagKey.create(Registries.BLOCK,id),values));
        blocks.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,tags)).apply(); lookup=with(blocks);
    }
    @Test void immediateBottleFillingsExcludeHoneyThatRequiresStoredHiveProduction() {
        var reading=read("glass_bottle",lookup,prototypes.get(id("glass_bottle")));
        assertTrue(reading.supported()); assertEquals(2,reading.rules().size());
        assertEquals(Set.of(List.of(g("potion",1)),List.of(g("dragon_breath",1))),
                reading.rules().stream().map(MarketAcquisitionBounds.RecipeConversion::outputs).collect(java.util.stream.Collectors.toSet()));
        assertTrue(reading.rules().stream().allMatch(rule -> rule.inputs().getFirst().choices().equals(List.of(g("glass_bottle",1)))));
    }
    @Test void mudConversionRequiresTheActualConsumedDirtAndIncludesBothProducts() {
        var reading=read("potion",lookup,prototypes.get(id("potion")));
        assertTrue(reading.supported()); var rule=reading.rules().getFirst(); assertEquals(2,rule.inputs().size());
        assertTrue(rule.inputs().stream().anyMatch(position -> position.choices().equals(List.of(g("potion",1)))));
        assertTrue(rule.inputs().stream().anyMatch(position -> position.choices().contains(g("dirt",1))));
        assertEquals(List.of(g("glass_bottle",1),g("mud",1)),rule.outputs());
    }
    @Test void fixedSplashBottleDoesNotReplaceTheDifferentLiveConsumptionRemainder() {
        var data=DataComponentMap.builder().addAll(prototypes.get(id("potion")))
                .set(DataComponents.USE_REMAINDER,new UseRemainder(new ItemStackTemplate(Items.GLASS_BOTTLE,3))).build();
        assertTrue(read("potion",lookup,data).rules().getFirst().outputs().contains(g("glass_bottle",1)));
        assertEquals(List.of(g("glass_bottle",3)),NativeMarketConsumptionRules.read(id("potion"),data).rules().getFirst().outputs());
    }
    @Test void missingMudTagIsUnknownButAnExplicitEmptyTagProvesNoConversion() {
        var unbound=blocks(); assertFalse(read("potion",with(unbound),prototypes.get(id("potion"))).supported());
        var empty=blocks(); empty.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK,Map.of(BlockTags.CONVERTIBLE_TO_MUD,List.of()))).apply();
        var reading=read("potion",with(empty),prototypes.get(id("potion")));
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    private static NativeMarketRecipeRules.Reading read(String name,HolderLookup.Provider current,DataComponentMap data) {
        return NativeMarketDirectUseRules.read(id(name),data,current,catalog);
    }
    private static MappedRegistry<Block> blocks() {
        var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        BuiltInRegistries.BLOCK.listElements().forEach(holder -> blocks.register(holder.key(),holder.value(),RegistrationInfo.BUILT_IN));
        blocks.freeze(); return blocks;
    }
    private static HolderLookup.Provider with(MappedRegistry<Block> blocks) {
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
