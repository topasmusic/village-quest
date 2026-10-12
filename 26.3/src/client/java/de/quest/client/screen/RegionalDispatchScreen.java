package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import de.quest.client.compat.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A compact board page for actual two-route regional freight. */
public final class RegionalDispatchScreen extends CompatScreen {
    private static final int WIDTH = NoticeJourneyLayout.WIDTH, HEIGHT = NoticeJourneyLayout.HEIGHT;
    private static final int FOOTER_Y = 162;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.RegionalDispatchPayload data;

    public RegionalDispatchScreen(VillageNetworkPayloads.RegionalDispatchPayload data) {
        super(Component.translatable("screen.village-quest.dispatch.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.RegionalDispatchPayload data) {
        this.data = data;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.drawCompactNoticeBoard(graphics, left, top, WIDTH, HEIGHT);
            boolean freight = !data.offers().isEmpty() || !data.activeCargo().isEmpty()
                    || !data.path().getString().isBlank() || data.canClaim();
            VillageUiTheme.drawCard(graphics, left + 18, top + 39, 324, freight ? 120 : 98, false, false);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon("story"), left + 26, top + 49, 20);
            center(graphics, Component.translatable("screen.village-quest.notice_journey.title").getString(),
                    left + 180, top + 12, CREAM, 0.78f, 200);
            center(graphics, data.source().getString(), left + 180, top + 31, CREAM, 0.62f, 260);
            center(graphics, freight ? data.status().getString() : title.getString(),
                    left + 188, top + 50, INK, 0.85f, 260);
            if (!freight) {
                List<String> description = wrap(data.status().getString(), 357);
                for (int i = 0; i < Math.min(5, description.size()); i++) {
                    VillageUiTheme.drawStringScaled(graphics, font, description.get(i),
                            left + 28, top + 72 + i * 9, MUTED, 0.85f);
                }
            } else if (!data.path().getString().isBlank()) {
                VillageUiTheme.drawStringScaled(graphics, font,
                        VillageUiTheme.ellipsize(font, data.path().getString(), 402),
                        left + 50, top + 63, MUTED, .7f);
            }
            if (freight && within(uiX, uiY, left + 50, top + 49, 282, 23)) {
                graphics.setTooltipForNextFrame(font, data.path().getString().isBlank()
                        ? data.status() : Component.empty().append(data.status()).append(" · ").append(data.path()), uiX, uiY);
            }
            if (!data.activeCargo().isEmpty()) {
                graphics.renderItem(data.activeCargo(), left + 28, top + 73);
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font,
                        Component.translatable("screen.village-quest.dispatch.cargo", data.activeAmount(),
                                data.activeCargo().getHoverName()).getString(), 376), left + 50, top + 78, INK, .75f);
            }
            for (int i = 0; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + (data.activeCargo().isEmpty() ? 76 : 94) + i * 20;
                if (rowY > top + 136) break;
                graphics.renderItem(offer.cargo(), left + 28, rowY + 1);
                String label = Component.translatable("screen.village-quest.dispatch.offer",
                        offer.destination(), offer.amount(), offer.cargo().getHoverName(),
                        offer.reward()).getString();
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, label, 300),
                        left + 50, rowY + 5, INK, .75f);
                if (within(uiX, uiY, left + 28, rowY, 247, 18)) {
                    graphics.setTooltipForNextFrame(font, Component.literal(label), uiX, uiY);
                }
                boolean enabled = data.canStart() && offer.inventory() >= offer.amount();
                VillageUiTheme.drawButton(graphics, font, left + 283, rowY, 49, 16,
                        Component.translatable("screen.village-quest.dispatch.send").getString(),
                        enabled, within(uiX, uiY, left + 283, rowY, 49, 16), false);
            }
            if (data.canClaim()) {
                VillageUiTheme.drawButton(graphics, font, left + 136, top + FOOTER_Y, 130, 14,
                        Component.translatable("screen.village-quest.dispatch.claim").getString(),
                        true, within(uiX, uiY, left + 136, top + FOOTER_Y, 130, 14), true);
            }
            VillageUiTheme.drawButton(graphics, font, left + 18, top + FOOTER_Y, 110, 14,
                    Component.translatable("screen.village-quest.dispatch.back").getString(), true,
                    within(uiX, uiY, left + 18, top + FOOTER_Y, 110, 14), false);
            VillageUiTheme.drawButton(graphics, font, left + 282, top + FOOTER_Y, 60, 14,
                    Component.translatable("screen.village-quest.notice_board.close").getString(), true,
                    within(uiX, uiY, left + 282, top + FOOTER_Y, 60, 14), false);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT);
        int y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (data.canStart()) {
            for (int i = 0; i < data.offers().size(); i++) {
                var offer = data.offers().get(i);
                int rowY = top + (data.activeCargo().isEmpty() ? 76 : 94) + i * 20;
                if (rowY > top + 136) break;
                if (offer.inventory() >= offer.amount() && within(x, y, left + 283, rowY, 49, 16)) {
                    send(VillageNetworkPayloads.RegionalDispatchActionPayload.START, offer.routeIndex());
                    return true;
                }
            }
        }
        if (data.canClaim() && within(x, y, left + 136, top + FOOTER_Y, 130, 14)) {
            send(VillageNetworkPayloads.RegionalDispatchActionPayload.CLAIM, -1);
            return true;
        }
        if (within(x, y, left + 18, top + FOOTER_Y, 110, 14)) {
            UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(),
                    VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH));
            return true;
        }
        if (within(x, y, left + 282, top + FOOTER_Y, 60, 14)) {
            onClose();
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private void send(int action, int route) {
        UiTestTour.send(new VillageNetworkPayloads.RegionalDispatchActionPayload(
                data.worldX(), data.worldY(), data.worldZ(), action, route));
    }

    private void center(GuiGraphics graphics, String value, float center, float y,
                        int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(candidate);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
