package de.quest.client.ui;

import de.quest.VillageQuest;
import de.quest.economy.CurrencyService;
import de.quest.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

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
    public static final int MUTED = 0xFF70543B;
    public static final int GOLD = 0xFF9A6620;
    public static final int TEAL = 0xFF365E59;
    public static final int PAPER = 0xFFF1D8A2;
    public static final int WOOD = 0xFF4B2C17;
    public static final int PANEL_PADDING = 12;
    public static final int LIGHT_TEXT = 0xFFF7E8CB;
    public static final int DISABLED_TEXT = 0xFF725E47;
    public static final int SCREEN_SHADE = 0x4018120D;

    private static final Identifier CARD = texture("card");
    private static final Identifier FRAME_V3 = texture("v3/frame");
    private static final Identifier LANTERN_V3 = texture("v3/lantern");
    public static final Identifier JOURNAL_HERO = texture("v3/journal_hero");
    private static final Identifier SCROLL_TRACK = texture("scroll_track");
    private static final Identifier SCROLL_THUMB = texture("scroll_thumb");
    private static final int SCROLL_TRACK_TEXTURE_WIDTH = 12;
    private static final int SCROLL_TRACK_TEXTURE_HEIGHT = 96;
    private static final int SCROLL_THUMB_TEXTURE_WIDTH = 16;
    private static final int SCROLL_THUMB_TEXTURE_HEIGHT = 32;
    private static final int SCROLL_TRACK_RENDER_WIDTH = 3;
    private static final int SCROLL_THUMB_RENDER_WIDTH = 8;
    private static final int SCROLL_THUMB_SOURCE_CAP = 10;
    private static final int WALLET_GAP = 3;
    private static final int CARD_TEXTURE_SIZE = 64;
    private static final int CARD_SOURCE_BORDER = 5;
    private static final int CARD_RENDER_BORDER = 5;

    private VillageUiTheme() {}

    public static Identifier icon(String name) {
        return switch (name) {
            case "dispatch" -> texture("icon_story");
            case "wayshrine" -> marker("shrine");
            case "guildpath" -> texture("icon_guide");
            default -> texture("icon_" + name);
        };
    }

    public static Identifier marker(String name) {
        return texture("map_" + name);
    }

    public static Identifier control(String name) {
        return texture(name);
    }

    /**
     * Applies the shared, restrained world dimming used behind every primary
     * Village Quest panel. The board textures keep transparent exteriors and
     * carry their own contact shadows.
     */
    public static void drawScreenShade(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, SCREEN_SHADE);
    }

    /**
     * Retained shared hook for panel rendering. Contact shadows are part of
     * the frame artwork; no rectangular fill is added behind its silhouette.
     */
    public static void drawPanelShadow(GuiGraphics graphics, int x, int y, int width, int height) {
        // The frame carries its own contact shadows. A rectangular backdrop
        // shows through its transparent silhouette and creates a second panel.
    }

    public static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                  String label, boolean enabled, boolean hovered, boolean active) {
        drawButton(graphics,font,x,y,width,height,label,enabled,hovered,active,ButtonTone.TEAL);
    }
    public enum ButtonTone { TEAL, OAK, PAPER }
    public static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                  String label, boolean enabled, boolean hovered, boolean active, ButtonTone tone) {
        if ("×".equals(label)) {
            JournalV3Visuals.close(graphics,x,y,width,height,hovered);
            return;
        }
        if (!enabled) {
            graphics.fill(x,y,x+width,y+height,0xFFB69A62);
            graphics.fill(x+1,y+1,x+width-1,y+height-1,0xFFE4CFAB);
            graphics.fill(x+2,y+2,x+width-2,y+3,0xFFF4E5CA);
        } else {
            graphics.fill(x,y,x+width,y+height,0xFF9A662C);
            graphics.fill(x+1,y+1,x+width-1,y+height-1,0xFFF0C775);
            graphics.fill(x+2,y+2,x+width-2,y+height-2,tone==ButtonTone.PAPER?(hovered?0xFFF0D6A6:0xFFE3C795):tone==ButtonTone.OAK?(hovered?0xFF795437:0xFF5C3B25):(hovered?0xFF346D65:0xFF234E4C));
            graphics.fill(x+3,y+3,x+width-3,y+4,tone==ButtonTone.TEAL?0xFF527D71:0xFFB79661);
        }
        if (enabled && active) {
            graphics.fill(x+2,y+2,x+4,y+height-2,0xFFF5D486);
            graphics.fill(x+4,y+height-3,x+width-3,y+height-2,0xFFD1AA59);
            graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, tone==ButtonTone.TEAL?0x66365E59:0x22B88B44);
        }
        // The selected edge and warm hover cue remain visible in every palette.
        if (enabled && active && hovered) {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x33FFC66D);
        }
        float textScale = height < 18 ? 0.78f : 0.92f;
        int available = Math.max(4, Math.round((width - 12) / textScale));
        String visible = ellipsize(font, label, available);
        float renderedWidth = font.width(visible) * textScale;
        float renderedHeight = font.lineHeight * textScale;
        drawStringScaled(graphics, font, visible,
                x + (width - renderedWidth) / 2.0f,
                y + (height - renderedHeight) / 2.0f,
                enabled ? tone==ButtonTone.PAPER?INK:LIGHT_TEXT : DISABLED_TEXT, textScale);
    }

    /** Local navigation uses a permanent selected edge, independent of hover. */
    public static void drawNavigationTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                                          String label, boolean enabled, boolean hover, boolean selected) {
        if (selected) {
            drawButton(g,font,x,y,w,h,label,enabled,hover,false);
            g.fill(x+2,y+h-3,x+w-2,y+h-1,0xFFFFD486);
            g.fill(x+2,y+2,x+4,y+h-3,0xFFFFD486);
        } else JournalV3Visuals.secondary(g,font,label,x,y,w,h,hover);
    }

    public static void drawTab(GuiGraphics graphics, int x, int y, int width, int height,
                               boolean selected, boolean hovered) {
        drawButtonSurface(graphics, selected ? ButtonState.ACTIVE.texture : ButtonState.NORMAL.texture,
                x, y, width, height);
        if (selected) graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x66365E59);
        if (hovered && !selected) {
            graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x22FFF1C7);
        }
    }

    /** Quiet paper and fixed-size gilded edges; also used for title and detail plates. */
    public static void drawPaper(GuiGraphics graphics, int x, int y, int width, int height) {
        JournalV3Visuals.paper(graphics,x,y,width,height);
    }

    /** Compact VQ overlays share the palette while retaining configurable background opacity. */
    public static void drawHudPaper(GuiGraphics g, int x, int y, int width, int height, float opacity) {
        int alpha = Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        g.fill(x + 2, y + 2, x + width + 2, y + height + 2, (alpha / 3) << 24 | 0x18120D);
        g.fill(x, y, x + width, y + height, alpha << 24 | WOOD & 0xFFFFFF);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, alpha << 24 | GOLD & 0xFFFFFF);
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, alpha << 24 | PAPER & 0xFFFFFF);
    }

    /** Transparent wood frame. Only straight rails stretch, never corner hardware. */
    public static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        blitNineSlice(graphics, FRAME_V3, x, y, width, height, 416, 234, 32, 32, false);
    }

    public static void drawBoard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawPaper(graphics, x + 12, y + 20, width - 24, height - 32);
        drawFrame(graphics, x, y, width, height);
        JournalV3Visuals.ornaments(graphics,x,y,width,height);
    }

    /** Optional outer-corner ornament; callers reserve its space outside live content. */
    public static void drawLantern(GuiGraphics graphics, int x, int y) {
        blitScaled(graphics, LANTERN_V3, x, y, 32, 40, 32, 40);
    }

    /** Titles are live Minecraft text; the plaque never contains generated lettering. */
    public static void drawTitle(GuiGraphics graphics, Font font, String label, String icon,
                                 int x, int y, int width) {
        JournalV3Visuals.titlePlate(graphics,x,y,width);
        float scale=Math.min(1.5f,Math.max(.9f,(width-40f)/Math.max(1,font.width(label))));
        String visible=ellipsize(font,label,Math.max(1,(int)((width-40)/scale)));
        float groupWidth=23+6+font.width(visible)*scale;
        int start=x+Math.round((width-groupWidth)/2);
        if(icon.equals("wayshrine")) JournalV3Visuals.sprite(graphics,icon,start+2,y+3,19,19);
        else drawIcon(graphics,icon(icon),start,y+(icon.equals("quests")?-2:1),23);
        JournalV3Visuals.heading(graphics,font,visible,start+29,y+(30-font.lineHeight*scale)/2-2,
                Math.round(font.width(visible)*scale)+2,scale,INK);
    }

    private static void drawButtonSurface(GuiGraphics graphics, Identifier texture, int x, int y, int width, int height) {
        // Sample the painted bevel, excluding the sprite's black exterior padding.
        int capX = Math.min(2, width / 2), capY = Math.min(2, height / 2);
        int[] sx = {5, 8, 112}, sy = {3, 6, 26};
        int[] sw = {3, 104, 3}, sh = {3, 20, 3};
        int[] dx = {x, x + capX, x + width - capX}, dy = {y, y + capY, y + height - capY};
        int[] dw = {capX, Math.max(0, width - 2 * capX), capX}, dh = {capY, Math.max(0, height - 2 * capY), capY};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            blitRegionScaled(graphics, texture, dx[col], dy[row], dw[col], dh[row], sx[col], sy[row], sw[col], sh[row], 120, 32);
        }
    }

    public static List<String> wrapScaled(Font font, String text, int width, float scale) {
        int limit = Math.max(1, Math.round(width / scale));
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(next) > limit) { lines.add(ellipsize(font, line, limit)); line = word; }
            else line = next;
        }
        if (!line.isEmpty()) lines.add(ellipsize(font, line, limit));
        return lines;
    }

    public static void drawCard(GuiGraphics graphics, int x, int y, int width, int height,
                                boolean hovered, boolean selected) {
        // A card is commonly rendered at roughly 4:1, while the source asset is
        // square. Scaling the complete bitmap made its four-pixel frame expand
        // to more than twenty pixels horizontally, which pushed labels into the
        // painted border. Keep corners and edges at a fixed size and only stretch
        // the parchment centre.
        JournalV3Visuals.paper(graphics,x,y,width,height);
        if (selected) {
            graphics.fill(x+2,y+2,x+width-2,y+height-2,0x24365E59);
            graphics.fill(x+1,y+1,x+width-1,y+2,0xFF365E59);
            graphics.fill(x+1,y+height-2,x+width-1,y+height-1,0xFF365E59);
            graphics.fill(x + 4, y + 4, x + 7, y + height - 4, 0xCC236B68);
        } else if (hovered) {
            graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x12FFFFFF);
        }
    }

    public static void drawIcon(GuiGraphics graphics, Identifier icon, int x, int y, int size) {
        int source = icon.getPath().contains("/map_") ? 48 : 32;
        blitScaled(graphics, icon, x, y, size, size, source, source);
    }

    public static void drawMarker(GuiGraphics graphics, String marker, int centerX, int centerY, int size) {
        blitScaled(graphics, marker(marker), centerX - size / 2, centerY - size / 2,
                size, size, 48, 48);
    }

    public static void drawScrollBar(GuiGraphics graphics, int x, int y, int height,
                                     int viewportHeight, int contentHeight, int offset, int maxOffset) {
        if (contentHeight <= viewportHeight || height <= 12) {
            return;
        }
        // One continuous recessed track; the thumb is visually distinct from its end caps.
        graphics.fill(x-1,y,x+4,y+height,0xFF9A662C);
        graphics.fill(x,y+1,x+3,y+height-1,0xFF4B2C17);
        int thumbHeight = Math.max(12, (int) Math.round((double) viewportHeight / contentHeight * (height-4)));
        thumbHeight = Math.min(height-4, thumbHeight);
        int travel = Math.max(0, height - 4 - thumbHeight);
        int thumbOffset = maxOffset <= 0 ? 0
                : (int) Math.round((double) Math.max(0, Math.min(offset, maxOffset)) / maxOffset * travel);
        int thumbX = x - (SCROLL_THUMB_RENDER_WIDTH - SCROLL_TRACK_RENDER_WIDTH) / 2;
        int thumbY=y+2+thumbOffset;
        graphics.fill(thumbX,thumbY,thumbX+SCROLL_THUMB_RENDER_WIDTH,thumbY+thumbHeight,0xFF9A662C);
        graphics.fill(thumbX+1,thumbY+1,thumbX+SCROLL_THUMB_RENDER_WIDTH-1,thumbY+thumbHeight-1,TEAL);
        graphics.fill(thumbX+2,thumbY+2,thumbX+SCROLL_THUMB_RENDER_WIDTH-2,thumbY+3,0xFF84AB94);
    }

    /** Draws the shared compact icon wallet used in every Village Quest wood header. */
    public static void drawWalletStrip(GuiGraphics graphics, Font font,
                                       int left, int top, int windowWidth, long balance,
                                       int rightInset, int topOffset) {
        drawWalletStrip(graphics,font,left,top,windowWidth,balance,rightInset,topOffset,false);
    }
    public static void drawPaperWalletStrip(GuiGraphics graphics,Font font,int left,int top,int windowWidth,long balance,int rightInset,int topOffset) {
        drawWalletStrip(graphics,font,left,top,windowWidth,balance,rightInset,topOffset,true);
    }
    private static void drawWalletStrip(GuiGraphics graphics,Font font,int left,int top,int windowWidth,long balance,int rightInset,int topOffset,boolean paper) {
        long crownAmount = balance / CurrencyService.CROWN;
        long silverAmount = balance % CurrencyService.CROWN;
        String crownText = compactWalletAmount(crownAmount);
        String silverText = compactWalletAmount(silverAmount);
        int crownWidth = walletEntryWidth(font, crownText);
        int silverWidth = walletEntryWidth(font, silverText);
        int totalWidth = crownWidth + silverWidth + WALLET_GAP;
        int startX = left + windowWidth - rightInset - totalWidth;
        if(paper) JournalV3Visuals.paper(graphics,startX-5,top+topOffset-3,totalWidth+10,20);
        drawWalletEntry(graphics, font, startX, top + topOffset, crownWidth,
                new ItemStack(ModItems.CROWN), crownText, paper || crownAmount > 0L, paper?INK:0xFFFFD27A, 0.74f);
        drawWalletEntry(graphics, font, startX + crownWidth + WALLET_GAP, top + topOffset, silverWidth,
                new ItemStack(ModItems.SILVERMARK), silverText, paper || silverAmount > 0L, paper?BODY:0xFFD9E2F0, 0.68f);
    }

    /** Wallet constrained to a small plate, including icons and text. */
    public static void drawCompactWalletStrip(GuiGraphics graphics, Font font,
                                              int right, int top, long balance) {
        String crowns = compactWalletAmount(balance / CurrencyService.CROWN);
        String silver = compactWalletAmount(balance % CurrencyService.CROWN);
        int crownWidth = 11 + Math.round(font.width(crowns) * .72f);
        int silverWidth = 11 + Math.round(font.width(silver) * .72f);
        int x = right - crownWidth - silverWidth - 4;
        JournalV3Visuals.paper(graphics,x-5,top-4,right-x+10,20);
        drawScaledItem(graphics, new ItemStack(ModItems.CROWN), x, top, .5f);
        drawStringScaled(graphics, font, crowns, x + 10, top + 2, INK, .72f);
        x += crownWidth + 4;
        drawScaledItem(graphics, new ItemStack(ModItems.SILVERMARK), x, top, .5f);
        drawStringScaled(graphics, font, silver, x + 10, top + 2, BODY, .72f);
    }

    /** Reuses the accepted board artwork without stretching its vertical ornaments. */
    public static void drawCompactNoticeBoard(GuiGraphics g, int x, int y, int width, int height) {
        Identifier paper = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/journal_board.png");
        Identifier frame = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
        Identifier header = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
        // Cropped frame rails contain transparent pixels: keep an opaque wood bed underneath.
        g.fill(x + 3, y + 18, x + width - 3, y + height - 3, 0xFF40220F);
        blitRegionScaled(g, paper, x + 14, y + 14, width - 28, height - 27,
                90, 44, 285, 150, 416, 234);
        blitRegionScaled(g, header, x + 14, y + 14, width - 28, 25,
                0, 0, 400, 24, 400, 207);
        blitRegionScaled(g, frame, x, y, width, 40, 0, 0, 416, 40, 416, 234);
        blitRegionScaled(g, frame, x, y + 40, 14, height - 56, 0, 40, 14, 178, 416, 234);
        blitRegionScaled(g, frame, x + width - 14, y + 40, 14, height - 56, 402, 40, 14, 178, 416, 234);
        blitRegionScaled(g, frame, x, y + height - 16, width, 16, 0, 218, 416, 16, 416, 234);
    }

    /** Uses one continuous accepted Atlas perimeter instead of overlapping cropped corners. */
    public static void drawMapBoard(GuiGraphics g, Identifier board, int x, int y, int width, int height) {
        Identifier frame = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/guild_atlas_frame.png");
        // Preserve the native inner map/list panels, excluding all old outer ornaments.
        g.fill(x + 14, y + 25, x + width - 14, y + height - 14, 0xFF40220F);
        blitRegionScaled(g, board, x + 26, y + 32, width - 52, height - 58,
                26, 32, width - 52, height - 58, width, height);
        blitScaled(g, frame, x, y, width, height, 416, 234);
    }

    /** Thin gilded lip around live terrain; never paints over the map's interior. */
    public static void drawMapRim(GuiGraphics g,int x,int y,int width,int height) {
        g.fill(x-2,y-2,x+width+2,y,0xFFB18A47);
        g.fill(x-2,y+height,x+width+2,y+height+2,0xFF73532D);
        g.fill(x-2,y,x,y+height,0xFFC7AA6B);
        g.fill(x+width,y,x+width+2,y+height,0xFF73532D);
    }

    /** Draw the native parchment lip above the live map, with its inner contact shadow. */
    public static void drawEmbeddedMapRim(GuiGraphics g, Identifier board, int left, int top,
                                          int boardWidth, int boardHeight,
                                          int mapX, int mapY, int mapWidth, int mapHeight) {
        int x = left + mapX, y = top + mapY;
        blitRegionScaled(g, board, x - 2, y - 2, mapWidth + 4, 4,
                mapX - 4, mapY - 4, mapWidth + 8, 4, boardWidth, boardHeight);
        blitRegionScaled(g, board, x - 2, y + mapHeight - 2, mapWidth + 4, 4,
                mapX - 4, mapY + mapHeight, mapWidth + 8, 4, boardWidth, boardHeight);
        blitRegionScaled(g, board, x - 2, y + 2, 4, mapHeight - 4,
                mapX - 4, mapY, 4, mapHeight, boardWidth, boardHeight);
        blitRegionScaled(g, board, x + mapWidth - 2, y + 2, 4, mapHeight - 4,
                mapX + mapWidth, mapY, 4, mapHeight, boardWidth, boardHeight);
        g.fill(x + 2, y + 2, x + mapWidth - 2, y + 3, 0x883A2717);
        g.fill(x + 2, y + 3, x + 3, y + mapHeight - 2, 0x663A2717);
    }

    private static void blitScrollThumb(GuiGraphics graphics, int x, int y, int width, int height) {
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

    private static void drawWalletEntry(GuiGraphics graphics, Font font, int x, int y, int entryWidth,
                                        ItemStack stack, String amountText, boolean positive,
                                        int countColor, float iconScale) {
        drawScaledItem(graphics, stack, x + 1, y + 1, iconScale);
        int amountX = x + entryWidth - 1 - font.width(amountText);
        graphics.drawString(font, amountText, amountX, y + 4,
                positive ? countColor : 0xFFBDAF9B, false);
    }

    private static int walletEntryWidth(Font font, String amountText) {
        return 17 + font.width(amountText);
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

    public static void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.renderItem(stack, 0, 0);
        matrices.popMatrix();
    }

    public static void blitScaled(GuiGraphics graphics, Identifier texture, int x, int y,
                                  int width, int height, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(width / (float) textureWidth, height / (float) textureHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f,
                textureWidth, textureHeight, textureWidth, textureHeight);
        matrices.popMatrix();
    }

    private static void blitNineSlice(GuiGraphics graphics, Identifier texture,
                                      int x, int y, int width, int height,
                                      int textureWidth, int textureHeight,
                                      int sourceBorder, int renderBorder) {
        blitNineSlice(graphics, texture, x, y, width, height, textureWidth, textureHeight,
                sourceBorder, renderBorder, true);
    }

    private static void blitNineSlice(GuiGraphics graphics, Identifier texture,
                                      int x, int y, int width, int height,
                                      int textureWidth, int textureHeight,
                                      int sourceBorder, int renderBorder, boolean drawCenter) {
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
        if (drawCenter) blitRegionScaled(graphics, texture, x + left, y + top, centerWidth, centerHeight,
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

    public static void blitRegionScaled(GuiGraphics graphics, Identifier texture,
                                         int x, int y, int width, int height,
                                         int sourceX, int sourceY,
                                         int sourceWidth, int sourceHeight,
                                         int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(width / (float) sourceWidth, height / (float) sourceHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0,
                (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
        matrices.popMatrix();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        String value = text;
        while (!value.isEmpty() && font.width(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    public static void drawStringScaled(GuiGraphics graphics, Font font, String text,
                                        float x, float y, int color, float scale) {
        if (text == null || text.isEmpty() || scale <= 0.0f) {
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        graphics.drawString(font, text, 0, 0, color, false);
        matrices.popMatrix();
    }

    /** Draws compact body copy and never emits more than {@code maxLines}. */
    public static int drawWrappedScaled(GuiGraphics graphics, Font font, String text,
                                        int x, int y, int maxWidth, int color,
                                        float scale, int maxLines) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
            return 0;
        }
        int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
        List<String> lines = wrapScaledLines(font, text, maxWidth, scale);
        int visibleLines = Math.min(maxLines, lines.size());
        int lineStep = Math.max(7, Math.round((font.lineHeight + 2) * scale));
        for (int i = 0; i < visibleLines; i++) {
            String visible = lines.get(i);
            if (i == visibleLines - 1 && lines.size() > maxLines) {
                visible = ellipsize(font, visible + " ...", unscaledWidth);
            }
            // Word wrapping alone cannot bound a long localized compound or external title.
            visible = ellipsize(font, visible, unscaledWidth);
            drawStringScaled(graphics, font, visible, x, y + i * lineStep, color, scale);
        }
        return visibleLines;
    }

    /** The same font-aware wrapping used for rendering, also available for card measurement. */
    public static List<String> wrapScaledLines(Font font, String text, int maxWidth, float scale) {
        if (text == null || text.isBlank() || maxWidth <= 0 || scale <= 0) return List.of();
        int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > unscaledWidth) {
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
        return lines;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/ui/" + name + ".png");
    }
}
