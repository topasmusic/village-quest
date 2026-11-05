package de.quest.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("leftPos")
    int villageQuest$getX();

    @Accessor("topPos")
    int villageQuest$getY();

    @Accessor("imageWidth")
    int villageQuest$getBackgroundWidth();

    @Accessor("imageHeight")
    int villageQuest$getBackgroundHeight();
}
