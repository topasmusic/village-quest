package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import java.util.ArrayList;
import java.util.List;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The first-village and local-story page of the physical Notice Post. */
public final class NoticeJourneyScreen extends CompatScreen {
    private static final int WIDTH = NoticeJourneyLayout.WIDTH, HEIGHT = NoticeJourneyLayout.HEIGHT;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, TEAL = 0xFF246E6A;
    private static final int CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.NoticeJourneyPayload data;
    private String trustFeedback = "";

    public NoticeJourneyScreen(VillageNetworkPayloads.NoticeJourneyPayload data) {
        super(Component.translatable("screen.village-quest.notice_journey.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.NoticeJourneyPayload data) {
        this.data = data;
        trustFeedback = "";
    }

    public void showTrustFeedback(String reasonKey) {
        trustFeedback = reasonKey;
    }

    // This page exchanges server packets; singleplayer cooldowns must keep advancing.
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
            VillageUiTheme.drawCard(graphics, left + 18, top + 39, 324, 98, false, false);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon("story"), left + 26, top + 49, 20);
            drawCentered(graphics, title.getString(), left + 180, top + 12, CREAM, 0.78f, 200);
            drawCentered(graphics, data.village().getString(), left + 180, top + 31, CREAM, 0.62f, 260);
            drawCentered(graphics, trustFeedback.isBlank() ? data.title().getString()
                    : Component.translatable("screen.village-quest.reputation.title").getString(),
                    left + 188, top + 50, INK, 0.85f, 260);
            List<String> description = wrap(trustFeedback.isBlank() ? data.detail().getString()
                    : Component.translatable(trustFeedback).getString(), 357);
            for (int i = 0; i < Math.min(trustFeedback.isBlank() ? 3 : 5, description.size()); i++) {
                VillageUiTheme.drawStringScaled(graphics, font, description.get(i),
                        left + 28, top + 72 + i * 9, MUTED, 0.85f);
            }
            if (trustFeedback.isBlank()) drawProgress(graphics, left, top);
            drawButtons(graphics, left, top, uiX, uiY);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawProgress(GuiGraphics graphics, int left, int top) {
        if (data.firstTarget() > 0 && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.READY
                && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_READY) {
            String label = data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                    ? Component.translatable("screen.village-quest.notice_journey.greetings").getString()
                    : data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RESETTLEMENT
                    || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RECOVERING
                    ? Component.translatable("screen.village-quest.village_life.settlers").getString()
                    : data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_ACTIVE
                    ? Component.translatable("screen.village-quest.afterstory.supplies").getString()
                    : Component.translatable("screen.village-quest.notice_journey.objective_one").getString();
            progress(graphics, left + 28, top + 113, label, data.first(), data.firstTarget());
            if (data.secondTarget() > 0) {
                progress(graphics, left + 190, top + 113,
                        Component.translatable(data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RESETTLEMENT
                                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.RECOVERING
                                ? "screen.village-quest.village_life.beds"
                                : "screen.village-quest.notice_journey.objective_two").getString(),
                        data.second(), data.secondTarget());
            }
        }
        if ((data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.READY
                || data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_READY)
                && !data.delivery().isEmpty()) {
            graphics.renderItem(data.delivery(), left + 48, top + 113);
            VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, Component.translatable("screen.village-quest.notice_journey.bundle",
                    data.deliveryCount(), data.delivery().getHoverName()).getString(), 342), left + 71, top + 118, INK, .75f);
        }
    }

    private void progress(GuiGraphics graphics, int x, int y, String label, int current, int target) {
        VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, label + "  " + current + "/" + target, 190), x, y, INK, 0.7f);
        graphics.fill(x, y + 11, x + 132, y + 17, 0xFF6A4B30);
        graphics.fill(x + 2, y + 13, x + 130, y + 15, 0xFFD7BC8A);
        graphics.fill(x + 2, y + 13, x + 2 + Math.round(128f * Math.min(target, current) / target),
                y + 15, TEAL);
    }

    private void drawButtons(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        for (var button : NoticeJourneyLayout.buttons(data.stage(), data.requests() != null)) {
            VillageUiTheme.drawButton(graphics, font, left + button.x(), top + button.y(),
                    button.width(), button.height(), Component.translatable(button.key()).getString(),
                    true, button.contains(mouseX - left, mouseY - top), button.primary());
            if (button.contains(mouseX - left, mouseY - top)) {
                graphics.setTooltipForNextFrame(font, Component.translatable(button.key()), mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT) - left;
        int y = responsiveMouseY(click.y(), WIDTH, HEIGHT) - top;
        for (var button : NoticeJourneyLayout.buttons(data.stage(), data.requests() != null)) {
            if (!button.contains(x, y)) continue;
            if (button.action() != NoticeJourneyLayout.TRUST) trustFeedback = "";
            switch (button.action()) {
                case NoticeJourneyLayout.CLOSE -> onClose();
                case NoticeJourneyLayout.TRUST -> {
                    if (!data.preview()) UiTestTour.send(new de.quest.network.ReputationPayloads.OpenPayload(0,
                            new net.minecraft.core.BlockPos(data.worldX(), data.worldY(), data.worldZ()), 0));
                }
                case NoticeJourneyLayout.REQUESTS -> UiTestTour.navigate(new GuildNoticeBoardScreen(data.requests(), data));
                case NoticeJourneyLayout.QUESTMASTER -> {
                    if (!data.preview() && minecraft != null && minecraft.player != null) {
                        UiTestTour.command("vq questmaster");
                        onClose();
                    }
                }
                default -> {
                    if (!data.preview()) UiTestTour.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                            data.worldX(), data.worldY(), data.worldZ(), button.action()));
                }
            }
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private void drawCentered(GuiGraphics graphics, String value, float center, float y,
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
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
