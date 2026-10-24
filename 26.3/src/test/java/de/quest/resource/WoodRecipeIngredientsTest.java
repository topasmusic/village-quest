package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Decode the actual recipe ingredients using the pinned Minecraft plank tag, without global registry edits. */
final class WoodRecipeIngredientsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allWoodenRecipesAcceptEveryNativePlankIncludingNetherAndBamboo() throws Exception {
        var items = new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable());
        BuiltInRegistries.ITEM.listElements().forEach(holder -> items.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
        items.freeze();
        List<Identifier> plankIds;
        try (var stream = Item.class.getResourceAsStream("/data/minecraft/tags/item/planks.json")) {
            assertNotNull(stream);
            plankIds = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values").asList().stream()
                    .map(value -> Identifier.parse(value.getAsString())).toList();
        }
        var holders = plankIds.stream().map(id -> (Holder<Item>) items.get(id).orElseThrow()).toList();
        items.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, Map.of(ItemTags.PLANKS, holders))).apply();
        var base = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var lookup = HolderLookup.Provider.create(Stream.concat(base.listRegistries()
                .filter(registry -> !registry.key().equals(Registries.ITEM)), Stream.of(items)));
        var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        for (var recipe : List.of("council_seat", "market_desk", "market_stall", "guild_notice_post")) {
            var data = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/village-quest/recipe/" + recipe + ".json"))).getAsJsonObject();
            var key = data.getAsJsonObject("key");
            var wood = Ingredient.CODEC.parse(ops, key.get(recipe.equals("guild_notice_post") ? "W" : "P")).getOrThrow();
            var accepted = wood.items().map(holder -> holder.unwrapKey().orElseThrow().identifier()).toList();
            assertEquals(java.util.Set.copyOf(plankIds), java.util.Set.copyOf(accepted), recipe);
            assertTrue(accepted.contains(Identifier.withDefaultNamespace("bamboo_planks")));
            assertTrue(accepted.contains(Identifier.withDefaultNamespace("crimson_planks")));
            assertFalse(accepted.contains(Identifier.withDefaultNamespace("dirt")));
        }
    }

    @Test void differentVillageItemsKeepSeparateRecipeBookGroups() throws Exception {
        var groups = new java.util.HashSet<String>();
        try (var paths = Files.list(Path.of("src/main/resources/data/village-quest/recipe"))) {
            for (var path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                var data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertTrue(groups.add(data.get("group").getAsString()), path.toString());
            }
        }
    }
}
