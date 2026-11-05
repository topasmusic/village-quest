package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class RecipeSearchCategoryTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @SuppressWarnings("unchecked")
    private static List<RecipeBookCategory> include(boolean crafting, List<RecipeBookCategory> base,
            RecipeBookCategory village) throws Exception {
        var method = Class.forName("de.quest.client.ui.VillageQuestRecipeSearch").getDeclaredMethod("include",
                boolean.class, List.class, RecipeBookCategory.class);
        return (List<RecipeBookCategory>) method.invoke(null, crafting, base, village);
    }
    @Test void generalCraftingSearchIncludesVillageQuestAlongsideEveryVanillaCategory() throws Exception {
        var village = new RecipeBookCategory();
        var vanilla = SearchRecipeBookCategory.CRAFTING.includedCategories();
        var included = include(true, vanilla, village);
        assertTrue(included.containsAll(vanilla));
        assertTrue(included.contains(village));
        assertEquals(vanilla.size() + 1, included.size());
        assertEquals(included, include(true, included, village));
    }
    @Test void furnaceSearchAndEarlyInitializationKeepTheirOwnCategories() throws Exception {
        var vanilla = SearchRecipeBookCategory.FURNACE.includedCategories();
        assertEquals(vanilla, include(false, vanilla, new RecipeBookCategory()));
        assertEquals(vanilla, include(true, vanilla, null));
    }
}
