package de.quest.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    private static final float MAX_PANEL_WIDTH_RATIO = 0.82f;
    private static final float MAX_PANEL_HEIGHT_RATIO = 0.86f;

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

    /**
     * Keeps Village Quest's fixed-layout boards from becoming disproportionately large when
     * Minecraft raises its automatic GUI scale in fullscreen mode. The transform is centered,
     * so existing authored coordinates and textures remain pixel-consistent.
     */
    protected float beginResponsivePanel(GuiGraphics context, int panelWidth, int panelHeight) {
        float scale = responsivePanelScale(panelWidth, panelHeight);
        if (scale < 0.999f) {
            var matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate(this.width / 2.0f, this.height / 2.0f);
            matrices.scale(scale, scale);
            matrices.translate(-this.width / 2.0f, -this.height / 2.0f);
        }
        return scale;
    }

    protected void endResponsivePanel(GuiGraphics context, float scale) {
        if (scale < 0.999f) {
            context.pose().popMatrix();
        }
    }

    protected int responsiveMouseX(double mouseX, int panelWidth, int panelHeight) {
        float scale = responsivePanelScale(panelWidth, panelHeight);
        return Math.round(this.width / 2.0f + ((float) mouseX - this.width / 2.0f) / scale);
    }

    protected int responsiveMouseY(double mouseY, int panelWidth, int panelHeight) {
        float scale = responsivePanelScale(panelWidth, panelHeight);
        return Math.round(this.height / 2.0f + ((float) mouseY - this.height / 2.0f) / scale);
    }

    protected double responsiveDrag(double delta, int panelWidth, int panelHeight) {
        return delta / responsivePanelScale(panelWidth, panelHeight);
    }

    private float responsivePanelScale(int panelWidth, int panelHeight) {
        float widthScale = this.width * MAX_PANEL_WIDTH_RATIO / Math.max(1, panelWidth);
        float heightScale = this.height * MAX_PANEL_HEIGHT_RATIO / Math.max(1, panelHeight);
        return Math.max(0.5f, Math.min(1.0f, Math.min(widthScale, heightScale)));
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
