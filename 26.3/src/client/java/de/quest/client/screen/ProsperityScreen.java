package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.QuestArtwork;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Modular Prosperity, commission, services, collection and statistics board. */
public final class ProsperityScreen extends CompatScreen {
    private static final int WINDOW_WIDTH = 560;
    private static final int WINDOW_HEIGHT = 315;
    private static final int TAB_X = 22;
    private static final int TAB_Y = 59;
    private static final int TAB_WIDTH = 92;
    private static final int TAB_HEIGHT = 34;
    private static final int TAB_GAP = 2;
    private static final int LIST_X = 124;
    private static final int LIST_Y = 77;
    private static final int LIST_WIDTH = 180;
    private static final int LIST_HEIGHT = 174;
    private static final int ENTRY_HEIGHT = 42;
    private static final int DETAIL_X = 314;
    private static final int DETAIL_Y = 59;
    private static final int DETAIL_WIDTH = 214;
    private static final int DETAIL_HEIGHT = 192;
    private static final int ACTION_Y = 225;
    private static final int ACTION_HEIGHT = 18;
    private static final int FOOTER_Y = 257;
    private static final int ROUTE_SELECTOR_Y = FOOTER_Y;
    private static final int HEADER_WALLET_RIGHT_INSET = 40;
    private static final int HEADER_WALLET_TOP = 10;
    private static final int TAB_ICON_SIZE = 21;
    private static final int ENTRY_ICON_SIZE = 26;
    private static final int ENTRY_ICON_AREA_X = 8;
    private static final int ENTRY_ICON_AREA_WIDTH = 28;
    private static final int DONE_WIDTH = 78;
    private static final int DONE_RIGHT_INSET = 32;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF8A7661;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFF9A6620;
    private static final int RED = 0xFF9B4337;
    private static final int GREEN = 0xFF47713F;
    private static final float TITLE_SCALE = 0.82f;
    private static final float BODY_SCALE = 0.82f;

    private Payloads.EconomyPayload data;
    private int sectionIndex;
    private int selectedEntryIndex;
    private int selectedRouteIndex;
    private int listScroll;
    private int detailScroll;

    public ProsperityScreen(Payloads.EconomyPayload data) {
        super(Component.translatable("screen.village-quest.prosperity.title"));
        this.data = data;
    }

    void selectTourSection(String sectionId) {
        restoreSection(sectionId);
        selectedEntryIndex = listScroll = detailScroll = 0;
        clampState();
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key)) {
            onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(graphics, width, height);
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        float panelScale = beginResponsivePanel(graphics, WINDOW_WIDTH, WINDOW_HEIGHT);
        try {
            int left = (width - WINDOW_WIDTH) / 2;
            int top = (height - WINDOW_HEIGHT) / 2;
            VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            de.quest.client.ui.JournalV3Visuals.insetBoard(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
            graphics.fill(left+20,top + 39,left+116,top+WINDOW_HEIGHT-39,VillageUiTheme.WOOD);
            drawHeader(graphics, left, top);
            drawTabs(graphics, left, top, uiMouseX, uiMouseY);
            drawEntryList(graphics, left, top, uiMouseX, uiMouseY);
            drawDetail(graphics, left, top, uiMouseX, uiMouseY);
            if(needsRouteSelector())VillageUiTheme.drawPaper(graphics,left+124,top + 253,180,22);
            drawFooter(graphics, left, top, uiMouseX, uiMouseY);
            KingdomUiAssets.closeButton(graphics,left,top,WINDOW_WIDTH,WINDOW_HEIGHT,KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(uiMouseX,uiMouseY));
            super.render(graphics, uiMouseX, uiMouseY, delta);
        } finally {
            endResponsivePanel(graphics, panelScale);
        }
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        String titleText = title.getString();
        VillageUiTheme.drawTitle(graphics, font, titleText, "resettlement", left + 158, top + 6, 244);
        Payloads.EconomySectionData section = selectedSection();
        if (section != null) {
            VillageUiTheme.drawStringScaled(graphics, font, section.label().getString(),
                    left + LIST_X, top + 61, INK, 1f);
        }
        VillageUiTheme.drawWalletStrip(graphics, font, left, top, WINDOW_WIDTH, data.balance(),
                HEADER_WALLET_RIGHT_INSET, HEADER_WALLET_TOP);
    }

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        for (int i = 0; i < data.sections().size(); i++) {
            Payloads.EconomySectionData section = data.sections().get(i);
            int x = left + TAB_X;
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            boolean selected = i == sectionIndex;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            VillageUiTheme.drawTab(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, selected, hovered);
            VillageUiTheme.drawIcon(graphics, icon(section.iconName()),
                    x + 5,
                    y + 7,
                    20);
            String label=VillageUiTheme.ellipsize(font,section.label().getString(),(int)((TAB_WIDTH-32)/.65f));
            VillageUiTheme.drawStringScaled(graphics,font,label,x+29,y+13,VillageUiTheme.LIGHT_TEXT,.65f);
            if (hovered) graphics.setTooltipForNextFrame(font, section.label(), mouseX, mouseY);
        }
    }

    private void drawEntryList(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
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
            boolean hovered = within(mouseX, mouseY, viewportX, y, LIST_WIDTH - 12, ENTRY_HEIGHT - 2);
            VillageUiTheme.drawCard(graphics, viewportX, y, LIST_WIDTH - 12, ENTRY_HEIGHT - 2,
                    hovered, i == selectedEntryIndex);
            if (entry.owned()) graphics.fill(viewportX + 5, y + 5, viewportX + 8, y + ENTRY_HEIGHT - 7, TEAL);
            int cardHeight = ENTRY_HEIGHT - 2;
            int iconX = viewportX + ENTRY_ICON_AREA_X
                    + (ENTRY_ICON_AREA_WIDTH - ENTRY_ICON_SIZE) / 2;
            int iconY = y + (cardHeight - ENTRY_ICON_SIZE) / 2;
            VillageUiTheme.drawIcon(graphics, icon(entry.iconName()), iconX, iconY, ENTRY_ICON_SIZE);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(entry.title().getString(), LIST_WIDTH-53, TITLE_SCALE),
                    viewportX + 38, y + 5, INK, TITLE_SCALE);
            VillageUiTheme.drawStringScaled(graphics, font,
                    compact(entry.subtitle().getString(), LIST_WIDTH-53, BODY_SCALE),
                    viewportX + 38, y + 24, entry.actionEnabled() || entry.owned() ? TEAL : MUTED, BODY_SCALE);
        }
        graphics.disableScissor();
        VillageUiTheme.drawScrollBar(graphics, viewportX + LIST_WIDTH - 6, viewportY, LIST_HEIGHT,
                LIST_HEIGHT, section.entries().size() * ENTRY_HEIGHT,
                first * ENTRY_HEIGHT, Math.max(0, (section.entries().size() - visible) * ENTRY_HEIGHT));
    }

    private void drawDetail(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        Payloads.EconomyEntryData entry = selectedEntry();
        int x = left + DETAIL_X;
        int y = top + DETAIL_Y;
        if (entry == null) return;
        VillageUiTheme.drawCard(graphics, x, y, DETAIL_WIDTH, detailHeight(), false, true);
        VillageUiTheme.drawIcon(graphics,icon(entry.iconName()),x+9,y+8,28);
        JournalV3Visuals.heading(graphics,font,entry.title().getString(),x+43,y+9,DETAIL_WIDTH-51,1f,INK);
        List<String> subtitleLines = entry.subtitle().equals(CurrencyService.formatBalance(entry.price())) ? List.of() : wrap(entry.subtitle().getString(), DETAIL_WIDTH - 51, 0.8f);
        int subtitleColor = entry.actionEnabled() || entry.owned() ? TEAL : MUTED;
        for (int i = 0; i < Math.min(2, subtitleLines.size()); i++) {
            VillageUiTheme.drawStringScaled(graphics, font, subtitleLines.get(i),
                    x + 43, y + 22 + i * 7, subtitleColor, 0.8f);
        }
        if (within(mouseX, mouseY, x + 7, y + 5, DETAIL_WIDTH - 14, 34))
            graphics.setTooltipForNextFrame(font, List.of(entry.title(), entry.subtitle()), mouseX, mouseY);

        var motif=QuestArtwork.forComponent(entry.title());
        if(motif.isEmpty() && selectedSection()!=null) motif=java.util.Optional.of(switch(selectedSection().sectionId()) {
            case "prosperity" -> entry.iconName().contains("market")?QuestArtwork.Motif.MARKET:QuestArtwork.Motif.PROJECTS;
            case "services" -> QuestArtwork.Motif.VILLAGE_HOME;
            case "collection" -> entry.iconName().contains("livery")?QuestArtwork.Motif.CARAVAN:QuestArtwork.Motif.ARCHIVE;
            case "statistics" -> QuestArtwork.Motif.ARCHIVE;
            default -> QuestArtwork.Motif.COUNCIL;
        });
        if(motif.isPresent()) JournalV3Visuals.illustration(graphics,motif.get().largeTexture(),x+10,y+43,DETAIL_WIDTH-20,48);
        int descriptionTop = y + 99;
        boolean hasAction = !entry.actionLabel().getString().isBlank();
        boolean showInvestmentCost = selectedSection() != null
                && !selectedSection().sectionId().equals("statistics")
                && entry.price() > 0L
                && !entry.owned();
        int descriptionBottom = hasAction
                ? top + actionY() - (showInvestmentCost ? 14 : 5)
                : y + detailHeight() - 7;
        boolean compactLedger = selectedSection() != null
                && selectedSection().sectionId().equals("statistics");
        float descriptionScale = compactLedger ? 0.62f : BODY_SCALE;
        int lineStep = compactLedger ? 7 : 10;
        int paragraphGap = compactLedger ? 0 : 2;
        int contentHeight = 0;
        var paragraphs=detailParagraphs(entry);
        for (DetailParagraph detail : paragraphs)
            contentHeight += wrap(detail.text().getString(), DETAIL_WIDTH - 24, descriptionScale).size() * lineStep + paragraphGap;
        int maxScroll = Math.max(0, contentHeight - (descriptionBottom - descriptionTop));
        detailScroll = Math.clamp(detailScroll, 0, maxScroll);
        graphics.enableScissor(x + 7, descriptionTop, x + DETAIL_WIDTH - 7, descriptionBottom);
        int lineY = descriptionTop - detailScroll;
        for (DetailParagraph detail : paragraphs) {
            for (String line : wrap(detail.text().getString(), DETAIL_WIDTH - 24, descriptionScale)) {
                if(detail.heading()) JournalV3Visuals.heading(graphics,font,line,x+10,lineY,DETAIL_WIDTH-24,descriptionScale,INK);
                else VillageUiTheme.drawStringScaled(graphics, font, line, x + 12, lineY, BODY, descriptionScale);
                lineY += lineStep;
            }
            lineY += paragraphGap;
        }
        graphics.disableScissor();
        VillageUiTheme.drawScrollBar(graphics, x + DETAIL_WIDTH - 7, descriptionTop,
                descriptionBottom - descriptionTop, descriptionBottom - descriptionTop, contentHeight, detailScroll, maxScroll);

        if (showInvestmentCost) {
            VillageUiTheme.drawStringScaled(
                    graphics,
                    font,
                    Component.translatable(
                            "screen.village-quest.ui_review.cost",
                            CurrencyService.formatBalance(entry.price())
                    ).getString(),
                    x + 10,
                    top + actionY() - 13,
                    INK,
                    0.8f
            );
        }

        if (hasAction) {
            int buttonX = x + 10;
            int buttonY = top + actionY();
            boolean hovered = within(mouseX, mouseY, buttonX, buttonY, DETAIL_WIDTH - 20, ACTION_HEIGHT);
            VillageUiTheme.drawButton(graphics, font, buttonX, buttonY, DETAIL_WIDTH - 20, ACTION_HEIGHT,
                    entry.actionLabel().getString(), entry.actionEnabled(), hovered && entry.actionEnabled(), true);
        }
    }

    private int actionY() {
        return ACTION_Y-(selectedSection()!=null && selectedSection().sectionId().equals("collection")?22:0);
    }
    private int detailHeight() {
        return DETAIL_HEIGHT-(selectedSection()!=null && selectedSection().sectionId().equals("collection")?22:0);
    }

    private record DetailParagraph(Component text, boolean heading) {}
    private List<DetailParagraph> detailParagraphs(Payloads.EconomyEntryData entry) {
        var result=new java.util.ArrayList<DetailParagraph>();
        boolean prosperity=selectedSection()!=null && selectedSection().sectionId().equals("prosperity");
        for(int i=0;i<entry.descriptionLines().size();i++) {
            Component text=entry.descriptionLines().get(i);
            if(prosperity && i==0) result.add(new DetailParagraph(Component.translatable("screen.village-quest.ui_review.current_benefit"),true));
            if(prosperity && text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("screen.village-quest.prosperity.next_benefit") && t.getArgs().length==1
                    && text.getSiblings().isEmpty() && t.getArgs()[0] instanceof Component benefit) {
                result.add(new DetailParagraph(Component.translatable("screen.village-quest.ui_review.next_benefit"),true));
                text=benefit;
            }
            result.add(new DetailParagraph(text,false));
        }
        return result;
    }

    private void drawFooter(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        boolean routeSelector = needsRouteSelector() && !data.routeNames().isEmpty();
        if (routeSelector) {
            int previousX = left + 124;
            int nextX = left + 282;
            int selectorY = top + ROUTE_SELECTOR_Y;
            VillageUiTheme.drawButton(graphics, font, previousX, selectorY, 18, 18, "<", true,
                    within(mouseX, mouseY, previousX, selectorY, 18, 18), false);
            String route = data.routeNames().get(selectedRouteIndex).getString();
            VillageUiTheme.drawStringScaled(graphics, font, compact(route, 131, 0.76f),
                    previousX + 23, selectorY + 4, INK, 0.76f);
            VillageUiTheme.drawButton(graphics, font, nextX, selectorY, 18, 18, ">", true,
                    within(mouseX, mouseY, nextX, selectorY, 18, 18), false);
        }
        int doneX = left + WINDOW_WIDTH - DONE_RIGHT_INSET - DONE_WIDTH;
        boolean hovered = within(mouseX, mouseY, doneX, top + FOOTER_Y, DONE_WIDTH, 18);
        JournalV3Visuals.secondary(graphics,font,Component.translatable("screen.village-quest.journal.done").getString(),doneX,top+FOOTER_Y,DONE_WIDTH,18,hovered);
    }

    @Override
    protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click, doubled);
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = responsiveMouseX(click.x(), WINDOW_WIDTH, WINDOW_HEIGHT);
        int mouseY = responsiveMouseY(click.y(), WINDOW_WIDTH, WINDOW_HEIGHT);
        if(KingdomUiAssets.closeBounds(left,top,WINDOW_WIDTH,WINDOW_HEIGHT).contains(mouseX,mouseY)) {onClose();return true;}
        for (int i = 0; i < data.sections().size(); i++) {
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            if (within(mouseX, mouseY, left + TAB_X, y, TAB_WIDTH, TAB_HEIGHT)) {
                sectionIndex = i;
                selectedEntryIndex = 0;
                listScroll = 0;
                detailScroll = 0;
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
                if (within(mouseX, mouseY, left + LIST_X, y, LIST_WIDTH - 12, ENTRY_HEIGHT - 2)) {
                    selectedEntryIndex = i;
                    detailScroll = 0;
                    playClick();
                    return true;
                }
            }
        }
        if (needsRouteSelector() && !data.routeNames().isEmpty()) {
            if (within(mouseX, mouseY, left + 124, top + ROUTE_SELECTOR_Y, 18, 18)) {
                selectedRouteIndex = Math.floorMod(selectedRouteIndex - 1, data.routeNames().size());
                playClick();
                return true;
            }
            if (within(mouseX, mouseY, left + 282, top + ROUTE_SELECTOR_Y, 18, 18)) {
                selectedRouteIndex = (selectedRouteIndex + 1) % data.routeNames().size();
                playClick();
                return true;
            }
        }
        Payloads.EconomyEntryData entry = selectedEntry();
        if (entry != null && entry.actionEnabled()
                && within(mouseX, mouseY, left + DETAIL_X + 10, top + actionY(), DETAIL_WIDTH - 20, ACTION_HEIGHT)) {
            String action = entry.entryId();
            if ((action.startsWith("service:") || action.startsWith("collection:")) && !data.routeNames().isEmpty()) {
                action += ":" + selectedRouteIndex;
            }
            UiTestTour.send(new Payloads.EconomyActionPayload(action));
            playClick();
            return true;
        }
        int doneX = left + WINDOW_WIDTH - DONE_RIGHT_INSET - DONE_WIDTH;
        if (within(mouseX, mouseY, doneX, top + FOOTER_Y, DONE_WIDTH, 18)) {
            returnToJournal();
            return true;
        }
        return super.handleMouseClicked(click, doubled);
    }

    private void returnToJournal() {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            onClose();
            return;
        }
        UiTestTour.command("vq journal open");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        Payloads.EconomySectionData section = selectedSection();
        int uiMouseX = responsiveMouseX(mouseX, WINDOW_WIDTH, WINDOW_HEIGHT);
        int uiMouseY = responsiveMouseY(mouseY, WINDOW_WIDTH, WINDOW_HEIGHT);
        var entry=selectedEntry();
        boolean hasAction=entry!=null && !entry.actionLabel().getString().isBlank();
        boolean priced=section!=null && section.sectionId().equals("prosperity") && entry!=null && entry.price()>0 && !entry.owned();
        int bodyBottom=hasAction?actionY()-(priced?14:5):DETAIL_Y+detailHeight()-7;
        if (within(uiMouseX, uiMouseY, left + DETAIL_X, top + DETAIL_Y + 99,
                DETAIL_WIDTH, bodyBottom-DETAIL_Y-99)) {
            detailScroll = Math.max(0, detailScroll - (int)Math.signum(verticalAmount) * 16);
            return true;
        }
        if (section != null && within(uiMouseX, uiMouseY,
                left + LIST_X, top + LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            int visible = Math.max(1, LIST_HEIGHT / ENTRY_HEIGHT);
            listScroll -= (int) Math.signum(verticalAmount);
            listScroll = Math.max(0, Math.min(listScroll, Math.max(0, section.entries().size() - visible)));
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
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,
                "textures/gui/prosperity/" + name + ".png");
    }

    private String compact(String text, int maxWidth, float scale) {
        if (text == null) return "";
        if (font.width(text) * scale <= maxWidth) return text;
        String suffix = "...";
        String value = text;
        while (!value.isEmpty() && font.width(value + suffix) * scale > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private List<String> wrap(String text, int maxWidth, float scale) {
        return VillageUiTheme.wrapScaledLines(font, text, maxWidth, scale);
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int areaWidth, int areaHeight) {
        return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
