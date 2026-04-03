package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.network.Payloads;
import de.quest.util.TimeUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class QuestMasterScreen extends CompatScreen {
    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "textures/gui/questmaster_board.png");
    private static final int BOARD_TEXTURE_WIDTH = 350;
    private static final int BOARD_TEXTURE_HEIGHT = 232;

    public record CategoryView(String categoryId, Component label, int entryCount) {}

    public record EntryView(
            String entryId,
            String categoryId,
            Component title,
            Component subtitle,
            Component status,
            List<Component> descriptionLines,
            List<Component> objectiveLines,
            List<Component> rewardLines,
            int primaryAction,
            Component primaryLabel,
            boolean primaryEnabled,
            int secondaryAction,
            Component secondaryLabel,
            boolean secondaryEnabled,
            boolean locked
    ) {}

    public record QuestMasterData(
            int entityId,
            Component questMasterName,
            List<CategoryView> categories,
            List<EntryView> entries
    ) {}

    private record DetailLine(String text, int color, int indent, boolean spacer) {}

    private record ButtonAction(int action, Component label, boolean enabled) {}

    private static final int WINDOW_WIDTH = 350;
    private static final int WINDOW_HEIGHT = 232;

    private static final int TITLE_Y = 10;

    private static final int CATEGORY_SLOT_X = 18;
    private static final int CATEGORY_SLOT_Y = 48;
    private static final int CATEGORY_SLOT_WIDTH = 76;
    private static final int CATEGORY_SLOT_HEIGHT = 24;
    private static final int CATEGORY_SLOT_GAP = 6;

    private static final int LIST_PANEL_X = 110;
    private static final int LIST_PANEL_Y = 45;
    private static final int LIST_PANEL_WIDTH = 95;
    private static final int LIST_PANEL_HEIGHT = 160;
    private static final int ENTRY_X = 118;
    private static final int ENTRY_Y = 54;
    private static final int ENTRY_WIDTH = 76;
    private static final int ENTRY_HEIGHT = 29;
    private static final int ENTRY_GAP = 6;

    private static final int DETAIL_HEADER_X = 215;
    private static final int DETAIL_HEADER_Y = 45;
    private static final int DETAIL_HEADER_WIDTH = 111;
    private static final int DETAIL_HEADER_HEIGHT = 41;

    private static final int DETAIL_BODY_X = 215;
    private static final int DETAIL_BODY_Y = 92;
    private static final int DETAIL_BODY_WIDTH = 111;
    private static final int DETAIL_BODY_HEIGHT = 62;

    private static final int BUTTON_X = 223;
    private static final int BUTTON_Y = 173;
    private static final int BUTTON_WIDTH = 89;
    private static final int BUTTON_HEIGHT = 18;

    private static final int SCREEN_SHADE = 0xB018120D;
    private static final int SHADOW = 0x71000000;
    private static final int TITLE = 0xFF2B170E;
    private static final int BODY = 0xFF5C4030;
    private static final int MUTED = 0xFF8A715E;
    private static final int SECTION_HEADER = 0xFF8F5A2F;
    private static final int SLOT_FILL = 0xFFE6C98F;
    private static final int SLOT_HOVER = 0xFFF1DEC0;
    private static final int SLOT_LOCKED = 0xFFE5D7BF;
    private static final int ENTRY_BG = 0xFFF2DEB6;
    private static final int ENTRY_HOVER = 0xFFF8EAC8;
    private static final int ENTRY_SELECTED = 0xFFE6C98F;
    private static final int ENTRY_SELECTED_HOVER = 0xFFEEDAAE;
    private static final int ENTRY_LOCKED = 0xFFE5D7BF;
    private static final int FRAME_DARK = 0xFF5A321E;
    private static final int FRAME_LIGHT = 0xFFC08A4B;
    private static final int STATUS_TEXT = 0xFFF8EFD8;
    private static final int STATUS_AVAILABLE = 0xFF8D5A1C;
    private static final int STATUS_ACTIVE = 0xFF285C36;
    private static final int STATUS_READY = 0xFF936617;
    private static final int STATUS_DONE = 0xFF3E5C67;
    private static final int STATUS_LOCKED = 0xFF6D6256;
    private static final int BUTTON_ENABLED_OVERLAY = 0x22000000;
    private static final int BUTTON_HOVER_OVERLAY = 0x33000000;
    private static final int BUTTON_DISABLED_OVERLAY = 0x55000000;
    private static final int BUTTON_TEXT = 0xFFF9ECD4;
    private static final int BUTTON_DISABLED_TEXT = 0xFFD2BEA4;
    private static final int SCROLL_TRACK = 0x33A77A42;
    private static final int SCROLL_THUMB = 0xAA7C4A27;
    private static final String ENTRY_DAILY_MAIN = "daily_main";
    private static final String ENTRY_WEEKLY = "weekly_main";

    private QuestMasterData data;
    private String selectedCategoryId = "";
    private String selectedEntryId = "";
    private boolean closeNotified = false;
    private int entryListScrollOffset = 0;
    private int entryListScrollMax = 0;
    private int detailScrollOffset = 0;
    private int detailScrollMax = 0;
    private Component hoveredEntryTooltip;

    public QuestMasterScreen(QuestMasterData data) {
        super(Component.translatable("screen.village-quest.questmaster.title"));
        this.data = data;
    }

    public void updateData(QuestMasterData data) {
        String previousCategory = this.selectedCategoryId;
        String previousEntry = this.selectedEntryId;
        this.data = data;
        this.selectedCategoryId = previousCategory;
        this.selectedEntryId = previousEntry;
        ensureSelection();
        clampEntryListScroll();
        ensureSelectedEntryVisible();
        clampDetailScroll();
    }

    @Override
    protected void init() {
        ensureSelection();
        clampEntryListScroll();
        ensureSelectedEntryVisible();
        this.closeNotified = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        notifyClosed();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        this.hoveredEntryTooltip = null;

        context.fill(0, 0, this.width, this.height, SCREEN_SHADE);
        drawBoard(context, left, top);
        drawHeader(context, left, top);
        drawSidebar(context, left, top, mouseX, mouseY);
        drawEntryList(context, left, top, mouseX, mouseY);
        drawDetailPanel(context, left, top, mouseX, mouseY);
        drawFooter(context, left, top);

        super.render(context, mouseX, mouseY, delta);
        if (this.hoveredEntryTooltip != null) {
            context.setTooltipForNextFrame(this.font, this.hoveredEntryTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        List<CategoryView> categories = data.categories();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).entryCount() <= 0) {
                continue;
            }
            int x = left + CATEGORY_SLOT_X;
            int y = top + CATEGORY_SLOT_Y + (i * (CATEGORY_SLOT_HEIGHT + CATEGORY_SLOT_GAP));
            if (isWithin(mouseX, mouseY, x, y, CATEGORY_SLOT_WIDTH, CATEGORY_SLOT_HEIGHT)) {
                this.selectedCategoryId = categories.get(i).categoryId();
                this.selectedEntryId = "";
                ensureSelection();
                this.entryListScrollOffset = 0;
                this.detailScrollOffset = 0;
                playClick();
                return true;
            }
        }

        List<EntryView> entries = getVisibleEntries();
        int entryViewportY = top + ENTRY_Y;
        int entryViewportHeight = entryViewportHeight();
        for (int i = 0; i < entries.size(); i++) {
            int x = left + ENTRY_X;
            int y = entryViewportY + (i * (ENTRY_HEIGHT + ENTRY_GAP)) - this.entryListScrollOffset;
            if ((y + ENTRY_HEIGHT) < entryViewportY || y > entryViewportY + entryViewportHeight) {
                continue;
            }
            if (isWithin(mouseX, mouseY, x, y, ENTRY_WIDTH, ENTRY_HEIGHT)) {
                this.selectedEntryId = entries.get(i).entryId();
                this.detailScrollOffset = 0;
                ensureSelectedEntryVisible();
                playClick();
                return true;
            }
        }

        EntryView selected = getSelectedEntry();
        if (selected != null) {
            ButtonAction buttonAction = visibleButtonAction(selected);
            if (buttonAction != null && hasVisibleLabel(buttonAction.label()) && isWithin(mouseX, mouseY, left + BUTTON_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                ClientPlayNetworking.send(new Payloads.QuestMasterActionPayload(
                        data.entityId(),
                        buttonAction.action(),
                        selected.entryId()
                ));
                playClick();
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        if (this.entryListScrollMax > 0 && isWithin((int) mouseX, (int) mouseY, left + ENTRY_X, top + ENTRY_Y, ENTRY_WIDTH, entryViewportHeight())) {
            int step = this.font.lineHeight * 2;
            this.entryListScrollOffset -= (int) Math.signum(verticalAmount) * step;
            clampEntryListScroll();
            return true;
        }
        if (this.detailScrollMax > 0 && isWithin((int) mouseX, (int) mouseY, left + DETAIL_BODY_X, top + DETAIL_BODY_Y, DETAIL_BODY_WIDTH, DETAIL_BODY_HEIGHT)) {
            int step = this.font.lineHeight * 2;
            this.detailScrollOffset -= (int) Math.signum(verticalAmount) * step;
            clampDetailScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawBoard(GuiGraphics context, int left, int top) {
        context.fill(left + 6, top + 6, left + WINDOW_WIDTH + 6, top + WINDOW_HEIGHT + 6, SHADOW);
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                BOARD_TEXTURE,
                left,
                top,
                0.0f,
                0.0f,
                WINDOW_WIDTH,
                WINDOW_HEIGHT,
                BOARD_TEXTURE_WIDTH,
                BOARD_TEXTURE_HEIGHT
        );
    }

    private void drawHeader(GuiGraphics context, int left, int top) {
        String screenTitle = this.title.getString();
        int titleX = left + (WINDOW_WIDTH - this.font.width(screenTitle)) / 2;
        context.drawString(this.font, screenTitle, titleX, top + TITLE_Y, TITLE, false);
    }

    private void drawSidebar(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        List<CategoryView> categories = data.categories();
        for (int i = 0; i < categories.size(); i++) {
            CategoryView category = categories.get(i);
            int x = left + CATEGORY_SLOT_X;
            int y = top + CATEGORY_SLOT_Y + (i * (CATEGORY_SLOT_HEIGHT + CATEGORY_SLOT_GAP));
            boolean hovered = isWithin(mouseX, mouseY, x, y, CATEGORY_SLOT_WIDTH, CATEGORY_SLOT_HEIGHT);
            boolean selected = category.categoryId().equals(selectedCategoryId);
            boolean locked = category.entryCount() <= 0;
            drawCategorySlot(context, x, y, category, hovered, selected, locked);
        }
    }

    private void drawCategorySlot(GuiGraphics context, int x, int y, CategoryView category, boolean hovered, boolean selected, boolean locked) {
        int fill = locked
                ? SLOT_LOCKED
                : selected ? SLOT_FILL : (hovered ? SLOT_HOVER : ENTRY_BG);
        drawFrame(context, x, y, CATEGORY_SLOT_WIDTH, CATEGORY_SLOT_HEIGHT, FRAME_DARK, FRAME_LIGHT, fill);
        int textColor = locked ? MUTED : TITLE;
        context.drawString(this.font, ellipsize(category.label().getString(), CATEGORY_SLOT_WIDTH - 22), x + 7, y + 8, textColor, false);
        if (category.entryCount() > 0) {
            String count = Integer.toString(category.entryCount());
            int countX = x + CATEGORY_SLOT_WIDTH - 8 - this.font.width(count);
            context.drawString(this.font, count, countX, y + 8, locked ? MUTED : SECTION_HEADER, false);
        }
    }

    private void drawEntryList(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        List<EntryView> entries = getVisibleEntries();
        updateEntryListScrollBounds(entries.size());
        if (entries.isEmpty()) {
            String empty = Component.translatable("screen.village-quest.questmaster.empty").getString();
            int x = left + LIST_PANEL_X + (LIST_PANEL_WIDTH - this.font.width(empty)) / 2;
            context.drawString(this.font, empty, x, top + LIST_PANEL_Y + (LIST_PANEL_HEIGHT / 2), MUTED, false);
            return;
        }

        int viewportX = left + ENTRY_X;
        int viewportY = top + ENTRY_Y;
        int viewportHeight = entryViewportHeight();
        context.enableScissor(viewportX, viewportY, viewportX + ENTRY_WIDTH, viewportY + viewportHeight);
        for (int i = 0; i < entries.size(); i++) {
            EntryView entry = entries.get(i);
            int x = viewportX;
            int y = viewportY + (i * (ENTRY_HEIGHT + ENTRY_GAP)) - this.entryListScrollOffset;
            if ((y + ENTRY_HEIGHT) < viewportY || y > viewportY + viewportHeight) {
                continue;
            }
            boolean hovered = isWithin(mouseX, mouseY, x, y, ENTRY_WIDTH, ENTRY_HEIGHT);
            boolean selected = entry.entryId().equals(selectedEntryId);
            drawEntryCard(context, x, y, entry, hovered, selected);
        }
        context.disableScissor();
        drawEntryListScrollIndicator(context, left, top, viewportHeight, entries.size());
    }

    private void drawEntryCard(GuiGraphics context, int x, int y, EntryView entry, boolean hovered, boolean selected) {
        int fill = entry.locked()
                ? ENTRY_LOCKED
                : selected ? (hovered ? ENTRY_SELECTED_HOVER : ENTRY_SELECTED) : (hovered ? ENTRY_HOVER : ENTRY_BG);
        drawFrame(context, x, y, ENTRY_WIDTH, ENTRY_HEIGHT, FRAME_DARK, FRAME_LIGHT, fill);
        String fullTitle = entry.title().getString();
        String title = ellipsize(fullTitle, ENTRY_WIDTH - 12);
        context.drawString(this.font, title, x + 6, y + 5, TITLE, false);
        context.drawString(this.font, ellipsize(entry.status().getString(), ENTRY_WIDTH - 12), x + 6, y + 16, pickStatusColor(entry), false);
        if (hovered && !title.equals(fullTitle)) {
            this.hoveredEntryTooltip = entry.title();
        }
    }

    private void drawDetailPanel(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        EntryView entry = getSelectedEntry();
        if (entry == null) {
            String empty = Component.translatable("screen.village-quest.questmaster.empty").getString();
            int textX = left + DETAIL_BODY_X + 6;
            int textY = top + DETAIL_BODY_Y + 6;
            drawWrappedLines(context, empty, textX, textY, DETAIL_BODY_WIDTH - 12, BODY, 5);
            return;
        }

        drawDetailHeader(context, left + DETAIL_HEADER_X, top + DETAIL_HEADER_Y, entry);
        drawDetailBody(context, left + DETAIL_BODY_X, top + DETAIL_BODY_Y, entry);
        drawTemplateButtons(context, left, top, mouseX, mouseY, entry);
    }

    private void drawFooter(GuiGraphics context, int left, int top) {
        String timerText = footerTimerText();
        if (timerText == null || timerText.isBlank()) {
            return;
        }

        int timerX = left + WINDOW_WIDTH - 28 - this.font.width(timerText);
        context.drawString(this.font, timerText, timerX, top + WINDOW_HEIGHT - 22, MUTED, false);
    }

    private void drawDetailHeader(GuiGraphics context, int x, int y, EntryView entry) {
        List<String> titleLines = wrapText(entry.title().getString(), DETAIL_HEADER_WIDTH - 12);
        int lineY = y + 3;
        int maxTitleLines = Math.min(2, titleLines.size());
        for (int i = 0; i < maxTitleLines; i++) {
            String line = titleLines.get(i);
            if (i == maxTitleLines - 1 && titleLines.size() > maxTitleLines) {
                line = ellipsize(line, DETAIL_HEADER_WIDTH - 12);
            }
            context.drawString(this.font, line, x + 6, lineY, TITLE, false);
            lineY += this.font.lineHeight;
        }

        boolean titleWrapped = titleLines.size() > 1;
        if (!titleWrapped && hasVisibleLabel(entry.subtitle())) {
            context.drawString(this.font, ellipsize(entry.subtitle().getString(), DETAIL_HEADER_WIDTH - 12), x + 6, y + 18, MUTED, false);
        }
        drawStatusTag(context, entry, x + 6, y + (titleWrapped ? 29 : 27), DETAIL_HEADER_WIDTH - 12);
    }

    private void drawDetailBody(GuiGraphics context, int x, int y, EntryView entry) {
        int textX = x + 6;
        int textY = y + 12;
        int textWidth = DETAIL_BODY_WIDTH - 14;
        int viewportHeight = DETAIL_BODY_HEIGHT - 20;
        int clipTop = textY;
        int clipBottom = textY + viewportHeight;
        List<DetailLine> lines = buildDetailLines(entry, textWidth);
        int contentHeight = measureDetailHeight(lines);
        this.detailScrollMax = Math.max(0, contentHeight - viewportHeight);
        clampDetailScroll();

        context.enableScissor(x + 4, clipTop, x + DETAIL_BODY_WIDTH - 6, clipBottom);
        int cursorY = textY - this.detailScrollOffset;
        for (DetailLine line : lines) {
            if (line.spacer()) {
                cursorY += 3;
                continue;
            }
            if (cursorY + this.font.lineHeight >= clipTop && cursorY <= clipBottom) {
                context.drawString(this.font, line.text(), textX + line.indent(), cursorY, line.color(), false);
            }
            cursorY += this.font.lineHeight;
        }
        context.disableScissor();
        drawScrollIndicator(context, x, y, viewportHeight, contentHeight);
    }

    private List<DetailLine> buildDetailLines(EntryView entry, int maxWidth) {
        List<DetailLine> lines = new ArrayList<>();
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.description"), entry.descriptionLines(), maxWidth, BODY);
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.objectives"), entry.objectiveLines(), maxWidth, BODY);
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.rewards"), entry.rewardLines(), maxWidth, SECTION_HEADER);
        return lines;
    }

    private void addDetailSection(List<DetailLine> lines, Component heading, List<Component> content, int maxWidth, int bodyColor) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (!lines.isEmpty()) {
            lines.add(new DetailLine("", BODY, 0, true));
        }
        lines.add(new DetailLine(heading.getString(), SECTION_HEADER, 0, false));
        for (String wrapped : collectWrappedLines(content, maxWidth - 2, Integer.MAX_VALUE)) {
            lines.add(new DetailLine(wrapped, bodyColor, 2, false));
        }
    }

    private int measureDetailHeight(List<DetailLine> lines) {
        int height = 0;
        for (DetailLine line : lines) {
            height += line.spacer() ? 3 : this.font.lineHeight;
        }
        return height;
    }

    private void drawScrollIndicator(GuiGraphics context, int x, int y, int viewportHeight, int contentHeight) {
        if (contentHeight <= viewportHeight) {
            return;
        }
        int trackX = x + DETAIL_BODY_WIDTH - 5;
        int trackY = y + 12;
        int trackHeight = DETAIL_BODY_HEIGHT - 20;
        context.fill(trackX, trackY, trackX + 2, trackY + trackHeight, SCROLL_TRACK);

        int thumbHeight = Math.max(10, (int) Math.round((double) viewportHeight / contentHeight * trackHeight));
        int thumbTravel = trackHeight - thumbHeight;
        int thumbOffset = this.detailScrollMax == 0 ? 0 : (int) Math.round((double) this.detailScrollOffset / this.detailScrollMax * thumbTravel);
        context.fill(trackX, trackY + thumbOffset, trackX + 2, trackY + thumbOffset + thumbHeight, SCROLL_THUMB);
    }

    private void drawEntryListScrollIndicator(GuiGraphics context, int left, int top, int viewportHeight, int entryCount) {
        int contentHeight = entryContentHeight(entryCount);
        if (contentHeight <= viewportHeight) {
            return;
        }
        int trackX = left + LIST_PANEL_X + LIST_PANEL_WIDTH - 5;
        int trackY = top + ENTRY_Y;
        context.fill(trackX, trackY, trackX + 2, trackY + viewportHeight, SCROLL_TRACK);

        int thumbHeight = Math.max(10, (int) Math.round((double) viewportHeight / contentHeight * viewportHeight));
        int thumbTravel = viewportHeight - thumbHeight;
        int thumbOffset = this.entryListScrollMax == 0 ? 0 : (int) Math.round((double) this.entryListScrollOffset / this.entryListScrollMax * thumbTravel);
        context.fill(trackX, trackY + thumbOffset, trackX + 2, trackY + thumbOffset + thumbHeight, SCROLL_THUMB);
    }

    private void drawTemplateButtons(GuiGraphics context, int left, int top, int mouseX, int mouseY, EntryView entry) {
        ButtonAction buttonAction = visibleButtonAction(entry);
        if (buttonAction != null && hasVisibleLabel(buttonAction.label())) {
            drawTemplateButton(
                    context,
                    left + BUTTON_X,
                    top + BUTTON_Y,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    buttonAction.label(),
                    buttonAction.enabled(),
                    isWithin(mouseX, mouseY, left + BUTTON_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
            );
        }
    }

    private void drawTemplateButton(GuiGraphics context, int x, int y, int width, int height, Component label, boolean enabled, boolean hovered) {
        int overlay = enabled ? (hovered ? BUTTON_HOVER_OVERLAY : BUTTON_ENABLED_OVERLAY) : BUTTON_DISABLED_OVERLAY;
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, overlay);
        String text = label.getString();
        int textX = x + (width - this.font.width(text)) / 2;
        int textY = y + Math.max(1, (height - this.font.lineHeight) / 2);
        context.drawString(this.font, text, textX, textY, enabled ? BUTTON_TEXT : BUTTON_DISABLED_TEXT, false);
    }

    private void drawStatusTag(GuiGraphics context, EntryView entry, int x, int y, int maxWidth) {
        String statusText = ellipsize(entry.status().getString(), maxWidth - 10);
        int width = this.font.width(statusText) + 10;
        int color = pickStatusColor(entry);
        context.fill(x, y, x + width, y + 11, FRAME_DARK);
        context.fill(x + 1, y + 1, x + width - 1, y + 10, color);
        context.drawString(this.font, statusText, x + 5, y + 2, STATUS_TEXT, false);
    }

    private void ensureSelection() {
        List<CategoryView> categories = data.categories();
        if (categories.isEmpty()) {
            this.selectedCategoryId = "";
            this.selectedEntryId = "";
            return;
        }

        boolean hasCategory = categories.stream().anyMatch(category -> category.categoryId().equals(selectedCategoryId));
        if (!hasCategory) {
            this.selectedCategoryId = categories.getFirst().categoryId();
        }

        List<EntryView> visibleEntries = getVisibleEntries();
        boolean hasEntry = visibleEntries.stream().anyMatch(entry -> entry.entryId().equals(selectedEntryId));
        if (!hasEntry) {
            this.selectedEntryId = visibleEntries.isEmpty() ? "" : visibleEntries.getFirst().entryId();
            this.detailScrollOffset = 0;
        }
    }

    private void ensureSelectedEntryVisible() {
        List<EntryView> visibleEntries = getVisibleEntries();
        updateEntryListScrollBounds(visibleEntries.size());
        if (visibleEntries.isEmpty()) {
            this.entryListScrollOffset = 0;
            return;
        }

        int selectedIndex = -1;
        for (int i = 0; i < visibleEntries.size(); i++) {
            if (visibleEntries.get(i).entryId().equals(selectedEntryId)) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0) {
            return;
        }

        int selectedTop = selectedIndex * (ENTRY_HEIGHT + ENTRY_GAP);
        int selectedBottom = selectedTop + ENTRY_HEIGHT;
        int viewportHeight = entryViewportHeight();
        if (selectedTop < this.entryListScrollOffset) {
            this.entryListScrollOffset = selectedTop;
        } else if (selectedBottom > this.entryListScrollOffset + viewportHeight) {
            this.entryListScrollOffset = selectedBottom - viewportHeight;
        }
        clampEntryListScroll();
    }

    private List<EntryView> getVisibleEntries() {
        List<EntryView> visible = new ArrayList<>();
        for (EntryView entry : data.entries()) {
            if (entry.categoryId().equals(selectedCategoryId)) {
                visible.add(entry);
            }
        }
        return visible;
    }

    private EntryView getSelectedEntry() {
        for (EntryView entry : data.entries()) {
            if (entry.entryId().equals(selectedEntryId)) {
                return entry;
            }
        }
        List<EntryView> visibleEntries = getVisibleEntries();
        return visibleEntries.isEmpty() ? null : visibleEntries.getFirst();
    }

    private void notifyClosed() {
        if (this.closeNotified || data.entityId() < 0) {
            return;
        }
        this.closeNotified = true;
        ClientPlayNetworking.send(new Payloads.QuestMasterSessionPayload(
                data.entityId(),
                Payloads.QuestMasterSessionPayload.ACTION_CLOSE
        ));
    }

    private void playClick() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void drawFrame(GuiGraphics context, int x, int y, int width, int height, int outer, int inner, int fill) {
        context.fill(x, y, x + width, y + height, outer);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, inner);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, fill);
    }

    private int drawWrappedLines(GuiGraphics context, String text, int x, int y, int maxWidth, int color, int maxLines) {
        List<String> lines = wrapText(text, maxWidth);
        int lineCount = Math.min(lines.size(), maxLines);
        for (int i = 0; i < lineCount; i++) {
            String rendered = lines.get(i);
            if (i == lineCount - 1 && lines.size() > maxLines) {
                rendered = ellipsize(rendered, maxWidth);
            }
            context.drawString(this.font, rendered, x, y, color, false);
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    private List<String> collectWrappedLines(List<Component> lines, int maxWidth, int maxLines) {
        List<String> wrapped = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return wrapped;
        }
        for (Component line : lines) {
            for (String wrappedLine : wrapText(line.getString(), maxWidth)) {
                if (wrapped.size() == maxLines) {
                    int last = wrapped.size() - 1;
                    wrapped.set(last, ellipsize(wrapped.get(last), maxWidth));
                    return wrapped;
                }
                wrapped.add(wrappedLine);
            }
        }
        return wrapped;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            String candidate = current + " " + word;
            if (this.font.width(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.append(' ').append(word);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && this.font.width(trimmed + ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private int pickStatusColor(EntryView entry) {
        if (entry.locked()) {
            return STATUS_LOCKED;
        }
        if (entry.primaryEnabled() && entry.primaryAction() == Payloads.QuestMasterActionPayload.ACTION_CLAIM) {
            return STATUS_READY;
        }
        if (entry.primaryEnabled() && entry.primaryAction() == Payloads.QuestMasterActionPayload.ACTION_ACCEPT) {
            return STATUS_AVAILABLE;
        }
        if (entry.secondaryEnabled() && entry.secondaryAction() == Payloads.QuestMasterActionPayload.ACTION_CANCEL) {
            return STATUS_ACTIVE;
        }
        return STATUS_DONE;
    }

    private boolean hasVisibleLabel(Component label) {
        return label != null && !label.getString().isBlank();
    }

    private String footerTimerText() {
        EntryView selected = getSelectedEntry();
        if (selected != null && ENTRY_WEEKLY.equals(selected.entryId()) && shouldShowResetTimer(selected)) {
            return Component.translatable(
                    "screen.village-quest.questmaster.weekly_timer",
                    formatRemainingResetTime(TimeUtil.millisUntilNextWeeklyReset())
            ).getString();
        }
        for (EntryView entry : data.entries()) {
            if (!ENTRY_DAILY_MAIN.equals(entry.entryId())) {
                continue;
            }
            if (shouldShowResetTimer(entry)) {
                return Component.translatable(
                        "screen.village-quest.questmaster.timer",
                        formatRemainingResetTime(TimeUtil.millisUntilNextDailyReset())
                ).getString();
            }
        }
        return null;
    }

    private boolean shouldShowResetTimer(EntryView entry) {
        return entry != null
                && !entry.locked()
                && entry.status().getString().equals(Component.translatable("screen.village-quest.questmaster.status.completed").getString())
                && !hasVisibleLabel(entry.primaryLabel())
                && !hasVisibleLabel(entry.secondaryLabel());
    }

    private ButtonAction visibleButtonAction(EntryView entry) {
        if (entry == null) {
            return null;
        }
        if (hasVisibleLabel(entry.primaryLabel())) {
            return new ButtonAction(entry.primaryAction(), entry.primaryLabel(), entry.primaryEnabled());
        }
        if (hasVisibleLabel(entry.secondaryLabel())) {
            return new ButtonAction(entry.secondaryAction(), entry.secondaryLabel(), entry.secondaryEnabled());
        }
        return null;
    }

    private void clampDetailScroll() {
        this.detailScrollOffset = Math.max(0, Math.min(this.detailScrollOffset, this.detailScrollMax));
    }

    private int entryViewportHeight() {
        return LIST_PANEL_HEIGHT - (ENTRY_Y - LIST_PANEL_Y);
    }

    private int entryContentHeight(int entryCount) {
        if (entryCount <= 0) {
            return 0;
        }
        return (entryCount * (ENTRY_HEIGHT + ENTRY_GAP)) - ENTRY_GAP;
    }

    private void updateEntryListScrollBounds(int entryCount) {
        this.entryListScrollMax = Math.max(0, entryContentHeight(entryCount) - entryViewportHeight());
        clampEntryListScroll();
    }

    private void clampEntryListScroll() {
        this.entryListScrollOffset = Math.max(0, Math.min(this.entryListScrollOffset, this.entryListScrollMax));
    }

    private String formatRemainingResetTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = totalSeconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
    }

    private boolean isWithin(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
