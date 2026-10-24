package de.quest.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeBookCategory;

/** Keep the dedicated tab and include its recipes in Vanilla's general crafting search. */
public final class VillageQuestRecipeSearch {
    private VillageQuestRecipeSearch() {}

    public static List<RecipeBookCategory> include(boolean crafting, List<RecipeBookCategory> base,
            RecipeBookCategory village) {
        if (!crafting || village == null || base.contains(village)) return base;
        var result = new ArrayList<>(base);
        result.add(village);
        return List.copyOf(result);
    }
}
