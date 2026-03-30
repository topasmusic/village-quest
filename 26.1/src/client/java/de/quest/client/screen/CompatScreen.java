package de.quest.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        render(new GuiGraphics(extractor), mouseX, mouseY, delta);
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context.extractor(), mouseX, mouseY, delta);
    }

    protected void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context.extractor(), mouseX, mouseY, delta);
    }
}
