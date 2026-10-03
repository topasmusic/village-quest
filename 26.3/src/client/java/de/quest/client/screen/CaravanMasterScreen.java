package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Read-only snapshot of the interacted master and their authoritative route. */
public final class CaravanMasterScreen extends CompatScreen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_frame.png");
    private static final Identifier INNER = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/guild_notice_board_inner.png");
    private static final int WIDTH = 416, HEIGHT = 234;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;

    private VillageNetworkPayloads.CaravanMasterPayload data;
    private int refreshTicks;
    private de.quest.reputation.ReputationViewService.View reputation;
    public void updateReputation(de.quest.reputation.ReputationViewService.View view) { reputation = view; }

    public CaravanMasterScreen(VillageNetworkPayloads.CaravanMasterPayload data) {
        super(Component.translatable("screen.village-quest.caravan_master.title"));
        this.data = data;
    }

    public void updateData(VillageNetworkPayloads.CaravanMasterPayload data) {
        if (this.data.entityId().equals(data.entityId())) this.data = data;
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 100) {
            refreshTicks = 0;
            ClientPlayNetworking.send(new VillageNetworkPayloads.CaravanMasterActionPayload(
                    data.entityId(), VillageNetworkPayloads.CaravanMasterActionPayload.REFRESH));
        }
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
            center(graphics, data.masterName().getString(), left + 208, top + 14, CREAM, 0.80f, 280);
            center(graphics, title.getString(), left + 208, top + 28, CREAM, 0.63f, 280);

            int textX = left + 39;
            line(graphics, data.routeName(), textX, top + 52, INK, 330);
            line(graphics, data.journey(), textX, top + 66, MUTED, 330);
            labelValue(graphics, "journey_progress", data.progressPercent() + "%", textX, top + 83, 330);
            graphics.fill(textX, top + 95, textX + 286, top + 99, 0xFFB79A6A);
            graphics.fill(textX, top + 95, textX + 286 * Math.max(0, Math.min(100, data.progressPercent())) / 100,
                    top + 99, VillageUiTheme.TEAL);
            String arrival = data.etaSeconds() < 0
                    ? Component.translatable("screen.village-quest.caravan_master.delayed").getString()
                    : String.format("%d:%02d", data.etaSeconds() / 60, data.etaSeconds() % 60);
            labelValue(graphics, "arrival", arrival, textX, top + 106, 145);
            labelValue(graphics, "quality", data.roadQuality() + " / 100", textX + 153, top + 106, 178);
            labelValue(graphics, "status", data.roadStatus().getString(), textX, top + 119, 145);
            line(graphics, data.availability(), textX + 153, top + 119, MUTED, 178);
            if (!data.incident().getString().isBlank()) {
                labelValue(graphics, "incident", data.incident().getString(), textX, top + 132, 330);
            }
            labelValue(graphics, "cargo", data.cargo().getString(), textX, top + 147, 330);
            if (reputation != null) {
                Component standing = ReputationPanel.text("master_standing", ReputationPanel.rank(reputation.guildTrust()), reputation.guildTrust(), reputation.buyLimit());
                VillageUiTheme.drawStringScaled(graphics, font, VillageUiTheme.ellipsize(font, standing.getString(), 450), textX, top + 163, INK, 0.72f);
                if (within(uiX, uiY, textX, top + 161, 330, 13)) graphics.setTooltipForNextFrame(font,
                        reputation.reparation() == null ? standing : standing.copy().append(" ").append(ReputationPanel.text("case_locations")), uiX, uiY);
            }
            line(graphics, data.quote(), textX, top + 177, MUTED, 330);

            if (data.canViewRoute()) {
                VillageUiTheme.drawButton(graphics, font, left + 132, top + 210, 152, 18,
                        Component.translatable("screen.village-quest.caravan_master.view_route").getString(),
                        true, within(uiX, uiY, left + 132, top + 210, 152, 18), false);
            }
            VillageUiTheme.drawButton(graphics, font, left + 331, top + 210, 67, 18,
                    Component.translatable("screen.village-quest.caravan_master.close").getString(),
                    true, within(uiX, uiY, left + 331, top + 210, 67, 18), false);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT), y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (data.canViewRoute() && within(x, y, left + 132, top + 210, 152, 18)) {
            ClientPlayNetworking.send(new VillageNetworkPayloads.CaravanMasterActionPayload(
                    data.entityId(), VillageNetworkPayloads.CaravanMasterActionPayload.VIEW_ROUTE));
            return true;
        }
        if (within(x, y, left + 331, top + 210, 67, 18)) {
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void line(GuiGraphics graphics, Component value, int x, int y, int color, int maxWidth) {
        graphics.drawString(font, VillageUiTheme.ellipsize(font, value.getString(), maxWidth), x, y, color, false);
    }

    private void labelValue(GuiGraphics graphics, String label, String value, int x, int y, int maxWidth) {
        String prefix = Component.translatable("screen.village-quest.caravan_master." + label).getString() + ": ";
        graphics.drawString(font, VillageUiTheme.ellipsize(font, prefix + value, maxWidth), x, y, INK, false);
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
