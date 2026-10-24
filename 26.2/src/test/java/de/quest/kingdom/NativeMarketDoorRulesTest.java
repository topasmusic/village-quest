package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Static registry/loot/credit proof only. No cell, neighbor, player or world executes. */
final class NativeMarketDoorRulesTest {
    private static HolderLookup.Provider lookup;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        lookup=NativeMarketTestData.lookup();
    }
    @Test void allNativePartnerDoorsAndTheirCurrentJointLootHaveZeroCredit() throws Exception {
        var family=NativeMarketDoorRules.family(lookup); assertTrue(family.size()>16);
        for(var block:family) {
            var item=BuiltInRegistries.ITEM.getKey(block.asItem());
            var reading=NativeMarketDoorRules.read(item,lookup); assertTrue(reading.supported(),item.toString());
            assertEquals(family.size(),reading.rules().size());
            var rules=new java.util.ArrayList<>(reading.rules());
            for(var target:family) {
                var targetItem=BuiltInRegistries.ITEM.getKey(target.asItem());
                var key=target.getLootTable().orElseThrow().identifier();
                try(var in=getClass().getResourceAsStream("/data/"+key.getNamespace()+"/loot_table/"+key.getPath()+".json")) {
                    assertNotNull(in); var loot=NativeMarketDropRules.read(targetItem,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)));
                    assertTrue(loot.supported()); rules.addAll(loot.rules());
                }
            }
            var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,
                    List.of(new MarketAcquisitionBounds.Goods(item,1)))),List.of(),rules);
            assertTrue(bounds.complete());
            for(var rule:rules) for(var goods:rule.outputs()) assertEquals(0,bounds.maximumPayout(goods.item(),4096),item+" -> "+goods.item());
        }
    }
    @Test void missingOrForeignDoorPartnerCannotBeSilentlyOmitted() {
        for(boolean foreign:List.of(false,true)) {
            var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
            BuiltInRegistries.BLOCK.listElements().filter(h -> foreign || h.value()!=Blocks.OAK_DOOR)
                    .forEach(h -> blocks.register(h.key(),h.value(),RegistrationInfo.BUILT_IN));
            if(foreign) blocks.register(ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("foreign","door")),
                    org.mockito.Mockito.mock(DoorBlock.class),RegistrationInfo.BUILT_IN);
            blocks.freeze();
            var changed=HolderLookup.Provider.create(Stream.concat(lookup.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
            assertFalse(NativeMarketDoorRules.read(id("copper_door"),changed).supported());
        }
        assertFalse(NativeMarketDoorRules.member(Blocks.OAK_TRAPDOOR));
        assertFalse(NativeMarketDoorRules.read(id("sunflower"),lookup).supported(),"Other double-high items need their own proof");
    }
    @Test void onlyFamilyTransformsFitTheTwoCellEnvelope() {
        var prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var target:List.of(Blocks.DIAMOND_BLOCK,Blocks.AIR,Blocks.OAK_TRAPDOOR)) {
            var transform=NativeMarketTestData.transforms(target);
            assertFalse(NativeMarketDirectUseRules.read(id("copper_door"),prototype,lookup,
                    new NativeMarketTransformerCatalog.Reading(true,transform)).supported(),target.toString());
        }
        var transformed=NativeMarketTestData.transforms(Blocks.OAK_DOOR);
        assertTrue(NativeMarketDirectUseRules.read(id("copper_door"),prototype,lookup,
                new NativeMarketTransformerCatalog.Reading(true,transformed)).supported());
        assertFalse(NativeMarketBlockTransformRules.read(id("stone"),transformed,lookup).supported(),"Other paid source cannot acquire door halves through an unproved transform");
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
}
