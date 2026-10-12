package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.VillageTooltip;
import de.quest.network.VillageNetworkPayloads;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Read-only snapshot of the interacted master and their authoritative route. */
public final class CaravanMasterScreen extends CompatScreen {
    private static final int WIDTH = 360, HEIGHT = 186;
    private static final int INK = 0xFF302015, MUTED = 0xFF69523C, CREAM = 0xFFF1D8AA;

    private VillageNetworkPayloads.CaravanMasterPayload data;
    private int refreshTicks;
    private final VillageTooltip tooltip = new VillageTooltip();
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
        if (!isPreview() && ++refreshTicks >= 100) {
            refreshTicks = 0;
            UiTestTour.send(new VillageNetworkPayloads.CaravanMasterActionPayload(
                    data.entityId(), VillageNetworkPayloads.CaravanMasterActionPayload.REFRESH));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        tooltip.clear();
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiX = responsiveMouseX(mouseX, WIDTH, HEIGHT);
        int uiY = responsiveMouseY(mouseY, WIDTH, HEIGHT);
        float scale = beginResponsivePanel(graphics, WIDTH, HEIGHT);
        try {
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WIDTH, HEIGHT);
            VillageUiTheme.drawCompactNoticeBoard(graphics, left, top, WIDTH, HEIGHT);
            center(graphics, title.getString(), left + 180, top + 12, INK, 1.0f, 125);
            center(graphics, data.progressPercent() + "%", left + 317, top + 10, INK, 0.85f, 46);

            inset(graphics, left + 18, top + 40, 324, 50);
            inset(graphics, left + 18, top + 94, 158, 32);
            inset(graphics, left + 182, top + 94, 160, 32);
            inset(graphics, left + 18, top + 129, 324, 33);
            int textX = left + 24;
            compact(graphics, data.masterName().getString(), textX, top + 44, MUTED, 312, uiX, uiY,
                    java.util.List.of(data.masterName(), data.quote()));
            compact(graphics, data.routeName().getString(), textX, top + 57, INK, 312, uiX, uiY,
                    java.util.List.of(data.routeName()));
            compact(graphics, data.journey().getString(), textX, top + 70, MUTED, 312, uiX, uiY,
                    java.util.List.of(data.journey()));
            int progress = Math.clamp(data.progressPercent(), 0, 100);
            graphics.fill(textX, top + 82, left + 336, top + 86, 0xFFB79A6A);
            graphics.fill(textX, top + 82, textX + 312 * progress / 100, top + 86, VillageUiTheme.TEAL);
            if (within(uiX, uiY, textX, top + 80, 312, 12)) {
                tooltip.set(java.util.List.of(
                        Component.translatable("screen.village-quest.caravan_master.journey_progress").append(": " + progress + "%"),
                        data.availability()));
            }

            String arrival = data.etaSeconds() < 0
                    ? Component.translatable("screen.village-quest.caravan_master.delayed").getString()
                    : String.format(java.util.Locale.ROOT, "%d:%02d", data.etaSeconds() / 60, data.etaSeconds() % 60);
            compact(graphics, labelled("arrival", arrival), textX, top + 100, INK, 146, uiX, uiY,
                    java.util.List.of(Component.literal(labelled("arrival", arrival)), data.availability()));
            compact(graphics, data.availability().getString(), textX, top + 114, MUTED, 146, uiX, uiY,
                    java.util.List.of(data.availability()));
            compact(graphics, labelled("status", data.roadStatus().getString()), left + 190, top + 100, INK, 146, uiX, uiY,
                    java.util.List.of(data.roadStatus(), Component.literal(labelled("quality", data.roadQuality() + " / 100"))));
            compact(graphics, labelled("quality", data.roadQuality() + " / 100"), left + 190, top + 114, MUTED, 146, uiX, uiY,
                    java.util.List.of(Component.literal(labelled("quality", data.roadQuality() + " / 100"))));

            compact(graphics, labelled("cargo", data.cargo().getString()), textX, top + 132, INK, 312, uiX, uiY,
                    java.util.List.of(data.cargo()));
            if (!data.incident().getString().isBlank()) {
                compact(graphics, labelled("incident", data.incident().getString()), textX, top + 142, 0xFF8B3934, 312, uiX, uiY,
                        java.util.List.of(data.incident()));
            }
            if (reputation != null) {
                Component standing = ReputationPanel.text("master_standing", ReputationPanel.rank(reputation.guildTrust()), reputation.guildTrust(), reputation.buyLimit());
                compact(graphics, standing.getString(), textX, top + 152, INK, 312, uiX, uiY,
                        reputation.reparation() == null ? java.util.List.of(standing) : java.util.List.of(standing, ReputationPanel.text("case_locations")));
            }

            if (data.canViewRoute()) {
                VillageUiTheme.drawButton(graphics, font, left + 102, top + 164, 152, 14,
                        Component.translatable("screen.village-quest.caravan_master.view_route").getString(),
                        true, within(uiX, uiY, left + 102, top + 164, 152, 14), false);
            }
            VillageUiTheme.drawButton(graphics, font, left + 278, top + 164, 58, 14,
                    Component.translatable("screen.village-quest.caravan_master.close").getString(),
                    true, within(uiX, uiY, left + 278, top + 164, 58, 14), false);
            super.render(graphics, uiX, uiY, delta);
        } finally {
            endResponsivePanel(graphics, scale);
        }
        tooltip.draw(graphics, font, mouseX, mouseY);
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        int x = responsiveMouseX(click.x(), WIDTH, HEIGHT), y = responsiveMouseY(click.y(), WIDTH, HEIGHT);
        if (data.canViewRoute() && within(x, y, left + 102, top + 164, 152, 14)) {
            if (!isPreview()) {
                UiTestTour.send(new VillageNetworkPayloads.CaravanMasterActionPayload(
                        data.entityId(), VillageNetworkPayloads.CaravanMasterActionPayload.VIEW_ROUTE));
            }
            return true;
        }
        if (within(x, y, left + 278, top + 164, 58, 14)) {
            onClose();
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private boolean isPreview() {
        return de.quest.network.ReputationPayloads.NONE.equals(data.entityId());
    }

    private static String labelled(String key, String value) {
        return Component.translatable("screen.village-quest.caravan_master." + key).getString() + ": " + value;
    }

    private void compact(GuiGraphics graphics, String value, int x, int y, int color, int width,
                         int mouseX, int mouseY, java.util.List<Component> tooltip) {
        float scale = 0.85f;
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(width / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible, x, y, color, scale);
        if (within(mouseX, mouseY, x, y - 1, width, 12)) {
            this.tooltip.set(tooltip);
        }
    }

    private void center(GuiGraphics graphics, String value, float center, float y,
                        int color, float scale, int maxWidth) {
        String visible = VillageUiTheme.ellipsize(font, value, Math.round(maxWidth / scale));
        VillageUiTheme.drawStringScaled(graphics, font, visible,
                center - font.width(visible) * scale / 2f, y, color, scale);
    }

    private static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x20FFFFFF);
        int edge = 0x609A6620;
        graphics.fill(x, y, x + width, y + 1, edge);
        graphics.fill(x, y + height - 1, x + width, y + height, edge);
        graphics.fill(x, y + 1, x + 1, y + height - 1, edge);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, edge);
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
