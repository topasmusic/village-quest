package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native 26.3 codecs, with mod IDs aliased to the native two-block door. No world or loot execution. */
final class ModLootTableCodecTest {
    private static final Path TABLES = Path.of("src/main/resources/data/village-quest/loot_table/blocks");
    private static HolderLookup.Provider lookup;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
    }

    @Test void everyShippedBlockLootTableDecodesWithTheMinecraft263Codec() throws Exception {
        try (var files = Files.list(TABLES)) {
            var paths = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertFalse(paths.isEmpty());
            for (var path : paths) {
                var result = LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup), fixture(path));
                assertTrue(result.isSuccess(), () -> path + ": " + result.error());
            }
        }
    }

    @Test void wayshrineUsesRegisteredConditionTypes() throws Exception {
        var pool = JsonParser.parseString(Files.readString(TABLES.resolve("guild_wayshrine.json")))
                .getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject();
        var guards = pool.has("conditions") ? pool.getAsJsonArray("conditions")
                : pool.getAsJsonObject("condition").getAsJsonArray("terms");
        for (var guard : guards) {
            var value = guard.getAsJsonObject();
            var type = value.get(value.has("type") ? "type" : "condition").getAsString();
            assertTrue(BuiltInRegistries.LOOT_CONDITION_TYPE.containsKey(Identifier.parse(type)),
                    "Unknown loot condition type: " + type);
        }
    }

    @Test void wayshrineRetainsItsLowerHalfAndExplosionGuardsAfterNativeDecoding() throws Exception {
        var source = JsonParser.parseString(Files.readString(TABLES.resolve("guild_wayshrine.json")))
                .getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("village-quest:guild_wayshrine", source.getAsJsonArray("entries")
                .get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("village-quest:guild_wayshrine", source.getAsJsonObject("condition")
                .getAsJsonArray("terms").get(0).getAsJsonObject().get("blocks").getAsString());
        var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        var table = LootTable.DIRECT_CODEC.parse(ops, fixture(TABLES.resolve("guild_wayshrine.json"))).getOrThrow();
        var encoded = LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow().getAsJsonObject();
        var pools = encoded.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        var pool = pools.get(0).getAsJsonObject();
        var condition = pool.getAsJsonObject("condition");
        assertNotNull(condition, "Native decoding must retain both drop guards");
        assertEquals("minecraft:all_of", condition.get("type").getAsString());
        var guards = condition.getAsJsonArray("terms");
        assertEquals(2, guards.size());
        var state = guards.get(0).getAsJsonObject();
        assertEquals("minecraft:match_block", state.get("type").getAsString());
        assertEquals("minecraft:oak_door", state.get("blocks").getAsString());
        assertEquals("lower", state.getAsJsonObject("state").get("half").getAsString());
        assertEquals("minecraft:survives_explosion", guards.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals(1, pool.get("rolls").getAsInt());
        var entries = pool.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals("minecraft:oak_door", entries.get(0).getAsJsonObject().get("name").getAsString());
    }

    private static JsonElement fixture(Path path) throws Exception {
        var data = JsonParser.parseString(Files.readString(path));
        aliasModIds(data);
        return data;
    }

    private static void aliasModIds(JsonElement data) {
        // Item codecs resolve the frozen built-in item registry. Substitute identifiers only;
        // leave all codec fields, condition IDs and property names/values exactly as shipped.
        if (data.isJsonArray()) {
            data.getAsJsonArray().forEach(ModLootTableCodecTest::aliasModIds);
        } else if (data instanceof JsonObject object) {
            for (var entry : object.entrySet()) {
                var value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && value.getAsString().startsWith("village-quest:")) {
                    entry.setValue(new com.google.gson.JsonPrimitive("minecraft:oak_door"));
                } else {
                    aliasModIds(value);
                }
            }
        }
    }
}
