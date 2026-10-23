package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import de.quest.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PilgrimTradeScreen extends CompatScreen {
    private record ContractDetailLine(String text, int color, float scale, boolean spacer, boolean heading) {
        ContractDetailLine(String text,int color,float scale,boolean spacer) {this(text,color,scale,spacer,false);}
    }

    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/pilgrim_board_v2.png");
    private static final Identifier RUMOR_BOARD_TEXTURE = BOARD_TEXTURE;
    private static final int BOARD_TEXTURE_WIDTH = 310;
    private static final int BOARD_TEXTURE_HEIGHT = 205;

    private static final int WINDOW_WIDTH = 560;
    private static final int WINDOW_HEIGHT = 315;

    private static final int GOODS_X = 32;
    private static final int GOODS_Y = 61;
    private static final int GOODS_WIDTH = 222;
    private static final int GOODS_HEIGHT = 190;
    private static final int GOODS_ENTRY_HEIGHT = 44;
    private static final int GOODS_VISIBLE_ENTRIES = 4;
    private static final int GOODS_ENTRY_WIDTH = GOODS_WIDTH - 16;
    private static final int GOODS_TEXT_X_OFFSET = 36;
    private static final int GOODS_TEXT_WIDTH = 168;

    private static final int DETAIL_X = 264;
    private static final int DETAIL_Y = 61;
    private static final int DETAIL_WIDTH = 264;
    private static final int DETAIL_HEIGHT = 190;

    private static final int BUY_WIDTH = 192;
    private static final int BUY_HEIGHT = 18;
    private static final int CONTRACT_BODY_MIN_Y_OFFSET = 68;

    private static final int FRAME_DARK = 0xFF3A2116;
    private static final int FRAME_MID = 0xFF6A3D24;
    private static final int FRAME_LIGHT = 0xFFB88943;
    private static final int PARCHMENT = 0xFFF1DFBE;
    private static final int PARCHMENT_DEEP = 0xFFE0C69A;
    private static final int INSET_BG = 0xFFF6EBD2;
    private static final int ENTRY_BG = 0xFFF4E6C9;
    private static final int ENTRY_HOVER = 0xFFF8EED9;
    private static final int ENTRY_SELECTED = 0xFFDCC58A;
    private static final int ENTRY_SELECTED_HOVER = 0xFFF0DDAA;
    private static final int BUTTON_BG = 0xFF5C2F1C;
    private static final int BUTTON_HOVER = 0xFF7A4126;
    private static final int BUTTON_DISABLED = 0xFF6C4634;
    private static final int BUTTON_READY = 0xFF236B68;
    private static final int BUTTON_READY_HOVER = 0xFF2E8580;
    private static final int BUTTON_LOCKED = 0xFF7A3E2D;
    private static final int BUTTON_LOCKED_HOVER = 0xFF934B36;
    private static final int BUTTON_TEXT = 0xFFF6E9D1;
    private static final int TITLE = 0xFF2C170F;
    private static final int BODY = 0xFF5C402C;
    private static final int ACCENT = 0xFF8C5A2E;
    private static final int ACCENT_BRIGHT = 0xFFD6B16A;
    private static final int AFFORD = 0xFF5C402C;
    private static final int TOO_EXPENSIVE = 0xFF854332;
    private static final int MUTED = 0xFF8C7C6A;
    private static final int TOOLTIP_BG = 0xFFF6E8C8;
    private static final int TOOLTIP_SHADOW = 0x66000000;
    private static final int SCROLL_TRACK = 0x33A77A42;
    private static final int SCROLL_THUMB = 0xAA7C4A27;
    private static final int CONTRACT_ROADMARK = 0xFF2A7282;
    private static final int CONTRACT_OVERWORLD = 0xFF4D7B35;
    private static final int CONTRACT_NETHER = 0xFFAF4135;
    private static final int CONTRACT_END = 0xFF8E5AC7;
    private static final float LIST_TITLE_SCALE = 0.75f;
    private static final float LIST_PRICE_SCALE = 0.72f;
    private static final float DETAIL_BODY_SCALE = 0.85f;
    private static final float DETAIL_TITLE_SCALE = 0.92f;
    private static final float DETAIL_SUBTEXT_SCALE = 0.82f;
    private static final float HEADER_TITLE_SCALE = 0.92f;
    private static final int HEADER_TITLE_TOP = 3;
    private static final int HEADER_TITLE_HEIGHT = 26;
    private static final int HEADER_WALLET_RIGHT_INSET = 32;
    private static final int HEADER_WALLET_TOP = 9;
    private static final int HEADER_CONTRACT_TOP = 39;
    private static final int FOOTER_LEFT_INSET = 32;
    private static final int FOOTER_RIGHT_INSET = 35;
    private static final int FOOTER_TEXT_Y_OFFSET = WINDOW_HEIGHT - 49;
    private static final float DETAIL_PRICE_COIN_SCALE = 1.08f;
    private static final int RUMOR_TAB_WIDTH = 96;
    private static final int RUMOR_TAB_HEIGHT = 18;
    private static final int CONTRACT_OPTION_HEIGHT = 20;
    private static final float CONTRACT_OPTION_TEXT_SCALE = 0.84f;

    public record TradeView(
            String offerId,
            Component title,
            Component description,
            long price,
            ItemStack previewStack
    ) {}

    public record PilgrimContractView(
            String contractId,
            Component title,
            Component status,
            List<Component> descriptionLines,
            List<Component> objectiveLines,
            List<Component> rewardLines,
            Component actionLabel,
            boolean actionEnabled,
            ItemStack previewStack
    ) {}

    public record PilgrimTradeData(
            int entityId,
            Component merchantName,
            long balance,
            int despawnTicks,
            List<TradeView> offers,
            List<PilgrimContractView> contracts
    ) {}

    private PilgrimTradeData data;
    private int selectedIndex = 0;
    private int goodsScrollIndex = 0;
    private boolean closeNotified = false;
    private long countdownSyncMillis = 0L;
    private boolean showContract = false;
    private boolean tourContract;
    void selectTourContract(boolean value) { tourContract = value; }
    private int selectedContractIndex = 0;
    private int shopDetailScrollOffset = 0;
    private int shopDetailScrollMax = 0;
    private int contractScrollOffset = 0;
    private int contractScrollMax = 0;

    public PilgrimTradeScreen(PilgrimTradeData data) {
        super(Component.translatable("screen.village-quest.pilgrim.title"));
        this.data = data;
    }

    public void updateData(PilgrimTradeData data) {
        String previousOfferId = getSelectedOfferId();
        String previousContractId = getSelectedContractId();
        boolean hadContractView = this.showContract;
        this.data = data;
        this.countdownSyncMillis = System.currentTimeMillis();
        restoreSelection(previousOfferId);
        ensureSelectedOfferVisible();
        restoreContractSelection(previousContractId);
        this.showContract = hadContractView && hasContract();
        String nextOfferId = getSelectedOfferId();
        String nextContractId = getSelectedContractId();
        if (previousOfferId == null ? nextOfferId != null : !previousOfferId.equals(nextOfferId)) {
            this.shopDetailScrollOffset = 0;
        }
        if (previousContractId == null ? nextContractId != null : !previousContractId.equals(nextContractId)) {
            this.contractScrollOffset = 0;
        }
        clampShopDetailScroll();
        clampContractScroll();
    }

    @Override
    protected void init() {
        restoreSelection(getSelectedOfferId());
        ensureSelectedOfferVisible();
        this.closeNotified = false;
        this.countdownSyncMillis = System.currentTimeMillis();
        this.showContract = tourContract;
        this.selectedContractIndex = 0;
        this.shopDetailScrollOffset = 0;
        this.shopDetailScrollMax = 0;
        this.contractScrollOffset = 0;
        this.contractScrollMax = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        notifyTradeClosed();
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(context, this.width, this.height);
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float panelScale = beginResponsivePanel(context, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            int left = (this.width - WINDOW_WIDTH) / 2;
            int top = (this.height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(context, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            drawBoard(context, left, top);
            drawHeader(context, left, top, uiMouseX, uiMouseY);
            drawWalletStrip(context, left, top);
            drawGoodsPanel(context, left, top, uiMouseX, uiMouseY);
            drawDetailPanel(context, left, top, uiMouseX, uiMouseY);
            drawFooter(context, left, top);

            super.render(context, uiMouseX, uiMouseY, delta);
            drawOfferTooltip(context, left, top, uiMouseX, uiMouseY);
        } finally {
            endResponsivePanel(context, panelScale);
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.handleMouseClicked(click, doubled);
        }

        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        if (KingdomUiAssets.closeBounds(left, top, WINDOW_WIDTH, WINDOW_HEIGHT).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        int firstOffer = this.goodsScrollIndex;
        int lastOffer = Math.min(data.offers().size(), firstOffer + GOODS_VISIBLE_ENTRIES);
        for (int i = firstOffer; !showContract && i < lastOffer; i++) {
            int entryX = left + GOODS_X + 8;
            int entryY = top + GOODS_Y + 8 + ((i - firstOffer) * GOODS_ENTRY_HEIGHT);
            if (isWithin(mouseX, mouseY, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2)) {
                this.selectedIndex = i;
                this.showContract = false;
                this.shopDetailScrollOffset = 0;
                this.contractScrollOffset = 0;
                playClickSound();
                return true;
            }
        }

        if(isWithin(mouseX,mouseY,left+GOODS_X+8,top+HEADER_CONTRACT_TOP,112,RUMOR_TAB_HEIGHT)) {
            this.showContract=false; this.contractScrollOffset=0; playClickSound(); return true;
        }
        if (hasContract() && isHoveringContractTab(mouseX, mouseY, left, top)) {
            this.showContract = !this.showContract;
            this.contractScrollOffset = 0;
            playClickSound();
            return true;
        }

        if (this.showContract && hasContract()) {
            int hoveredContract = getHoveredContractIndex(mouseX, mouseY, left, top);
            if (hoveredContract >= 0) {
                this.selectedContractIndex = hoveredContract;
                this.contractScrollOffset = 0;
                playClickSound();
                return true;
            }
        }

        if (isHoveringBuyButton(mouseX, mouseY, left, top) && hasActionableEntry()) {
            activateCurrentEntry();
            playClickSound();
            return true;
        }

        return super.handleMouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (!showContract && maxGoodsScrollIndex() > 0
                && isWithin(uiMouseX, uiMouseY,
                left + GOODS_X + 6, top + GOODS_Y + 6, GOODS_WIDTH - 12, GOODS_HEIGHT - 12)) {
            this.goodsScrollIndex -= (int) Math.signum(verticalAmount);
            clampGoodsScroll();
            return true;
        }
        if (this.showContract && hasMultipleContracts()
                && isWithin(uiMouseX, uiMouseY, getContractOptionsX(left), getContractOptionsY(top),
                getContractOptionsWidth(), CONTRACT_OPTION_HEIGHT)) {
            selectedContractIndex = Math.floorMod(selectedContractIndex - (int) Math.signum(verticalAmount), data.contracts().size());
            contractScrollOffset = 0;
            return true;
        }
        int bodyTop = getContractBodyTop(top);
        int bodyBottom = getBuyButtonY(top) - 10;
        if (this.showContract
                && hasContract()
                && this.contractScrollMax > 0
                && isWithin(uiMouseX, uiMouseY, left + DETAIL_X + 8, bodyTop, DETAIL_WIDTH - 16, Math.max(0, bodyBottom - bodyTop))) {
            int step = Math.max(8, Math.round((this.font.lineHeight + 2) * DETAIL_BODY_SCALE) * 2);
            this.contractScrollOffset -= (int) Math.signum(verticalAmount) * step;
            clampContractScroll();
            return true;
        }
        if (!this.showContract
                && this.shopDetailScrollMax > 0
                && isWithin(uiMouseX, uiMouseY, left + DETAIL_X + 8, top + DETAIL_Y + 124, DETAIL_WIDTH - 16, Math.max(0, getBuyButtonY(top) - 10 - (top + DETAIL_Y + 124)))) {
            int step = Math.max(8, Math.round((this.font.lineHeight + 2) * DETAIL_BODY_SCALE) * 2);
            this.shopDetailScrollOffset -= (int) Math.signum(verticalAmount) * step;
            clampShopDetailScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawBoard(GuiGraphics context, int left, int top) {
        de.quest.client.ui.JournalV3Visuals.insetBoard(context, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
        VillageUiTheme.drawCard(context, left + GOODS_X, top + GOODS_Y, GOODS_WIDTH, showContract ? GOODS_HEIGHT : Math.min(GOODS_HEIGHT,16+Math.max(1,Math.min(GOODS_VISIBLE_ENTRIES,data.offers().size()))*GOODS_ENTRY_HEIGHT), false, false);
        VillageUiTheme.drawCard(context, left + DETAIL_X, top + DETAIL_Y, DETAIL_WIDTH, 34, false, false);
        VillageUiTheme.drawCard(context, left + DETAIL_X, top + DETAIL_Y + 36, DETAIL_WIDTH, getBuyButtonY(top)-top-DETAIL_Y+BUY_HEIGHT+8-36, false, false);
    }

    private void drawHeader(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        VillageUiTheme.drawTitle(context, font, title.getString(), "special", left + 158, top + 3, 244);
        KingdomUiAssets.closeButton(context,left,top,WINDOW_WIDTH,WINDOW_HEIGHT,
                KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mouseX,mouseY));

        int sectionTitleY = top + GOODS_Y - 13;
        VillageUiTheme.drawNavigationTab(context,font,left+GOODS_X+8,top+HEADER_CONTRACT_TOP,112,RUMOR_TAB_HEIGHT,
                Component.translatable("screen.village-quest.pilgrim.goods").getString(),true,
                isWithin(mouseX,mouseY,left+GOODS_X+8,top+HEADER_CONTRACT_TOP,112,RUMOR_TAB_HEIGHT),!showContract);
        JournalV3Visuals.heading(context,font,Component.translatable(showContract?"screen.village-quest.pilgrim.contract.details":"screen.village-quest.pilgrim.offer").getString(),left+DETAIL_X+8,sectionTitleY+2,DETAIL_WIDTH-16,1f,TITLE);
        if (hasContract()) {
            drawContractTab(context, left, top, mouseX, mouseY);
        }
    }

    private void drawContractTab(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        int x = getContractTabX(left);
        int y = getContractTabY(top);
        String label = Component.translatable("screen.village-quest.pilgrim.contract.tab").getString();
        boolean hovered = isHoveringContractTab(mouseX, mouseY, left, top);
        VillageUiTheme.drawNavigationTab(context, this.font, x, y, RUMOR_TAB_WIDTH, RUMOR_TAB_HEIGHT,
                label, true, hovered, this.showContract);
    }

    private void drawContractOptions(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        List<PilgrimContractView> contracts = data.contracts();
        if (contracts == null || contracts.isEmpty()) {
            return;
        }

        int startX = getContractOptionsX(left);
        int y = getContractOptionsY(top);
        PilgrimContractView contract = getSelectedContract();
        boolean hovered = isWithin(mouseX, mouseY, startX, y, getContractOptionsWidth(), CONTRACT_OPTION_HEIGHT);
        VillageUiTheme.drawCard(context, startX, y, getContractOptionsWidth(), CONTRACT_OPTION_HEIGHT,
                hovered, true);
        int navigationSpace = hasMultipleContracts() ? 28 : 10;
        String label = ellipsizeToScale(contract.title().getString(), getContractOptionsWidth() - navigationSpace, CONTRACT_OPTION_TEXT_SCALE);
        int textX = startX + (getContractOptionsWidth() - Math.round(font.width(label) * CONTRACT_OPTION_TEXT_SCALE)) / 2;
        drawScaledText(context, label, textX, y + 6, contractColor(contract), CONTRACT_OPTION_TEXT_SCALE);
        if (hasMultipleContracts()) {
            drawScaledText(context, "<", startX + 4, y + 6, TITLE, CONTRACT_OPTION_TEXT_SCALE);
            drawScaledText(context, ">", startX + getContractOptionsWidth() - 9, y + 6, TITLE, CONTRACT_OPTION_TEXT_SCALE);
        }
    }

    private void drawContractDetail(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        PilgrimContractView contract = getSelectedContract();
        if (contract == null) {
            drawBuyButton(context, left, top, false, false);
            return;
        }
        int panelX = left + DETAIL_X;
        int panelY = top + DETAIL_Y;
        int contentX = panelX + 10;
        int contentWidth = DETAIL_WIDTH - 32;
        int contentBottom = getBuyButtonY(top) - 10;
        int currentY = panelY + 8;

        drawContractOptions(context, left, top, mouseX, mouseY);
        currentY = getContractBodyTop(top) + 2;

        List<ContractDetailLine> lines = buildContractDetailLines(contract, contentWidth, true);
        int viewportHeight = Math.max(0, contentBottom - currentY);
        var artwork = QuestArtwork.forQuest(contract.contractId(), contract.title()).filter(QuestArtwork.Motif::hasIllustration);
        // The illustration belongs to the left family card, never before the reading viewport.
        if (artwork.isPresent()) JournalV3Visuals.illustration(context,artwork.get().largeTexture(),
                left+GOODS_X+10,top+GOODS_Y+103,GOODS_WIDTH-20,80);
        int contentHeight = measureContractContentHeight(lines) + 1;
        this.contractScrollMax = Math.max(0, contentHeight - viewportHeight);
        clampContractScroll();

        int scissorTop = Math.max(panelY + 6, currentY - 2);
        context.enableScissor(panelX + 8, scissorTop, panelX + DETAIL_WIDTH - 8, contentBottom);
        int cursorY = currentY + 1 - this.contractScrollOffset;
        for (ContractDetailLine line : lines) {
            if (line.spacer()) {
                cursorY += 3;
                continue;
            }
            int lineHeight = scaledLineHeight(line.scale());
            if (cursorY >= scissorTop && cursorY + lineHeight <= contentBottom) {
                if(line.heading()) JournalV3Visuals.heading(context,font,line.text(),contentX,cursorY,contentWidth,line.scale(),TITLE);
                else drawScaledText(context, line.text(), contentX+3, cursorY, line.color(), line.scale());
            }
            cursorY += lineHeight;
        }
        context.disableScissor();
        drawContractScrollIndicator(context, panelX, scissorTop, contentBottom, contentHeight);

        drawBuyButton(context, left, top, contract.actionEnabled(), isHoveringBuyButton(mouseX, mouseY, left, top));
    }

    private void drawWalletStrip(GuiGraphics context, int left, int top) {
        VillageUiTheme.drawWalletStrip(context,font,left,top,WINDOW_WIDTH,data.balance(),40,10);
    }

    private void drawGoodsPanel(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        if (showContract && hasContract()) {
            PilgrimContractView contract=getSelectedContract();
            JournalV3Visuals.section(context,font,Component.translatable("screen.village-quest.pilgrim.contract.tab").getString(),
                    left+GOODS_X+8,top+GOODS_Y+8,GOODS_WIDTH-16,.95f,"special");
            drawWrappedScaledLines(context,contract.title().getString(),left+GOODS_X+12,top+GOODS_Y+36,
                    GOODS_WIDTH-24,TITLE,1.05f,3);
            drawWrappedScaledLines(context,contract.status().getString(),left+GOODS_X+12,top+GOODS_Y+69,
                    GOODS_WIDTH-24,TITLE,.9f,2);
            return;
        }
        List<TradeView> offers = data.offers();
        if (offers == null || offers.isEmpty()) {
            drawWrappedLines(
                    context,
                    Component.translatable("screen.village-quest.pilgrim.empty").getString(),
                    left + GOODS_X + 10,
                    top + GOODS_Y + 12,
                    GOODS_WIDTH - 20,
                    BODY,
                    3
            );
            return;
        }

        clampGoodsScroll();
        int firstOffer = this.goodsScrollIndex;
        int lastOffer = Math.min(offers.size(), firstOffer + GOODS_VISIBLE_ENTRIES);
        int viewportTop = top + GOODS_Y + 8;
        int viewportHeight = (GOODS_VISIBLE_ENTRIES * GOODS_ENTRY_HEIGHT) - 2;
        int viewportBottom = viewportTop + viewportHeight;
        context.enableScissor(
                left + GOODS_X + 7,
                viewportTop,
                left + GOODS_X + GOODS_WIDTH - 5,
                viewportBottom
        );
        for (int i = firstOffer; i < lastOffer; i++) {
            TradeView offer = offers.get(i);
            int entryX = left + GOODS_X + 8;
            int entryY = viewportTop + ((i - firstOffer) * GOODS_ENTRY_HEIGHT);
            boolean hovered = isWithin(mouseX, mouseY, entryX, entryY, GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2);
            VillageUiTheme.drawCard(context, entryX, entryY,
                    GOODS_ENTRY_WIDTH, GOODS_ENTRY_HEIGHT - 2, hovered, i == selectedIndex);

            if (offer.previewStack() != null && !offer.previewStack().isEmpty()) {
                drawFrame(context, entryX + 5, entryY + 5, 18, 18, FRAME_MID, FRAME_LIGHT, 0x00000000);
                context.renderItem(offer.previewStack(), entryX + 6, entryY + 6);
            }

            String title = ellipsizeToScale(offer.title().getString(), GOODS_TEXT_WIDTH, LIST_TITLE_SCALE);
            drawScaledText(context, title, entryX + GOODS_TEXT_X_OFFSET, entryY + 6, TITLE, LIST_TITLE_SCALE);
            int priceX = entryX + GOODS_TEXT_X_OFFSET;
            long crowns = offer.price() / CurrencyService.CROWN;
            long silver = offer.price() % CurrencyService.CROWN;
            drawScaledItem(context, new ItemStack(ModItems.CROWN), priceX, entryY + 15, .65f);
            String crownText = Long.toString(crowns);
            drawScaledText(context, crownText, priceX + 13, entryY + 16, BODY, .8f);
            int silverX = priceX + 20 + Math.round(font.width(crownText) * .8f);
            drawScaledItem(context, new ItemStack(ModItems.SILVERMARK), silverX, entryY + 15, .65f);
            drawScaledText(context, Long.toString(silver), silverX + 13, entryY + 16, BODY, .8f);
        }
        context.disableScissor();
        VillageUiTheme.drawScrollBar(
                context,
                left + GOODS_X + GOODS_WIDTH - 7,
                viewportTop,
                viewportHeight,
                GOODS_VISIBLE_ENTRIES * GOODS_ENTRY_HEIGHT,
                offers.size() * GOODS_ENTRY_HEIGHT,
                this.goodsScrollIndex * GOODS_ENTRY_HEIGHT,
                maxGoodsScrollIndex() * GOODS_ENTRY_HEIGHT
        );
    }

    private void drawDetailPanel(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        if (this.showContract && hasContract()) {
            drawContractDetail(context, left, top, mouseX, mouseY);
            return;
        }

        TradeView offer = getSelectedOffer();
        int panelX = left + DETAIL_X;
        int panelY = top + DETAIL_Y;
        int contentX = panelX + 10;
        int currentY = panelY + 10;

        if (offer == null) {
            drawWrappedLines(
                    context,
                    Component.translatable("screen.village-quest.pilgrim.empty").getString(),
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
        int tradeX=panelX+(DETAIL_WIDTH-148)/2;
        KingdomUiAssets.moneyCentered(context,font,offer.price(),tradeX,tradeY+7,72,false);
        drawTradeSlot(context, tradeX + 110, tradeY, offer.previewStack(), 1.0f);
        VillageUiTheme.drawStringScaled(context,font,"›",tradeX+84,tradeY+9,TITLE,1.3f);

        JournalV3Visuals.illustration(context,QuestArtwork.forComponent(offer.title()).orElse(QuestArtwork.Motif.MARKET).largeTexture(),contentX,panelY+42,DETAIL_WIDTH-20,45);
        int textX = contentX;
        // The lower parchment card begins below the trade-slot panel. Keep its
        // heading clear of the fixed five-pixel frame before drawing any text.
        currentY = panelY + 92;
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
        currentY = panelY + 107;
        String priceText = Component.translatable("screen.village-quest.pilgrim.price", CurrencyService.formatBalance(offer.price())).getString();
        float priceScale = fitScaleToWidth(priceText, DETAIL_WIDTH - 20, DETAIL_SUBTEXT_SCALE, 0.68f);
        currentY = drawSingleScaledLine(
                context,
                priceText,
                contentX,
                currentY,
                DETAIL_WIDTH - 20,
                canAfford(offer) ? AFFORD : TOO_EXPENSIVE,
                priceScale
        );

        currentY = panelY + 124;

        int descriptionBottom = getBuyButtonY(top) - 10;
        int descriptionLineHeight = scaledLineHeight(DETAIL_BODY_SCALE);
        String descriptionText = offer.description() == null || offer.description().getString().isBlank()
                ? Component.translatable("screen.village-quest.pilgrim.wallet_hint").getString()
                : offer.description().getString();
        List<String> descriptionLines = wrapTextToScale(descriptionText, DETAIL_WIDTH - 20, DETAIL_BODY_SCALE);
        int viewportHeight = Math.max(0, descriptionBottom - currentY);
        int contentHeight = descriptionLines.size() * Math.max(1, descriptionLineHeight);
        this.shopDetailScrollMax = Math.max(0, contentHeight - viewportHeight);
        clampShopDetailScroll();

        context.enableScissor(panelX + 8, currentY, panelX + DETAIL_WIDTH - 8, descriptionBottom);
        int textY = currentY - this.shopDetailScrollOffset;
        for (String line : descriptionLines) {
            if (textY >= currentY && textY + descriptionLineHeight <= descriptionBottom) {
                drawScaledText(context, line, contentX, textY, BODY, DETAIL_BODY_SCALE);
            }
            textY += descriptionLineHeight;
        }
        context.disableScissor();
        drawScrollIndicator(context, panelX, currentY, descriptionBottom, contentHeight, this.shopDetailScrollOffset, this.shopDetailScrollMax);

        drawBuyButton(context, left, top, true, isHoveringBuyButton(mouseX, mouseY, left, top));
    }

    private void drawBuyButton(GuiGraphics context, int left, int top, boolean active, boolean hovered) {
        int x = getBuyButtonX(left);
        int y = getBuyButtonY(top);
        String label;
        boolean enabled;
        if (this.showContract && hasContract()) {
            PilgrimContractView contract = getSelectedContract();
            if (contract == null) {
                return;
            }
            label = contract.actionLabel().getString();
            enabled = active && contract.actionEnabled();
        } else {
            TradeView offer = getSelectedOffer();
            label = Component.translatable("screen.village-quest.pilgrim.buy").getString();
            enabled = active && offer != null;
        }
        VillageUiTheme.drawButton(context, this.font, x, y, BUY_WIDTH, BUY_HEIGHT,
                label, enabled, hovered && enabled, true);
    }

    private void drawFooter(GuiGraphics context, int left, int top) {
        VillageUiTheme.drawPaper(context,left+28,top+FOOTER_TEXT_Y_OFFSET-4,WINDOW_WIDTH-56,14);
        String hint = Component.translatable("screen.village-quest.pilgrim.close_hint").getString();
        drawScaledText(context, hint, left + FOOTER_LEFT_INSET, top + FOOTER_TEXT_Y_OFFSET,
                VillageUiTheme.INK, 0.62f);

        String timerText = Component.translatable(
                "screen.village-quest.pilgrim.timer",
                formatRemainingTime(getRemainingDespawnTicks())
        ).getString();
        int timerX = left + WINDOW_WIDTH - FOOTER_RIGHT_INSET - Math.round(this.font.width(timerText) * 0.62f);
        drawScaledText(context, timerText, timerX, top + FOOTER_TEXT_Y_OFFSET,
                VillageUiTheme.INK, 0.62f);
    }

    private void drawTradeSlot(GuiGraphics context, int x, int y, ItemStack stack, float iconScale) {
        drawFrame(context, x, y, 24, 24, FRAME_MID, FRAME_LIGHT, 0x00000000);
        if (stack != null && !stack.isEmpty()) {
            int baseX = x + 4;
            int baseY = y + 4;
            int centeredX = baseX - Math.round((16.0f * (iconScale - 1.0f)) / 2.0f);
            int centeredY = baseY - Math.round((16.0f * (iconScale - 1.0f)) / 2.0f);
            drawScaledItem(context, stack, centeredX, centeredY, iconScale);
        }
    }

    private void drawScaledItem(GuiGraphics context, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (Math.abs(scale - 1.0f) < 0.001f) {
            context.renderItem(stack, x, y);
            return;
        }

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.renderItem(stack, 0, 0);
        matrices.popMatrix();
    }

    private void drawOfferTooltip(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        if(showContract) return;
        TradeView hoveredOffer = getHoveredOffer(left, top, mouseX, mouseY);
        if (hoveredOffer == null) {
            return;
        }
        if (!isTruncatedAtScale(hoveredOffer.title().getString(), GOODS_TEXT_WIDTH, LIST_TITLE_SCALE)) {
            return;
        }

        String title = hoveredOffer.title().getString();
        int tooltipWidth = Math.min(160, this.font.width(title) + 12);
        int tooltipHeight = this.font.lineHeight + 8;
        int tooltipX = Math.min(mouseX + 10, this.width - tooltipWidth - 6);
        int tooltipY = Math.max(6, mouseY - 14);

        context.fill(tooltipX + 2, tooltipY + 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, TOOLTIP_SHADOW);
        drawFrame(context, tooltipX, tooltipY, tooltipWidth, tooltipHeight, FRAME_DARK, FRAME_LIGHT, TOOLTIP_BG);
        context.drawString(this.font, ellipsize(title, tooltipWidth - 10), tooltipX + 5, tooltipY + 4, TITLE, false);
    }

    private void activateCurrentEntry() {
        if (this.showContract && hasContract()) {
            activateContract();
            return;
        }
        buySelectedOffer();
    }

    private void buySelectedOffer() {
        TradeView offer = getSelectedOffer();
        if (offer == null || data.entityId() < 0) {
            return;
        }
        UiTestTour.send(new Payloads.PilgrimTradeActionPayload(data.entityId(), offer.offerId()));
    }

    private void activateContract() {
        PilgrimContractView contract = getSelectedContract();
        if (!hasContract() || data.entityId() < 0 || contract == null || !contract.actionEnabled()) {
            return;
        }
        UiTestTour.send(new Payloads.PilgrimTradeActionPayload(
                data.entityId(),
                "__pilgrim_contract__:" + contract.contractId()
        ));
    }

    private void notifyTradeClosed() {
        if (this.closeNotified || data.entityId() < 0) {
            return;
        }
        this.closeNotified = true;
        UiTestTour.send(new Payloads.PilgrimTradeSessionPayload(
                data.entityId(),
                Payloads.PilgrimTradeSessionPayload.ACTION_CLOSE
        ));
    }

    private void playClickSound() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
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
        int firstOffer = this.goodsScrollIndex;
        int lastOffer = Math.min(offers.size(), firstOffer + GOODS_VISIBLE_ENTRIES);
        for (int i = firstOffer; i < lastOffer; i++) {
            int entryX = left + GOODS_X + 8;
            int entryY = top + GOODS_Y + 8 + ((i - firstOffer) * GOODS_ENTRY_HEIGHT);
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

    private PilgrimContractView getSelectedContract() {
        List<PilgrimContractView> contracts = data.contracts();
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        if (this.selectedContractIndex < 0 || this.selectedContractIndex >= contracts.size()) {
            this.selectedContractIndex = 0;
        }
        return contracts.get(this.selectedContractIndex);
    }

    private String getSelectedContractId() {
        PilgrimContractView contract = getSelectedContract();
        return contract == null ? null : contract.contractId();
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

    private int maxGoodsScrollIndex() {
        List<TradeView> offers = data.offers();
        int offerCount = offers == null ? 0 : offers.size();
        return Math.max(0, offerCount - GOODS_VISIBLE_ENTRIES);
    }

    private void clampGoodsScroll() {
        this.goodsScrollIndex = Math.max(0, Math.min(this.goodsScrollIndex, maxGoodsScrollIndex()));
    }

    private void ensureSelectedOfferVisible() {
        if (this.selectedIndex < this.goodsScrollIndex) {
            this.goodsScrollIndex = this.selectedIndex;
        } else if (this.selectedIndex >= this.goodsScrollIndex + GOODS_VISIBLE_ENTRIES) {
            this.goodsScrollIndex = this.selectedIndex - GOODS_VISIBLE_ENTRIES + 1;
        }
        clampGoodsScroll();
    }

    private void restoreContractSelection(String contractId) {
        List<PilgrimContractView> contracts = data.contracts();
        if (contracts == null || contracts.isEmpty()) {
            this.selectedContractIndex = 0;
            return;
        }
        if (contractId != null) {
            for (int i = 0; i < contracts.size(); i++) {
                if (contractId.equals(contracts.get(i).contractId())) {
                    this.selectedContractIndex = i;
                    return;
                }
            }
        }
        this.selectedContractIndex = Math.min(this.selectedContractIndex, contracts.size() - 1);
        if (this.selectedContractIndex < 0) {
            this.selectedContractIndex = 0;
        }
    }

    private int getBuyButtonX(int left) {
        return left + DETAIL_X + (DETAIL_WIDTH - BUY_WIDTH) / 2;
    }

    private int getBuyButtonY(int top) {
        // Keep the action and hitbox inside the runtime card, clear of its bottom rim.
        int bottom=top+DETAIL_Y+DETAIL_HEIGHT-BUY_HEIGHT-8;
        TradeView offer=getSelectedOffer();
        if(!showContract && offer!=null && font!=null) {
            String description=offer.description()==null?"":offer.description().getString();
            int lines=wrapTextToScale(description,DETAIL_WIDTH-20,DETAIL_BODY_SCALE).size();
            bottom=Math.min(bottom,top+DETAIL_Y+124+Math.max(1,lines)*scaledLineHeight(DETAIL_BODY_SCALE)+12);
        }
        return bottom;
    }

    private int getContractTabX(int left) {
        return left + 150;
    }

    private int getContractTabY(int top) {
        return top + HEADER_CONTRACT_TOP;
    }

    private int getContractBodyTop(int top) {
        return top + DETAIL_Y + 44;
    }

    private int getContractOptionsX(int left) {
        return left + DETAIL_X + 10;
    }

    private int getContractOptionsY(int top) {
        return top + DETAIL_Y + 6;
    }

    private int getContractOptionsWidth() {
        return DETAIL_WIDTH - 20;
    }

    private boolean isHoveringBuyButton(int mouseX, int mouseY, int left, int top) {
        return isWithin(mouseX, mouseY, getBuyButtonX(left), getBuyButtonY(top), BUY_WIDTH, BUY_HEIGHT);
    }

    private boolean isHoveringContractTab(int mouseX, int mouseY, int left, int top) {
        return isWithin(mouseX, mouseY, getContractTabX(left), getContractTabY(top), RUMOR_TAB_WIDTH, RUMOR_TAB_HEIGHT);
    }

    private int getHoveredContractIndex(int mouseX, int mouseY, int left, int top) {
        List<PilgrimContractView> contracts = data.contracts();
        if (contracts == null || contracts.isEmpty()) {
            return -1;
        }
        int startX = getContractOptionsX(left);
        int y = getContractOptionsY(top);
        if (isWithin(mouseX, mouseY, startX, y, getContractOptionsWidth(), CONTRACT_OPTION_HEIGHT)) {
            if (mouseX < startX + 14) return Math.floorMod(selectedContractIndex - 1, contracts.size());
            if (mouseX >= startX + getContractOptionsWidth() - 14) return (selectedContractIndex + 1) % contracts.size();
            return selectedContractIndex;
        }
        return -1;
    }

    private boolean hasContract() {
        return data.contracts() != null && !data.contracts().isEmpty();
    }

    private boolean hasMultipleContracts() {
        return data.contracts() != null && data.contracts().size() > 1;
    }

    private void clampContractScroll() {
        this.contractScrollOffset = Math.max(0, Math.min(this.contractScrollOffset, this.contractScrollMax));
    }

    private boolean hasActionableEntry() {
        if (this.showContract && hasContract()) {
            PilgrimContractView contract = getSelectedContract();
            return contract != null && contract.actionEnabled();
        }
        return getSelectedOffer() != null;
    }

    private boolean canAfford(TradeView offer) {
        return offer != null && data.balance() >= offer.price();
    }

    private int contractColor(PilgrimContractView contract) {
        if (contract == null || contract.contractId() == null) {
            return TITLE;
        }
        return switch (contract.contractId()) {
            case "roadmarks_for_the_compass" -> CONTRACT_ROADMARK;
            case "ash_on_the_pass", "smoke_over_blackstone" -> CONTRACT_NETHER;
            case "stillness_beyond_the_gate" -> CONTRACT_END;
            default -> CONTRACT_OVERWORLD;
        };
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

    private int drawWrappedLines(GuiGraphics context, String text, int x, int y, int maxWidth, int color, int maxLines) {
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
            context.drawString(this.font, rendered, x, y, color, false);
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    private int drawSingleScaledLine(GuiGraphics context,
                                     String text,
                                     int x,
                                     int y,
                                     int maxWidth,
                                     int color,
                                     float scale) {
        if (text == null || text.isBlank()) {
            return y;
        }
        drawScaledText(context, ellipsizeToScale(text, maxWidth, scale), x, y, color, scale);
        return y + Math.round((this.font.lineHeight + 2) * scale);
    }

    private List<ContractDetailLine> buildContractDetailLines(PilgrimContractView contract, int maxWidth, boolean includeStatus) {
        List<ContractDetailLine> lines = new ArrayList<>();
        if (includeStatus) {
            lines.add(new ContractDetailLine(contract.status().getString(), ACCENT, DETAIL_SUBTEXT_SCALE, false));
        }
        addContractParagraphs(lines, contract.descriptionLines(), maxWidth, BODY, DETAIL_BODY_SCALE);
        addContractSection(
                lines,
                Component.translatable("screen.village-quest.pilgrim.contract.objectives"),
                contract.objectiveLines(),
                maxWidth,
                ACCENT,
                DETAIL_SUBTEXT_SCALE,
                BODY,
                DETAIL_BODY_SCALE
        );
        addContractSection(
                lines,
                Component.translatable("screen.village-quest.pilgrim.contract.rewards"),
                contract.rewardLines(),
                maxWidth,
                ACCENT,
                DETAIL_SUBTEXT_SCALE,
                BODY,
                DETAIL_BODY_SCALE
        );
        return lines;
    }

    private void addContractParagraphs(List<ContractDetailLine> lines,
                                       List<Component> content,
                                       int maxWidth,
                                       int color,
                                       float scale) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (!lines.isEmpty()) {
            lines.add(new ContractDetailLine("", color, scale, true));
        }
        for (String wrapped : collectWrappedScaledLines(content, maxWidth, scale)) {
            lines.add(new ContractDetailLine(wrapped, color, scale, false));
        }
    }

    private void addContractSection(List<ContractDetailLine> lines,
                                    Component heading,
                                    List<Component> content,
                                    int maxWidth,
                                    int headingColor,
                                    float headingScale,
                                    int bodyColor,
                                    float bodyScale) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (!lines.isEmpty()) {
            lines.add(new ContractDetailLine("", bodyColor, bodyScale, true));
        }
        lines.add(new ContractDetailLine(heading.getString(), TITLE, Math.max(.9f,headingScale), false, true));
        for (String wrapped : collectWrappedScaledLines(content, maxWidth - 2, bodyScale)) {
            lines.add(new ContractDetailLine(wrapped, bodyColor, bodyScale, false));
        }
    }

    private List<String> collectWrappedScaledLines(List<Component> content, int maxWidth, float scale) {
        List<String> wrapped = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return wrapped;
        }
        for (Component line : content) {
            wrapped.addAll(wrapTextToScale(line.getString(), maxWidth, scale));
        }
        return wrapped;
    }

    private int measureContractContentHeight(List<ContractDetailLine> lines) {
        int height = 0;
        for (ContractDetailLine line : lines) {
            height += line.spacer() ? 3 : scaledLineHeight(line.scale());
        }
        return height;
    }

    private int scaledLineHeight(float scale) {
        return Math.max(1, Math.round((this.font.lineHeight + 2) * scale));
    }

    private void clampShopDetailScroll() {
        this.shopDetailScrollOffset = Math.max(0, Math.min(this.shopDetailScrollOffset, this.shopDetailScrollMax));
    }

    private void drawContractScrollIndicator(GuiGraphics context, int panelX, int top, int bottom, int contentHeight) {
        drawScrollIndicator(context, panelX, top, bottom, contentHeight, this.contractScrollOffset, this.contractScrollMax);
    }

    private void drawScrollIndicator(GuiGraphics context,
                                     int panelX,
                                     int top,
                                     int bottom,
                                     int contentHeight,
                                     int scrollOffset,
                                     int scrollMax) {
        int viewportHeight = Math.max(0, bottom - top);
        if (contentHeight <= viewportHeight || viewportHeight <= 0) {
            return;
        }
        int trackX = panelX + DETAIL_WIDTH - 6;
        VillageUiTheme.drawScrollBar(context, trackX - 2, top, viewportHeight,
                viewportHeight, contentHeight, scrollOffset, scrollMax);
    }

    private float fitScaleToWidth(String text, int maxWidth, float preferredScale, float minimumScale) {
        if (text == null || text.isBlank()) {
            return preferredScale;
        }
        int textWidth = this.font.width(text);
        if (textWidth <= 0) {
            return preferredScale;
        }
        float preferredWidth = textWidth * preferredScale;
        if (preferredWidth <= maxWidth) {
            return preferredScale;
        }
        float scaled = preferredScale * ((float) maxWidth / preferredWidth);
        return Math.max(minimumScale, Math.min(preferredScale, scaled));
    }

    private int drawWrappedScaledLines(GuiGraphics context,
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

        int lineHeight = Math.round((this.font.lineHeight + 2) * scale);
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

    private void drawFrame(GuiGraphics context, int x, int y, int width, int height, int outer, int inner, int fill) {
        VillageUiTheme.drawPaper(context,x,y,width,height);
    }

    private ItemStack getPricePreviewStack(long price) {
        if (price >= CurrencyService.CROWN) {
            return new ItemStack(ModItems.CROWN);
        }
        return new ItemStack(ModItems.SILVERMARK);
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        String trimmed = text;
        while (!trimmed.isEmpty() && this.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    private boolean isTruncated(String text, int maxWidth) {
        return text != null && this.font.width(text) > maxWidth;
    }

    private String ellipsizeToScale(String text, int maxWidth, float scale) {
        int unscaledWidth = Math.max(1, Math.round(maxWidth / scale));
        return ellipsize(text, unscaledWidth);
    }

    private boolean isTruncatedAtScale(String text, int maxWidth, float scale) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return Math.round(this.font.width(text) * scale) > maxWidth;
    }

    private List<String> wrapTextToScale(String text, int maxWidth, float scale) {
        int unscaledWidth = Math.max(1, Math.round(maxWidth / scale));
        return wrapText(text, unscaledWidth);
    }

    private void drawScaledText(GuiGraphics context, String text, int x, int y, int color, float scale) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        context.drawString(this.font, text, Math.round(x / scale), Math.round(y / scale), color, false);
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
            if (this.font.width(candidate) > maxWidth) {
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
