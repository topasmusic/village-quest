package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvents;

/** Modular Prosperity, commission, services, collection and statistics board. */
public final class ProsperityScreen extends ResponsiveScreen {
    private static final Identifier BOARD_TEXTURE = Identifier.of(
            VillageQuest.MOD_ID, "textures/gui/journal_board.png");
    private static final int WINDOW_WIDTH = 416;
    private static final int WINDOW_HEIGHT = 234;
    private static final int TAB_X = 22;
    private static final int TAB_Y = 30;
    private static final int TAB_WIDTH = 45;
    private static final int TAB_HEIGHT = 31;
    private static final int TAB_GAP = 2;
    private static final int LIST_X = 82;
    private static final int LIST_Y = 58;
    private static final int LIST_WIDTH = 142;
    private static final int LIST_HEIGHT = 132;
    private static final int ENTRY_HEIGHT = 30;
    private static final int DETAIL_X = 231;
    private static final int DETAIL_Y = 58;
    private static final int DETAIL_WIDTH = 154;
    private static final int DETAIL_HEIGHT = 132;
    private static final int ACTION_Y = 166;
    private static final int ACTION_HEIGHT = 18;
    private static final int FOOTER_Y = 203;
    private static final int ROUTE_SELECTOR_Y = FOOTER_Y - 4;
    private static final int HEADER_WALLET_RIGHT_INSET = 40;
    private static final int HEADER_WALLET_TOP = 10;
    private static final int TAB_ICON_SIZE = 21;
    private static final int ENTRY_ICON_SIZE = 19;
    private static final int ENTRY_ICON_AREA_X = 8;
    private static final int ENTRY_ICON_AREA_WIDTH = 28;
    private static final int DONE_WIDTH = 78;
    private static final int DONE_RIGHT_INSET = 24;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF8A7661;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFF9A6620;
    private static final int RED = 0xFF9B4337;
    private static final int GREEN = 0xFF47713F;
    private static final float TITLE_SCALE = 0.82f;
    private static final float BODY_SCALE = 0.70f;

    private Payloads.EconomyPayload data;
    private int sectionIndex;
    private int selectedEntryIndex;
    private int selectedRouteIndex;
    private int listScroll;
    /** Deferred until end of render — 1.21.1 drawTooltip is immediate (unlike 1.21.11+ z-order). */
    private Text hoveredTabTooltip;

    public ProsperityScreen(Payloads.EconomyPayload data) {
        super(Text.translatable("screen.village-quest.prosperity.title"));
        this.data = data;
    }

    public void updateData(Payloads.EconomyPayload data) {
        String sectionId = selectedSection() == null ? "" : selectedSection().sectionId();
        String entryId = selectedEntry() == null ? "" : selectedEntry().entryId();
        this.data = data;
        restoreSection(sectionId);
        restoreEntry(entryId);
        clampState();
    }

    @Override
    protected void init() {
        clampState();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        prepareBoardBackdrop(graphics, delta);
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float panelScale = beginResponsivePanel(graphics, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            this.hoveredTabTooltip = null;
            int left = (width - WINDOW_WIDTH) / 2;
            int top = (height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            graphics.drawTexture(BOARD_TEXTURE, left, top, 0, 0,
                    WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
            drawHeader(graphics, left, top);
            drawTabs(graphics, left, top, uiMouseX, uiMouseY);
            drawEntryList(graphics, left, top, uiMouseX, uiMouseY);
            drawDetail(graphics, left, top, uiMouseX, uiMouseY);
            drawFooter(graphics, left, top, uiMouseX, uiMouseY);
            super.render(graphics, uiMouseX, uiMouseY, delta);
            if (this.hoveredTabTooltip != null) {
                graphics.drawTooltip(textRenderer, this.hoveredTabTooltip, uiMouseX, uiMouseY);
            }
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    private void drawHeader(DrawContext graphics, int left, int top) {
        String titleText = title.getString();
        graphics.drawText(textRenderer, titleText, left + (WINDOW_WIDTH - textRenderer.getWidth(titleText)) / 2,
                top + 14, INK, false);
        Payloads.EconomySectionData section = selectedSection();
        if (section != null) {
            VillageUiTheme.drawStringScaled(graphics, textRenderer, section.label().getString(),
                    left + LIST_X, top + 43, GOLD, 0.80f);
        }
        VillageUiTheme.drawWalletStrip(graphics, textRenderer, left, top, WINDOW_WIDTH, data.balance(),
                HEADER_WALLET_RIGHT_INSET, HEADER_WALLET_TOP);
    }

    private void drawTabs(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        for (int i = 0; i < data.sections().size(); i++) {
            Payloads.EconomySectionData section = data.sections().get(i);
            int x = left + TAB_X;
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            boolean selected = i == sectionIndex;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            VillageUiTheme.drawTab(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, selected, hovered);
            VillageUiTheme.drawIcon(graphics, icon(section.iconName()),
                    x + (TAB_WIDTH - TAB_ICON_SIZE) / 2,
                    y + (TAB_HEIGHT - TAB_ICON_SIZE) / 2,
                    TAB_ICON_SIZE);
            if (hovered) {
                this.hoveredTabTooltip = section.label();
            }
        }
    }

    private void drawEntryList(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        Payloads.EconomySectionData section = selectedSection();
        if (section == null || section.entries().isEmpty()) return;
        int visible = Math.max(1, LIST_HEIGHT / ENTRY_HEIGHT);
        int first = Math.min(listScroll, Math.max(0, section.entries().size() - visible));
        int last = Math.min(section.entries().size(), first + visible);
        int viewportX = left + LIST_X;
        int viewportY = top + LIST_Y;
        graphics.enableScissor(viewportX, viewportY, viewportX + LIST_WIDTH, viewportY + LIST_HEIGHT);
        for (int i = first; i < last; i++) {
            Payloads.EconomyEntryData entry = section.entries().get(i);
            int y = viewportY + (i - first) * ENTRY_HEIGHT;
            boolean hovered = within(mouseX, mouseY, viewportX, y, LIST_WIDTH - 7, ENTRY_HEIGHT - 2);
            VillageUiTheme.drawCard(graphics, viewportX, y, LIST_WIDTH - 7, ENTRY_HEIGHT - 2,
                    hovered, i == selectedEntryIndex);
            if (entry.owned()) graphics.fill(viewportX + 5, y + 5, viewportX + 8, y + ENTRY_HEIGHT - 7, TEAL);
            int cardHeight = ENTRY_HEIGHT - 2;
            int iconX = viewportX + ENTRY_ICON_AREA_X
                    + (ENTRY_ICON_AREA_WIDTH - ENTRY_ICON_SIZE) / 2;
            int iconY = y + (cardHeight - ENTRY_ICON_SIZE) / 2;
            VillageUiTheme.drawIcon(graphics, icon(entry.iconName()), iconX, iconY, ENTRY_ICON_SIZE);
            VillageUiTheme.drawStringScaled(graphics, textRenderer,
                    compact(entry.title().getString(), 91, TITLE_SCALE),
                    viewportX + 38, y + 5, INK, TITLE_SCALE);
            VillageUiTheme.drawStringScaled(graphics, textRenderer,
                    compact(entry.subtitle().getString(), 91, BODY_SCALE),
                    viewportX + 38, y + 16, entry.actionEnabled() || entry.owned() ? TEAL : MUTED, BODY_SCALE);
        }
        graphics.disableScissor();
        VillageUiTheme.drawScrollBar(graphics, viewportX + LIST_WIDTH - 6, viewportY, LIST_HEIGHT,
                LIST_HEIGHT, section.entries().size() * ENTRY_HEIGHT,
                first * ENTRY_HEIGHT, Math.max(0, (section.entries().size() - visible) * ENTRY_HEIGHT));
    }

    private void drawDetail(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        Payloads.EconomyEntryData entry = selectedEntry();
        int x = left + DETAIL_X;
        int y = top + DETAIL_Y;
        if (entry == null) return;
        VillageUiTheme.drawCard(graphics, x, y, DETAIL_WIDTH, DETAIL_HEIGHT, false, true);
        graphics.drawTexture(icon(entry.iconName()),
                x + 9, y + 8, 0, 0, 28, 28, 32, 32);
        VillageUiTheme.drawStringScaled(graphics, textRenderer,
                compact(entry.title().getString(), DETAIL_WIDTH - 51, 0.88f),
                x + 43, y + 9, INK, 0.88f);
        List<String> subtitleLines = wrap(entry.subtitle().getString(), DETAIL_WIDTH - 51, 0.64f);
        int subtitleColor = entry.actionEnabled() || entry.owned() ? TEAL : MUTED;
        for (int i = 0; i < Math.min(2, subtitleLines.size()); i++) {
            VillageUiTheme.drawStringScaled(graphics, textRenderer, subtitleLines.get(i),
                    x + 43, y + 22 + i * 7, subtitleColor, 0.64f);
        }

        int descriptionTop = y + 42;
        boolean hasAction = !entry.actionLabel().getString().isBlank();
        boolean showInvestmentCost = selectedSection() != null
                && selectedSection().sectionId().equals("prosperity")
                && entry.price() > 0L
                && !entry.owned();
        int descriptionBottom = hasAction
                ? top + ACTION_Y - (showInvestmentCost ? 14 : 5)
                : y + DETAIL_HEIGHT - 7;
        boolean compactLedger = selectedSection() != null
                && selectedSection().sectionId().equals("statistics");
        float descriptionScale = compactLedger ? 0.62f : BODY_SCALE;
        int lineStep = compactLedger ? 7 : 8;
        int paragraphGap = compactLedger ? 0 : 2;
        graphics.enableScissor(x + 7, descriptionTop, x + DETAIL_WIDTH - 7, descriptionBottom);
        int lineY = descriptionTop;
        for (Text detail : entry.descriptionLines()) {
            for (String line : wrap(detail.getString(), DETAIL_WIDTH - 20, descriptionScale)) {
                VillageUiTheme.drawStringScaled(graphics, textRenderer, line, x + 10, lineY, BODY, descriptionScale);
                lineY += lineStep;
            }
            lineY += paragraphGap;
        }
        graphics.disableScissor();

        if (showInvestmentCost) {
            VillageUiTheme.drawStringScaled(
                    graphics,
                    textRenderer,
                    Text.translatable(
                            "screen.village-quest.prosperity.investment_cost",
                            CurrencyService.formatBalance(entry.price())
                    ).getString(),
                    x + 10,
                    top + ACTION_Y - 11,
                    GOLD,
                    0.66f
            );
        }

        if (hasAction) {
            int buttonX = x + 10;
            int buttonY = top + ACTION_Y;
            boolean hovered = within(mouseX, mouseY, buttonX, buttonY, DETAIL_WIDTH - 20, ACTION_HEIGHT);
            VillageUiTheme.drawButton(graphics, textRenderer, buttonX, buttonY, DETAIL_WIDTH - 20, ACTION_HEIGHT,
                    entry.actionLabel().getString(), entry.actionEnabled(), hovered && entry.actionEnabled(), false);
        }
    }

    private void drawFooter(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        boolean routeSelector = needsRouteSelector() && !data.routeNames().isEmpty();
        if (routeSelector) {
            int previousX = left + 82;
            int nextX = left + 206;
            int selectorY = top + ROUTE_SELECTOR_Y;
            VillageUiTheme.drawButton(graphics, textRenderer, previousX, selectorY, 18, 18, "<", true,
                    within(mouseX, mouseY, previousX, selectorY, 18, 18), false);
            String route = data.routeNames().get(selectedRouteIndex).getString();
            VillageUiTheme.drawStringScaled(graphics, textRenderer, compact(route, 101, 0.76f),
                    previousX + 23, selectorY + 4, INK, 0.76f);
            VillageUiTheme.drawButton(graphics, textRenderer, nextX, selectorY, 18, 18, ">", true,
                    within(mouseX, mouseY, nextX, selectorY, 18, 18), false);
        }
        int doneX = left + WINDOW_WIDTH - DONE_RIGHT_INSET - DONE_WIDTH;
        boolean hovered = within(mouseX, mouseY, doneX, top + FOOTER_Y, DONE_WIDTH, 18);
        VillageUiTheme.drawButton(graphics, textRenderer, doneX, top + FOOTER_Y, DONE_WIDTH, 18,
                Text.translatable("screen.village-quest.journal.done").getString(), true, hovered, false);
    }

    @Override
    public boolean mouseClicked(double mouseXd, double mouseYd, int button) {
        if (button != 0) return super.mouseClicked(mouseXd, mouseYd, button);
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(mouseXd, WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(mouseYd, WINDOW_WIDTH, WINDOW_HEIGHT);
        for (int i = 0; i < data.sections().size(); i++) {
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            if (within(mouseX, mouseY, left + TAB_X, y, TAB_WIDTH, TAB_HEIGHT)) {
                sectionIndex = i;
                selectedEntryIndex = 0;
                listScroll = 0;
                playClick();
                return true;
            }
        }
        Payloads.EconomySectionData section = selectedSection();
        if (section != null) {
            int visible = Math.max(1, LIST_HEIGHT / ENTRY_HEIGHT);
            int first = Math.min(listScroll, Math.max(0, section.entries().size() - visible));
            int last = Math.min(section.entries().size(), first + visible);
            for (int i = first; i < last; i++) {
                int y = top + LIST_Y + (i - first) * ENTRY_HEIGHT;
                if (within(mouseX, mouseY, left + LIST_X, y, LIST_WIDTH - 7, ENTRY_HEIGHT - 2)) {
                    selectedEntryIndex = i;
                    playClick();
                    return true;
                }
            }
        }
        if (needsRouteSelector() && !data.routeNames().isEmpty()) {
            if (within(mouseX, mouseY, left + 82, top + ROUTE_SELECTOR_Y, 18, 18)) {
                selectedRouteIndex = Math.floorMod(selectedRouteIndex - 1, data.routeNames().size());
                playClick();
                return true;
            }
            if (within(mouseX, mouseY, left + 206, top + ROUTE_SELECTOR_Y, 18, 18)) {
                selectedRouteIndex = (selectedRouteIndex + 1) % data.routeNames().size();
                playClick();
                return true;
            }
        }
        Payloads.EconomyEntryData entry = selectedEntry();
        if (entry != null && entry.actionEnabled()
                && within(mouseX, mouseY, left + DETAIL_X + 10, top + ACTION_Y, DETAIL_WIDTH - 20, ACTION_HEIGHT)) {
            String action = entry.entryId();
            if ((action.startsWith("service:") || action.startsWith("collection:")) && !data.routeNames().isEmpty()) {
                action += ":" + selectedRouteIndex;
            }
            ClientPlayNetworking.send(new Payloads.EconomyActionPayload(action));
            playClick();
            return true;
        }
        int doneX = left + WINDOW_WIDTH - DONE_RIGHT_INSET - DONE_WIDTH;
        if (within(mouseX, mouseY, doneX, top + FOOTER_Y, DONE_WIDTH, 18)) {
            returnToJournal();
            return true;
        }
        return super.mouseClicked(mouseXd, mouseYd, button);
    }

    private void returnToJournal() {
        if (client == null || client.player == null || client.player.networkHandler == null) {
            close();
            return;
        }
        client.player.networkHandler.sendChatCommand("vq journal open");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        Payloads.EconomySectionData section = selectedSection();
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if (section != null && within(uiMouseX, uiMouseY,
                left + LIST_X, top + LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            int visible = Math.max(1, LIST_HEIGHT / ENTRY_HEIGHT);
            listScroll -= (int) Math.signum(verticalAmount);
            listScroll = Math.max(0, Math.min(listScroll, Math.max(0, section.entries().size() - visible)));
            if (selectedEntryIndex < listScroll) selectedEntryIndex = listScroll;
            if (selectedEntryIndex >= listScroll + visible) selectedEntryIndex = listScroll + visible - 1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private Payloads.EconomySectionData selectedSection() {
        if (data == null || data.sections().isEmpty()) return null;
        sectionIndex = Math.max(0, Math.min(sectionIndex, data.sections().size() - 1));
        return data.sections().get(sectionIndex);
    }

    private Payloads.EconomyEntryData selectedEntry() {
        Payloads.EconomySectionData section = selectedSection();
        if (section == null || section.entries().isEmpty()) return null;
        selectedEntryIndex = Math.max(0, Math.min(selectedEntryIndex, section.entries().size() - 1));
        return section.entries().get(selectedEntryIndex);
    }

    private boolean needsRouteSelector() {
        Payloads.EconomySectionData section = selectedSection();
        if (section == null) return false;
        if (section.sectionId().equals("services")) {
            Payloads.EconomyEntryData entry = selectedEntry();
            return entry != null && (entry.entryId().startsWith("service:road_patrol")
                    || entry.entryId().startsWith("service:survey_report")
                    || entry.entryId().startsWith("service:emergency_recall"));
        }
        return section.sectionId().equals("collection")
                && selectedEntry() != null && selectedEntry().entryId().startsWith("collection:livery_");
    }

    private void clampState() {
        selectedSection();
        selectedEntry();
        if (data.routeNames().isEmpty()) selectedRouteIndex = 0;
        else selectedRouteIndex = Math.max(0, Math.min(selectedRouteIndex, data.routeNames().size() - 1));
        Payloads.EconomySectionData section = selectedSection();
        int visible = Math.max(1, LIST_HEIGHT / ENTRY_HEIGHT);
        listScroll = section == null ? 0 : Math.max(0,
                Math.min(listScroll, Math.max(0, section.entries().size() - visible)));
    }

    private void restoreSection(String id) {
        for (int i = 0; i < data.sections().size(); i++) {
            if (data.sections().get(i).sectionId().equals(id)) {
                sectionIndex = i;
                return;
            }
        }
        sectionIndex = 0;
    }

    private void restoreEntry(String id) {
        Payloads.EconomySectionData section = selectedSection();
        if (section == null) return;
        for (int i = 0; i < section.entries().size(); i++) {
            if (section.entries().get(i).entryId().equals(id)) {
                selectedEntryIndex = i;
                return;
            }
        }
        selectedEntryIndex = 0;
    }

    private Identifier icon(String name) {
        return Identifier.of(VillageQuest.MOD_ID,
                "textures/gui/prosperity/" + name + ".png");
    }

    private String compact(String text, int maxWidth, float scale) {
        if (text == null) return "";
        if (textRenderer.getWidth(text) * scale <= maxWidth) return text;
        String suffix = "...";
        String value = text;
        while (!value.isEmpty() && textRenderer.getWidth(value + suffix) * scale > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private List<String> wrap(String text, int maxWidth, float scale) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textRenderer.getWidth(candidate) * scale > maxWidth) {
                result.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int areaWidth, int areaHeight) {
        return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
