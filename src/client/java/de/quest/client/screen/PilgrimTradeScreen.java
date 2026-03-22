package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import de.quest.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PilgrimTradeScreen extends Screen {
    private static final Identifier BOARD_TEXTURE = Identifier.of(VillageQuest.MOD_ID, "textures/gui/pilgrim_board.png");
    private static final int BOARD_TEXTURE_WIDTH = 310;
    private static final int BOARD_TEXTURE_HEIGHT = 205;

    private static final int WINDOW_WIDTH = 310;
    private static final int WINDOW_HEIGHT = 205;

    private static final int GOODS_X = 16;
    private static final int GOODS_Y = 46;
    private static final int GOODS_WIDTH = 126;
    private static final int GOODS_HEIGHT = 132;
    private static final int GOODS_ENTRY_HEIGHT = 24;
    private static final int GOODS_ENTRY_WIDTH = GOODS_WIDTH - 16;
    private static final int GOODS_TEXT_X_OFFSET = 28;
    private static final int GOODS_TEXT_WIDTH = 72;

    private static final int DETAIL_X = 154;
    private static final int DETAIL_Y = 46;
    private static final int DETAIL_WIDTH = 140;
    private static final int DETAIL_HEIGHT = 132;

    private static final int BUY_WIDTH = 108;
    private static final int BUY_HEIGHT = 18;

    private static final int SCREEN_SHADE = 0xA0120F0B;
    private static final int SHADOW = 0x6A000000;
    private static final int FRAME_DARK = 0xFF3A2116;
    private static final int FRAME_MID = 0xFF6A3D24;
    private static final int FRAME_LIGHT = 0xFF9B6641;
    private static final int PARCHMENT = 0xFFF1DFBE;
    private static final int PARCHMENT_DEEP = 0xFFE0C69A;
    private static final int INSET_BG = 0xFFF6EBD2;
    private static final int ENTRY_BG = 0xFFF4E6C9;
    private static final int ENTRY_HOVER = 0xFFF8EED9;
    private static final int ENTRY_SELECTED = 0xFFE5C98C;
    private static final int ENTRY_SELECTED_HOVER = 0xFFEDD7A4;
    private static final int BUTTON_BG = 0xFF5C2F1C;
    private static final int BUTTON_HOVER = 0xFF7A4126;
    private static final int BUTTON_DISABLED = 0xFF6C4634;
    private static final int BUTTON_READY = 0xFF3F5F2D;
    private static final int BUTTON_READY_HOVER = 0xFF50793A;
    private static final int BUTTON_LOCKED = 0xFF7A3E2D;
    private static final int BUTTON_LOCKED_HOVER = 0xFF934B36;
    private static final int BUTTON_TEXT = 0xFFF6E9D1;
    private static final int TITLE = 0xFF2C170F;
    private static final int BODY = 0xFF5C402C;
    private static final int ACCENT = 0xFF8C5A2E;
    private static final int ACCENT_BRIGHT = 0xFFD6B16A;
    private static final int AFFORD = 0xFF396D2E;
    private static final int TOO_EXPENSIVE = 0xFF8E312D;
    private static final int MUTED = 0xFF8C7C6A;
    private static final int TOOLTIP_BG = 0xFFF6E8C8;
    private static final int TOOLTIP_SHADOW = 0x66000000;
    private static final float LIST_TITLE_SCALE = 0.75f;
    private static final float LIST_PRICE_SCALE = 0.72f;
    private static final float DETAIL_BODY_SCALE = 0.85f;
    private static final float DETAIL_TITLE_SCALE = 0.92f;
    private static final float DETAIL_SUBTEXT_SCALE = 0.82f;
    private static final float HEADER_GOLD_COIN_SCALE = 1.0f;
    private static final float HEADER_SILVER_COIN_SCALE = 0.9f;
    private static final float DETAIL_PRICE_COIN_SCALE = 1.08f;

    public record TradeView(
            String offerId,
            Text title,
            Text description,
            long price,
            ItemStack previewStack
    ) {}

    public record PilgrimTradeData(
            int entityId,
            Text merchantName,
            long balance,
            int despawnTicks,
            List<TradeView> offers
    ) {}

    private PilgrimTradeData data;
    private int selectedIndex = 0;
    private boolean closeNotified = false;
    private long countdownSyncMillis = 0L;

    public PilgrimTradeScreen(PilgrimTradeData data) {
        super(Text.translatable("screen.village-quest.pilgrim.title"));
        this.data = data;
    }

    public void updateData(PilgrimTradeData data) {
        String previousOfferId = getSelectedOfferId();
        this.data = data;
        this.countdownSyncMillis = System.currentTimeMillis();
        restoreSelection(previousOfferId);
    }

    @Override
    protected void init() {
        restoreSelection(getSelectedOfferId());
        this.closeNotified = false;
        this.countdownSyncMillis = System.currentTimeMillis();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        notifyTradeClosed();
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;

        context.fill(0, 0, this.width, this.height, SCREEN_SHADE);
        drawBoard(context, left, top);
        drawHeader(context, left, top);
        drawWalletStrip(context, left, top);
        drawGoodsPanel(context, left, top, mouseX, mouseY);
        drawDetailPanel(context, left, top, mouseX, mouseY);
        drawFooter(context, left, top);

        super.render(context, mouseX, mouseY, delta);
        drawOfferTooltip(context, left, top, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        for (int i = 0; i < data.offers().size(); i++) {
            int entryX = left + GOODS_X + 8;
            int entryY = top + GOODS_Y + 8 + (i * GOODS_ENTRY_HEIGHT);
            if (isWithin(mouseX, mouseY, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2)) {
                this.selectedIndex = i;
                playClickSound();
                return true;
            }
        }

        if (isHoveringBuyButton(mouseX, mouseY, left, top) && getSelectedOffer() != null) {
            buySelectedOffer();
            playClickSound();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void drawBoard(DrawContext context, int left, int top) {
        context.fill(left + 6, top + 7, left + WINDOW_WIDTH + 6, top + WINDOW_HEIGHT + 7, SHADOW);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                BOARD_TEXTURE,
                left,
                top,
                0.0f,
                0.0f,
                WINDOW_WIDTH,
                WINDOW_HEIGHT,
                BOARD_TEXTURE_WIDTH,
                BOARD_TEXTURE_HEIGHT
        );
    }

    private void drawHeader(DrawContext context, int left, int top) {
        String merchantName = data.merchantName() == null || data.merchantName().getString().isEmpty()
                ? Text.translatable("entity.village-quest.pilgrim").getString()
                : data.merchantName().getString();
        context.drawText(this.textRenderer, merchantName, left + 18, top + 13, 0xFFF3E7D2, false);

        int sectionTitleY = top + GOODS_Y - 9;
        context.drawText(this.textRenderer, Text.translatable("screen.village-quest.pilgrim.goods"), left + GOODS_X + 8, sectionTitleY, ACCENT, false);
        context.drawText(this.textRenderer, this.title.getString(), left + DETAIL_X + 8, sectionTitleY, ACCENT, false);
    }

    private void drawWalletStrip(DrawContext context, int left, int top) {
        int stripWidth = 146;
        int stripX = left + (WINDOW_WIDTH - stripWidth) / 2;
        int stripY = top + 8;

        drawFrame(context, stripX, stripY, stripWidth, 24, FRAME_DARK, FRAME_LIGHT, PARCHMENT_DEEP);
        context.drawText(
                this.textRenderer,
                Text.translatable("screen.village-quest.pilgrim.wallet").getString(),
                stripX + 8,
                stripY + 8,
                TITLE,
                false
        );

        drawWalletEntry(context, stripX + 78, stripY + 4, new ItemStack(ModItems.GOLD_GROSCHEN), data.balance() / CurrencyService.CROWN, 0xFFB07A1C, HEADER_GOLD_COIN_SCALE);
        drawWalletEntry(context, stripX + 108, stripY + 4, new ItemStack(ModItems.EISEN_GROSCHEN), data.balance() % CurrencyService.CROWN, 0xFF7C8594, HEADER_SILVER_COIN_SCALE);
    }

    private void drawGoodsPanel(DrawContext context, int left, int top, int mouseX, int mouseY) {
        List<TradeView> offers = data.offers();
        if (offers == null || offers.isEmpty()) {
            drawWrappedLines(
                    context,
                    Text.translatable("screen.village-quest.pilgrim.empty").getString(),
                    left + GOODS_X + 10,
                    top + GOODS_Y + 12,
                    GOODS_WIDTH - 20,
                    BODY,
                    3
            );
            return;
        }

        for (int i = 0; i < offers.size(); i++) {
            TradeView offer = offers.get(i);
            int entryX = left + GOODS_X + 8;
            int entryY = top + GOODS_Y + 8 + (i * GOODS_ENTRY_HEIGHT);
            boolean hovered = isWithin(mouseX, mouseY, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2);
            int bg = i == selectedIndex
                    ? (hovered ? ENTRY_SELECTED_HOVER : ENTRY_SELECTED)
                    : (hovered ? ENTRY_HOVER : ENTRY_BG);

            drawFrame(context, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2, FRAME_DARK, FRAME_LIGHT, bg);
            if (i == selectedIndex) {
                context.fill(entryX + 2, entryY + 2, entryX + 6, entryY + GOODS_ENTRY_HEIGHT - 4, ACCENT_BRIGHT);
                context.fill(entryX + GOODS_WIDTH - 21, entryY + 9, entryX + GOODS_WIDTH - 17, entryY + 13, ACCENT_BRIGHT);
            }

            if (offer.previewStack() != null && !offer.previewStack().isEmpty()) {
                drawFrame(context, entryX + 4, entryY + 3, 18, 18, FRAME_MID, FRAME_LIGHT, 0x00000000);
                context.drawItem(offer.previewStack(), entryX + 5, entryY + 4);
            }

            String title = ellipsizeToScale(offer.title().getString(), GOODS_TEXT_WIDTH, LIST_TITLE_SCALE);
            drawScaledText(context, title, entryX + GOODS_TEXT_X_OFFSET, entryY + 5, TITLE, LIST_TITLE_SCALE);
            drawScaledText(
                    context,
                    ellipsizeToScale(CurrencyService.formatBalance(offer.price()).getString(), GOODS_TEXT_WIDTH, LIST_PRICE_SCALE),
                    entryX + GOODS_TEXT_X_OFFSET,
                    entryY + 13,
                    canAfford(offer) ? AFFORD : TOO_EXPENSIVE,
                    LIST_PRICE_SCALE
            );
        }
    }

    private void drawDetailPanel(DrawContext context, int left, int top, int mouseX, int mouseY) {
        TradeView offer = getSelectedOffer();
        int panelX = left + DETAIL_X;
        int panelY = top + DETAIL_Y;
        int contentX = panelX + 10;
        int currentY = panelY + 10;

        if (offer == null) {
            drawWrappedLines(
                    context,
                    Text.translatable("screen.village-quest.pilgrim.empty").getString(),
                    contentX,
                    currentY,
                    DETAIL_WIDTH - 20,
                    BODY,
                    4
            );
            drawBuyButton(context, left, top, false, false);
            return;
        }

        int tradeY = panelY + 4;
        drawTradeSlot(context, contentX + 6, tradeY, getPricePreviewStack(offer.price()), DETAIL_PRICE_COIN_SCALE);
        drawTradeSlot(context, contentX + 86, tradeY, offer.previewStack(), 1.0f);

        int textX = contentX;
        currentY = tradeY + 31;
        currentY = drawWrappedScaledLines(
                context,
                offer.title().getString(),
                textX,
                currentY,
                DETAIL_WIDTH - 20,
                TITLE,
                DETAIL_TITLE_SCALE,
                1
        );
        currentY -= 2;
        currentY = drawWrappedScaledLines(
                context,
                Text.translatable("screen.village-quest.pilgrim.price", CurrencyService.formatBalance(offer.price())).getString(),
                textX,
                currentY,
                DETAIL_WIDTH - 20,
                canAfford(offer) ? AFFORD : TOO_EXPENSIVE,
                DETAIL_SUBTEXT_SCALE,
                2
        );

        int infoY = panelY + 68;
        context.fill(contentX, infoY, panelX + DETAIL_WIDTH - 10, infoY + 1, FRAME_LIGHT);
        currentY = drawWrappedScaledLines(
                context,
                Text.translatable("screen.village-quest.pilgrim.wallet_hint").getString(),
                contentX,
                infoY + 8,
                DETAIL_WIDTH - 20,
                BODY,
                DETAIL_BODY_SCALE,
                3
        );
        currentY += 4;

        int descriptionBottom = getBuyButtonY(top) - 10;
        int descriptionLineHeight = Math.round((this.textRenderer.fontHeight + 2) * DETAIL_BODY_SCALE);
        int descriptionLines = Math.max(0, (descriptionBottom - currentY) / Math.max(1, descriptionLineHeight));
        drawWrappedScaledLines(
                context,
                offer.description().getString(),
                contentX,
                currentY,
                DETAIL_WIDTH - 20,
                BODY,
                DETAIL_BODY_SCALE,
                descriptionLines
        );

        drawBuyButton(context, left, top, true, isHoveringBuyButton(mouseX, mouseY, left, top));
    }

    private void drawBuyButton(DrawContext context, int left, int top, boolean active, boolean hovered) {
        int x = getBuyButtonX(left);
        int y = getBuyButtonY(top);
        TradeView offer = getSelectedOffer();
        boolean affordable = offer != null && canAfford(offer);
        String label = Text.translatable("screen.village-quest.pilgrim.buy").getString();
        int color = !active
                ? MUTED
                : affordable
                ? (hovered ? ACCENT_BRIGHT : BUTTON_TEXT)
                : 0xFFD2BEA4;
        int textX = x + (BUY_WIDTH - this.textRenderer.getWidth(label)) / 2;
        int textY = y + (BUY_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, label, textX, textY, color, false);
    }

    private void drawFooter(DrawContext context, int left, int top) {
        String hint = Text.translatable("screen.village-quest.pilgrim.close_hint").getString();
        drawScaledText(context, hint, left + 16, top + WINDOW_HEIGHT - 18, BODY, 0.85f);

        String timerText = Text.translatable(
                "screen.village-quest.pilgrim.timer",
                formatRemainingTime(getRemainingDespawnTicks())
        ).getString();
        int timerX = left + WINDOW_WIDTH - 16 - Math.round(this.textRenderer.getWidth(timerText) * 0.85f);
        drawScaledText(context, timerText, timerX, top + WINDOW_HEIGHT - 18, MUTED, 0.85f);
    }

    private void drawWalletEntry(DrawContext context, int x, int y, ItemStack stack, long amount, int countColor, float iconScale) {
        drawFrame(context, x, y, 20, 16, FRAME_MID, FRAME_LIGHT, 0x00000000);
        if (!stack.isEmpty()) {
            drawScaledItem(context, stack, x + 2, y, iconScale);
        }
        context.drawText(this.textRenderer, Long.toString(amount), x + 22, y + 5, amount > 0L ? countColor : MUTED, false);
    }

    private void drawTradeSlot(DrawContext context, int x, int y, ItemStack stack, float iconScale) {
        drawFrame(context, x, y, 24, 24, FRAME_MID, FRAME_LIGHT, 0x00000000);
        if (stack != null && !stack.isEmpty()) {
            int baseX = x + 4;
            int baseY = y + 4;
            int centeredX = baseX - Math.round((16.0f * (iconScale - 1.0f)) / 2.0f);
            int centeredY = baseY - Math.round((16.0f * (iconScale - 1.0f)) / 2.0f);
            drawScaledItem(context, stack, centeredX, centeredY, iconScale);
        }
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (Math.abs(scale - 1.0f) < 0.001f) {
            context.drawItem(stack, x, y);
            return;
        }

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.drawItem(stack, 0, 0);
        matrices.popMatrix();
    }

    private void drawOfferTooltip(DrawContext context, int left, int top, int mouseX, int mouseY) {
        TradeView hoveredOffer = getHoveredOffer(left, top, mouseX, mouseY);
        if (hoveredOffer == null) {
            return;
        }
        if (!isTruncatedAtScale(hoveredOffer.title().getString(), GOODS_TEXT_WIDTH, LIST_TITLE_SCALE)) {
            return;
        }

        String title = hoveredOffer.title().getString();
        int tooltipWidth = Math.min(160, this.textRenderer.getWidth(title) + 12);
        int tooltipHeight = this.textRenderer.fontHeight + 8;
        int tooltipX = Math.min(mouseX + 10, this.width - tooltipWidth - 6);
        int tooltipY = Math.max(6, mouseY - 14);

        context.fill(tooltipX + 2, tooltipY + 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, TOOLTIP_SHADOW);
        drawFrame(context, tooltipX, tooltipY, tooltipWidth, tooltipHeight, FRAME_DARK, FRAME_LIGHT, TOOLTIP_BG);
        context.drawText(this.textRenderer, ellipsize(title, tooltipWidth - 10), tooltipX + 5, tooltipY + 4, TITLE, false);
    }

    private void buySelectedOffer() {
        TradeView offer = getSelectedOffer();
        if (offer == null || data.entityId() < 0) {
            return;
        }
        ClientPlayNetworking.send(new Payloads.PilgrimTradeActionPayload(data.entityId(), offer.offerId()));
    }

    private void notifyTradeClosed() {
        if (this.closeNotified || data.entityId() < 0) {
            return;
        }
        this.closeNotified = true;
        ClientPlayNetworking.send(new Payloads.PilgrimTradeSessionPayload(
                data.entityId(),
                Payloads.PilgrimTradeSessionPayload.ACTION_CLOSE
        ));
    }

    private void playClickSound() {
        if (this.client == null) {
            return;
        }
        this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private TradeView getSelectedOffer() {
        List<TradeView> offers = data.offers();
        if (offers == null || offers.isEmpty()) {
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= offers.size()) {
            selectedIndex = 0;
        }
        return offers.get(selectedIndex);
    }

    private TradeView getHoveredOffer(int left, int top, int mouseX, int mouseY) {
        List<TradeView> offers = data.offers();
        if (offers == null || offers.isEmpty()) {
            return null;
        }
        for (int i = 0; i < offers.size(); i++) {
            int entryX = left + GOODS_X + 8;
            int entryY = top + GOODS_Y + 8 + (i * GOODS_ENTRY_HEIGHT);
            if (isWithin(mouseX, mouseY, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2)) {
                return offers.get(i);
            }
        }
        return null;
    }

    private String getSelectedOfferId() {
        TradeView offer = getSelectedOffer();
        return offer == null ? null : offer.offerId();
    }

    private void restoreSelection(String offerId) {
        List<TradeView> offers = data.offers();
        if (offers == null || offers.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        if (offerId != null) {
            for (int i = 0; i < offers.size(); i++) {
                if (offerId.equals(offers.get(i).offerId())) {
                    selectedIndex = i;
                    return;
                }
            }
        }
        selectedIndex = Math.min(selectedIndex, offers.size() - 1);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
    }

    private int getBuyButtonX(int left) {
        return left + DETAIL_X + (DETAIL_WIDTH - BUY_WIDTH) / 2;
    }

    private int getBuyButtonY(int top) {
        return top + DETAIL_Y + DETAIL_HEIGHT - BUY_HEIGHT + 2;
    }

    private boolean isHoveringBuyButton(int mouseX, int mouseY, int left, int top) {
        return isWithin(mouseX, mouseY, getBuyButtonX(left), getBuyButtonY(top), BUY_WIDTH, BUY_HEIGHT);
    }

    private boolean canAfford(TradeView offer) {
        return offer != null && data.balance() >= offer.price();
    }

    private int getRemainingDespawnTicks() {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.countdownSyncMillis);
        long elapsedTicks = elapsedMillis / 50L;
        return Math.max(0, data.despawnTicks() - (int) Math.min(Integer.MAX_VALUE, elapsedTicks));
    }

    private String formatRemainingTime(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private boolean isWithin(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int drawWrappedLines(DrawContext context, String text, int x, int y, int maxWidth, int color, int maxLines) {
        List<String> lines = wrapText(text, maxWidth);
        if (lines.isEmpty()) {
            return y;
        }

        int lineCount = Math.min(lines.size(), maxLines);
        for (int i = 0; i < lineCount; i++) {
            String rendered = lines.get(i);
            if (i == lineCount - 1 && lines.size() > maxLines) {
                StringBuilder remaining = new StringBuilder(rendered);
                for (int j = i + 1; j < lines.size(); j++) {
                    remaining.append(' ').append(lines.get(j));
                }
                rendered = ellipsize(remaining.toString(), maxWidth);
            }
            context.drawText(this.textRenderer, rendered, x, y, color, false);
            y += this.textRenderer.fontHeight + 1;
        }
        return y;
    }

    private int drawWrappedScaledLines(DrawContext context,
                                       String text,
                                       int x,
                                       int y,
                                       int maxWidth,
                                       int color,
                                       float scale,
                                       int maxLines) {
        if (maxLines <= 0) {
            return y;
        }

        List<String> lines = wrapTextToScale(text, maxWidth, scale);
        if (lines.isEmpty()) {
            return y;
        }

        int lineHeight = Math.round((this.textRenderer.fontHeight + 2) * scale);
        int lineCount = Math.min(lines.size(), maxLines);
        for (int i = 0; i < lineCount; i++) {
            String rendered = lines.get(i);
            if (i == lineCount - 1 && lines.size() > maxLines) {
                StringBuilder remaining = new StringBuilder(rendered);
                for (int j = i + 1; j < lines.size(); j++) {
                    remaining.append(' ').append(lines.get(j));
                }
                rendered = ellipsizeToScale(remaining.toString(), maxWidth, scale);
            }
            drawScaledText(context, rendered, x, y, color, scale);
            y += lineHeight;
        }
        return y;
    }

    private void drawFrame(DrawContext context, int x, int y, int width, int height, int outer, int inner, int fill) {
        context.fill(x, y, x + width, y + height, outer);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, fill);
    }

    private ItemStack getPricePreviewStack(long price) {
        if (price >= CurrencyService.CROWN) {
            return new ItemStack(ModItems.GOLD_GROSCHEN);
        }
        return new ItemStack(ModItems.EISEN_GROSCHEN);
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (this.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = this.textRenderer.getWidth(ellipsis);
        String trimmed = text;
        while (!trimmed.isEmpty() && this.textRenderer.getWidth(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    private boolean isTruncated(String text, int maxWidth) {
        return text != null && this.textRenderer.getWidth(text) > maxWidth;
    }

    private String ellipsizeToScale(String text, int maxWidth, float scale) {
        int unscaledWidth = Math.max(1, Math.round(maxWidth / scale));
        return ellipsize(text, unscaledWidth);
    }

    private boolean isTruncatedAtScale(String text, int maxWidth, float scale) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return Math.round(this.textRenderer.getWidth(text) * scale) > maxWidth;
    }

    private List<String> wrapTextToScale(String text, int maxWidth, float scale) {
        int unscaledWidth = Math.max(1, Math.round(maxWidth / scale));
        return wrapText(text, unscaledWidth);
    }

    private void drawScaledText(DrawContext context, String text, int x, int y, int color, float scale) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        context.drawText(this.textRenderer, text, Math.round(x / scale), Math.round(y / scale), color, false);
        matrices.popMatrix();
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }

            String candidate = current + " " + word;
            if (this.textRenderer.getWidth(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.append(' ').append(word);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
