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
        super.extractRenderState(unwrap(context), mouseX, mouseY, delta);
    }

    protected void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.extractBackground(unwrap(context), mouseX, mouseY, delta);
    }

    private static GuiGraphicsExtractor unwrap(GuiGraphics context) {
        try {
            return (GuiGraphicsExtractor) context.getClass().getMethod("unwrap").invoke(context);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            return (GuiGraphicsExtractor) context.getClass().getMethod("extractor").invoke(context);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Village Quest could not unwrap GuiGraphics for screen rendering", exception);
        }
    }
}
