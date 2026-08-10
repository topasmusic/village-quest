package de.quest.client.screen;

import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Shared fullscreen-safe sizing for Village Quest's authored board layouts.
 *
 * <p>On Minecraft 1.21.1, {@link Screen#render} always invokes {@link #renderBackground}
 * before drawables. Our board screens keep the upstream call order (paint the panel, then
 * {@code super.render(...)} for drawables), which is safe on 1.21.11+'s GUI pipeline but
 * on 1.21.1 would blur/darken the already-drawn panel. {@link #renderBackground} is therefore
 * a no-op here; {@link #prepareBoardBackdrop} applies world blur + the shared shade
 * <em>before</em> panel content instead.
 */
abstract class ResponsiveScreen extends Screen {
    private static final float MAX_PANEL_WIDTH_RATIO = 0.82f;
    private static final float MAX_PANEL_HEIGHT_RATIO = 0.86f;

    protected ResponsiveScreen(Text title) {
        super(title);
    }

    /**
     * World blur (when in-game) plus {@link VillageUiTheme#drawScreenShade}, matching the
     * visual backdrop boards already expected from upstream without re-running it after
     * the panel is painted.
     */
    protected void prepareBoardBackdrop(DrawContext context, float delta) {
        if (this.client != null && this.client.world != null) {
            this.applyBlur(delta);
        } else {
            this.renderPanoramaBackground(context, delta);
        }
        VillageUiTheme.drawScreenShade(context, this.width, this.height);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // See class javadoc: vanilla blur/darkening must not run after board content.
    }

    protected float beginResponsivePanel(DrawContext context, int panelWidth, int panelHeight) {
        float scale = responsivePanelScale(panelWidth, panelHeight);
        if (scale < 0.999f) {
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(this.width / 2.0f, this.height / 2.0f, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-this.width / 2.0f, -this.height / 2.0f, 0);
        }
        return scale;
    }

    protected void endResponsivePanel(DrawContext context, float scale) {
        if (scale < 0.999f) {
            context.getMatrices().pop();
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
