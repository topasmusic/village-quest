package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native codecs and pure native result semantics, without a Level, player or menu execution. */
final class NativeMarketSpecialRecipeRulesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(components);
    }
    @Test void smithingConsumesTemplateBaseAndAdditionWithoutCraftingRemainders() {
        var reading=read("""
            {"type":"minecraft:smithing_transform","template":"minecraft:netherite_upgrade_smithing_template",
             "base":"minecraft:diamond_pickaxe","addition":"minecraft:netherite_ingot","result":{"id":"minecraft:netherite_pickaxe"}}
            """);
        assertTrue(reading.supported()); var rule=reading.rules().getFirst(); assertEquals(3,rule.inputs().size());
        assertEquals(List.of(g("netherite_pickaxe",1)),rule.outputs());
        var container=read("""
            {"type":"minecraft:smithing_transform","base":"minecraft:stone","addition":"minecraft:honey_bottle",
             "result":{"id":"minecraft:gold_ingot","count":2}}
            """);
        assertTrue(container.supported()); assertEquals(2,container.rules().getFirst().inputs().size());
        assertEquals(List.of(g("gold_ingot",2)),container.rules().getFirst().outputs());
    }
    @Test void optionalSmithingInputsAreNotInvented() {
        var reading=read("""
            {"type":"minecraft:smithing_transform","base":"minecraft:stone","result":{"id":"minecraft:iron_ingot","count":4}}
            """);
        assertTrue(reading.supported()); assertEquals(1,reading.rules().getFirst().inputs().size());
        assertEquals(List.of(g("iron_ingot",4)),reading.rules().getFirst().outputs());
    }
    @Test void bannerCopyReturnsItsPatternSourceAndUsesTheActualConfiguredResultCount() {
        var reading=read("""
            {"type":"minecraft:crafting_special_bannerduplicate","banner":"minecraft:white_banner",
             "result":{"id":"minecraft:white_banner","count":2}}
            """);
        assertTrue(reading.supported()); var rule=reading.rules().getFirst();
        assertEquals(2,rule.inputs().size()); assertEquals(List.of(g("white_banner",3)),rule.outputs());
    }
    @Test void bookCopyCountsBlankBooksAndKeepsItsSourceInTheSameBudget() {
        var reading=read("""
            {"type":"minecraft:crafting_special_bookcloning","source":"minecraft:written_book",
             "material":"minecraft:writable_book","result":{"id":"minecraft:written_book","count":2}}
            """);
        assertTrue(reading.supported()); assertEquals(8,reading.rules().size());
        for(int count=1;count<=8;count++) {
            var rule=reading.rules().get(count-1); assertEquals(count+1,rule.inputs().size());
            // Native assemble adds materialCount-1 to the template's result count; the source is returned once.
            assertEquals(List.of(g("written_book",count+2)),rule.outputs());
        }
    }
    @Test void mapKeepInputIsCorrelatedAndConsumesEightPapers() {
        var reading=read("""
            {"type":"minecraft:crafting_special_mapextending","map":["minecraft:filled_map","minecraft:map"],
             "material":"minecraft:paper","result":{}}
            """);
        assertTrue(reading.supported()); assertEquals(2,reading.rules().size());
        for(var rule:reading.rules()) {
            assertEquals(9,rule.inputs().size());
            assertEquals(8,rule.inputs().stream().filter(c -> c.choices().getFirst().item().equals(id("paper"))).count());
            assertEquals(rule.outputs().getFirst().item(),rule.inputs().stream()
                    .filter(c -> !c.choices().getFirst().item().equals(id("paper"))).findFirst().orElseThrow().choices().getFirst().item());
        }
    }
    @Test void potConsumesFourDistinctPatternPositions() {
        var reading=read("""
            {"type":"minecraft:crafting_decorated_pot","back":"minecraft:brick","front":"minecraft:archer_pottery_sherd",
             "left":"minecraft:brick","right":"minecraft:arms_up_pottery_sherd","result":{"id":"minecraft:decorated_pot"}}
            """);
        assertTrue(reading.supported()); var rule=reading.rules().getFirst(); assertEquals(4,rule.inputs().size());
        assertEquals(2,rule.inputs().stream().filter(c -> c.choices().getFirst().item().equals(id("brick"))).count());
        assertEquals(List.of(g("decorated_pot",1)),rule.outputs());
    }
    @Test void shieldDecorationConsumesTheBannerAndPreservesConfiguredResult() {
        var reading=read("""
            {"type":"minecraft:crafting_special_shielddecoration","banner":"minecraft:white_banner",
             "target":"minecraft:shield","result":{"id":"minecraft:shield"}}
            """);
        assertTrue(reading.supported()); assertEquals(2,reading.rules().getFirst().inputs().size());
        assertEquals(List.of(g("shield",1)),reading.rules().getFirst().outputs());
    }
    @Test void trimIsProvenQuantityNeutralUsingTheNativeOutputFunction() {
        var material=Holder.direct(org.mockito.Mockito.mock(TrimMaterial.class));
        var pattern=Holder.direct(org.mockito.Mockito.mock(TrimPattern.class));
        var base=new ItemStack(Items.IRON_CHESTPLATE,4);
        var addition=new ItemStack(Items.IRON_INGOT); addition.set(DataComponents.PROVIDES_TRIM_MATERIAL,material);
        var actual=SmithingTrimRecipe.applyTrim(base,addition,pattern);
        assertEquals(base.getItem(),actual.getItem()); assertEquals(1,actual.getCount());
        var recipe=new SmithingTrimRecipe(new Recipe.CommonInfo(true),Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                Ingredient.of(Items.IRON_CHESTPLATE),Ingredient.of(Items.IRON_INGOT),pattern);
        var reading=NativeMarketRecipeRules.read(recipe);
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void repairCannotIncreaseAnyItemIdentityOrQuantity() {
        var left=new ItemStack(Items.IRON_PICKAXE); var right=new ItemStack(Items.IRON_PICKAXE);
        for(var stack:List.of(left,right)) {
            stack.set(DataComponents.MAX_DAMAGE,250); stack.set(DataComponents.DAMAGE,150);
        }
        var actual=RepairItemRecipe.INSTANCE.assemble(CraftingInput.of(2,1,List.of(left,right)));
        assertEquals(Items.IRON_PICKAXE,actual.getItem()); assertEquals(1,actual.getCount());
        var reading=NativeMarketRecipeRules.read(RepairItemRecipe.INSTANCE);
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void rocketsCoverAllFuelAndStarCountsWithinNineSlots() {
        var reading=read("""
            {"type":"minecraft:crafting_special_firework_rocket","fuel":"minecraft:gunpowder","shell":"minecraft:paper",
             "star":"minecraft:firework_star","result":{"id":"minecraft:firework_rocket","count":3}}
            """);
        assertTrue(reading.supported()); assertEquals(21,reading.rules().size());
        for(var rule:reading.rules()) {
            long fuels=rule.inputs().stream().filter(c -> c.choices().getFirst().item().equals(id("gunpowder"))).count();
            assertTrue(fuels>=1 && fuels<=3); assertTrue(rule.inputs().size()<=9);
            assertEquals(List.of(g("firework_rocket",3)),rule.outputs());
        }
        assertTrue(reading.rules().stream().anyMatch(r -> r.inputs().size()==2));
        assertTrue(reading.rules().stream().anyMatch(r -> r.inputs().size()==9));
    }
    @Test void starsIncludeAllOptionalModifiersAndTheirNativeContainers() {
        var reading=read("""
            {"type":"minecraft:crafting_special_firework_star","fuel":"minecraft:gunpowder","dye":"minecraft:red_dye",
             "trail":"minecraft:diamond","twinkle":"minecraft:glowstone_dust",
             "shapes":{"burst":"minecraft:honey_bottle"},"result":{"id":"minecraft:firework_star"}}
            """);
        assertTrue(reading.supported()); assertEquals(52,reading.rules().size());
        assertTrue(reading.rules().stream().anyMatch(r -> r.inputs().size()==2 && r.outputs().equals(List.of(g("firework_star",1)))));
        for(var rule:reading.rules()) {
            assertTrue(rule.inputs().size()<=9); assertTrue(rule.outputs().contains(g("firework_star",1)));
            boolean shape=rule.inputs().stream().anyMatch(c -> c.choices().getFirst().item().equals(id("honey_bottle")));
            assertEquals(shape,rule.outputs().contains(g("glass_bottle",1)));
        }
    }
    @Test void starFadeConsumesOneStarAndOneToEightDyes() {
        var reading=read("""
            {"type":"minecraft:crafting_special_firework_star_fade","target":"minecraft:firework_star",
             "dye":"minecraft:red_dye","result":{"id":"minecraft:firework_star","count":2}}
            """);
        assertTrue(reading.supported()); assertEquals(8,reading.rules().size());
        for(int i=0;i<8;i++) {
            assertEquals(i+2,reading.rules().get(i).inputs().size());
            assertEquals(List.of(g("firework_star",2)),reading.rules().get(i).outputs());
        }
    }
    private static NativeMarketRecipeRules.Reading read(String json) {
        var lookup=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var recipe=Recipe.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),JsonParser.parseString(json)).getOrThrow();
        return NativeMarketRecipeRules.read(recipe,lookup);
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
