package de.quest.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import de.quest.client.ui.VillageTooltip;

public final class GuiGraphics {
    private final GuiGraphicsExtractor extractor;
    private final VillageTooltip textTooltip = new VillageTooltip();

    public GuiGraphics(GuiGraphicsExtractor extractor) {
        this.extractor = extractor;
    }

    public GuiGraphicsExtractor extractor() {
        return extractor;
    }

    public GuiGraphicsExtractor unwrap() {
        return extractor;
    }

    public int guiWidth() {
        return extractor.guiWidth();
    }

    public int guiHeight() {
        return extractor.guiHeight();
    }

    public Matrix3x2fStack pose() {
        return extractor.pose();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        extractor.fill(x1, y1, x2, y2, color);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        extractor.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        extractor.blitSprite(pipeline, sprite, x, y, width, height);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        extractor.text(font, text, x, y, color);
        return x;
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        extractor.text(font, text, x, y, color, shadow);
        return x;
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        extractor.text(font, text, x, y, color);
        return x;
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        extractor.text(font, text, x, y, color, shadow);
        return x;
    }
    public int drawString(Font font,net.minecraft.util.FormattedCharSequence text,int x,int y,int color,boolean shadow) {
        extractor.text(font,text,x,y,color,shadow); return x;
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        extractor.centeredText(font, text, centerX, y, color);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        extractor.centeredText(font, text, centerX, y, color);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        extractor.item(stack, x, y);
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        extractor.fakeItem(stack, x, y);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        extractor.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        extractor.disableScissor();
    }

    public void setTooltipForNextFrame(Font font, Component tooltip, int x, int y) {
        textTooltip.set(tooltip);
    }

    public void setTooltipForNextFrame(Font font, List<Component> tooltip, int x, int y) {
        textTooltip.set(tooltip);
    }

    /** VQ text hints draw once, after board transforms, at the physical pointer. */
    public void renderDeferredTooltip(Font font, int mouseX, int mouseY) {
        textTooltip.draw(this,font,mouseX,mouseY);
        textTooltip.clear();
    }
}
