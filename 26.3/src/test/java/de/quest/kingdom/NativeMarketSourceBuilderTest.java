package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native source lookup fixtures only: no server, world, reload or loot execution. */
final class NativeMarketSourceBuilderTest {
    private static HolderLookup.Provider base;
    private static DataComponentMap prototype;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        prototype=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(prototype);
        base=VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
    }
    @Test void actualBlockLootKeyAddsDropsAndDownstreamRecipesToTheClosure() {
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:book","modifier":{"type":"minecraft:set_count","count":3}}]}]}
            """)).getOrThrow();
        var snapshot=build(lookup(table),recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:writable_book"}}
            """),NativeMarketSourceBuilderTest::neutral);
        assertTrue(snapshot.complete()); assertTrue(snapshot.reachable().contains(id("writable_book")));
        assertEquals(4,snapshot.bounds().maximumPayout(id("book"),1));
        assertEquals(12,snapshot.bounds().maximumPayout(id("writable_book"),3));
    }
    @Test void editableCurrentPrototypeOnAnotherItemMustIncludeServerPacketSigning() {
        var editable=DataComponentMap.builder().addAll(prototype).set(DataComponents.WRITABLE_BOOK_CONTENT,
                net.minecraft.world.item.component.WritableBookContent.EMPTY).build();
        var unrelated=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """);
        var source=List.of(new MarketAcquisitionBounds.Acquisition(4,List.of(g("feather",64))));
        var result=NativeMarketSourceBuilder.build(source,List.of(unrelated),base,
                item -> item.equals(id("feather"))?editable:prototype,NativeMarketSourceBuilderTest::neutral);
        assertTrue(result.complete()); assertTrue(result.reachable().contains(id("written_book")),
                "The signing packet checks a component, not the original item's class");
        assertEquals(4,result.bounds().maximumPayout(id("written_book"),64));
    }
    @Test void importedSignTextOnEitherSideCannotCarryCommandAuthorityIntoTheItemOnlyProof() {
        var text=net.minecraft.world.level.block.entity.SignText.EMPTY.asMutable().setLine(0,
                net.minecraft.network.chat.Component.literal("claim").withStyle(style -> style.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent.RunCommand("give @s minecraft:diamond 64")))).asImmutable();
        var source=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("feather",1))));
        var recipes=List.of(recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """));
        assertTrue(NativeMarketSourceBuilder.build(source,recipes,base,item -> prototype,NativeMarketSourceBuilderTest::neutral).complete(),
                "The control must prove an ordinary prototype before importing either sign text component");
        for(var component:List.of(DataComponents.SIGN_TEXT_FRONT,DataComponents.SIGN_TEXT_BACK)) {
            var empty=DataComponentMap.builder().addAll(prototype).set(component,net.minecraft.world.level.block.entity.SignText.EMPTY).build();
            assertTrue(NativeMarketSourceBuilder.build(source,recipes,base,item -> empty,NativeMarketSourceBuilderTest::neutral).complete());
            var imported=DataComponentMap.builder().addAll(prototype).set(component,text).build();
            var held=NativeMarketSourceBuilder.build(source,recipes,base,item -> imported,NativeMarketSourceBuilderTest::neutral);
            assertFalse(held.complete(),"Neither front nor back sign text is an ordinary item SKU proof");
            assertTrue(held.bounds().credits().isEmpty());
        }
    }
    @Test void recipeSignTextPatchesAreHeldEvenWhenTheirEncodedLinesLookEmpty() {
        for(String side:List.of("sign_text_front","sign_text_back")) {
            var changed=recipe("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:book\"],\"result\":{\"id\":\"minecraft:oak_sign\",\"components\":{\"minecraft:"+side+"\":{\"messages\":[\"\",\"\",\"\",\"\"]}}}}");
            assertFalse(NativeMarketSourceBuilder.readRecipes(List.of(changed),base).supported(),side);
        }
    }
    @Test void freshCropLootProofStillRequiresItsIndependentDirectUseAndRejectsImportedAge() throws Exception {
        var block=((net.minecraft.world.item.BlockItem)Items.CARROT).getBlock();
        LootTable table;
        try(var in=getClass().getResourceAsStream("/data/minecraft/loot_table/blocks/carrots.json")) {
            assertNotNull(in);
            table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),
                    JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
        }
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        registry.register(block.getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN); registry.freeze();
        var lookup=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
        var acquisitions=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("carrot",1))));
        var recipes=List.of(recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var held=NativeMarketSourceBuilder.build(acquisitions,recipes,lookup,item -> prototype,item -> {
            calls.incrementAndGet(); return new NativeMarketRecipeRules.Reading(false,List.of());
        });
        assertEquals(1,calls.get(),"Fresh ordinary loot passes before the independent mandatory callback gate");
        assertFalse(held.complete()); assertTrue(held.bounds().credits().isEmpty());
        var imported=DataComponentMap.builder().addAll(prototype).set(DataComponents.BLOCK_STATE,
                new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("age","7"))).build();
        var denied=NativeMarketSourceBuilder.build(acquisitions,recipes,lookup,item -> imported,NativeMarketSourceBuilderTest::neutral);
        assertFalse(denied.complete()); assertTrue(denied.bounds().credits().isEmpty());
    }
    @Test void freshStemLootChargesSeedlingYieldAndCannotImportMatureProduction() {
        for(String[] kind:List.of(new String[]{"pumpkin_seeds","pumpkin_stem"},new String[]{"melon_seeds","melon_stem"})) {
            var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),JsonParser.parseString("""
                {"type":"minecraft:block","pools":[
                {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:diamond",
                  "condition":{"type":"minecraft:match_block","blocks":"minecraft:%s","state":{"age":"0"}},
                  "modifier":{"type":"minecraft:set_count","count":2}}]},
                {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:emerald",
                  "condition":{"type":"minecraft:match_block","blocks":"minecraft:%s","state":{"age":"7"}},
                  "modifier":{"type":"minecraft:set_count","count":64}}]}]}
                """.formatted(kind[1],kind[1]))).getOrThrow();
            var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
            var block=((net.minecraft.world.item.BlockItem)BuiltInRegistries.ITEM.getValue(id(kind[0]))).getBlock();
            registry.register(block.getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN); registry.freeze();
            var lookup=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
            var recipes=List.of(recipe("""
                {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
                """));
            var sources=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g(kind[0],1))));
            // Neutral callbacks isolate the changed loot/state reader; the full native dataset
            // independently asserts the unresolved BoneMeal callback still holds both roots.
            var proof=NativeMarketSourceBuilder.build(sources,recipes,lookup,item -> prototype,NativeMarketSourceBuilderTest::neutral);
            assertTrue(proof.complete()); assertTrue(proof.reachable().contains(id("diamond")));
            assertFalse(proof.reachable().contains(id("emerald")),"Mature yield needs later production or a separate fertilizer input");
            assertEquals(12,proof.bounds().maximumPayout(id("diamond"),2));
            var imported=DataComponentMap.builder().addAll(prototype).set(DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("age","7"))).build();
            var held=NativeMarketSourceBuilder.build(sources,recipes,lookup,item -> imported,NativeMarketSourceBuilderTest::neutral);
            assertFalse(held.complete()); assertTrue(held.bounds().credits().isEmpty());
        }
    }
    @Test void nativeSlabLootCannotTurnTheSecondPlacedSlabIntoFreeGoods() throws Exception {
        LootTable table;
        try(var in=getClass().getResourceAsStream("/data/minecraft/loot_table/blocks/oak_slab.json")) {
            assertNotNull(in); table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),
                    JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
        }
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        registry.register(net.minecraft.world.level.block.Blocks.OAK_SLAB.getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN); registry.freeze();
        var lookup=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
        var recipes=List.of(recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """));
        var acquisitions=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("oak_slab",1))));
        var snapshot=NativeMarketSourceBuilder.build(acquisitions,recipes,lookup,item -> prototype,NativeMarketSourceBuilderTest::neutral);
        assertTrue(snapshot.complete()); assertEquals(12,snapshot.bounds().maximumPayout(id("oak_slab"),1),"Two placed slabs must pay for two dropped slabs");
        var imported=DataComponentMap.builder().addAll(prototype).set(DataComponents.BLOCK_STATE,
                new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("type","double"))).build();
        assertFalse(NativeMarketSourceBuilder.build(acquisitions,recipes,lookup,item -> imported,NativeMarketSourceBuilderTest::neutral).complete());
    }
    @Test void storedComposterFillCannotBecomeFreeBoneMealFromOnePurchasedBlock() {
        var table=LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:composter"}]}]}
            """)).getOrThrow();
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        registry.register(net.minecraft.world.level.block.Blocks.COMPOSTER.getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN); registry.freeze();
        var lookup=HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
        var recipes=List.of(recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """));
        var sources=List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("composter",1))));
        assertTrue(NativeMarketSourceBuilder.build(sources,recipes,lookup,item -> prototype,NativeMarketSourceBuilderTest::neutral).complete());
        for(int fill=1;fill<=8;fill++) {
            var filled=DataComponentMap.builder().addAll(prototype).set(DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of("level",Integer.toString(fill)))).build();
            var held=NativeMarketSourceBuilder.build(sources,recipes,lookup,item -> filled,NativeMarketSourceBuilderTest::neutral);
            assertFalse(held.complete(),"Imported compost level "+fill); assertTrue(held.bounds().credits().isEmpty());
        }
    }
    @Test void absentLiveLootKeyHoldsButExplicitNativeEmptyTableIsAProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """);
        assertFalse(build(lookup(null),recipe,NativeMarketSourceBuilderTest::neutral).complete());
        var snapshot=build(lookup(LootTable.EMPTY),recipe,NativeMarketSourceBuilderTest::neutral);
        assertTrue(snapshot.complete()); assertFalse(snapshot.reachable().contains(id("book")));
    }
    @Test void nativeRecipeOutputWithUseRemainderPatchCannotBypassThePrototypeProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper","components":{"minecraft:use_remainder":{"id":"minecraft:iron_ingot","count":64}}}}
            """);
        var snapshot=build(lookup(LootTable.EMPTY),recipe,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertEquals(0,snapshot.bounds().maximumPayout(id("paper"),1));
    }
    @Test void recipeBeePayloadCannotBypassTheEmptyPrototypeEntityProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper","components":{"minecraft:bees":[]}}}
            """);
        var snapshot=build(lookup(LootTable.EMPTY),recipe,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertTrue(snapshot.bounds().credits().isEmpty());
    }
    @Test void recipePlacementStateCannotBypassTheDefaultPairedBlockProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:black_bed","components":{"minecraft:block_state":{"part":"head"}}}}
            """);
        var snapshot=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("book",1)))),
                List.of(recipe),base,item -> prototype,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertTrue(snapshot.bounds().credits().isEmpty());
    }
    @Test void recipeCushionColorPatchCannotBypassTheCurrentPrototypeRecoveryProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:black_cushion","components":{"minecraft:cushion/color":"white"}}}
            """);
        var snapshot=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("book",1)))),
                List.of(recipe),base,item -> prototype,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertTrue(snapshot.bounds().credits().isEmpty());
    }
    @Test void missingComponentDataAndUnprovenDirectUseHoldAllPartialCredits() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """);
        assertFalse(build(lookup(LootTable.EMPTY),recipe,item -> new NativeMarketRecipeRules.Reading(false,List.of())).complete());
        var snapshot=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("bookshelf",1)))),
                List.of(recipe),lookup(LootTable.EMPTY),item -> null,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertTrue(snapshot.bounds().credits().isEmpty());
    }
    @Test void livePrototypeWithContainerContentsCannotUseAnItemOnlyPriceProof() {
        var recipe=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """);
        var filled=DataComponentMap.builder().addAll(prototype).set(DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new net.minecraft.world.item.ItemStack(Items.IRON_INGOT,64)))).build();
        var snapshot=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("bookshelf",1)))),
                List.of(recipe),lookup(LootTable.EMPTY),item -> filled,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); assertEquals(0,snapshot.bounds().maximumPayout(id("bookshelf"),1));
    }
    @Test void loadedProjectilesAndRecipeEnchantmentsCannotBypassPlainPrototypeProofs() {
        var ordinary=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:book"],"result":{"id":"minecraft:paper"}}
            """);
        var filled=DataComponentMap.builder().addAll(prototype).set(DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(List.of(new net.minecraft.world.item.ItemStack(Items.ARROW)))).build();
        var loaded=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("book",1)))),
                List.of(ordinary),base,item -> filled,NativeMarketSourceBuilderTest::neutral);
        assertFalse(loaded.complete()); assertTrue(loaded.bounds().credits().isEmpty());
        for(String patch:List.of("\"minecraft:charged_projectiles\":[]","\"minecraft:enchantments\":{\"minecraft:infinity\":1}")) {
            var changed=recipe("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:book\"],\"result\":{\"id\":\"minecraft:paper\",\"components\":{"+patch+"}}}");
            var held=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("book",1)))),
                    List.of(changed),base,item -> prototype,NativeMarketSourceBuilderTest::neutral);
            assertFalse(held.complete(),patch); assertTrue(held.bounds().credits().isEmpty());
        }
    }
    @Test void foreignRecipeCodecsAreNeverInvokedEvenForPayloadInspection() {
        Recipe<?> foreign=org.mockito.Mockito.mock(Recipe.class);
        var snapshot=NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("book",1)))),
                List.of(foreign),base,item -> prototype,NativeMarketSourceBuilderTest::neutral);
        assertFalse(snapshot.complete()); org.mockito.Mockito.verifyNoInteractions(foreign);
    }
    @Test void nativeServerBuilderRefusesOffThreadBeforeReadingAnyLiveData() {
        var server=org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        org.mockito.Mockito.when(server.isSameThread()).thenReturn(false);
        assertFalse(NativeMarketSourceBuilder.build(server).complete());
        org.mockito.Mockito.verify(server).isSameThread(); org.mockito.Mockito.verifyNoMoreInteractions(server);
        assertFalse(NativeMarketSourceBuilder.build((net.minecraft.server.MinecraftServer)null).complete());
    }
    private static MarketSourceClosure.Snapshot build(HolderLookup.Provider lookup,Recipe<?> recipe,
            java.util.function.Function<Identifier,NativeMarketRecipeRules.Reading> direct) {
        return NativeMarketSourceBuilder.build(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("bookshelf",1)))),
                List.of(recipe),lookup,item -> prototype,direct);
    }
    private static HolderLookup.Provider lookup(LootTable table) {
        var registry=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
        if(table!=null) registry.register(((net.minecraft.world.item.BlockItem)Items.BOOKSHELF).getBlock().getLootTable().orElseThrow(),table,RegistrationInfo.BUILT_IN);
        registry.freeze();
        return HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(r -> !r.key().equals(Registries.LOOT_TABLE)),Stream.of(registry)));
    }
    private static Recipe<?> recipe(String json) { return Recipe.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,base),JsonParser.parseString(json)).getOrThrow(); }
    private static NativeMarketRecipeRules.Reading neutral(Identifier item) { return new NativeMarketRecipeRules.Reading(true,List.of()); }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
