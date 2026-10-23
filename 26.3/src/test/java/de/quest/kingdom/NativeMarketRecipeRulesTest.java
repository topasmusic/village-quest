package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual 26.3 recipe codecs and fixed result inspection, with no Level/Server or player crafting runtime. */
final class NativeMarketRecipeRulesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void shapelessUnpackingReadsTheActualNineWheatResult() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:hay_block"],"result":{"id":"minecraft:wheat","count":9}}
            """));
        assertTrue(reading.supported()); var rule=reading.rules().getFirst();
        assertEquals(1,rule.inputs().size()); assertEquals(id("hay_block"),rule.inputs().getFirst().choices().getFirst().item());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("wheat"),9)),rule.outputs());
    }
    @Test void repeatedIngredientsAndShapedEmptyPositionsAreCountedExactly() {
        var book=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_shaped","pattern":["PP","PL"],"key":{"P":"minecraft:paper","L":"minecraft:leather"},"result":{"id":"minecraft:book"}}
            """));
        assertTrue(book.supported()); var inputs=book.rules().getFirst().inputs();
        assertEquals(4,inputs.size());
        assertEquals(3,inputs.stream().filter(i -> i.choices().getFirst().item().equals(id("paper"))).count());
        assertEquals(1,inputs.stream().filter(i -> i.choices().getFirst().item().equals(id("leather"))).count());
    }
    @Test void nativeHoneyCraftingRemaindersArePartOfTheSameOutputBudget() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_shaped","pattern":["##","##"],"key":{"#":"minecraft:honey_bottle"},"result":{"id":"minecraft:honey_block"}}
            """));
        assertTrue(reading.supported()); var rule=reading.rules().getFirst();
        assertEquals(4,rule.inputs().size());
        assertTrue(rule.outputs().contains(new MarketAcquisitionBounds.Goods(id("honey_block"),1)));
        assertTrue(rule.outputs().contains(new MarketAcquisitionBounds.Goods(id("glass_bottle"),4)));
        var result=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(20,List.of(
                new MarketAcquisitionBounds.Goods(id("honey_bottle"),4)))),List.of(),reading.rules());
        assertTrue(result.complete());
        assertTrue(result.maximumPayout(id("honey_block"),1)+result.maximumPayout(id("glass_bottle"),4)<=20);
    }
    @Test void nativeCookingAndStonecuttingReadRealOutputCountsWithoutAWorld() {
        var smelt=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:smelting","ingredient":"minecraft:raw_iron","experience":0.7,"cookingtime":200,"result":{"id":"minecraft:iron_ingot"}}
            """));
        assertTrue(smelt.supported()); assertEquals(id("raw_iron"),smelt.rules().getFirst().inputs().getFirst().choices().getFirst().item());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("iron_ingot"),1)),smelt.rules().getFirst().outputs());
        var cut=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:stonecutting","ingredient":"minecraft:stone","result":{"id":"minecraft:stone_brick_slab","count":2}}
            """));
        assertTrue(cut.supported()); assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("stone_brick_slab"),2)),cut.rules().getFirst().outputs());
    }
    @Test void unknownRecipeImplementationsNeverReturnAPartialCompleteGraph() {
        var fixed=recipe("""
            {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:hay_block"],"result":{"id":"minecraft:wheat","count":9}}
            """);
        Recipe<?> foreign=org.mockito.Mockito.mock(Recipe.class);
        var reading=NativeMarketRecipeRules.readAll(List.of(fixed,foreign));
        assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
        assertFalse(NativeMarketRecipeRules.read(null).supported());
        assertFalse(NativeMarketRecipeRules.readAll(List.of()).supported());
    }
    @Test void brewingSharesOneReagentAcrossOneToThreeBottleResults() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:brewing","input":{"item":"minecraft:potion"},
             "reagent":{"item":"minecraft:gunpowder"},"output":{"id":"minecraft:splash_potion"}}
            """));
        assertTrue(reading.supported()); assertEquals(3,reading.rules().size());
        for(int count=1;count<=3;count++) {
            var rule=reading.rules().get(count-1);
            assertEquals(count+1,rule.inputs().size());
            assertEquals(1,rule.inputs().stream().filter(i -> i.choices().getFirst().item().equals(id("gunpowder"))).count());
            assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("splash_potion"),count)),rule.outputs());
        }
        var bounds=MarketAcquisitionBounds.solve(List.of(
                new MarketAcquisitionBounds.Acquisition(3,List.of(new MarketAcquisitionBounds.Goods(id("potion"),3))),
                new MarketAcquisitionBounds.Acquisition(3,List.of(new MarketAcquisitionBounds.Goods(id("gunpowder"),1)))),List.of(),reading.rules());
        assertEquals(6,bounds.maximumPayout(id("splash_potion"),3));
    }
    @Test void transmuteCountsEveryAllowedMaterialPositionAndScaledOutput() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_transmute","input":"minecraft:white_wool","material":"minecraft:red_dye",
             "material_count":{"min":1,"max":3},"add_material_count_to_result":true,
             "result":{"id":"minecraft:red_wool","count":1}}
            """));
        assertTrue(reading.supported()); assertEquals(3,reading.rules().size());
        for(int count=1;count<=3;count++) {
            var rule=reading.rules().get(count-1); assertEquals(count+1,rule.inputs().size());
            assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("red_wool"),count+1)),rule.outputs());
        }
    }
    @Test void transmuteKeepingInputDoesNotCreateCrossVariantOutputs() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_transmute","input":["minecraft:white_wool","minecraft:red_wool"],
             "material":"minecraft:paper","result":{}}
            """));
        assertTrue(reading.supported()); assertEquals(2,reading.rules().size());
        for(var rule:reading.rules()) {
            var wool=rule.inputs().stream().filter(i -> !i.choices().getFirst().item().equals(id("paper"))).findFirst().orElseThrow();
            assertEquals(1,wool.choices().size()); assertEquals(wool.choices().getFirst().item(),rule.outputs().getFirst().item());
        }
    }
    @Test void dyeEnumeratesOneToEightDyesAndImbueConsumesEightMaterials() {
        var dye=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_dye","target":"minecraft:leather_leggings","dye":"minecraft:red_dye",
             "result":{"id":"minecraft:leather_leggings"}}
            """));
        assertTrue(dye.supported()); assertEquals(8,dye.rules().size());
        assertEquals(9,dye.rules().getLast().inputs().size());
        var imbue=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:crafting_imbue","source":"minecraft:lingering_potion","material":"minecraft:arrow",
             "result":{"id":"minecraft:tipped_arrow","count":8}}
            """));
        assertTrue(imbue.supported()); var rule=imbue.rules().getFirst(); assertEquals(9,rule.inputs().size());
        assertEquals(8,rule.inputs().stream().filter(i -> i.choices().getFirst().item().equals(id("arrow"))).count());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("tipped_arrow"),8)),rule.outputs());
    }
    @Test void brewingReturnsOnlyTheSingleReagentContainerForAThreeBottleBatch() {
        var reading=NativeMarketRecipeRules.read(recipe("""
            {"type":"minecraft:brewing","input":{"item":"minecraft:splash_potion"},
             "reagent":{"item":"minecraft:dragon_breath"},"output":{"id":"minecraft:lingering_potion"}}
            """));
        assertTrue(reading.supported()); var outputs=reading.rules().getLast().outputs();
        assertTrue(outputs.contains(new MarketAcquisitionBounds.Goods(id("glass_bottle"),1)));
        assertTrue(outputs.contains(new MarketAcquisitionBounds.Goods(id("lingering_potion"),3)));
        assertEquals(2,outputs.size());
    }
    @Test void expandedGraphBudgetAndMissingLiveLookupHoldTheEntireReading() {
        var brewing=recipe("""
            {"type":"minecraft:brewing","input":{"item":"minecraft:potion"},
             "reagent":{"item":"minecraft:gunpowder"},"output":{"id":"minecraft:splash_potion"}}
            """);
        var held=NativeMarketRecipeRules.readAll(java.util.Collections.nCopies(2731,brewing));
        assertFalse(held.supported()); assertTrue(held.rules().isEmpty());
        assertFalse(NativeMarketRecipeRules.read(brewing,null).supported());
    }
    @Test void fixedRecipesUseTheCurrentRegisteredOutputAndInputKeysIncludingProjectNamespace() {
        // Private registry key fixture only: it does not register project items globally or prove plaque callbacks.
        var own=Identifier.fromNamespaceAndPath("village-quest","fixed_output_fixture");
        var registry=new net.minecraft.core.MappedRegistry<net.minecraft.world.item.Item>(net.minecraft.core.registries.Registries.ITEM,com.mojang.serialization.Lifecycle.stable());
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            var key=holder.value()==net.minecraft.world.item.Items.STICK?net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,own):holder.key();
            var copy=registry.register(key,holder.value(),net.minecraft.core.RegistrationInfo.BUILT_IN); copy.bindComponents(holder.components());
        }); registry.freeze();
        var nativeLookup=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var lookup=net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.concat(nativeLookup.listRegistries()
                .filter(r -> !r.key().equals(net.minecraft.core.registries.Registries.ITEM)),java.util.stream.Stream.of(registry)));
        var common=new Recipe.CommonInfo(true);
        var book=new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(net.minecraft.world.item.crafting.CraftingBookCategory.MISC,"");
        var ownHolder=registry.get(own).orElseThrow();
        var output=new net.minecraft.world.item.crafting.ShapelessRecipe(common,book,new net.minecraft.world.item.ItemStackTemplate(ownHolder,2,
                net.minecraft.core.component.DataComponentPatch.EMPTY),List.of(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.WHEAT)));
        var reading=NativeMarketRecipeRules.read(output,lookup); assertTrue(reading.supported());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(own,2)),reading.rules().getFirst().outputs());
        var unpack=new net.minecraft.world.item.crafting.ShapelessRecipe(common,book,new net.minecraft.world.item.ItemStackTemplate(net.minecraft.world.item.Items.WHEAT,3),
                List.of(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.core.HolderSet.direct(ownHolder))));
        var returned=NativeMarketRecipeRules.read(unpack,lookup); assertTrue(returned.supported());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(own,1)),returned.rules().getFirst().inputs().getFirst().choices());
        assertEquals(List.of(new MarketAcquisitionBounds.Goods(id("wheat"),3)),returned.rules().getFirst().outputs());
        var combined=NativeMarketRecipeRules.readAll(List.of(output,unpack),lookup); assertTrue(combined.supported());
        assertFalse(MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(8,List.of(new MarketAcquisitionBounds.Goods(own,1)))),combined,
                item -> new NativeMarketRecipeRules.Reading(false,List.of())).complete(),"Recipe amounts do not bypass unproven item callbacks");
    }
    private static Recipe<?> recipe(String json) {
        var lookup=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return Recipe.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),JsonParser.parseString(json)).getOrThrow();
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
}
