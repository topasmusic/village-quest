package de.quest.mixin;

import de.quest.registry.ModRecipeBookCategories;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingRecipe.class)
public interface CraftingRecipeMixin {
    @Inject(method = "recipeBookCategory", at = @At("HEAD"), cancellable = true)
    private void villagequest$useVillageQuestCategory(CallbackInfoReturnable<RecipeBookCategory> cir) {
        CraftingRecipe recipe = (CraftingRecipe) (Object) this;
        if ("village_quest".equals(recipe.group()) && ModRecipeBookCategories.VILLAGE_QUEST != null) {
            cir.setReturnValue(ModRecipeBookCategories.VILLAGE_QUEST);
        }
    }
}
