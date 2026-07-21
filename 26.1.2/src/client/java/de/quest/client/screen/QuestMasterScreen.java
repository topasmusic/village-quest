package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import de.quest.util.TimeUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class QuestMasterScreen extends CompatScreen {
    private static final Identifier BOARD_TEXTURE = Identifier.fromNamespaceAndPath(
            VillageQuest.MOD_ID, "textures/gui/journal_board.png");
    private static final int BOARD_TEXTURE_WIDTH = 416;
    private static final int BOARD_TEXTURE_HEIGHT = 234;

    public record CategoryView(String categoryId, Component label, int entryCount) {}

    public record EntryView(
            String entryId,
            String categoryId,
            Component title,
            Component subtitle,
            Component status,
            boolean partyShareable,
            Component partyStatus,
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

    public record PartyMemberView(
            String playerId,
            Component name,
            boolean leader,
            boolean self
    ) {}

    public record PartyCandidateView(
            String playerId,
            Component name,
            Component status,
            boolean inviteable
    ) {}

    public record PartyView(
            boolean hasParty,
            boolean leader,
            Component summary,
            List<PartyMemberView> members,
            List<PartyCandidateView> candidates
    ) {}

    public record QuestMasterData(
            int entityId,
            Component questMasterName,
            List<CategoryView> categories,
            List<EntryView> entries,
            PartyView party
    ) {}

    private record DetailLine(String text, int color, int indent, boolean spacer, boolean descriptionPreview) {}

    private record ButtonAction(int action, Component label, boolean enabled) {}

    private record ButtonSlot(ButtonAction action, int x, int width) {}

    private static final int WINDOW_WIDTH = 392;
    private static final int WINDOW_HEIGHT = 220;

    private static final int TITLE_Y = 14;

    private static final int CATEGORY_SLOT_X = 22;
    private static final int CATEGORY_SLOT_Y = 28;
    private static final int CATEGORY_SLOT_WIDTH = 45;
    private static final int CATEGORY_SLOT_HEIGHT = 29;
    private static final int CATEGORY_SLOT_GAP = 2;

    private static final int CONTENT_HEADER_X = 80;
    private static final int CONTENT_HEADER_Y = 40;
    private static final int LIST_PANEL_X = 78;
    private static final int LIST_PANEL_Y = 52;
    private static final int LIST_PANEL_WIDTH = 108;
    private static final int LIST_PANEL_HEIGHT = 134;
    private static final int ENTRY_X = LIST_PANEL_X;
    private static final int ENTRY_Y = LIST_PANEL_Y;
    private static final int ENTRY_WIDTH = LIST_PANEL_WIDTH;
    private static final int ENTRY_HEIGHT = 30;
    private static final int ENTRY_GAP = 3;

    private static final int DETAIL_HEADER_X = 191;
    private static final int DETAIL_HEADER_Y = 52;
    private static final int DETAIL_HEADER_WIDTH = 175;
    private static final int DETAIL_HEADER_HEIGHT = 40;

    private static final int DETAIL_BODY_X = DETAIL_HEADER_X;
    private static final int DETAIL_BODY_Y = DETAIL_HEADER_Y + DETAIL_HEADER_HEIGHT;
    private static final int DETAIL_BODY_WIDTH = DETAIL_HEADER_WIDTH;
    private static final int DETAIL_BODY_HEIGHT = 94;
    private static final int DETAIL_HEADER_HORIZONTAL_PADDING = 12;
    private static final int DETAIL_TEXT_LEFT = 12;
    private static final int DETAIL_TEXT_RIGHT = 15;
    private static final int DETAIL_TEXT_TOP = 5;
    private static final int DETAIL_TEXT_BOTTOM = 7;
    private static final float DETAIL_TEXT_SCALE = 0.72f;
    private static final int DETAIL_LINE_STEP = 8;
    private static final int DESCRIPTION_POPUP_WIDTH = 196;
    private static final int DESCRIPTION_POPUP_PADDING = 6;
    private static final int DESCRIPTION_POPUP_OFFSET = 12;
    private static final int DESCRIPTION_POPUP_MARGIN = 8;

    private static final int BUTTON_Y = 191;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PARTY_BUTTON_WIDTH = 48;
    private static final int PARTY_BUTTON_HEIGHT = 15;
    private static final int PARTY_BUTTON_X = DETAIL_HEADER_X + DETAIL_HEADER_WIDTH - PARTY_BUTTON_WIDTH - 6;
    private static final int PARTY_BUTTON_Y = 38;
    private static final int PARTY_DRAWER_X = DETAIL_HEADER_X;
    private static final int PARTY_DRAWER_Y = DETAIL_HEADER_Y;
    private static final int PARTY_DRAWER_WIDTH = DETAIL_HEADER_WIDTH;
    private static final int PARTY_DRAWER_HEIGHT = DETAIL_HEADER_HEIGHT + DETAIL_BODY_HEIGHT;
    private static final int PARTY_ROW_HEIGHT = 14;
    private static final int PARTY_VISIBLE_CANDIDATES = 4;

    private static final int TITLE = 0xFF2B170E;
    private static final int BODY = 0xFF5C4030;
    private static final int MUTED = 0xFF8A715E;
    private static final int SECTION_HEADER = 0xFF8F5A2F;
    private static final int SLOT_FILL = 0xFFE5C785;
    private static final int SLOT_HOVER = 0xFFFFF0CF;
    private static final int SLOT_LOCKED = 0xFFE5D7BF;
    private static final int ENTRY_BG = 0xFFF2DEB6;
    private static final int ENTRY_HOVER = 0xFFF8EAC8;
    private static final int ENTRY_SELECTED = 0xFFDCC58A;
    private static final int ENTRY_SELECTED_HOVER = 0xFFF0DDAA;
    private static final int ENTRY_LOCKED = 0xFFE5D7BF;
    private static final int FRAME_DARK = 0xFF5A321E;
    private static final int FRAME_LIGHT = 0xFFB88943;
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
    private static final int PARTY_BUTTON_FILL = 0xFF5C2F1C;
    private static final int PARTY_BUTTON_HOVER_FILL = 0xFF7A4126;
    private static final int PARTY_BUTTON_ACTIVE_FILL = 0xFF236B68;
    private static final int PARTY_BUTTON_ACTIVE_HOVER_FILL = 0xFF2E8580;
    private static final int PARTY_BUTTON_TEXT = 0xFFF6E9D1;
    private static final int PARTY_DRAWER_FILL = 0xFFF5E7C9;
    private static final int PARTY_SECTION = 0xFF7D4A22;
    private static final int PARTY_MUTED = 0xFF7F6A57;
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
    private boolean partyDrawerOpen = false;
    private int partyCandidateScrollIndex = 0;
    private Component hoveredEntryTooltip;
    private List<Component> hoveredDescriptionLines = List.of();

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
        clampPartyDrawerState();
    }

    @Override
    protected void init() {
        ensureSelection();
        clampEntryListScroll();
        ensureSelectedEntryVisible();
        clampPartyDrawerState();
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
    public boolean keyPressed(KeyEvent key) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int left = (this.width - WINDOW_WIDTH) / 2;
        int top = (this.height - WINDOW_HEIGHT) / 2;
        this.hoveredEntryTooltip = null;
        this.hoveredDescriptionLines = List.of();

        VillageUiTheme.drawScreenShade(context, this.width, this.height);
        VillageUiTheme.drawPanelShadow(context, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
        drawBoard(context, left, top);
        drawHeader(context, left, top);
        drawSidebar(context, left, top, mouseX, mouseY);
        drawEntryList(context, left, top, mouseX, mouseY);
        drawDetailPanel(context, left, top, mouseX, mouseY);
        drawPartyDrawer(context, left, top, mouseX, mouseY);
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
                clampPartyDrawerState();
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
                clampPartyDrawerState();
                playClick();
                return true;
            }
        }

        EntryView selected = getSelectedEntry();
        if (selected != null) {
            if (isPartyDrawerAvailable(selected)
                    && isWithin(mouseX, mouseY, left + PARTY_BUTTON_X, top + PARTY_BUTTON_Y, PARTY_BUTTON_WIDTH, PARTY_BUTTON_HEIGHT)) {
                this.partyDrawerOpen = !this.partyDrawerOpen;
                clampPartyDrawerState();
                playClick();
                return true;
            }
            if (this.partyDrawerOpen && handlePartyDrawerClick(selected, mouseX, mouseY, left, top)) {
                playClick();
                return true;
            }
            for (ButtonSlot slot : buttonSlots(selected)) {
                if (isWithin(mouseX, mouseY, left + slot.x(), top + BUTTON_Y, slot.width(), BUTTON_HEIGHT)) {
                    if (slot.action().enabled()) {
                        ClientPlayNetworking.send(new Payloads.QuestMasterActionPayload(
                                data.entityId(),
                                slot.action().action(),
                                selected.entryId()
                        ));
                        playClick();
                    }
                    return true;
                }
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
        if (this.partyDrawerOpen
                && isWithin((int) mouseX, (int) mouseY, left + PARTY_DRAWER_X, top + PARTY_DRAWER_Y, PARTY_DRAWER_WIDTH, PARTY_DRAWER_HEIGHT)
                && candidateScrollMax() > 0) {
            this.partyCandidateScrollIndex -= (int) Math.signum(verticalAmount);
            clampPartyDrawerState();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawBoard(GuiGraphics context, int left, int top) {
        // Keep the Quest Board compact while sampling the complete journal frame.
        VillageUiTheme.blitScaled(context, BOARD_TEXTURE, left, top,
                WINDOW_WIDTH, WINDOW_HEIGHT, BOARD_TEXTURE_WIDTH, BOARD_TEXTURE_HEIGHT);
    }

    private void drawHeader(GuiGraphics context, int left, int top) {
        String screenTitle = this.title.getString();
        int titleX = left + (WINDOW_WIDTH - this.font.width(screenTitle)) / 2;
        context.drawString(this.font, screenTitle, titleX, top + TITLE_Y, TITLE, false);

        CategoryView selected = selectedCategory();
        if (selected != null) {
            context.drawString(this.font, selected.label().getString(),
                    left + CONTENT_HEADER_X, top + CONTENT_HEADER_Y, SECTION_HEADER, false);
            String count = Integer.toString(selected.entryCount());
            int countX = left + LIST_PANEL_X + LIST_PANEL_WIDTH - this.font.width(count) - 3;
            context.drawString(this.font, count, countX, top + CONTENT_HEADER_Y, MUTED, false);
        }
        String masterName = ellipsize(data.questMasterName().getString(), 112);
        VillageUiTheme.drawStringScaled(context, this.font, masterName,
                left + DETAIL_HEADER_X + 3, top + CONTENT_HEADER_Y + 1, MUTED, 0.72f);
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
        VillageUiTheme.drawTab(context, x, y, CATEGORY_SLOT_WIDTH, CATEGORY_SLOT_HEIGHT,
                selected && !locked, hovered && !locked);
        VillageUiTheme.drawIcon(context, VillageUiTheme.icon(categoryIcon(category.categoryId())),
                x + (CATEGORY_SLOT_WIDTH - 21) / 2, y + (CATEGORY_SLOT_HEIGHT - 21) / 2, 21);
        if (locked) {
            context.fill(x + 4, y + 4, x + CATEGORY_SLOT_WIDTH - 4,
                    y + CATEGORY_SLOT_HEIGHT - 4, 0x669A8B76);
        }
        if (category.entryCount() > 0) {
            String count = Integer.toString(category.entryCount());
            int badgeX = x + CATEGORY_SLOT_WIDTH - this.font.width(count) - 8;
            context.fill(badgeX - 2, y + 4, x + CATEGORY_SLOT_WIDTH - 4, y + 14, 0xCC5A321E);
            context.drawString(this.font, count, badgeX, y + 5, STATUS_TEXT, false);
        }
        if (hovered) {
            Component tooltip = Component.literal(category.label().getString() + " (" + category.entryCount() + ")");
            context.setTooltipForNextFrame(this.font, tooltip, x + CATEGORY_SLOT_WIDTH, y + 8);
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
        VillageUiTheme.drawCard(context, x, y, ENTRY_WIDTH, ENTRY_HEIGHT,
                hovered && !entry.locked(), selected);
        context.fill(x + 7, y + 6, x + 10, y + ENTRY_HEIGHT - 6,
                categoryAccent(entry.categoryId()));
        if (entry.locked()) {
            context.fill(x + 4, y + 4, x + ENTRY_WIDTH - 4, y + ENTRY_HEIGHT - 4, 0x559A8B76);
        }
        String fullTitle = entry.title().getString();
        String title = compactScaled(fullTitle, ENTRY_WIDTH - 29, 0.78f);
        VillageUiTheme.drawStringScaled(context, this.font, title,
                x + 15, y + 6, TITLE, 0.78f);
        VillageUiTheme.drawStringScaled(context, this.font,
                compactScaled(entry.status().getString(), ENTRY_WIDTH - 29, 0.68f),
                x + 15, y + 18, pickStatusColor(entry), 0.68f);
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

        VillageUiTheme.drawCard(context, left + DETAIL_HEADER_X, top + DETAIL_HEADER_Y,
                DETAIL_HEADER_WIDTH, DETAIL_HEADER_HEIGHT + DETAIL_BODY_HEIGHT, false, true);
        context.fill(left + DETAIL_HEADER_X + 7, top + DETAIL_HEADER_Y + 7,
                left + DETAIL_HEADER_X + 10, top + DETAIL_HEADER_Y + DETAIL_HEADER_HEIGHT + DETAIL_BODY_HEIGHT - 7,
                categoryAccent(entry.categoryId()));
        drawDetailHeader(context, left + DETAIL_HEADER_X, top + DETAIL_HEADER_Y, entry);
        drawDetailBody(context, left + DETAIL_BODY_X, top + DETAIL_BODY_Y, entry, mouseX, mouseY);
        if (isPartyDrawerAvailable(entry)) {
            drawPartyToggleButton(
                    context,
                    left + PARTY_BUTTON_X,
                    top + PARTY_BUTTON_Y,
                    PARTY_BUTTON_WIDTH,
                    PARTY_BUTTON_HEIGHT,
                    Component.translatable("screen.village-quest.questmaster.party.button"),
                    this.partyDrawerOpen,
                    isWithin(mouseX, mouseY, left + PARTY_BUTTON_X, top + PARTY_BUTTON_Y, PARTY_BUTTON_WIDTH, PARTY_BUTTON_HEIGHT)
            );
        }
        drawTemplateButtons(context, left, top, mouseX, mouseY, entry);
    }

    private void drawFooter(GuiGraphics context, int left, int top) {
        String timerText = footerTimerText();
        if (timerText == null || timerText.isBlank()) {
            return;
        }

        VillageUiTheme.drawStringScaled(context, this.font, timerText,
                left + 79, top + 196, MUTED, 0.68f);
    }

    private void drawDetailHeader(GuiGraphics context, int x, int y, EntryView entry) {
        int contentWidth = DETAIL_HEADER_WIDTH - (DETAIL_HEADER_HORIZONTAL_PADDING * 2);
        String titleText = compactScaled(entry.title().getString(), contentWidth, 0.86f);
        VillageUiTheme.drawStringScaled(context, this.font, titleText,
                x + DETAIL_HEADER_HORIZONTAL_PADDING, y + 7, TITLE, 0.86f);

        String subtitle = entry.subtitle().getString();
        if (hasVisibleLabel(entry.partyStatus())) {
            subtitle = subtitle.isBlank()
                    ? entry.partyStatus().getString()
                    : subtitle + " / " + entry.partyStatus().getString();
        }
        VillageUiTheme.drawStringScaled(context, this.font,
                compactScaled(subtitle, contentWidth - 58, 0.68f),
                x + DETAIL_HEADER_HORIZONTAL_PADDING, y + 21, MUTED, 0.68f);
        drawStatusTag(context, entry, x + DETAIL_HEADER_HORIZONTAL_PADDING,
                y + 30, contentWidth);
    }

    private void drawDetailBody(GuiGraphics context, int x, int y, EntryView entry, int mouseX, int mouseY) {
        int textX = x + DETAIL_TEXT_LEFT;
        int textY = y + DETAIL_TEXT_TOP;
        int textWidth = DETAIL_BODY_WIDTH - DETAIL_TEXT_LEFT - DETAIL_TEXT_RIGHT;
        int viewportHeight = DETAIL_BODY_HEIGHT - DETAIL_TEXT_TOP - DETAIL_TEXT_BOTTOM;
        int clipTop = textY;
        int clipBottom = textY + viewportHeight;
        List<DetailLine> lines = buildDetailLines(entry,
                Math.max(1, (int) Math.floor(textWidth / DETAIL_TEXT_SCALE)));
        int contentHeight = measureDetailHeight(lines);
        this.detailScrollMax = Math.max(0, contentHeight - viewportHeight);
        clampDetailScroll();

        context.enableScissor(x + DETAIL_TEXT_LEFT - 2, clipTop,
                x + DETAIL_BODY_WIDTH - DETAIL_TEXT_RIGHT + 2, clipBottom);
        int cursorY = textY - this.detailScrollOffset;
        for (DetailLine line : lines) {
            if (line.spacer()) {
                cursorY += 3;
                continue;
            }
            if (cursorY >= clipTop && cursorY + DETAIL_LINE_STEP <= clipBottom) {
                VillageUiTheme.drawStringScaled(context, this.font, line.text(),
                        textX + line.indent(), cursorY, line.color(), DETAIL_TEXT_SCALE);
            }
            cursorY += DETAIL_LINE_STEP;
        }
        context.disableScissor();
        drawScrollIndicator(context, x, y, viewportHeight, contentHeight);
    }

    private List<DetailLine> buildDetailLines(EntryView entry, int maxWidth) {
        List<DetailLine> lines = new ArrayList<>();
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.description"), entry.descriptionLines(), maxWidth, BODY, true);
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.objectives"), entry.objectiveLines(), maxWidth, BODY, false);
        addDetailSection(lines, Component.translatable("screen.village-quest.questmaster.rewards"), entry.rewardLines(), maxWidth, SECTION_HEADER, false);
        return lines;
    }

    private void addDetailSection(List<DetailLine> lines, Component heading, List<Component> content, int maxWidth, int bodyColor, boolean descriptionPreview) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (!lines.isEmpty()) {
            lines.add(new DetailLine("", BODY, 0, true, false));
        }
        lines.add(new DetailLine(heading.getString(), SECTION_HEADER, 0, false, descriptionPreview));
        for (String wrapped : collectWrappedLines(content, maxWidth - 2, Integer.MAX_VALUE)) {
            lines.add(new DetailLine(wrapped, bodyColor, 2, false, descriptionPreview));
        }
    }

    private int measureDetailHeight(List<DetailLine> lines) {
        int height = 0;
        for (DetailLine line : lines) {
            height += line.spacer() ? 3 : DETAIL_LINE_STEP;
        }
        return height;
    }

    private void drawScrollIndicator(GuiGraphics context, int x, int y, int viewportHeight, int contentHeight) {
        if (contentHeight <= viewportHeight) {
            return;
        }
        int trackX = x + DETAIL_BODY_WIDTH - 5;
        int trackY = y + DETAIL_TEXT_TOP;
        int trackHeight = DETAIL_BODY_HEIGHT - DETAIL_TEXT_TOP - DETAIL_TEXT_BOTTOM;
        VillageUiTheme.drawScrollBar(context, trackX - 2, trackY, trackHeight,
                viewportHeight, contentHeight, this.detailScrollOffset, this.detailScrollMax);
    }

    private void drawDescriptionPopup(GuiGraphics context, int mouseX, int mouseY) {
        int textWidth = DESCRIPTION_POPUP_WIDTH - (DESCRIPTION_POPUP_PADDING * 2);
        int lineAdvance = this.font.lineHeight + 1;
        int maxPopupHeight = Math.max(72, this.height - (DESCRIPTION_POPUP_MARGIN * 2));
        int maxLines = Math.max(2, (maxPopupHeight - (DESCRIPTION_POPUP_PADDING * 2) - this.font.lineHeight - 6) / lineAdvance);
        List<String> lines = collectWrappedLines(this.hoveredDescriptionLines, textWidth, maxLines);
        if (lines.isEmpty()) {
            return;
        }

        int popupHeight = (DESCRIPTION_POPUP_PADDING * 2) + this.font.lineHeight + 6 + (lines.size() * lineAdvance);
        int popupX = mouseX + DESCRIPTION_POPUP_OFFSET;
        if (popupX + DESCRIPTION_POPUP_WIDTH > this.width - DESCRIPTION_POPUP_MARGIN) {
            popupX = mouseX - DESCRIPTION_POPUP_WIDTH - DESCRIPTION_POPUP_OFFSET;
        }
        popupX = Math.max(DESCRIPTION_POPUP_MARGIN, popupX);

        int popupY = mouseY - 8;
        if (popupY + popupHeight > this.height - DESCRIPTION_POPUP_MARGIN) {
            popupY = this.height - popupHeight - DESCRIPTION_POPUP_MARGIN;
        }
        popupY = Math.max(DESCRIPTION_POPUP_MARGIN, popupY);

        VillageUiTheme.drawCard(context, popupX, popupY, DESCRIPTION_POPUP_WIDTH, popupHeight, false, true);
        context.drawString(
                this.font,
                Component.translatable("screen.village-quest.questmaster.description").getString(),
                popupX + DESCRIPTION_POPUP_PADDING,
                popupY + DESCRIPTION_POPUP_PADDING,
                SECTION_HEADER,
                false
        );
        int separatorY = popupY + DESCRIPTION_POPUP_PADDING + this.font.lineHeight + 2;
        context.fill(
                popupX + DESCRIPTION_POPUP_PADDING,
                separatorY,
                popupX + DESCRIPTION_POPUP_WIDTH - DESCRIPTION_POPUP_PADDING,
                separatorY + 1,
                SCROLL_TRACK
        );

        int lineY = separatorY + 4;
        for (String line : lines) {
            context.drawString(this.font, line, popupX + DESCRIPTION_POPUP_PADDING, lineY, BODY, false);
            lineY += lineAdvance;
        }
    }

    private void drawEntryListScrollIndicator(GuiGraphics context, int left, int top, int viewportHeight, int entryCount) {
        int contentHeight = entryContentHeight(entryCount);
        if (contentHeight <= viewportHeight) {
            return;
        }
        int trackX = left + LIST_PANEL_X + LIST_PANEL_WIDTH - 5;
        int trackY = top + ENTRY_Y;
        VillageUiTheme.drawScrollBar(context, trackX - 2, trackY, viewportHeight,
                viewportHeight, contentHeight, this.entryListScrollOffset, this.entryListScrollMax);
    }

    private void drawTemplateButtons(GuiGraphics context, int left, int top, int mouseX, int mouseY, EntryView entry) {
        for (ButtonSlot slot : buttonSlots(entry)) {
            drawTemplateButton(
                    context,
                    left + slot.x(),
                    top + BUTTON_Y,
                    slot.width(),
                    BUTTON_HEIGHT,
                    slot.action().label(),
                    slot.action().enabled(),
                    isWithin(mouseX, mouseY, left + slot.x(), top + BUTTON_Y, slot.width(), BUTTON_HEIGHT)
            );
        }
    }

    private void drawPartyDrawer(GuiGraphics context, int left, int top, int mouseX, int mouseY) {
        EntryView selected = getSelectedEntry();
        if (!this.partyDrawerOpen || selected == null || !isPartyDrawerAvailable(selected)) {
            return;
        }

        int x = left + PARTY_DRAWER_X;
        int y = top + PARTY_DRAWER_Y;
        VillageUiTheme.drawCard(context, x, y, PARTY_DRAWER_WIDTH, PARTY_DRAWER_HEIGHT, false, true);
        context.drawString(this.font, Component.translatable("screen.village-quest.questmaster.party.title").getString(), x + 6, y + 6, TITLE, false);
        context.drawString(this.font, ellipsize(data.party().summary().getString(), PARTY_DRAWER_WIDTH - 12), x + 6, y + 18, PARTY_MUTED, false);

        int cursorY = y + 34;
        context.drawString(this.font, Component.translatable("screen.village-quest.questmaster.party.members").getString(), x + 6, cursorY, PARTY_SECTION, false);
        cursorY += this.font.lineHeight + 2;
        for (PartyMemberView member : data.party().members()) {
            String label = member.name().getString()
                    + (member.leader() ? " [L]" : "")
                    + (member.self() ? " *" : "");
            context.drawString(this.font, ellipsize(label, PARTY_DRAWER_WIDTH - 12), x + 6, cursorY, BODY, false);
            cursorY += PARTY_ROW_HEIGHT;
        }

        cursorY += 2;
        context.drawString(this.font, Component.translatable("screen.village-quest.questmaster.party.online").getString(), x + 6, cursorY, PARTY_SECTION, false);
        cursorY += this.font.lineHeight + 2;

        List<PartyCandidateView> visibleCandidates = visibleCandidates();
        for (PartyCandidateView candidate : visibleCandidates) {
            boolean hovered = isWithin(mouseX, mouseY, x + 6, cursorY - 1, PARTY_DRAWER_WIDTH - 12, PARTY_ROW_HEIGHT);
            int rowFill = hovered ? 0x22FFFFFF : 0x11000000;
            context.fill(x + 4, cursorY - 1, x + PARTY_DRAWER_WIDTH - 4, cursorY + PARTY_ROW_HEIGHT - 2, rowFill);
            context.drawString(this.font, ellipsize(candidate.name().getString(), PARTY_DRAWER_WIDTH - 54), x + 6, cursorY + 2, BODY, false);
            context.drawString(this.font, ellipsize(candidate.status().getString(), 40), x + PARTY_DRAWER_WIDTH - 46, cursorY + 2, candidate.inviteable() ? STATUS_ACTIVE : PARTY_MUTED, false);
            cursorY += PARTY_ROW_HEIGHT;
        }

        if (data.party().hasParty()) {
            Component actionLabel = Component.translatable(data.party().leader()
                    ? "screen.village-quest.questmaster.party.disband"
                    : "screen.village-quest.questmaster.party.leave");
            drawTemplateButton(
                    context,
                    x + 6,
                    y + PARTY_DRAWER_HEIGHT - 22,
                    PARTY_DRAWER_WIDTH - 12,
                    14,
                    actionLabel,
                    true,
                    isWithin(mouseX, mouseY, x + 6, y + PARTY_DRAWER_HEIGHT - 22, PARTY_DRAWER_WIDTH - 12, 14)
            );
        }
    }

    private void drawTemplateButton(GuiGraphics context, int x, int y, int width, int height, Component label, boolean enabled, boolean hovered) {
        VillageUiTheme.drawButton(context, this.font, x, y, width, height,
                label.getString(), enabled, hovered && enabled, false);
    }

    private void drawPartyToggleButton(GuiGraphics context, int x, int y, int width, int height, Component label, boolean active, boolean hovered) {
        VillageUiTheme.drawButton(context, this.font, x, y, width, height,
                label.getString(), true, hovered, active);
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

    private CategoryView selectedCategory() {
        for (CategoryView category : data.categories()) {
            if (category.categoryId().equals(this.selectedCategoryId)) {
                return category;
            }
        }
        return null;
    }

    private String categoryIcon(String categoryId) {
        return switch (categoryId) {
            case "daily" -> "daily";
            case "weekly" -> "weekly";
            case "story" -> "story";
            case "special" -> "special";
            default -> "quests";
        };
    }

    private int categoryAccent(String categoryId) {
        return switch (categoryId) {
            case "daily" -> 0xFF3F667F;
            case "weekly" -> 0xFF9A6620;
            case "story" -> 0xFF47713F;
            case "special" -> 0xFF725083;
            default -> VillageUiTheme.TEAL;
        };
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

    private String compactScaled(String text, int maxWidth, float scale) {
        return ellipsize(text, Math.max(1, (int) Math.floor(maxWidth / scale)));
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

    private List<ButtonSlot> buttonSlots(EntryView entry) {
        if (entry == null) {
            return List.of();
        }
        List<ButtonAction> actions = new ArrayList<>();
        if (hasVisibleLabel(entry.secondaryLabel())) {
            actions.add(new ButtonAction(entry.secondaryAction(), entry.secondaryLabel(), entry.secondaryEnabled()));
        }
        if (hasVisibleLabel(entry.primaryLabel())) {
            actions.add(new ButtonAction(entry.primaryAction(), entry.primaryLabel(), entry.primaryEnabled()));
        }
        if (actions.isEmpty()) {
            return List.of();
        }
        if (actions.size() == 1) {
            return List.of(new ButtonSlot(actions.getFirst(), DETAIL_HEADER_X + 34, 129));
        }
        return List.of(
                new ButtonSlot(actions.get(0), DETAIL_HEADER_X + 7, 58),
                new ButtonSlot(actions.get(1), DETAIL_HEADER_X + 69, 94)
        );
    }

    private boolean handlePartyDrawerClick(EntryView selected, int mouseX, int mouseY, int left, int top) {
        int x = left + PARTY_DRAWER_X;
        int y = top + PARTY_DRAWER_Y;
        if (!isWithin(mouseX, mouseY, x, y, PARTY_DRAWER_WIDTH, PARTY_DRAWER_HEIGHT)) {
            return false;
        }

        int rowY = y + 34 + this.font.lineHeight + 2;
        rowY += data.party().members().size() * PARTY_ROW_HEIGHT;
        rowY += 2 + this.font.lineHeight + 2;
        for (PartyCandidateView candidate : visibleCandidates()) {
            if (candidate.inviteable()
                    && isWithin(mouseX, mouseY, x + 4, rowY - 1, PARTY_DRAWER_WIDTH - 8, PARTY_ROW_HEIGHT)) {
                ClientPlayNetworking.send(new Payloads.QuestMasterPartyActionPayload(
                        data.entityId(),
                        Payloads.QuestMasterPartyActionPayload.ACTION_INVITE,
                        candidate.playerId()
                ));
                return true;
            }
            rowY += PARTY_ROW_HEIGHT;
        }

        if (data.party().hasParty()
                && isWithin(mouseX, mouseY, x + 6, y + PARTY_DRAWER_HEIGHT - 22, PARTY_DRAWER_WIDTH - 12, 14)) {
            ClientPlayNetworking.send(new Payloads.QuestMasterPartyActionPayload(
                    data.entityId(),
                    data.party().leader()
                            ? Payloads.QuestMasterPartyActionPayload.ACTION_DISBAND
                            : Payloads.QuestMasterPartyActionPayload.ACTION_LEAVE,
                    ""
            ));
            return true;
        }

        return true;
    }

    private void clampDetailScroll() {
        this.detailScrollOffset = Math.max(0, Math.min(this.detailScrollOffset, this.detailScrollMax));
    }

    private boolean isPartyDrawerAvailable(EntryView entry) {
        return entry != null && entry.partyShareable() && (this.minecraft == null || !this.minecraft.isLocalServer());
    }

    private void clampPartyDrawerState() {
        if (!isPartyDrawerAvailable(getSelectedEntry())) {
            this.partyDrawerOpen = false;
        }
        this.partyCandidateScrollIndex = Math.max(0, Math.min(this.partyCandidateScrollIndex, candidateScrollMax()));
    }

    private List<PartyCandidateView> visibleCandidates() {
        List<PartyCandidateView> candidates = data.party().candidates();
        if (candidates.isEmpty()) {
            return List.of();
        }
        int from = Math.min(this.partyCandidateScrollIndex, Math.max(0, candidates.size() - 1));
        int to = Math.min(candidates.size(), from + PARTY_VISIBLE_CANDIDATES);
        return candidates.subList(from, to);
    }

    private int candidateScrollMax() {
        return Math.max(0, data.party().candidates().size() - PARTY_VISIBLE_CANDIDATES);
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
