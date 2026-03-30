package de.quest.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int villageQuest$getX();

    @Accessor("y")
    int villageQuest$getY();

    @Accessor("backgroundWidth")
    int villageQuest$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int villageQuest$getBackgroundHeight();
}
