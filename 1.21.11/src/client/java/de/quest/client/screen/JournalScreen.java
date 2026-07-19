package de.quest.client.screen;

import de.quest.VillageQuest;
import de.quest.client.ui.InventoryJournalTutorialState;
import de.quest.client.ui.TutorialHintRenderer;
import de.quest.client.ui.VillageUiTheme;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads.JournalActionPayload;
import de.quest.quest.special.RelicQuestProgressionService;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvents;

/**
 * Compact, tabbed journal. The old linear book could exceed fourteen pages and made
 * reference material compete with the player's active quests. This view keeps the
 * same data, but reveals details only when the player expands a card.
 */
public class JournalScreen extends Screen {
    private static final Identifier BOARD_TEXTURE = Identifier.of(
            VillageQuest.MOD_ID, "textures/gui/journal_board.png");
    private static final int WINDOW_WIDTH = 416;
    private static final int WINDOW_HEIGHT = 234;
    private static final int TAB_X = 22;
    private static final int TAB_Y = 30;
    private static final int TAB_WIDTH = 45;
    private static final int TAB_HEIGHT = 31;
    private static final int TAB_GAP = 2;
    private static final int CONTENT_X = 76;
    private static final int CONTENT_Y = 40;
    private static final int CONTENT_WIDTH = 313;
    private static final int CONTENT_TOP = 56;
    private static final int CONTENT_BOTTOM = 197;
    private static final int CARD_GAP = 5;
    private static final int CARD_COLLAPSED_HEIGHT = 31;
    private static final int CARD_TEXT_INSET = 20;
    private static final float CARD_TITLE_SCALE = 0.84f;
    private static final float CARD_BODY_SCALE = 0.72f;
    private static final int CARD_DETAIL_STEP = 8;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_Y = 203;
    private static final int INK = 0xFF2D1B12;
    private static final int BODY = 0xFF5B4635;
    private static final int MUTED = 0xFF8A7661;
    private static final int FRAME_DARK = 0xFF4C2C19;
    private static final int FRAME_LIGHT = 0xFFB88943;
    private static final int PAPER = 0xFFF5E7C7;
    private static final int PAPER_HOVER = 0xFFFFF0D5;
    private static final int PAPER_EXPANDED = 0xFFFFE8B8;
    private static final int TEAL = 0xFF236B68;
    private static final int TEAL_HOVER = 0xFF2E8580;
    private static final int GOLD = 0xFF9A6620;
    private static final int GREEN = 0xFF47713F;
    private static final int BLUE = 0xFF3F667F;
    private static final int RED = 0xFF9B4337;
    private static final int PURPLE = 0xFF725083;
    private static final int SCROLL_TRACK = 0x335B3C21;
    private static final int SCROLL_THUMB = 0xAA7A522B;

    private enum Section {
        OVERVIEW("screen.village-quest.journal.v2.tab.overview"),
        QUESTS("screen.village-quest.journal.v2.tab.quests"),
        REPUTATION("screen.village-quest.journal.v2.tab.reputation"),
        COLLECTION("screen.village-quest.journal.v2.tab.collection"),
        GUIDE("screen.village-quest.journal.v2.tab.guide");

        private final String key;

        Section(String key) {
            this.key = key;
        }
    }

    private record JournalCard(
            String id,
            Text title,
            Text subtitle,
            List<Text> details,
            int accent,
            int cancelAction
    ) {}

    private record SpecialItemEntry(String nameKey, String loreKey) {}

    private record ProjectEntry(String keyPrefix, boolean unlocked) {}

    public static class JournalData {
        public final int total;
        public final int discovered;
        public final int completed;
        public final int active;
        public final long currencyBalance;
        public final int farmingReputation;
        public final int craftingReputation;
        public final int animalReputation;
        public final int tradeReputation;
        public final int monsterReputation;
        public final boolean hasStarreachRing;
        public final boolean hasMerchantSeal;
        public final boolean hasShepherdFlute;
        public final boolean hasApiaristSmoker;
        public final boolean hasSurveyorCompass;
        public final boolean hasCaravanLedger;
        public final boolean dailyActive;
        public final Text dailyTitle;
        public final Text dailyProgress;
        public final boolean weeklyActive;
        public final Text weeklyTitle;
        public final Text weeklyProgress;
        public final boolean storyActive;
        public final Text storyTitle;
        public final Text storyProgress;
        public final boolean pilgrimActive;
        public final Text pilgrimTitle;
        public final Text pilgrimProgress;
        public final boolean specialActive;
        public final Text specialTitle;
        public final Text specialProgress;
        public final boolean hasVillageLedgerProject;
        public final boolean hasApiaryCharterProject;
        public final boolean hasForgeCharterProject;
        public final boolean hasMarketCharterProject;
        public final boolean hasPastureCharterProject;
        public final boolean hasWatchBellProject;
        public final boolean hasCaravanYardProject;

        public JournalData(
                int total, int discovered, int completed, int active, long currencyBalance,
                int farmingReputation, int craftingReputation, int animalReputation,
                int tradeReputation, int monsterReputation,
                boolean hasStarreachRing, boolean hasMerchantSeal, boolean hasShepherdFlute,
                boolean hasApiaristSmoker, boolean hasSurveyorCompass, boolean hasCaravanLedger,
                boolean dailyActive, Text dailyTitle, Text dailyProgress,
                boolean weeklyActive, Text weeklyTitle, Text weeklyProgress,
                boolean storyActive, Text storyTitle, Text storyProgress,
                boolean pilgrimActive, Text pilgrimTitle, Text pilgrimProgress,
                boolean specialActive, Text specialTitle, Text specialProgress,
                boolean hasVillageLedgerProject, boolean hasApiaryCharterProject,
                boolean hasForgeCharterProject, boolean hasMarketCharterProject,
                boolean hasPastureCharterProject, boolean hasWatchBellProject,
                boolean hasCaravanYardProject
        ) {
            this.total = total;
            this.discovered = discovered;
            this.completed = completed;
            this.active = active;
            this.currencyBalance = currencyBalance;
            this.farmingReputation = farmingReputation;
            this.craftingReputation = craftingReputation;
            this.animalReputation = animalReputation;
            this.tradeReputation = tradeReputation;
            this.monsterReputation = monsterReputation;
            this.hasStarreachRing = hasStarreachRing;
            this.hasMerchantSeal = hasMerchantSeal;
            this.hasShepherdFlute = hasShepherdFlute;
            this.hasApiaristSmoker = hasApiaristSmoker;
            this.hasSurveyorCompass = hasSurveyorCompass;
            this.hasCaravanLedger = hasCaravanLedger;
            this.dailyActive = dailyActive;
            this.dailyTitle = safe(dailyTitle);
            this.dailyProgress = safe(dailyProgress);
            this.weeklyActive = weeklyActive;
            this.weeklyTitle = safe(weeklyTitle);
            this.weeklyProgress = safe(weeklyProgress);
            this.storyActive = storyActive;
            this.storyTitle = safe(storyTitle);
            this.storyProgress = safe(storyProgress);
            this.pilgrimActive = pilgrimActive;
            this.pilgrimTitle = safe(pilgrimTitle);
            this.pilgrimProgress = safe(pilgrimProgress);
            this.specialActive = specialActive;
            this.specialTitle = safe(specialTitle);
            this.specialProgress = safe(specialProgress);
            this.hasVillageLedgerProject = hasVillageLedgerProject;
            this.hasApiaryCharterProject = hasApiaryCharterProject;
            this.hasForgeCharterProject = hasForgeCharterProject;
            this.hasMarketCharterProject = hasMarketCharterProject;
            this.hasPastureCharterProject = hasPastureCharterProject;
            this.hasWatchBellProject = hasWatchBellProject;
            this.hasCaravanYardProject = hasCaravanYardProject;
        }

        public boolean hasAnySpecialItem() {
            return hasStarreachRing || hasMerchantSeal || hasShepherdFlute || hasApiaristSmoker
                    || hasSurveyorCompass || hasCaravanLedger;
        }

        private static Text safe(Text value) {
            return value == null ? Text.empty() : value;
        }
    }

    private JournalData data;
    private Section section = Section.OVERVIEW;
    private String expandedCardId = "overview_progress";
    private int scrollOffset;
    private int scrollMax;
    private boolean closeNotified;
    private boolean navigating;

    public JournalScreen(JournalData data) {
        super(Text.translatable("screen.village-quest.journal.title"));
        this.data = data;
    }

    public void updateData(JournalData data) {
        this.data = data;
        clampScroll();
    }

    @Override
    protected void init() {
        this.closeNotified = false;
        this.navigating = false;
        this.scrollOffset = 0;
        ensureExpandedCard();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (this.client != null && this.client.options.inventoryKey.matchesKey(key)) {
            close();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void close() {
        if (!this.closeNotified && !this.navigating) {
            this.closeNotified = true;
            sendJournalToggle();
        }
        super.close();
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        VillageUiTheme.drawScreenShade(graphics, width, height);
        VillageUiTheme.drawPanelShadow(graphics, left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
        graphics.drawTexture(RenderPipelines.GUI_TEXTURED, BOARD_TEXTURE, left, top, 0.0f, 0.0f,
                WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
        drawHeader(graphics, left, top);
        drawTabs(graphics, left, top, mouseX, mouseY);
        drawCards(graphics, left, top, mouseX, mouseY);
        drawFooterButtons(graphics, left, top, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);

        if (shouldShowQuestMasterTutorial()) {
            int buttonX = left + WINDOW_WIDTH - 176;
            TutorialHintRenderer.drawHint(
                    graphics, textRenderer,
                    Text.translatable("screen.village-quest.journal.active.questmaster_button_tutorial"),
                    width, height, buttonX, top + BUTTON_Y, 78, BUTTON_HEIGHT,
                    TutorialHintRenderer.Placement.ABOVE, true,
                    (int) Math.round(Math.sin(System.currentTimeMillis() / 180.0d) * 2.0d)
            );
        }
    }

    private void drawHeader(DrawContext graphics, int left, int top) {
        String titleText = title.getString();
        graphics.drawText(textRenderer, titleText, left + (WINDOW_WIDTH - textRenderer.getWidth(titleText)) / 2,
                top + 14, INK, false);
        String sectionText = Text.translatable(section.key).getString();
        graphics.drawText(textRenderer, sectionText, left + CONTENT_X + 7, top + CONTENT_Y + 3, GOLD, false);
    }

    private void drawTabs(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        String[] icons = {"home", "quests", "trust", "social", "guide"};
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            Section candidate = sections[i];
            int x = left + TAB_X;
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            boolean selected = candidate == section;
            boolean hovered = within(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            VillageUiTheme.drawTab(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, selected, hovered);
            VillageUiTheme.drawIcon(graphics, VillageUiTheme.icon(icons[i]),
                    x + (TAB_WIDTH - 21) / 2, y + (TAB_HEIGHT - 21) / 2, 21);
            if (hovered) {
                graphics.drawTooltip(textRenderer, Text.translatable(candidate.key), mouseX, mouseY);
            }
        }
    }

    private void drawCards(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        List<JournalCard> cards = cardsForSection();
        int viewportX = left + CONTENT_X + 5;
        int viewportY = top + CONTENT_TOP;
        int viewportWidth = CONTENT_WIDTH - 10;
        int cardWidth = viewportWidth - 5;
        int viewportHeight = CONTENT_BOTTOM - CONTENT_TOP;
        int contentHeight = contentHeight(cards, cardWidth);
        scrollMax = Math.max(0, contentHeight - viewportHeight);
        clampScroll();

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        int y = viewportY - scrollOffset;
        for (JournalCard card : cards) {
            int cardHeight = cardHeight(card, cardWidth);
            if (y + cardHeight >= viewportY && y <= viewportY + viewportHeight) {
                drawCard(graphics, card, viewportX, y, cardWidth, cardHeight, mouseX, mouseY);
            }
            y += cardHeight + CARD_GAP;
        }
        graphics.disableScissor();
        drawScrollBar(graphics, viewportX + viewportWidth - 3, viewportY, viewportHeight, contentHeight);
    }

    private void drawCard(DrawContext graphics, JournalCard card, int x, int y, int cardWidth,
                          int cardHeight, int mouseX, int mouseY) {
        boolean expanded = card.id().equals(expandedCardId);
        boolean hovered = within(mouseX, mouseY, x, y, cardWidth, cardHeight);
        VillageUiTheme.drawCard(graphics, x, y, cardWidth, cardHeight, hovered, expanded);
        graphics.fill(x + 6, y + 6, x + 9, y + cardHeight - 6, card.accent());
        VillageUiTheme.drawStringScaled(graphics, textRenderer,
                compact(card.title().getString(), cardWidth - CARD_TEXT_INSET - 28, CARD_TITLE_SCALE),
                x + CARD_TEXT_INSET, y + 6, INK, CARD_TITLE_SCALE);
        VillageUiTheme.drawStringScaled(graphics, textRenderer,
                compact(card.subtitle().getString(), cardWidth - CARD_TEXT_INSET - 28, CARD_BODY_SCALE),
                x + CARD_TEXT_INSET, y + 18, MUTED, CARD_BODY_SCALE);
        VillageUiTheme.blitScaled(graphics,
                VillageUiTheme.control(expanded ? "chevron_up" : "chevron_down"),
                x + cardWidth - 20, y + 7, 12, 12, 16, 16);
        if (card.cancelAction() >= 0) {
            VillageUiTheme.blitScaled(graphics, VillageUiTheme.control("close"),
                    x + cardWidth - 38, y + 5, 15, 15, 24, 24);
        }

        if (!expanded) {
            return;
        }
        int lineY = y + CARD_COLLAPSED_HEIGHT;
        for (String line : compactDetails(card, cardWidth)) {
            VillageUiTheme.drawStringScaled(graphics, textRenderer, line,
                    x + CARD_TEXT_INSET, lineY, BODY, CARD_BODY_SCALE);
            lineY += CARD_DETAIL_STEP;
        }
    }

    private void drawFooterButtons(DrawContext graphics, int left, int top, int mouseX, int mouseY) {
        int doneX = left + WINDOW_WIDTH - 92;
        int questMasterX = doneX - 84;
        int mapX = questMasterX - 58;
        if (data.hasCaravanLedger) {
            drawButton(graphics, mapX, top + BUTTON_Y, 52,
                    Text.translatable("screen.village-quest.journal.v2.button.map").getString(),
                    within(mouseX, mouseY, mapX, top + BUTTON_Y, 52, BUTTON_HEIGHT));
        }
        drawButton(graphics, questMasterX, top + BUTTON_Y, 78,
                Text.translatable("screen.village-quest.journal.active.questmaster_button").getString(),
                within(mouseX, mouseY, questMasterX, top + BUTTON_Y, 78, BUTTON_HEIGHT));
        drawButton(graphics, doneX, top + BUTTON_Y, 78,
                Text.translatable("screen.village-quest.journal.done").getString(),
                within(mouseX, mouseY, doneX, top + BUTTON_Y, 78, BUTTON_HEIGHT));
    }

    private void drawButton(DrawContext graphics, int x, int y, int buttonWidth, String label, boolean hovered) {
        VillageUiTheme.drawButton(graphics, textRenderer, x, y, buttonWidth, BUTTON_HEIGHT,
                label, true, hovered, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            int y = top + TAB_Y + i * (TAB_HEIGHT + TAB_GAP);
            if (within(mouseX, mouseY, left + TAB_X, y, TAB_WIDTH, TAB_HEIGHT)) {
                section = sections[i];
                scrollOffset = 0;
                expandedCardId = "";
                ensureExpandedCard();
                playClick();
                return true;
            }
        }

        int doneX = left + WINDOW_WIDTH - 92;
        int questMasterX = doneX - 84;
        int mapX = questMasterX - 58;
        if (data.hasCaravanLedger && within(mouseX, mouseY, mapX, top + BUTTON_Y, 52, BUTTON_HEIGHT)) {
            openRouteMap();
            return true;
        }
        if (within(mouseX, mouseY, questMasterX, top + BUTTON_Y, 78, BUTTON_HEIGHT)) {
            summonQuestMasterFromJournal();
            return true;
        }
        if (within(mouseX, mouseY, doneX, top + BUTTON_Y, 78, BUTTON_HEIGHT)) {
            close();
            return true;
        }

        List<JournalCard> cards = cardsForSection();
        int cardX = left + CONTENT_X + 5;
        int cardWidth = CONTENT_WIDTH - 15;
        int y = top + CONTENT_TOP - scrollOffset;
        for (JournalCard card : cards) {
            int height = cardHeight(card, cardWidth);
            if (within(mouseX, mouseY, cardX, y, cardWidth, height)) {
                if (card.cancelAction() >= 0
                        && within(mouseX, mouseY, cardX + cardWidth - 40, y + 3, 18, 19)) {
                    ClientPlayNetworking.send(new JournalActionPayload(card.cancelAction()));
                    playClick();
                    return true;
                }
                expandedCardId = card.id().equals(expandedCardId) ? "" : card.id();
                clampScroll();
                playPageTurn();
                return true;
            }
            y += height + CARD_GAP;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        if (scrollMax > 0 && within((int) mouseX, (int) mouseY,
                left + CONTENT_X, top + CONTENT_TOP, CONTENT_WIDTH, CONTENT_BOTTOM - CONTENT_TOP)) {
            scrollOffset -= (int) Math.signum(verticalAmount) * 22;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private List<JournalCard> cardsForSection() {
        return switch (section) {
            case OVERVIEW -> overviewCards();
            case QUESTS -> activeQuestCards();
            case REPUTATION -> reputationCards();
            case COLLECTION -> collectionCards();
            case GUIDE -> guideCards();
        };
    }

    private List<JournalCard> overviewCards() {
        int reputationTotal = reputationTotal();
        return List.of(
                card("overview_progress", "screen.village-quest.journal.v2.overview.progress",
                        Text.translatable("screen.village-quest.journal.v2.overview.progress_short",
                                data.completed, data.discovered),
                        List.of(
                                Text.translatable("screen.village-quest.journal.summary.total", data.total),
                                Text.translatable("screen.village-quest.journal.summary.discovered", data.discovered),
                                Text.translatable("screen.village-quest.journal.summary.active", data.active),
                                Text.translatable("screen.village-quest.journal.summary.completed", data.completed)
                        ), TEAL),
                card("overview_village", "screen.village-quest.journal.v2.overview.village",
                        Text.translatable("screen.village-quest.journal.summary.balance",
                                CurrencyService.formatBalance(data.currencyBalance)),
                        List.of(
                                Text.translatable("screen.village-quest.journal.summary.reputation", reputationTotal),
                                Text.translatable("screen.village-quest.journal.summary.projects", completedProjectCount())
                        ), GOLD),
                card("overview_next", "screen.village-quest.journal.v2.overview.next",
                        Text.translatable("screen.village-quest.journal.v2.overview.next_short"),
                        List.of(
                                Text.translatable("screen.village-quest.journal.v2.overview.next_body"),
                                Text.translatable(data.hasCaravanLedger
                                        ? "screen.village-quest.journal.v2.overview.routes_ready"
                                        : "screen.village-quest.journal.v2.overview.routes_locked")
                        ), BLUE)
        );
    }

    private List<JournalCard> activeQuestCards() {
        List<JournalCard> cards = new ArrayList<>();
        addActiveCard(cards, data.dailyActive, "daily", "screen.village-quest.journal.active.daily",
                data.dailyTitle, data.dailyProgress, "screen.village-quest.journal.active.daily_hint",
                BLUE, JournalActionPayload.ACTION_CANCEL_DAILY);
        addActiveCard(cards, data.weeklyActive, "weekly", "screen.village-quest.journal.active.weekly",
                data.weeklyTitle, data.weeklyProgress, "screen.village-quest.journal.active.weekly_hint",
                GOLD, JournalActionPayload.ACTION_CANCEL_WEEKLY);
        addActiveCard(cards, data.storyActive, "story", "screen.village-quest.journal.active.story",
                data.storyTitle, data.storyProgress, "screen.village-quest.journal.v2.active.story_hint",
                GREEN, -1);
        addActiveCard(cards, data.pilgrimActive, "pilgrim", "screen.village-quest.journal.active.pilgrim",
                data.pilgrimTitle, data.pilgrimProgress, "screen.village-quest.journal.v2.active.pilgrim_hint",
                0xFF9B6B34, -1);
        addActiveCard(cards, data.specialActive, "special", "screen.village-quest.journal.active.special",
                data.specialTitle, data.specialProgress, "screen.village-quest.journal.v2.active.special_hint",
                PURPLE, -1);
        if (cards.isEmpty()) {
            cards.add(card("active_none", "screen.village-quest.journal.active.none",
                    Text.translatable("screen.village-quest.journal.active.none_hint"),
                    List.of(Text.translatable("screen.village-quest.journal.v2.active.none_body")), MUTED));
        }
        return cards;
    }

    private void addActiveCard(List<JournalCard> cards, boolean active, String id, String labelKey,
                               Text title, Text progress, String hintKey, int accent, int cancelAction) {
        if (!active) {
            return;
        }
        List<Text> details = new ArrayList<>();
        if (!progress.getString().isBlank()) {
            details.add(progress);
        }
        details.add(Text.translatable(hintKey));
        cards.add(new JournalCard(id, title.getString().isBlank() ? Text.translatable(labelKey) : title,
                Text.translatable(labelKey), List.copyOf(details), accent, cancelAction));
    }

    private List<JournalCard> reputationCards() {
        List<JournalCard> cards = new ArrayList<>();
        addReputationCard(cards, ReputationService.ReputationTrack.FARMING, data.farmingReputation, GREEN);
        addReputationCard(cards, ReputationService.ReputationTrack.CRAFTING, data.craftingReputation, GOLD);
        addReputationCard(cards, ReputationService.ReputationTrack.ANIMALS, data.animalReputation, TEAL);
        addReputationCard(cards, ReputationService.ReputationTrack.TRADE, data.tradeReputation, BLUE);
        addReputationCard(cards, ReputationService.ReputationTrack.MONSTER_HUNTING, data.monsterReputation, RED);
        return cards;
    }

    private void addReputationCard(List<JournalCard> cards, ReputationService.ReputationTrack track,
                                   int value, int accent) {
        Text rank = Text.translatable(ReputationService.rankFor(value).translationKey());
        cards.add(new JournalCard("rep_" + track.name(), Text.translatable(track.translationKey()),
                Text.translatable("screen.village-quest.journal.v2.reputation.short", value, rank),
                List.of(Text.literal(storyAwareNextUnlockLine(track, value))), accent, -1));
    }

    private List<JournalCard> collectionCards() {
        List<JournalCard> cards = new ArrayList<>();
        List<ProjectEntry> projects = List.of(
                new ProjectEntry("quest.village-quest.project.village_ledger", data.hasVillageLedgerProject),
                new ProjectEntry("quest.village-quest.project.apiary_charter", data.hasApiaryCharterProject),
                new ProjectEntry("quest.village-quest.project.forge_charter", data.hasForgeCharterProject),
                new ProjectEntry("quest.village-quest.project.market_charter", data.hasMarketCharterProject),
                new ProjectEntry("quest.village-quest.project.pasture_charter", data.hasPastureCharterProject),
                new ProjectEntry("quest.village-quest.project.watch_bell", data.hasWatchBellProject),
                new ProjectEntry("quest.village-quest.project.caravan_yard", data.hasCaravanYardProject)
        );
        int index = 0;
        for (ProjectEntry project : projects) {
            List<Text> details = new ArrayList<>();
            details.add(Text.translatable(project.keyPrefix() + ".description"));
            details.add(Text.translatable(project.keyPrefix() + ".effect"));
            if (project.unlocked()) {
                details.add(Text.translatable(project.keyPrefix() + ".memory"));
            }
            cards.add(new JournalCard("project_" + index++, Text.translatable(project.keyPrefix() + ".title"),
                    Text.translatable(project.unlocked()
                            ? "screen.village-quest.journal.projects.built"
                            : "screen.village-quest.journal.projects.locked"),
                    List.copyOf(details), project.unlocked() ? GREEN : MUTED, -1));
        }
        for (SpecialItemEntry item : ownedSpecialItems()) {
            cards.add(new JournalCard("item_" + item.nameKey(), Text.translatable(item.nameKey()),
                    Text.translatable("screen.village-quest.journal.v2.collection.owned"),
                    List.of(Text.translatable(item.loreKey())), PURPLE, -1));
        }
        return cards;
    }

    private List<SpecialItemEntry> ownedSpecialItems() {
        List<SpecialItemEntry> items = new ArrayList<>();
        if (data.hasStarreachRing) items.add(new SpecialItemEntry("item.village-quest.starreach_ring", "item.village-quest.starreach_ring.lore"));
        if (data.hasMerchantSeal) items.add(new SpecialItemEntry("item.village-quest.merchant_seal", "item.village-quest.merchant_seal.lore"));
        if (data.hasShepherdFlute) items.add(new SpecialItemEntry("item.village-quest.shepherd_flute", "item.village-quest.shepherd_flute.lore"));
        if (data.hasApiaristSmoker) items.add(new SpecialItemEntry("item.village-quest.apiarists_smoker", "item.village-quest.apiarists_smoker.lore"));
        if (data.hasSurveyorCompass) items.add(new SpecialItemEntry("item.village-quest.surveyors_compass", "screen.village-quest.journal.relics.surveyors_compass"));
        if (data.hasCaravanLedger) items.add(new SpecialItemEntry("item.village-quest.caravan_ledger", "item.village-quest.caravan_ledger.lore"));
        return items;
    }

    private List<JournalCard> guideCards() {
        return List.of(
                guide("guide_start", "start", GREEN),
                guide("guide_quests", "quests", GOLD),
                guide("guide_routes", "routes", TEAL),
                guide("guide_controls", "controls", BLUE)
        );
    }

    private JournalCard guide(String id, String suffix, int accent) {
        return new JournalCard(id,
                Text.translatable("screen.village-quest.journal.v2.guide." + suffix + ".title"),
                Text.translatable("screen.village-quest.journal.v2.guide." + suffix + ".short"),
                List.of(Text.translatable("screen.village-quest.journal.v2.guide." + suffix + ".body")),
                accent, -1);
    }

    private JournalCard card(String id, String titleKey, Text subtitle, List<Text> details, int accent) {
        return new JournalCard(id, Text.translatable(titleKey), subtitle, details, accent, -1);
    }

    private int contentHeight(List<JournalCard> cards, int width) {
        int result = 0;
        for (JournalCard card : cards) {
            result += cardHeight(card, width) + CARD_GAP;
        }
        return Math.max(0, result - CARD_GAP);
    }

    private int cardHeight(JournalCard card, int width) {
        if (!card.id().equals(expandedCardId)) {
            return CARD_COLLAPSED_HEIGHT;
        }
        return CARD_COLLAPSED_HEIGHT + 6 + compactDetails(card, width).size() * CARD_DETAIL_STEP;
    }

    private List<String> compactDetails(JournalCard card, int cardWidth) {
        int renderedWidth = Math.max(12, cardWidth - CARD_TEXT_INSET - 18);
        int unscaledWidth = Math.max(1, (int) Math.floor(renderedWidth / CARD_BODY_SCALE));
        return wrappedDetails(card, unscaledWidth);
    }

    private String compact(String text, int renderedWidth, float scale) {
        return VillageUiTheme.ellipsize(textRenderer, text,
                Math.max(1, (int) Math.floor(renderedWidth / scale)));
    }

    private List<String> wrappedDetails(JournalCard card, int width) {
        List<String> lines = new ArrayList<>();
        for (Text detail : card.details()) {
            lines.addAll(wrapText(detail.getString(), width));
        }
        return lines;
    }

    private void ensureExpandedCard() {
        List<JournalCard> cards = cardsForSection();
        if (cards.stream().noneMatch(card -> card.id().equals(expandedCardId))) {
            expandedCardId = cards.isEmpty() ? "" : cards.getFirst().id();
        }
    }

    private void drawScrollBar(DrawContext graphics, int x, int y, int height, int contentHeight) {
        VillageUiTheme.drawScrollBar(graphics, x - 2, y, height,
                height, contentHeight, scrollOffset, scrollMax);
    }

    private void drawFrame(DrawContext graphics, int x, int y, int frameWidth, int frameHeight, int fill) {
        graphics.fill(x, y, x + frameWidth, y + frameHeight, FRAME_DARK);
        graphics.fill(x + 1, y + 1, x + frameWidth - 1, y + frameHeight - 1, FRAME_LIGHT);
        graphics.fill(x + 2, y + 2, x + frameWidth - 2, y + frameHeight - 2, fill);
    }

    private void openRouteMap() {
        if (client == null || client.player == null) return;
        navigating = true;
        client.player.networkHandler.sendChatCommand("vq routes");
        super.close();
    }

    private void summonQuestMasterFromJournal() {
        if (client == null || client.player == null) return;
        InventoryJournalTutorialState.markQuestMasterButtonHintSeen();
        navigating = true;
        client.player.networkHandler.sendChatCommand("vq questmaster");
        super.close();
    }

    private void sendJournalToggle() {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand("vq journal");
        }
    }

    private boolean shouldShowQuestMasterTutorial() {
        return section == Section.OVERVIEW && InventoryJournalTutorialState.shouldShowQuestMasterButtonHint();
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void playPageTurn() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
        }
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, scrollMax)));
    }

    private int reputationTotal() {
        return data.farmingReputation + data.craftingReputation + data.animalReputation
                + data.tradeReputation + data.monsterReputation;
    }

    private int completedProjectCount() {
        int count = 0;
        if (data.hasVillageLedgerProject) count++;
        if (data.hasApiaryCharterProject) count++;
        if (data.hasForgeCharterProject) count++;
        if (data.hasMarketCharterProject) count++;
        if (data.hasPastureCharterProject) count++;
        if (data.hasWatchBellProject) count++;
        if (data.hasCaravanYardProject) count++;
        return count;
    }

    private String storyAwareNextUnlockLine(ReputationService.ReputationTrack track, int value) {
        RelicQuestProgressionService.RelicUnlockPath path = RelicQuestProgressionService.pathFor(track);
        ReputationService.ReputationUnlock nextUnlock = ReputationService.nextReputationUnlock(track, value);
        boolean storyMet = hasStoryProjectForTrack(track);
        if (path != null && !storyMet) {
            String storyTitle = Text.translatable("quest.village-quest.story."
                    + path.requiredStoryArc().id() + ".title").getString();
            if (nextUnlock != null && nextUnlock.requiredReputation() >= path.requiredReputation()) {
                return Text.translatable("screen.village-quest.journal.reputation.next_unlock_story",
                        Text.translatable(nextUnlock.titleKey()).getString(), storyTitle,
                        path.requiredReputation()).getString();
            }
            if (nextUnlock == null) {
                return Text.translatable("screen.village-quest.journal.reputation.story_required",
                        Text.translatable(path.titleKey()).getString(), storyTitle).getString();
            }
        }
        if (nextUnlock == null) {
            return Text.translatable("screen.village-quest.journal.reputation.all_unlocked").getString();
        }
        return Text.translatable("screen.village-quest.journal.reputation.next_unlock",
                Text.translatable(nextUnlock.titleKey()).getString(),
                nextUnlock.requiredReputation()).getString();
    }

    private boolean hasStoryProjectForTrack(ReputationService.ReputationTrack track) {
        return switch (track) {
            case FARMING -> data.hasApiaryCharterProject;
            case CRAFTING -> data.hasForgeCharterProject;
            case ANIMALS -> data.hasPastureCharterProject;
            case TRADE -> data.hasMarketCharterProject;
            case MONSTER_HUNTING -> data.hasWatchBellProject;
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textRenderer.getWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private String ellipsize(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        String suffix = "…";
        String value = text;
        while (!value.isEmpty() && textRenderer.getWidth(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private static boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
