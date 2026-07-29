package de.quest.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Shared fullscreen-safe sizing for Village Quest's authored board layouts. */
abstract class ResponsiveScreen extends Screen {
    private static final float MAX_PANEL_WIDTH_RATIO = 0.82f;
    private static final float MAX_PANEL_HEIGHT_RATIO = 0.86f;

    protected ResponsiveScreen(Text title) {
        super(title);
    }

    protected float beginResponsivePanel(DrawContext context, int panelWidth, int panelHeight) {
        float scale = responsivePanelScale(panelWidth, panelHeight);
        if (scale < 0.999f) {
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(this.width / 2.0f, this.height / 2.0f);
            matrices.scale(scale, scale);
            matrices.translate(-this.width / 2.0f, -this.height / 2.0f);
        }
        return scale;
    }

    protected void endResponsivePanel(DrawContext context, float scale) {
        if (scale < 0.999f) {
            context.getMatrices().popMatrix();
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
}
