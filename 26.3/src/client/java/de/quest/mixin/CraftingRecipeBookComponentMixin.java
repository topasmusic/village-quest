package de.quest.mixin;

import de.quest.registry.ModItems;
import de.quest.registry.ModRecipeBookCategories;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingRecipeBookComponent.class)
public abstract class CraftingRecipeBookComponentMixin {
    @Shadow @Final @Mutable
    private static List<RecipeBookComponent.TabInfo> TABS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void villagequest$addRecipeTab(CallbackInfo ci) {
        if (ModRecipeBookCategories.VILLAGE_QUEST == null || ModItems.CARAVAN_LEDGER == null) return;
        List<RecipeBookComponent.TabInfo> tabs = new ArrayList<>(TABS);
        tabs.add(1, new RecipeBookComponent.TabInfo(
                new ItemStack(ModItems.CARAVAN_LEDGER), Optional.empty(),
                ModRecipeBookCategories.VILLAGE_QUEST));
        TABS = List.copyOf(tabs);
    }
}
