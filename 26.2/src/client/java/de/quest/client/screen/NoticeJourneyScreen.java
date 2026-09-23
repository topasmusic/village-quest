package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The first-village and local-story page of the physical Notice Post. */
public final class NoticeJourneyScreen extends CompatScreen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
    private static final Identifier INNER = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
    private static final int WIDTH = 416, HEIGHT = 234;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, TEAL = 0xFF246E6A;
    private static final int GOLD = 0xFF9B6A29, CREAM = 0xFFF1D8AA;
    private VillageNetworkPayloads.NoticeJourneyPayload data;

    public NoticeJourneyScreen(VillageNetworkPayloads.NoticeJourneyPayload data) {
        super(Component.translatable("screen.village-quest.notice_journey.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.NoticeJourneyPayload data) {
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
            VillageUiTheme.drawCard(graphics, left + 25, top + 43, 366, 145, false, false);
            graphics.fill(left + 31, top + 51, left + 385, top + 52, GOLD);
            graphics.fill(left + 31, top + 183, left + 385, top + 184, GOLD);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon("story"), left + 33, top + 57, 28);
            VillageUiTheme.blitScaled(graphics, FRAME, left, top, WIDTH, HEIGHT, WIDTH, HEIGHT);

            drawCentered(graphics, title.getString(), left + 208, top + 16, CREAM, 0.78f, 230);
            drawCentered(graphics, data.village().getString(), left + 208, top + 32, CREAM, 0.62f, 270);
            drawCentered(graphics, data.title().getString(), left + 210, top + 61, INK, 0.85f, 280);
            List<String> description = wrap(data.detail().getString(), 345);
            int lineY = top + 86;
            for (int i = 0; i < Math.min(3, description.size()); i++) {
                graphics.drawString(font, description.get(i), left + 36, lineY + i * 11, MUTED, false);
            }
            drawProgress(graphics, left, top);
            drawButtons(graphics, left, top, uiX, uiY);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    private void drawProgress(GuiGraphics graphics, int left, int top) {
        if (data.firstTarget() > 0 && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.READY) {
            String label = data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.INTRO
                    ? Component.translatable("screen.village-quest.notice_journey.greetings").getString()
                    : Component.translatable("screen.village-quest.notice_journey.objective_one").getString();
            progress(graphics, left + 48, top + 135, label, data.first(), data.firstTarget());
            if (data.secondTarget() > 0) {
                progress(graphics, left + 218, top + 135,
                        Component.translatable("screen.village-quest.notice_journey.objective_two").getString(),
                        data.second(), data.secondTarget());
            }
        }
        if (data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.READY && !data.delivery().isEmpty()) {
            graphics.renderItem(data.delivery(), left + 81, top + 145);
            graphics.drawString(font, Component.translatable("screen.village-quest.notice_journey.bundle",
                    data.deliveryCount(), data.delivery().getHoverName()), left + 106, top + 150, INK, false);
        }
    }

    private void progress(GuiGraphics graphics, int x, int y, String label, int current, int target) {
        graphics.drawString(font, label + "  " + current + "/" + target, x, y, INK, false);
        graphics.fill(x, y + 14, x + 148, y + 21, 0xFF6A4B30);
        graphics.fill(x + 2, y + 16, x + 146, y + 19, 0xFFD7BC8A);
        graphics.fill(x + 2, y + 16, x + 2 + Math.round(144f * Math.min(target, current) / target),
                y + 19, TEAL);
    }

    private void drawButtons(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int stage = data.stage();
        if (stage == VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE) {
            button(graphics, left + 59, top + 188, 135, 20, "screen.village-quest.notice_journey.reserve", mouseX, mouseY);
            button(graphics, left + 207, top + 188, 135, 20, "screen.village-quest.notice_journey.share", mouseX, mouseY);
        } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE
                || stage == VillageNetworkPayloads.NoticeJourneyPayload.READY) {
            button(graphics, left + 115, top + 188, 186, 20,
                    stage == VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE
                            ? "screen.village-quest.notice_journey.accept" : "screen.village-quest.notice_journey.deliver",
                    mouseX, mouseY);
        } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER) {
            button(graphics, left + 115, top + 188, 186, 20,
                    "screen.village-quest.notice_journey.questmaster", mouseX, mouseY);
        } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.PAUSED) {
            button(graphics, left + 146, top + 188, 124, 20,
                    "screen.village-quest.notice_journey.resume", mouseX, mouseY);
        } else {
            button(graphics, left + 146, top + 188, 124, 20,
                    "screen.village-quest.notice_journey.refresh", mouseX, mouseY);
        }
        if (data.requests() != null) {
            button(graphics, left + 17, top + 213, 141, 16,
                    "screen.village-quest.notice_journey.requests", mouseX, mouseY);
        }
        button(graphics, left + 331, top + 213, 67, 16,
                "screen.village-quest.notice_board.close", mouseX, mouseY);
    }

    private void button(GuiGraphics graphics, int x, int y, int width, int height,
                        String key, int mouseX, int mouseY) {
        VillageUiTheme.drawButton(graphics, font, x, y, width, height,
                Component.translatable(key).getString(), true,
                within(mouseX, mouseY, x, y, width, height), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT), y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        int action = 0;
        if (data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE) {
            if (within(x, y, left + 59, top + 188, 135, 20)) action = VillageNetworkPayloads.NoticeJourneyActionPayload.RESERVE;
            if (within(x, y, left + 207, top + 188, 135, 20)) action = VillageNetworkPayloads.NoticeJourneyActionPayload.SHARE;
        } else if (within(x, y, left + 115, top + 188, 186, 20)
                && data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE) {
            action = VillageNetworkPayloads.NoticeJourneyActionPayload.ACCEPT;
        } else if (within(x, y, left + 115, top + 188, 186, 20)
                && data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.READY) {
            action = VillageNetworkPayloads.NoticeJourneyActionPayload.DELIVER;
        } else if (within(x, y, left + 115, top + 188, 186, 20)
                && data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER) {
            if (!data.preview() && minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand("vq questmaster");
                onClose();
            }
            return true;
        } else if (within(x, y, left + 146, top + 188, 124, 20)
                && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE
                && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE
                && data.stage() != VillageNetworkPayloads.NoticeJourneyPayload.READY) {
            action = data.stage() == VillageNetworkPayloads.NoticeJourneyPayload.PAUSED
                    ? VillageNetworkPayloads.NoticeJourneyActionPayload.RESUME
                    : VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH;
        }
        if (action != 0) {
            if (!data.preview()) ClientPlayNetworking.send(new VillageNetworkPayloads.NoticeJourneyActionPayload(
                    data.worldX(), data.worldY(), data.worldZ(), action));
            return true;
        }
        if (data.requests() != null && within(x, y, left + 17, top + 213, 141, 16)) {
            minecraft.gui.setScreen(new GuildNoticeBoardScreen(data.requests(), data));
            return true;
        }
        if (within(x, y, left + 331, top + 213, 67, 16)) {
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
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
