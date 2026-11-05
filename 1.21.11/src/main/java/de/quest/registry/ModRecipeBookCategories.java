package de.quest.registry;

import de.quest.VillageQuest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeBookCategory;

public final class ModRecipeBookCategories {
    public static RecipeBookCategory VILLAGE_QUEST;

    private ModRecipeBookCategories() {}

    public static void register() {
        if (VILLAGE_QUEST != null) return;
        VILLAGE_QUEST = Registry.register(
                BuiltInRegistries.RECIPE_BOOK_CATEGORY,
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "village_quest"),
                new RecipeBookCategory());
        VillageQuest.LOGGER.info("Registered Village Quest recipe-book category");
    }
}
