package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A compact board page for actual two-route regional freight. */
public final class RegionalDispatchScreen extends CompatScreen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
    private static final Identifier INNER = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
    private static final int WIDTH = 416, HEIGHT = 234;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.RegionalDispatchPayload data;

    public RegionalDispatchScreen(VillageNetworkPayloads.RegionalDispatchPayload data) {
        super(Component.translatable("screen.village-quest.dispatch.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.RegionalDispatchPayload data) {
        this.data = data;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.blitScaled(graphics, INNER, left + 8, top + 14, 400, 207, 400, 207);
            VillageUiTheme.drawCard(graphics, left + 25, top + 43, 366, 158, false, false);
            VillageUiTheme.blitScaled(graphics, FRAME, left, top, WIDTH, HEIGHT, WIDTH, HEIGHT);
            center(graphics, title.getString(), left + 208, top + 16, CREAM, 0.78f, 250);
            center(graphics, data.source().getString(), left + 208, top + 32, CREAM, 0.61f, 280);
            graphics.drawString(font, data.status().getString(), left + 39, top + 52, INK, false);
            if (!data.path().getString().isBlank()) {
                graphics.drawString(font, VillageUiTheme.ellipsize(font, data.path().getString(), 340),
                        left + 39, top + 66, MUTED, false);
            }
            if (!data.activeCargo().isEmpty()) {
                graphics.renderItem(data.activeCargo(), left + 39, top + 82);
                graphics.drawString(font, Component.translatable("screen.village-quest.dispatch.cargo",
                        data.activeAmount(), data.activeCargo().getHoverName()), left + 61, top + 87, INK, false);
            }
            for (int i = 0; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + 103 + i * 24;
                if (rowY > top + 179) break;
                graphics.renderItem(offer.cargo(), left + 39, rowY + 1);
                String label = Component.translatable("screen.village-quest.dispatch.offer",
                        offer.destination(), offer.amount(), offer.cargo().getHoverName(),
                        offer.reward()).getString();
                graphics.drawString(font, VillageUiTheme.ellipsize(font, label, 258),
                        left + 62, rowY + 5, INK, false);
                if (within(uiX, uiY, left + 39, rowY, 280, 20)) {
                    graphics.setTooltipForNextFrame(font, Component.literal(label), uiX, uiY);
                }
                boolean enabled = data.canStart() && offer.inventory() >= offer.amount();
                VillageUiTheme.drawButton(graphics, font, left + 326, rowY, 58, 19,
                        Component.translatable("screen.village-quest.dispatch.send").getString(),
                        enabled, within(uiX, uiY, left + 326, rowY, 58, 19), false);
            }
            if (data.canClaim()) {
                VillageUiTheme.drawButton(graphics, font, left + 143, top + 183, 130, 18,
                        Component.translatable("screen.village-quest.dispatch.claim").getString(),
                        true, within(uiX, uiY, left + 143, top + 183, 130, 18), false);
            }
            VillageUiTheme.drawButton(graphics, font, left + 17, top + 213, 141, 16,
                    Component.translatable("screen.village-quest.dispatch.back").getString(), true,
                    within(uiX, uiY, left + 17, top + 213, 141, 16), false);
            VillageUiTheme.drawButton(graphics, font, left + 331, top + 213, 67, 16,
                    Component.translatable("screen.village-quest.notice_board.close").getString(), true,
                    within(uiX, uiY, left + 331, top + 213, 67, 16), false);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (data.canStart()) {
            for (int i = 0; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + 103 + i * 24;
                if (rowY > top + 179) break;
                if (offer.inventory() >= offer.amount() && within(x, y, left + 326, rowY, 58, 19)) {
                    send(VillageNetworkPayloads.RegionalDispatchActionPayload.START, offer.routeIndex());
                    return true;
                }
            }
        }
        if (data.canClaim() && within(x, y, left + 143, top + 183, 130, 18)) {
            send(VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM, -1);
            return true;
        }
        if (within(x, y, left + 17, top + 213, 141, 16)) {
            ClientPlayNetworking.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH));
            return true;
        }
        if (within(x, y, left + 331, top + 213, 67, 16)) {
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void send(int action, int route) {
        ClientPlayNetworking.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(
                data.worldX(), data.worldY(), data.worldZ(), action, route));
    }

    private void center(GuiGraphics graphics, String value, float center, float y,
                        int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
