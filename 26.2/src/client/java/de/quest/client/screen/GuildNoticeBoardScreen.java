package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
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
            VillageQuest.MOD_ID, "textures/gui/guild_atlas_frame.png");
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
    private static final int INNER_Y = 18;
    private static final int INNER_WIDTH = 400;
    private static final int INNER_HEIGHT = 207;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int META = 0xFF76501E;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFF9A6620;
    private static final int LIGHT = 0xFFF1D29A;

    private static final int WALLET_RIGHT_INSET = 34;
    private static final int WALLET_TOP = 7;

    private static final int DELIVER_X = 168;
    private static final int CLOSE_X = 330;
    private static final int BUTTON_Y = 211;
    private static final int DELIVER_WIDTH = 156;
    private static final int CLOSE_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 17;

    private static final int[] BOND_CENTERS = {116, 208, 300};
    private static final int BOND_CENTER_Y = 190;
    private static final int BOND_SEAL_SIZE = 24;

    private Payloads.NoticeBoardPayload data;

    public GuildNoticeBoardScreen(Payloads.NoticeBoardPayload data) {
        super(Component.translatable("screen.village-quest.notice_board.title"));
        this.data = data;
    }

    public void updateData(Payloads.NoticeBoardPayload data) {
        this.data = data;
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
            drawRequest(graphics, left, top);
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

        String village = data.villageType().getString() + " · " + data.bondLevel().getString();
        float villageScale = 0.64f;
        String visible = VillageUiTheme.ellipsize(font, village,
                Math.round((WIDTH - 48) / villageScale));
        float villageWidth = font.width(visible) * villageScale;
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                left + (WIDTH - villageWidth) / 2.0f, top + 35.0f, LIGHT, villageScale);
    }

    private void drawRequest(GuiGraphics graphics, int left, int top) {
        String section = Component.translatable("screen.village-quest.notice_board.request").getString();
        VillageUiTheme.drawStringScaled(graphics, font, section,
                left + 145, top + 57, META, 0.66f);
        drawScaledItem(graphics, data.requestStack(), left + 96, top + 79, 2.0f);

        String requestTitle = VillageUiTheme.ellipsize(font, data.requestTitle().getString(), 158);
        VillageUiTheme.drawStringScaled(graphics, font, requestTitle,
                left + 145, top + 72, INK, 0.72f);
        int required = Math.max(0, data.requiredAmount());
        int shown = required <= 0 ? 0 : Math.min(Math.max(0, data.inventoryAmount()), required);
        int remaining = Math.max(0, required - shown);
        String needed = data.requestAvailable()
                ? Component.translatable("screen.village-quest.notice_board.still_needed", remaining).getString()
                : Component.translatable("screen.village-quest.notice_board.available_after_reset").getString();
        String neededVisible = VillageUiTheme.ellipsize(font, needed, Math.round(195 / 0.58f));
        VillageUiTheme.drawStringScaled(graphics, font, neededVisible,
                left + 153, top + 88, BODY, 0.58f);

        int progressX = left + 92;
        int progressY = top + 139;
        int progressWidth = 176;
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 7, 0xFF5A351E);
        int innerWidth = progressWidth - 4;
        int filled = required <= 0 ? innerWidth : Math.round(innerWidth * (shown / (float) required));
        graphics.fill(progressX + 2, progressY + 2, progressX + 2 + filled, progressY + 5, TEAL);
        String progress = shown + " / " + required;
        VillageUiTheme.drawStringScaled(graphics, font, progress,
                left + 278, top + 140, INK, 0.58f);

        String rewardLabel = Component.translatable("screen.village-quest.notice_board.reward").getString();
        String reward = CurrencyService.formatBalance(data.reward()).getString();
        drawCenteredScaled(graphics, rewardLabel, left + 321, top + 63, META, 0.52f, 82);
        drawCenteredScaled(graphics, reward, left + 321, top + 122, GOLD, 0.56f, 105);
    }

    private void drawBondPath(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String bondDetail;
        if (data.nextThreshold() > 0) {
            String next = Component.translatable(
                    "screen.village-quest.notice_board.next_progress", data.nextLevel(),
                    data.completions(), data.nextThreshold()).getString();
            bondDetail = next + " · " + data.nextPerk().getString();
        } else {
            bondDetail = data.nextPerk().getString();
        }
        drawCenteredScaled(graphics, bondDetail, left + WIDTH / 2.0f, top + 163,
                LIGHT, 0.48f, 350);

        int bondTier = Math.max(0, Math.min(data.bondTier(), BOND_CENTERS.length - 1));
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
        if (within(mouseX, mouseY, left + 54, top + 168, 308, 40)) {
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
                data.canDeliver(), deliverHover, false);
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
                left + DELIVER_X, top + BUTTON_Y, DELIVER_WIDTH, BUTTON_HEIGHT) && data.canDeliver()) {
            ClientPlayNetworking.send(new Payloads.NoticeBoardActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    Payloads.NoticeBoardActionPayload.ACTION_DELIVER));
            return true;
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
}
