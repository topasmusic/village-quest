package de.quest.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ModCreativeTabsTest {
    @Test
    void exposesEveryCurrentItemAndKeepsCompatibilityItemsHidden() {
        Set<String> expected = Set.of(
                "SILVERMARK", "CROWN", "MAGIC_SHARD",
                "CARAVAN_LEDGER", "SURVEYORS_COMPASS", "STARREACH_RING", "MERCHANT_SEAL",
                "SHEPHERD_FLUTE", "APIARISTS_SMOKER", "ROADWARDEN_HORN",
                "APIARY_CHARTER_PLAQUE", "VILLAGE_LEDGER_PLAQUE", "FORGE_CHARTER_PLAQUE",
                "MARKET_CHARTER_PLAQUE", "PASTURE_CHARTER_PLAQUE", "WATCH_BELL_RELIQUARY",
                "CARTOGRAPHERS_LENS", "CRACKED_SHRINE_CORE", "RESTORED_SHRINE_CORE",
                "WAYFARERS_SIGIL", "GUILD_WAYSHRINE", "GUILD_NOTICE_POST",
                "EMBERGLASS_LANTERN", "GUILD_MILESTONE", "GUILD_COURIERS_SATCHEL");

        Set<String> actual = Arrays.stream(ModCreativeTabs.Entry.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(expected, actual);
    }

    @Test
    void reusesTheInventoryJournalArtworkForTheTabIcon() {
        ClassLoader resources = ModCreativeTabsTest.class.getClassLoader();

        assertNotNull(resources.getResource("assets/village-quest/textures/gui/journal_inventory_button.png"));
        assertNotNull(resources.getResource("assets/village-quest/items/journal_inventory_button.json"));
        assertNotNull(resources.getResource("assets/village-quest/models/item/journal_inventory_button.json"));
    }
}
