package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.economy.CurrencyService;
import de.quest.network.VillageNetworkPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Illustrated village commission board with server-authoritative delivery actions. */
public final class GuildNoticeBoardScreen extends CompatScreen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
    private static final Identifier INNER = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
    private static final Identifier BOND_COMPLETE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_complete.png");
    private static final Identifier BOND_CURRENT = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_current.png");
    private static final Identifier BOND_LOCKED = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/notice_board/bond_locked.png");
    private static final int FRAME_TEXTURE_WIDTH = 416;
    private static final int FRAME_TEXTURE_HEIGHT = 234;
    private static final int WIDTH = 416;
    private static final int HEIGHT = 234;
    private static final int INNER_X = 8;
    private static final int INNER_Y = 14;
    private static final int INNER_WIDTH = 400;
    private static final int INNER_HEIGHT = 207;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int META = 0xFF5A3B24;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFF704411;
    private static final int LIGHT = 0xFFF1D29A;

    private static final int WALLET_RIGHT_INSET = 26;
    private static final int WALLET_TOP = 7;

    private static final int DELIVER_X = 168;
    private static final int CLOSE_X = 330;
    private static final int BUTTON_Y = 214;
    private static final int DELIVER_WIDTH = 156;
    private static final int CLOSE_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 16;

    private static final int[] BOND_CENTERS = {143, 211, 271};
    private static final int BOND_CENTER_Y = 200;
    private static final int BOND_SEAL_SIZE = 20;
    private static final int[] OFFER_X = {81, 169, 258};
    private static final int[] OFFER_WIDTH = {78, 78, 79};
    private static final int OFFER_Y = 43;
    private static final int OFFER_HEIGHT = 38;
    private static final int[] OFFER_CENTER_X = {120, 208, 298};

    private VillageNetworkPayloads.NoticeBoardPayload data;
    private int selectedOfferId;

    public GuildNoticeBoardScreen(VillageNetworkPayloads.NoticeBoardPayload data) {
        super(Component.translatable("screen.village-quest.notice_board.title"));
        this.data = data;
        this.selectedOfferId = firstOffer().id();
    }

    public void updateData(VillageNetworkPayloads.NoticeBoardPayload data) {
        this.data = data;
        if (data.offers() == null || data.offers().stream().noneMatch(offer -> offer.id() == selectedOfferId)) {
            selectedOfferId = firstOffer().id();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.blitScaled(graphics, INNER,
                    left + INNER_X, top + INNER_Y, INNER_WIDTH, INNER_HEIGHT,
                    INNER_WIDTH, INNER_HEIGHT);
            drawRequest(graphics, left, top, uiX, uiY);
            drawBondPath(graphics, left, top, uiX, uiY);
            VillageUiTheme.blitScaled(graphics, FRAME, left, top, WIDTH, HEIGHT,
                    FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT);
            drawHeader(graphics, left, top);
            drawFooter(graphics, left, top, uiX, uiY);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        String heading = VillageUiTheme.ellipsize(font, title.getString(), 170);
        float titleScale = 0.78f;
        float titleWidth = font.width(heading) * titleScale;
        VillageUiTheme.drawStringScaled(graphics, font, heading,
                left + (WIDTH - titleWidth) / 2.0f, top + 14.0f, INK, titleScale);
        VillageUiTheme.drawWalletStrip(graphics, font, left, top, WIDTH, data.balance(),
                WALLET_RIGHT_INSET, WALLET_TOP);

        String village = data.villageType().getString() + " · " + data.bondLevel().getString()
                + " · " + data.villageCondition().getString() + " · " + data.adventureProfile().getString();
        float villageScale = 0.64f;
        String visible = VillageUiTheme.ellipsize(font, village,
                Math.round((WIDTH - 48) / villageScale));
        float villageWidth = font.width(visible) * villageScale;
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                left + (WIDTH - villageWidth) / 2.0f, top + 32.0f, LIGHT, villageScale);
    }

    private void drawRequest(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = offers();
        for (int i = 0; i < offers.size() && i < 3; i++) {
            VillageNetworkPayloads.NoticeBoardOfferData offer = offers.get(i);
            int x = left + OFFER_X[i];
            int offerY = top + OFFER_Y;
            int offerWidth = OFFER_WIDTH[i];
            boolean selected = offer.id() == selectedOfferId;
            if (selected) {
                graphics.fill(x + 6, offerY + OFFER_HEIGHT - 5,
                        x + offerWidth - 6, offerY + OFFER_HEIGHT - 2, TEAL);
            }
            drawScaledItem(graphics, offer.stack(), left + OFFER_CENTER_X[i] - 8, top + 50, 1.0f);
            drawCenteredScaled(graphics, offer.inventoryAmount() + " / " + offer.requiredAmount(),
                    left + OFFER_CENTER_X[i], top + 69, BODY, 0.60f, offerWidth - 8);
            if (within(mouseX, mouseY, x, offerY, offerWidth, OFFER_HEIGHT)) {
                int remaining = Math.max(0, offer.requiredAmount() - offer.inventoryAmount());
                graphics.setTooltipForNextFrame(font, List.of(
                        offer.title(),
                        Component.translatable("screen.village-quest.notice_board.selection",
                                offer.title(), remaining, offer.support()),
                        CurrencyService.formatBalance(offer.reward())), mouseX, mouseY);
            }
        }

        VillageNetworkPayloads.NoticeBoardOfferData selected = selectedOffer();
        int required = Math.max(0, selected.requiredAmount());
        int shown = required <= 0 ? 0 : Math.min(Math.max(0, selected.inventoryAmount()), required);
        drawCenteredScaled(graphics, selected.title().getString(),
                left + 208, top + 96, INK, 0.68f, 220);
        drawScaledItem(graphics, selected.stack(), left + 90, top + 107, 1.5f);
        drawCenteredScaled(graphics, shown + " / " + required,
                left + 208, top + 108, INK, 0.65f, 150);

        String context = data.requestAvailable()
                ? Component.translatable("screen.village-quest.notice_board.network_need",
                        data.villageNeed(), Math.max(0, Math.min(100, data.villageSupport())), 100).getString()
                : Component.translatable("screen.village-quest.notice_board.available_after_reset").getString();
        drawCenteredScaled(graphics, context, left + 216, top + 120, META, 0.60f, 190);
        drawCenteredScaled(graphics, CurrencyService.formatBalance(selected.reward()).getString(),
                left + 174, top + 132, GOLD, 0.60f, 92);
        drawCenteredScaled(graphics, "+" + selected.support(),
                left + 274, top + 132, TEAL, 0.60f, 58);

        int progressX = left + 128;
        int progressY = top + 141;
        int progressWidth = 168;
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 7, 0xFF5A351E);
        int innerWidth = progressWidth - 4;
        int filled = required <= 0 ? innerWidth : Math.round(innerWidth * (shown / (float) required));
        graphics.fill(progressX + 2, progressY + 2, progressX + 2 + filled, progressY + 5, TEAL);
        VillageUiTheme.drawStringScaled(graphics, font, shown + " / " + required,
                left + 301, top + 140, INK, 0.60f);
    }

    private void drawBondPath(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String bondSummary;
        if (data.nextThreshold() > 0) {
            bondSummary = Component.translatable(
                    "screen.village-quest.notice_board.next_progress", data.nextLevel(),
                    data.completions(), data.nextThreshold()).getString();
        } else {
            bondSummary = data.nextPerk().getString();
        }
        drawCenteredScaled(graphics, bondSummary, left + WIDTH / 2.0f, top + 172,
                INK, 0.60f, 260);

        int bondTier = Math.max(0, Math.min(data.bondTier(), BOND_CENTERS.length - 1));
        int connectorY = top + BOND_CENTER_Y;
        graphics.fill(left + BOND_CENTERS[0], connectorY - 1,
                left + BOND_CENTERS[BOND_CENTERS.length - 1], connectorY + 1, 0xFF5A351E);
        graphics.fill(left + BOND_CENTERS[0], connectorY - 1,
                left + BOND_CENTERS[BOND_CENTERS.length - 1], connectorY, TEAL);
        for (int i = 0; i < BOND_CENTERS.length; i++) {
            boolean completed = bondTier > i;
            boolean current = bondTier == i;
            int centerX = left + BOND_CENTERS[i];
            int centerY = top + BOND_CENTER_Y;
            Identifier seal = completed ? BOND_COMPLETE : current ? BOND_CURRENT : BOND_LOCKED;
            VillageUiTheme.blitScaled(graphics, seal,
                    centerX - BOND_SEAL_SIZE / 2, centerY - BOND_SEAL_SIZE / 2,
                    BOND_SEAL_SIZE, BOND_SEAL_SIZE, BOND_SEAL_SIZE, BOND_SEAL_SIZE);
        }

        String hint = Component.translatable("screen.village-quest.notice_board.bond_hint").getString();
        int finalThreshold = data.nextThreshold() > 0 ? data.nextThreshold() : 8;
        if (within(mouseX, mouseY, left + 90, top + 165, 238, 45)) {
            graphics.setTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.village-quest.notice_board.bond_path"),
                    Component.translatable("screen.village-quest.notice_board.bond_summary"),
                    Component.translatable("screen.village-quest.notice_board.bond_requests",
                            data.completions(), finalThreshold),
                    Component.literal(hint), data.nextPerk()), mouseX, mouseY);
        }
    }

    private void drawFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        boolean deliverHover = within(mouseX, mouseY,
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT);
        boolean closeHover = within(mouseX, mouseY,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT);
        VillageUiTheme.drawButton(graphics, font,
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT,
                Component.translatable(data.requestAvailable()
                        ? "screen.village-quest.notice_board.deliver"
                        : "screen.village-quest.notice_board.return_tomorrow").getString(),
                data.requestAvailable() && selectedOffer().canDeliver(), deliverHover, false);
        VillageUiTheme.drawButton(graphics, font,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.village-quest.notice_board.close").getString(),
                true, closeHover, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (within(mouseX, mouseY,
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT)
                && data.requestAvailable() && selectedOffer().canDeliver()) {
            ClientPlayNetworking.send(new VillageNetworkPayloads.NoticeBoardActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_DELIVER, selectedOffer().id()));
            return true;
        }
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = offers();
        for (int i = 0; i < offers.size() && i < 3; i++) {
            if (within(mouseX, mouseY, left + OFFER_X[i], top + OFFER_Y,
                    OFFER_WIDTH[i], OFFER_HEIGHT)) {
                selectedOfferId = offers.get(i).id();
                return true;
            }
        }
        if (within(mouseX, mouseY,
                left + CLOSE_X, top + BUTTON_Y, CLOSE_WIDTH, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void drawCenteredScaled(GuiGraphics graphics, String text,
                                    float centerX, float y, int color, float scale, int width) {
        String visible = VillageUiTheme.ellipsize(font, text, Math.round(width / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                centerX - font.width(visible) * scale / 2.0f, y, color, scale);
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack,
                                       int x, int y, float scale) {
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

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private List<VillageNetworkPayloads.NoticeBoardOfferData> offers() {
        if (data.offers() != null && !data.offers().isEmpty()) return data.offers();
        return List.of(new VillageNetworkPayloads.NoticeBoardOfferData(0, data.requestTitle(), data.requestStack(),
                data.requiredAmount(), data.inventoryAmount(), data.reward(), 0, false, data.canDeliver()));
    }

    private VillageNetworkPayloads.NoticeBoardOfferData firstOffer() {
        return offers().getFirst();
    }

    private VillageNetworkPayloads.NoticeBoardOfferData selectedOffer() {
        return offers().stream().filter(offer -> offer.id() == selectedOfferId).findFirst().orElse(firstOffer());
    }
}
