package de.quest.mixin;

import de.quest.client.ui.VillageQuestRecipeSearch;
import de.quest.registry.ModRecipeBookCategories;
import java.util.List;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SearchRecipeBookCategory.class)
public abstract class SearchRecipeBookCategoryMixin {
    @Inject(method = "includedCategories", at = @At("RETURN"), cancellable = true)
    private void villagequest$includeCraftingRecipes(CallbackInfoReturnable<List<RecipeBookCategory>> cir) {
        cir.setReturnValue(VillageQuestRecipeSearch.include(
                (Object) this == SearchRecipeBookCategory.CRAFTING, cir.getReturnValue(),
                ModRecipeBookCategories.VILLAGE_QUEST));
    }
}
