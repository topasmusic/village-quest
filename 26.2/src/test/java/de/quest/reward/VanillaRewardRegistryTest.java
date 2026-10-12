package de.quest.reward;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import org.junit.jupiter.api.Test;

final class VanillaRewardRegistryTest {
    @Test void wheatDailyHasExactSupplyAndFixedCurrencyAlternatives() {
        var options = VanillaRewardRegistry.options("daily:WHEAT_HARVEST", RepeatableTargetProfile.NORMAL);
        assertEquals(2, options.size()); assertEquals(12, options.getFirst().goods().getFirst().count());
        assertEquals(5, options.getLast().currency());
    }
    @Test void smithWeeklyUsesExplicitFiveSixSevenDiamonds() {
        for (var profile : RepeatableTargetProfile.values()) {
            var diamonds = VanillaRewardRegistry.options("weekly:SMITH_WEEK", profile).stream()
                    .filter(o -> o.id().equals("diamonds")).findFirst().orElseThrow();
            assertEquals(5 + profile.id(), diamonds.goods().getFirst().count());
            assertTrue(diamonds.diamondGate());
        }
    }
    @Test void potionsAndStoryEquipmentAreNeverScaled() {
        var a = VanillaRewardRegistry.options("pilgrim:ash_on_the_pass", RepeatableTargetProfile.HEAVY);
        assertEquals(2, a.getFirst().goods().getFirst().count());
        assertEquals("long_fire_resistance", a.getFirst().goods().getFirst().potion());
        var forge = VanillaRewardRegistry.options("story:silent_forge", RepeatableTargetProfile.NORMAL);
        assertEquals(4, forge.getFirst().goods().getFirst().enchantments().get("efficiency"));
        assertEquals(3, forge.getFirst().goods().getFirst().enchantments().get("unbreaking"));
        assertEquals(forge, VanillaRewardRegistry.options("story:silent_forge", RepeatableTargetProfile.HEAVY));
    }
    @Test void profilesRoundHalfUpWithoutStackCap() {
        assertEquals(56, VanillaRewardRegistry.scale(64, RepeatableTargetProfile.LIGHT));
        assertEquals(76, VanillaRewardRegistry.scale(64, RepeatableTargetProfile.HEAVY));
        assertEquals(1, VanillaRewardRegistry.scale(1, RepeatableTargetProfile.LIGHT));
    }
    @Test void farmTradeLegacyAndTransportHaveNoExtra() {
        for (String key : new String[]{"daily:VILLAGE_TRADING", "daily:WOOL_WEAVING", "daily:ZOMBIE_CULL", "weekly:MARKET_WEEK", "freight"})
            assertTrue(VanillaRewardRegistry.options(key, RepeatableTargetProfile.NORMAL).isEmpty());
    }
}
