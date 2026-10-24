package de.quest.kingdom;

import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;

/** Live native consumable/remainder prototypes only; no entity, use callback or second inventory. */
public final class NativeMarketConsumptionRules {
    private static final Set<Class<?>> EFFECTS=Set.of(ApplyStatusEffectsConsumeEffect.class,ClearAllStatusEffectsConsumeEffect.class,
            PlaySoundConsumeEffect.class,RemoveStatusEffectsConsumeEffect.class,TeleportRandomlyConsumeEffect.class);
    private NativeMarketConsumptionRules() {}
    public static NativeMarketRecipeRules.Reading read(Identifier item,DataComponentMap prototype) {
        try {
            if(NativeMarketProjectPlaques.registered(item)) return NativeMarketProjectPlaques.consumption(item,
                    BuiltInRegistries.ITEM.get(item).orElseThrow().value(),prototype);
            if(item==null || !item.getNamespace().equals("minecraft") || !BuiltInRegistries.ITEM.containsKey(item)
                    || item.getPath().equals("air") || prototype==null) return unknown();
            var max=prototype.get(DataComponents.MAX_STACK_SIZE);
            if(max==null || max<1 || max>99) return unknown();
            var consumable=prototype.get(DataComponents.CONSUMABLE); var remainder=prototype.get(DataComponents.USE_REMAINDER);
            if(consumable==null) return remainder==null ? new NativeMarketRecipeRules.Reading(true,List.of()) : unknown();
            if(consumable.onConsumeEffects().stream().anyMatch(effect -> !EFFECTS.contains(effect.getClass()))) return unknown();
            if(remainder==null) return new NativeMarketRecipeRules.Reading(true,List.of());
            var output=remainder.convertInto();
            // Patched containers could hide inventories, decorations or further use conversions; require a separate proof.
            if(!output.components().isEmpty() || output.item().value()==Items.AIR || output.count()<1) return unknown();
            var input=new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)));
            var goods=new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(output.item().value()),output.count());
            return new NativeMarketRecipeRules.Reading(true,List.of(new MarketAcquisitionBounds.RecipeConversion(List.of(input),List.of(goods))));
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
