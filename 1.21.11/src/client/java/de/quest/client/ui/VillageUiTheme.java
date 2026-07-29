package de.quest.client.ui;

import de.quest.VillageQuest;
import de.quest.economy.CurrencyService;
import de.quest.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Shared, modular visual language for Village Quest screens.
 *
 * <p>The generated art is deliberately split into small reusable components.
 * Screens remain responsible for layout while this class keeps button states,
 * tabs, cards, icons, map markers, and scrollbars visually consistent.</p>
 */
public final class VillageUiTheme {
    public enum ButtonState {
        NORMAL("button_normal"),
        HOVER("button_hover"),
        ACTIVE("button_active"),
        DISABLED("button_disabled");

        private final Identifier texture;

        ButtonState(String name) {
            this.texture = texture(name);
        }
    }

    public static final int INK = 0xFF2D1B12;
    public static final int BODY = 0xFF5B4635;
    public static final int MUTED = 0xFF88725D;
    public static final int GOLD = 0xFF9A6620;
    public static final int TEAL = 0xFF236B68;
    public static final int LIGHT_TEXT = 0xFFF7E8CB;
    public static final int DISABLED_TEXT = 0xFFC5B296;
    public static final int SCREEN_SHADE = 0x4018120D;
    private static final int PANEL_SHADOW_OUTER = 0x1218120D;
    private static final int PANEL_SHADOW_MIDDLE = 0x1818120D;
    private static final int PANEL_SHADOW_CONTACT = 0x2218120D;

    private static final Identifier TAB_NORMAL = texture("tab_normal");
    private static final Identifier TAB_SELECTED = texture("tab_selected");
    private static final Identifier CARD = texture("card");
    private static final Identifier SCROLL_TRACK = texture("scroll_track");
    private static final Identifier SCROLL_THUMB = texture("scroll_thumb");
    private static final int SCROLL_TRACK_TEXTURE_WIDTH = 12;
    private static final int SCROLL_TRACK_TEXTURE_HEIGHT = 96;
    private static final int SCROLL_THUMB_TEXTURE_WIDTH = 16;
    private static final int SCROLL_THUMB_TEXTURE_HEIGHT = 32;
    private static final int SCROLL_TRACK_RENDER_WIDTH = 7;
    private static final int SCROLL_THUMB_RENDER_WIDTH = 13;
    private static final int SCROLL_THUMB_SOURCE_CAP = 10;
    private static final int WALLET_GAP = 3;
    private static final int CARD_TEXTURE_SIZE = 64;
    private static final int CARD_SOURCE_BORDER = 5;
    private static final int CARD_RENDER_BORDER = 5;

    private VillageUiTheme() {}

    public static Identifier icon(String name) {
        return texture("icon_" + name);
    }

    public static Identifier marker(String name) {
        return texture("map_" + name);
    }

    public static Identifier control(String name) {
        return texture(name);
    }

    /**
     * Applies the shared, restrained world dimming used behind every primary
     * Village Quest panel. The board textures have transparent exteriors; the
     * single shared panel shadow is rendered separately below.
     */
    public static void drawScreenShade(DrawContext graphics, int width, int height) {
        graphics.fill(0, 0, width, height, SCREEN_SHADE);
    }

    /**
     * Draws one restrained pixel-soft shadow around a primary Village Quest
     * panel. A faint ambient edge keeps the frame grounded on every side while
     * the wider lower-right falloff supplies a consistent light direction.
     */
    public static void drawPanelShadow(DrawContext graphics, int x, int y, int width, int height) {
        graphics.fill(x - 2, y - 2, x + width + 7, y + height + 8, PANEL_SHADOW_OUTER);
        graphics.fill(x - 1, y - 1, x + width + 5, y + height + 6, PANEL_SHADOW_MIDDLE);
        graphics.fill(x, y, x + width + 3, y + height + 4, PANEL_SHADOW_CONTACT);
    }

    public static void drawButton(DrawContext graphics, TextRenderer font, int x, int y, int width, int height,
                                  String label, boolean enabled, boolean hovered, boolean active) {
        ButtonState state = !enabled ? ButtonState.DISABLED
                : active ? ButtonState.ACTIVE
                : hovered ? ButtonState.HOVER
                : ButtonState.NORMAL;
        blitScaled(graphics, state.texture, x, y, width, height, 120, 32);
        float textScale = height <= 18 ? 0.75f : 0.85f;
        int available = Math.max(4, Math.round((width - 12) / textScale));
        String visible = ellipsize(font, label, available);
        float renderedWidth = font.getWidth(visible) * textScale;
        float renderedHeight = font.fontHeight * textScale;
        drawStringScaled(graphics, font, visible,
                x + (width - renderedWidth) / 2.0f,
                y + (height - renderedHeight) / 2.0f,
                enabled ? LIGHT_TEXT : DISABLED_TEXT, textScale);
    }

    public static void drawTab(DrawContext graphics, int x, int y, int width, int height,
                               boolean selected, boolean hovered) {
        Identifier texture = selected ? TAB_SELECTED : TAB_NORMAL;
        blitScaled(graphics, texture, x, y, width, height, 48, 48);
        if (hovered && !selected) {
            graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x22FFF1C7);
        }
    }

    public static void drawCard(DrawContext graphics, int x, int y, int width, int height,
                                boolean hovered, boolean selected) {
        // A card is commonly rendered at roughly 4:1, while the source asset is
        // square. Scaling the complete bitmap made its four-pixel frame expand
        // to more than twenty pixels horizontally, which pushed labels into the
        // painted border. Keep corners and edges at a fixed size and only stretch
        // the parchment centre.
        blitNineSlice(graphics, CARD, x, y, width, height,
                CARD_TEXTURE_SIZE, CARD_TEXTURE_SIZE,
                CARD_SOURCE_BORDER, CARD_RENDER_BORDER);
        if (selected) {
            graphics.fill(x + 4, y + 4, x + 7, y + height - 4, 0xCC236B68);
        } else if (hovered) {
            graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x12FFFFFF);
        }
    }

    public static void drawIcon(DrawContext graphics, Identifier icon, int x, int y, int size) {
        blitScaled(graphics, icon, x, y, size, size, 32, 32);
    }

    public static void drawMarker(DrawContext graphics, String marker, int centerX, int centerY, int size) {
        blitScaled(graphics, marker(marker), centerX - size / 2, centerY - size / 2,
                size, size, 48, 48);
    }

    public static void drawScrollBar(DrawContext graphics, int x, int y, int height,
                                     int viewportHeight, int contentHeight, int offset, int maxOffset) {
        if (contentHeight <= viewportHeight || height <= 12) {
            return;
        }
        blitScaled(graphics, SCROLL_TRACK, x, y, SCROLL_TRACK_RENDER_WIDTH, height,
                SCROLL_TRACK_TEXTURE_WIDTH, SCROLL_TRACK_TEXTURE_HEIGHT);
        int thumbHeight = Math.max(24, (int) Math.round((double) viewportHeight / contentHeight * height));
        thumbHeight = Math.min(height, thumbHeight);
        int travel = Math.max(0, height - thumbHeight);
        int thumbOffset = maxOffset <= 0 ? 0
                : (int) Math.round((double) Math.max(0, Math.min(offset, maxOffset)) / maxOffset * travel);
        int thumbX = x - (SCROLL_THUMB_RENDER_WIDTH - SCROLL_TRACK_RENDER_WIDTH) / 2;
        blitScrollThumb(graphics, thumbX, y + thumbOffset, SCROLL_THUMB_RENDER_WIDTH, thumbHeight);
    }

    /** Draws the shared compact icon wallet used in every Village Quest wood header. */
    public static void drawWalletStrip(DrawContext graphics, TextRenderer font,
                                       int left, int top, int windowWidth, long balance,
                                       int rightInset, int topOffset) {
        long crownAmount = balance / CurrencyService.CROWN;
        long silverAmount = balance % CurrencyService.CROWN;
        String crownText = compactWalletAmount(crownAmount);
        String silverText = compactWalletAmount(silverAmount);
        int crownWidth = walletEntryWidth(font, crownText);
        int silverWidth = walletEntryWidth(font, silverText);
        int totalWidth = crownWidth + silverWidth + WALLET_GAP;
        int startX = left + windowWidth - rightInset - totalWidth;
        drawWalletEntry(graphics, font, startX, top + topOffset, crownWidth,
                new ItemStack(ModItems.CROWN), crownText, crownAmount > 0L, 0xFFFFD27A, 0.74f);
        drawWalletEntry(graphics, font, startX + crownWidth + WALLET_GAP, top + topOffset, silverWidth,
                new ItemStack(ModItems.SILVERMARK), silverText, silverAmount > 0L, 0xFFD9E2F0, 0.68f);
    }

    private static void blitScrollThumb(DrawContext graphics, int x, int y, int width, int height) {
        int renderCap = Math.min(9, height / 2);
        int sourceMiddleHeight = SCROLL_THUMB_TEXTURE_HEIGHT - (SCROLL_THUMB_SOURCE_CAP * 2);
        int renderMiddleHeight = Math.max(0, height - (renderCap * 2));
        blitRegionScaled(graphics, SCROLL_THUMB, x, y, width, renderCap,
                0, 0, SCROLL_THUMB_TEXTURE_WIDTH, SCROLL_THUMB_SOURCE_CAP,
                SCROLL_THUMB_TEXTURE_WIDTH, SCROLL_THUMB_TEXTURE_HEIGHT);
        blitRegionScaled(graphics, SCROLL_THUMB, x, y + renderCap, width, renderMiddleHeight,
                0, SCROLL_THUMB_SOURCE_CAP, SCROLL_THUMB_TEXTURE_WIDTH, sourceMiddleHeight,
                SCROLL_THUMB_TEXTURE_WIDTH, SCROLL_THUMB_TEXTURE_HEIGHT);
        blitRegionScaled(graphics, SCROLL_THUMB, x, y + height - renderCap, width, renderCap,
                0, SCROLL_THUMB_TEXTURE_HEIGHT - SCROLL_THUMB_SOURCE_CAP,
                SCROLL_THUMB_TEXTURE_WIDTH, SCROLL_THUMB_SOURCE_CAP,
                SCROLL_THUMB_TEXTURE_WIDTH, SCROLL_THUMB_TEXTURE_HEIGHT);
    }

    private static void drawWalletEntry(DrawContext graphics, TextRenderer font, int x, int y, int entryWidth,
                                        ItemStack stack, String amountText, boolean positive,
                                        int countColor, float iconScale) {
        drawScaledItem(graphics, stack, x + 1, y + 1, iconScale);
        int amountX = x + entryWidth - 1 - font.getWidth(amountText);
        graphics.drawText(font, amountText, amountX, y + 4,
                positive ? countColor : 0xFFBDAF9B, false);
    }

    private static int walletEntryWidth(TextRenderer font, String amountText) {
        return 17 + font.getWidth(amountText);
    }

    private static String compactWalletAmount(long amount) {
        if (amount < 1_000L) {
            return Long.toString(amount);
        }
        if (amount < 1_000_000L) {
            return compactWalletUnit(amount / 1_000.0d, "k");
        }
        if (amount < 1_000_000_000L) {
            return compactWalletUnit(amount / 1_000_000.0d, "m");
        }
        return compactWalletUnit(amount / 1_000_000_000.0d, "b");
    }

    private static String compactWalletUnit(double value, String suffix) {
        String pattern = value >= 100.0d ? "%.0f%s" : "%.1f%s";
        return String.format(Locale.ROOT, pattern, value, suffix);
    }

    private static void drawScaledItem(DrawContext graphics, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        var matrices = graphics.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.drawItem(stack, 0, 0);
        matrices.popMatrix();
    }

    public static void blitScaled(DrawContext graphics, Identifier texture, int x, int y,
                                  int width, int height, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        var matrices = graphics.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(width / (float) textureWidth, height / (float) textureHeight);
        graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f,
                textureWidth, textureHeight, textureWidth, textureHeight);
        matrices.popMatrix();
    }

    private static void blitNineSlice(DrawContext graphics, Identifier texture,
                                      int x, int y, int width, int height,
                                      int textureWidth, int textureHeight,
                                      int sourceBorder, int renderBorder) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int left = Math.min(renderBorder, width / 2);
        int right = Math.min(renderBorder, Math.max(0, width - left));
        int top = Math.min(renderBorder, height / 2);
        int bottom = Math.min(renderBorder, Math.max(0, height - top));
        int sourceRight = textureWidth - sourceBorder;
        int sourceBottom = textureHeight - sourceBorder;
        int sourceCenterWidth = textureWidth - (sourceBorder * 2);
        int sourceCenterHeight = textureHeight - (sourceBorder * 2);
        int centerWidth = Math.max(0, width - left - right);
        int centerHeight = Math.max(0, height - top - bottom);

        blitRegionScaled(graphics, texture, x, y, left, top,
                0, 0, sourceBorder, sourceBorder, textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + left, y, centerWidth, top,
                sourceBorder, 0, sourceCenterWidth, sourceBorder, textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + width - right, y, right, top,
                sourceRight, 0, sourceBorder, sourceBorder, textureWidth, textureHeight);

        blitRegionScaled(graphics, texture, x, y + top, left, centerHeight,
                0, sourceBorder, sourceBorder, sourceCenterHeight, textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + left, y + top, centerWidth, centerHeight,
                sourceBorder, sourceBorder, sourceCenterWidth, sourceCenterHeight,
                textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + width - right, y + top, right, centerHeight,
                sourceRight, sourceBorder, sourceBorder, sourceCenterHeight,
                textureWidth, textureHeight);

        blitRegionScaled(graphics, texture, x, y + height - bottom, left, bottom,
                0, sourceBottom, sourceBorder, sourceBorder, textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + left, y + height - bottom, centerWidth, bottom,
                sourceBorder, sourceBottom, sourceCenterWidth, sourceBorder,
                textureWidth, textureHeight);
        blitRegionScaled(graphics, texture, x + width - right, y + height - bottom, right, bottom,
                sourceRight, sourceBottom, sourceBorder, sourceBorder,
                textureWidth, textureHeight);
    }

    private static void blitRegionScaled(DrawContext graphics, Identifier texture,
                                         int x, int y, int width, int height,
                                         int sourceX, int sourceY,
                                         int sourceWidth, int sourceHeight,
                                         int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        var matrices = graphics.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(width / (float) sourceWidth, height / (float) sourceHeight);
        graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0,
                (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
        matrices.popMatrix();
    }

    public static String ellipsize(TextRenderer font, String text, int maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        String value = text;
        while (!value.isEmpty() && font.getWidth(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    public static void drawStringScaled(DrawContext graphics, TextRenderer font, String text,
                                        float x, float y, int color, float scale) {
        if (text == null || text.isEmpty() || scale <= 0.0f) {
            return;
        }
        var matrices = graphics.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.drawText(font, text, 0, 0, color, false);
        matrices.popMatrix();
    }

    /** Draws compact body copy and never emits more than {@code maxLines}. */
    public static int drawWrappedScaled(DrawContext graphics, TextRenderer font, String text,
                                        int x, int y, int maxWidth, int color,
                                        float scale, int maxLines) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
            return 0;
        }
        int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getWidth(candidate) > unscaledWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        int visibleLines = Math.min(maxLines, lines.size());
        int lineStep = Math.max(7, Math.round((font.fontHeight + 2) * scale));
        for (int i = 0; i < visibleLines; i++) {
            String visible = lines.get(i);
            if (i == visibleLines - 1 && lines.size() > maxLines) {
                visible = ellipsize(font, visible + " ...", unscaledWidth);
            }
            drawStringScaled(graphics, font, visible, x, y + i * lineStep, color, scale);
        }
        return visibleLines;
    }

    private static Identifier texture(String name) {
        return Identifier.of(VillageQuest.MOD_ID, "textures/gui/ui/" + name + ".png");
    }
}
