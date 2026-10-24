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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual native component initializer data; no apply(), entity, consumption or World execution. */
final class NativeMarketConsumptionRulesTest {
    private static Map<Identifier,DataComponentMap> prototypes;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var initial=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(initial);
        var lookup=NativeMarketTestData.lookup();
        var maps=new HashMap<Identifier,DataComponentMap>();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)) if(pending.key().equals(Registries.ITEM))
            pending.forEach((holder,components) -> maps.put(holder.key().identifier(),components));
        prototypes=Map.copyOf(maps);
    }
    @Test void actualPotionHoneyMilkAndStewsReturnTheirNativeContainers() {
        for(var entry:Map.of("potion","glass_bottle","honey_bottle","glass_bottle","milk_bucket","bucket",
                "mushroom_stew","bowl","rabbit_stew","bowl","beetroot_soup","bowl").entrySet()) {
            var data=prototypes.get(id(entry.getKey())); assertNotNull(data); assertTrue(data.has(DataComponents.CONSUMABLE));
            var reading=NativeMarketConsumptionRules.read(id(entry.getKey()),data);
            assertTrue(reading.supported(),entry.getKey()); assertEquals(1,reading.rules().size());
            assertEquals(List.of(g(entry.getValue(),1)),reading.rules().getFirst().outputs());
        }
    }
    @Test void liveComponentQuantityIsUsedAndSharesTheConsumedItemCredit() {
        var data=DataComponentMap.builder().addAll(prototypes.get(id("honey_bottle")))
                .set(DataComponents.USE_REMAINDER,new UseRemainder(new ItemStackTemplate(Items.GLASS_BOTTLE,3))).build();
        var reading=NativeMarketConsumptionRules.read(id("honey_bottle"),data);
        assertTrue(reading.supported()); assertEquals(List.of(g("glass_bottle",3)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(6,List.of(g("honey_bottle",1)))),List.of(),reading.rules());
        assertEquals(6,bounds.maximumPayout(id("glass_bottle"),3));
    }
    @Test void foodWithoutRemainderProducesNoNewTradeItem() {
        var reading=NativeMarketConsumptionRules.read(id("bread"),prototypes.get(id("bread")));
        assertTrue(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void missingPrototypeAndRemaindersWithHiddenPayloadHoldTheReading() {
        assertFalse(NativeMarketConsumptionRules.read(id("potion"),DataComponentMap.EMPTY).supported());
        var payload=net.minecraft.core.component.DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("unproven payload")).build();
        var data=DataComponentMap.builder().addAll(prototypes.get(id("potion")))
                .set(DataComponents.USE_REMAINDER,new UseRemainder(new ItemStackTemplate(Items.GLASS_BOTTLE.builtInRegistryHolder(),1,payload))).build();
        var reading=NativeMarketConsumptionRules.read(id("potion"),data);
        assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
