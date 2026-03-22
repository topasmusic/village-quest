package de.quest.client.screen;

import de.quest.economy.CurrencyService;
import de.quest.reputation.ReputationService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.Click;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import de.quest.network.Payloads.JournalActionPayload;

import java.util.ArrayList;
import java.util.List;

public class JournalScreen extends Screen {
    private static final float BASE_TEXT_SCALE = 0.7f;
    private static final float HEADER_TEXT_SCALE = BASE_TEXT_SCALE * 1.3f;
    private static final float BODY_TEXT_SCALE = BASE_TEXT_SCALE * 0.7f;
    private static final Identifier BOOK_TEXTURE = Identifier.of("minecraft", "textures/gui/book.png");
    private static final Identifier PAGE_BACKWARD = Identifier.of("minecraft", "widget/page_backward");
    private static final Identifier PAGE_BACKWARD_HIGHLIGHT = Identifier.of("minecraft", "widget/page_backward_highlighted");
    private static final Identifier PAGE_FORWARD = Identifier.of("minecraft", "widget/page_forward");
    private static final Identifier PAGE_FORWARD_HIGHLIGHT = Identifier.of("minecraft", "widget/page_forward_highlighted");
    private static final int PAGE_WIDTH = 176;
    private static final int PAGE_HEIGHT = 176;
    private static final int CONTENT_Y_OFFSET = 24;
    private static final int CONTENT_WIDTH = 104;
    private static final int CONTENT_HEIGHT = 118;
    private static final int CONTENT_X_OFFSET = 43;
    private static final int ARROW_WIDTH = 23;
    private static final int ARROW_HEIGHT = 13;
    private static final int DONE_BUTTON_WIDTH = 80;
    private static final int BUTTON_ROW_HEIGHT = 20;
    private static final String CANCEL_TEXT = "X";
    private static final int CANCEL_COLOR = 0xFFFF4B4B;

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
        public final boolean dailyActive;
        public final Text dailyTitle;
        public final Text dailyProgress;
        public final boolean weeklyActive;
        public final Text weeklyTitle;
        public final Text weeklyProgress;
        public final boolean specialActive;
        public final Text specialTitle;
        public final Text specialProgress;

        public JournalData(
                int total,
                int discovered,
                int completed,
                int active,
                long currencyBalance,
                int farmingReputation,
                int craftingReputation,
                int animalReputation,
                int tradeReputation,
                int monsterReputation,
                boolean hasStarreachRing,
                boolean hasMerchantSeal,
                boolean hasShepherdFlute,
                boolean hasApiaristSmoker,
                boolean hasSurveyorCompass,
                boolean dailyActive,
                Text dailyTitle,
                Text dailyProgress,
                boolean weeklyActive,
                Text weeklyTitle,
                Text weeklyProgress,
                boolean specialActive,
                Text specialTitle,
                Text specialProgress
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
            this.dailyActive = dailyActive;
            this.dailyTitle = dailyTitle == null ? Text.empty() : dailyTitle;
            this.dailyProgress = dailyProgress == null ? Text.empty() : dailyProgress;
            this.weeklyActive = weeklyActive;
            this.weeklyTitle = weeklyTitle == null ? Text.empty() : weeklyTitle;
            this.weeklyProgress = weeklyProgress == null ? Text.empty() : weeklyProgress;
            this.specialActive = specialActive;
            this.specialTitle = specialTitle == null ? Text.empty() : specialTitle;
            this.specialProgress = specialProgress == null ? Text.empty() : specialProgress;
        }

        public boolean hasAnySpecialItem() {
            return hasStarreachRing
                    || hasMerchantSeal
                    || hasShepherdFlute
                    || hasApiaristSmoker
                    || hasSurveyorCompass;
        }
    }

    private static class PageLine {
        private final String text;
        private final int color;
        private final float scale;
        private final int cancelAction;

        private PageLine(String text, int color, float scale, int cancelAction) {
            this.text = text;
            this.color = color;
            this.scale = scale;
            this.cancelAction = cancelAction;
        }
    }

    private static class Page {
        private final List<PageLine> lines = new ArrayList<>();

        private void addLine(String text, int color, float scale) {
            this.lines.add(new PageLine(text, color, scale, -1));
        }

        private void addLine(String text, int color, float scale, int cancelAction) {
            this.lines.add(new PageLine(text, color, scale, cancelAction));
        }
    }

    private static class CancelHit {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final int action;

        private CancelHit(int x, int y, int w, int h, int action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    private record SpecialItemEntry(String nameKey, String loreKey) {}

    private JournalData data;
    private List<Page> pages = List.of();
    private int pageIndex = 0;
    private ButtonWidget doneButton;
    private final List<CancelHit> cancelHits = new ArrayList<>();

    public JournalScreen(JournalData data) {
        super(Text.translatable("screen.village-quest.journal.title"));
        this.data = data;
    }

    public void updateData(JournalData data) {
        this.data = data;
        buildPages();
    }

    @Override
    protected void init() {
        buildPages();

        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;
        int buttonY = getButtonRowY(top);
        int centerX = left + PAGE_WIDTH / 2;

        this.doneButton = ButtonWidget.builder(Text.translatable("screen.village-quest.journal.done"), b -> this.close())
                .dimensions(centerX - DONE_BUTTON_WIDTH / 2, buttonY, DONE_BUTTON_WIDTH, BUTTON_ROW_HEIGHT)
                .build();

        addDrawableChild(this.doneButton);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        sendJournalToggle();
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, BOOK_TEXTURE, left, top, 0.0f, 0.0f, PAGE_WIDTH, PAGE_HEIGHT, 256, 256);

        if (!pages.isEmpty()) {
            cancelHits.clear();
            drawPage(context, left, top, pages.get(pageIndex));
            drawPageIndicator(context, left, top);
            drawArrows(context, left, top, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPage(DrawContext context, int left, int top, Page page) {
        int x = left + CONTENT_X_OFFSET;
        int y = top + CONTENT_Y_OFFSET;
        int rightEdge = left + CONTENT_X_OFFSET + CONTENT_WIDTH;

        for (PageLine line : page.lines) {
            if (y > top + CONTENT_Y_OFFSET + CONTENT_HEIGHT) {
                break;
            }
            if (!line.text.isEmpty()) {
                var matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.scale(line.scale, line.scale);
                context.drawText(this.textRenderer, line.text, Math.round(x / line.scale), Math.round(y / line.scale), line.color, false);
                matrices.popMatrix();
                addCancelHit(context, line, x, y, rightEdge);
            }
            y += Math.round((this.textRenderer.fontHeight + 2) * line.scale);
        }
    }

    private void addCancelHit(DrawContext context, PageLine line, int x, int y, int rightEdge) {
        if (line.cancelAction < 0) {
            return;
        }

        int textWidth = Math.round(this.textRenderer.getWidth(CANCEL_TEXT) * line.scale);
        int textHeight = Math.round(this.textRenderer.fontHeight * line.scale);
        int cancelX = rightEdge - textWidth;
        int cancelY = y;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(line.scale, line.scale);
        context.drawText(this.textRenderer, CANCEL_TEXT, Math.round(cancelX / line.scale), Math.round(cancelY / line.scale), CANCEL_COLOR, false);
        matrices.popMatrix();

        cancelHits.add(new CancelHit(cancelX, cancelY, textWidth, textHeight, line.cancelAction));
    }

    private void drawPageIndicator(DrawContext context, int left, int top) {
        if (pages.size() <= 1) {
            return;
        }
        String label = Text.translatable("screen.village-quest.journal.page", pageIndex + 1, pages.size()).getString();
        int textWidth = this.textRenderer.getWidth(label);
        float scale = 0.5f;
        int x = left + PAGE_WIDTH - 12 - Math.round(textWidth * scale) - 15;
        int y = top + 8 + 15 - 8;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        context.drawText(this.textRenderer, label, Math.round(x / scale), Math.round(y / scale), 0xFF9E9E9E, false);
        matrices.popMatrix();
    }

    private void drawArrows(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int buttonY = top + PAGE_HEIGHT - 14 - 15 + 8;
        int prevX = left + 20 + 15;
        int nextX = left + PAGE_WIDTH - 20 - ARROW_WIDTH - 15;
        boolean canPrev = pageIndex > 0;
        boolean canNext = pageIndex + 1 < pages.size();

        if (canPrev) {
            Identifier sprite = isHover(mouseX, mouseY, prevX, buttonY, ARROW_WIDTH, ARROW_HEIGHT)
                    ? PAGE_BACKWARD_HIGHLIGHT
                    : PAGE_BACKWARD;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, prevX, buttonY, ARROW_WIDTH, ARROW_HEIGHT);
        }
        if (canNext) {
            Identifier sprite = isHover(mouseX, mouseY, nextX, buttonY, ARROW_WIDTH, ARROW_HEIGHT)
                    ? PAGE_FORWARD_HIGHLIGHT
                    : PAGE_FORWARD;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, nextX, buttonY, ARROW_WIDTH, ARROW_HEIGHT);
        }
    }

    private boolean isHover(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;
        int buttonY = top + PAGE_HEIGHT - 14 - 15 + 8;
        int prevX = left + 20 + 15;
        int nextX = left + PAGE_WIDTH - 20 - ARROW_WIDTH - 15;

        if (click.button() == 0) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();
            for (CancelHit hit : cancelHits) {
                if (isHover(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    ClientPlayNetworking.send(new JournalActionPayload(hit.action));
                    return true;
                }
            }
            if (pageIndex > 0 && isHover(mouseX, mouseY, prevX, buttonY, ARROW_WIDTH, ARROW_HEIGHT)) {
                pageIndex--;
                playPageTurnSound();
                return true;
            }
            if (pageIndex + 1 < pages.size() && isHover(mouseX, mouseY, nextX, buttonY, ARROW_WIDTH, ARROW_HEIGHT)) {
                pageIndex++;
                playPageTurnSound();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void buildPages() {
        if (this.textRenderer == null || this.data == null) {
            return;
        }

        List<Page> result = new ArrayList<>();
        result.add(buildActivePage());
        result.add(buildCommandPageOne());
        result.add(buildCommandPageTwo());
        result.add(buildFlowPage());
        result.addAll(buildReputationPages());
        if (data.hasAnySpecialItem()) {
            result.addAll(buildSpecialItemsPages());
        }
        result.add(buildSummaryPage());
        this.pages = result;
        if (pageIndex >= pages.size()) {
            pageIndex = Math.max(0, pages.size() - 1);
        }
    }

    private Page buildSummaryPage() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int green = 0xFF6BCB6B;
        int blue = 0xFF6FA9FF;
        int gold = 0xFFD7B46A;

        page.addLine(Text.translatable("screen.village-quest.journal.summary.header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);

        String story = Text.translatable("screen.village-quest.journal.summary.story").getString();

        for (String line : wrapText(story, getBodyWidth())) {
            page.addLine(line, gray, BODY_TEXT_SCALE);
        }

        page.addLine("", gray, BODY_TEXT_SCALE);
        addWrappedLine(page, gray, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.summary.total", data.total).getString());
        addWrappedLine(page, gray, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.summary.discovered", data.discovered).getString());
        addWrappedLine(page, blue, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.summary.active", data.active).getString());
        addWrappedLine(page, green, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.summary.completed", data.completed).getString());
        addWrappedLine(page, gold, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.summary.balance", CurrencyService.formatBalance(data.currencyBalance)).getString());
        addWrappedLine(page, gold, BODY_TEXT_SCALE, Text.translatable(
                "screen.village-quest.journal.summary.reputation",
                data.farmingReputation + data.craftingReputation + data.animalReputation + data.tradeReputation + data.monsterReputation
        ).getString());
        return page;
    }

    private Page buildCommandPageOne() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int blue = 0xFF6FA9FF;
        int gold = 0xFFD7B46A;

        page.addLine(Text.translatable("screen.village-quest.journal.commands.header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.commands.intro").getString());
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.commands.available").getString());
        addHelpLine(page, blue, "/questmaster", Text.translatable("screen.village-quest.journal.commands.questmaster").getString());
        addHelpLine(page, blue, "/wallet", Text.translatable("screen.village-quest.journal.commands.wallet").getString());
        addHelpLine(page, blue, "/reputation", Text.translatable("screen.village-quest.journal.commands.reputation").getString());
        addHelpLine(page, blue, "/journal", Text.translatable("screen.village-quest.journal.commands.journal").getString());

        return page;
    }

    private Page buildCommandPageTwo() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int blue = 0xFF6FA9FF;
        int gold = 0xFFD7B46A;

        page.addLine(Text.translatable("screen.village-quest.journal.commands.quest_header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.commands.quest_intro").getString());
        addHelpLine(page, blue, "/dailyquest accept", Text.translatable("screen.village-quest.journal.commands.accept").getString());
        addHelpLine(page, blue, "/questtracker", Text.translatable("screen.village-quest.journal.commands.questtracker").getString());
        addHelpLine(page, blue, "/questtracker on", Text.translatable("screen.village-quest.journal.commands.questtracker_on").getString());
        addHelpLine(page, blue, "/questtracker off", Text.translatable("screen.village-quest.journal.commands.questtracker_off").getString());
        return page;
    }

    private Page buildFlowPage() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int gold = 0xFFD7B46A;

        page.addLine(Text.translatable("screen.village-quest.journal.flow.header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.flow.1").getString());
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.flow.2").getString());
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.flow.3").getString());
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.flow.4").getString());
        addParagraph(page, gray, Text.translatable("screen.village-quest.journal.flow.5").getString());
        return page;
    }

    private List<Page> buildReputationPages() {
        List<Page> result = new ArrayList<>();
        Page firstPage = createReputationPageHeader();
        Page secondPage = createReputationPageHeader();
        int gray = 0xFF9E9E9E;
        int gold = 0xFFD7B46A;

        addParagraph(firstPage, gray, Text.translatable("screen.village-quest.journal.reputation.story").getString());
        addReputationBlock(firstPage, gray, ReputationService.ReputationTrack.FARMING, data.farmingReputation, 0xFF6BCB6B);
        addReputationBlock(firstPage, gray, ReputationService.ReputationTrack.CRAFTING, data.craftingReputation, 0xFFD7B46A);
        addReputationBlock(firstPage, gray, ReputationService.ReputationTrack.ANIMALS, data.animalReputation, 0xFF7FD6E6);

        addReputationBlock(secondPage, gray, ReputationService.ReputationTrack.TRADE, data.tradeReputation, 0xFF6FA9FF);
        addReputationBlock(secondPage, gray, ReputationService.ReputationTrack.MONSTER_HUNTING, data.monsterReputation, 0xFFD86A6A);
        addWrappedLine(secondPage, gold, BODY_TEXT_SCALE, Text.translatable(
                "screen.village-quest.journal.reputation.total",
                data.farmingReputation + data.craftingReputation + data.animalReputation + data.tradeReputation + data.monsterReputation
        ).getString());
        result.add(firstPage);
        result.add(secondPage);
        return result;
    }

    private List<Page> buildSpecialItemsPages() {
        List<SpecialItemEntry> entries = new ArrayList<>();
        if (data.hasStarreachRing) {
            entries.add(new SpecialItemEntry("item.village-quest.starreach_ring", "item.village-quest.starreach_ring.lore"));
        }
        if (data.hasMerchantSeal) {
            entries.add(new SpecialItemEntry("item.village-quest.merchant_seal", "item.village-quest.merchant_seal.lore"));
        }
        if (data.hasShepherdFlute) {
            entries.add(new SpecialItemEntry("item.village-quest.shepherd_flute", "item.village-quest.shepherd_flute.lore"));
        }
        if (data.hasApiaristSmoker) {
            entries.add(new SpecialItemEntry("item.village-quest.apiarists_smoker", "item.village-quest.apiarists_smoker.lore"));
        }
        if (data.hasSurveyorCompass) {
            entries.add(new SpecialItemEntry("item.village-quest.surveyors_compass", "screen.village-quest.journal.relics.surveyors_compass"));
        }

        List<Page> result = new ArrayList<>();
        Page currentPage = createSpecialItemsPage(true);

        for (SpecialItemEntry entry : entries) {
            if (pageHeight(currentPage) + specialItemEntryHeight(entry) > CONTENT_HEIGHT && hasSpecialItemContent(currentPage)) {
                result.add(currentPage);
                currentPage = createSpecialItemsPage(false);
            }
            addSpecialItemEntry(currentPage, 0xFFD7B46A, 0xFF9E9E9E, entry.nameKey(), entry.loreKey());
        }

        result.add(currentPage);
        return result;
    }

    private Page createReputationPageHeader() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int gold = 0xFFD7B46A;
        page.addLine(Text.translatable("screen.village-quest.journal.reputation.header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);
        return page;
    }

    private void addReputationBlock(Page page, int detailColor, ReputationService.ReputationTrack track, int value, int valueColor) {
        addWrappedLine(page, valueColor, BODY_TEXT_SCALE, Text.translatable(
                "screen.village-quest.journal.reputation.line",
                Text.translatable(track.translationKey()).getString(),
                value,
                Text.translatable(ReputationService.rankFor(value).translationKey()).getString()
        ).getString());

        if (!ReputationService.hasReputationUnlocks(track)) {
            addWrappedLine(page, detailColor, BODY_TEXT_SCALE, Text.translatable("text.village-quest.reputation.no_unlocks_yet").getString());
        } else {
            ReputationService.ReputationUnlock nextUnlock = ReputationService.nextReputationUnlock(track, value);
            if (nextUnlock == null) {
                addWrappedLine(page, 0xFF6BCB6B, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.reputation.all_unlocked").getString());
            } else {
                addWrappedLine(page, detailColor, BODY_TEXT_SCALE, Text.translatable(
                        "screen.village-quest.journal.reputation.next_unlock",
                        Text.translatable(nextUnlock.titleKey()).getString(),
                        nextUnlock.requiredReputation()
                ).getString());
            }
        }
        page.addLine("", detailColor, BODY_TEXT_SCALE);
    }

    private void addHelpLine(Page page, int commandColor, String command, String description) {
        addWrappedLine(page, commandColor, BODY_TEXT_SCALE, command);
        addWrappedLine(page, 0xFF9E9E9E, BODY_TEXT_SCALE, description);
        page.addLine("", 0xFF9E9E9E, BODY_TEXT_SCALE);
    }

    private void addParagraph(Page page, int color, String text) {
        addWrappedLine(page, color, BODY_TEXT_SCALE, text);
        page.addLine("", color, BODY_TEXT_SCALE);
    }

    private void addSpecialItemEntry(Page page, int titleColor, int detailColor, String nameKey, String loreKey) {
        addWrappedLine(page, titleColor, BODY_TEXT_SCALE, Text.translatable(nameKey).getString());
        addWrappedLine(page, detailColor, BODY_TEXT_SCALE, Text.translatable(loreKey).getString());
        page.addLine("", detailColor, BODY_TEXT_SCALE);
    }

    private Page createSpecialItemsPage(boolean includeStory) {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int gold = 0xFFD7B46A;
        page.addLine(Text.translatable("screen.village-quest.journal.relics.header").getString(), gold, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);
        if (includeStory) {
            addParagraph(page, gray, Text.translatable("screen.village-quest.journal.relics.story").getString());
        }
        return page;
    }

    private boolean hasSpecialItemContent(Page page) {
        return page.lines.size() > createSpecialItemsPage(true).lines.size();
    }

    private int specialItemEntryHeight(SpecialItemEntry entry) {
        return wrappedTextHeight(Text.translatable(entry.nameKey()).getString(), BODY_TEXT_SCALE)
                + wrappedTextHeight(Text.translatable(entry.loreKey()).getString(), BODY_TEXT_SCALE)
                + lineHeight(BODY_TEXT_SCALE);
    }

    private int wrappedTextHeight(String text, float scale) {
        List<String> wrapped = wrapText(text, getLineWidth(scale));
        int lines = Math.max(1, wrapped.size());
        return lines * lineHeight(scale);
    }

    private int lineHeight(float scale) {
        return Math.round((this.textRenderer.fontHeight + 2) * scale);
    }

    private int pageHeight(Page page) {
        int height = 0;
        for (PageLine line : page.lines) {
            height += lineHeight(line.scale);
        }
        return height;
    }

    private Page buildActivePage() {
        Page page = new Page();
        int gray = 0xFF9E9E9E;
        int blue = 0xFF6FA9FF;
        int green = 0xFF6BCB6B;
        int yellow = 0xFFE3C76A;

        page.addLine(Text.translatable("screen.village-quest.journal.active.header").getString(), yellow, HEADER_TEXT_SCALE);
        page.addLine("", gray, BODY_TEXT_SCALE);

        if (data.dailyActive) {
            page.addLine(Text.translatable("screen.village-quest.journal.active.daily").getString(), blue, BODY_TEXT_SCALE, JournalActionPayload.ACTION_CANCEL_DAILY);
            if (!data.dailyTitle.getString().isEmpty()) {
                addWrappedLine(page, blue, BODY_TEXT_SCALE, data.dailyTitle.getString());
            }
            if (!data.dailyProgress.getString().isEmpty()) {
                addWrappedLine(page, green, BODY_TEXT_SCALE, data.dailyProgress.getString());
            }
            addWrappedLine(page, gray, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.active.daily_hint").getString());
            page.addLine("", gray, BODY_TEXT_SCALE);
        }

        if (data.weeklyActive) {
            page.addLine(Text.translatable("screen.village-quest.journal.active.weekly").getString(), 0xFFD7B46A, BODY_TEXT_SCALE);
            if (!data.weeklyTitle.getString().isEmpty()) {
                addWrappedLine(page, 0xFFD7B46A, BODY_TEXT_SCALE, data.weeklyTitle.getString());
            }
            if (!data.weeklyProgress.getString().isEmpty()) {
                addWrappedLine(page, green, BODY_TEXT_SCALE, data.weeklyProgress.getString());
            }
            page.addLine("", gray, BODY_TEXT_SCALE);
        }

        if (data.specialActive) {
            page.addLine(Text.translatable("screen.village-quest.journal.active.special").getString(), 0xFFB67CFF, BODY_TEXT_SCALE);
            if (!data.specialTitle.getString().isEmpty()) {
                addWrappedLine(page, 0xFFB67CFF, BODY_TEXT_SCALE, data.specialTitle.getString());
            }
            if (!data.specialProgress.getString().isEmpty()) {
                addWrappedLine(page, green, BODY_TEXT_SCALE, data.specialProgress.getString());
            }
            page.addLine("", gray, BODY_TEXT_SCALE);
        }

        if (!data.dailyActive && !data.weeklyActive && !data.specialActive) {
            addWrappedLine(page, gray, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.active.none").getString());
            page.addLine("", gray, BODY_TEXT_SCALE);
            addWrappedLine(page, blue, BODY_TEXT_SCALE, Text.translatable("screen.village-quest.journal.active.none_hint").getString());
        }
        return page;
    }

    private int getBodyWidth() {
        return Math.round(CONTENT_WIDTH / BODY_TEXT_SCALE);
    }

    private int getLineWidth(float scale) {
        return Math.round(CONTENT_WIDTH / scale);
    }

    private void addWrappedLine(Page page, int color, float scale, String text) {
        List<String> wrapped = wrapText(text, getLineWidth(scale));
        if (wrapped.isEmpty()) {
            page.addLine("", color, scale);
            return;
        }
        for (String line : wrapped) {
            page.addLine(line, color, scale);
        }
    }

    private int getButtonRowY(int top) {
        return top + PAGE_HEIGHT + 4;
    }

    private void sendJournalToggle() {
        if (this.client == null || this.client.player == null) {
            return;
        }
        this.client.player.networkHandler.sendChatCommand("journal");
    }

    private void playPageTurnSound() {
        if (this.client == null) {
            return;
        }
        this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.replace("\n", " \n ").split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if ("\n".equals(word)) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                continue;
            }
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            String candidate = line + " " + word;
            if (this.textRenderer.getWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.append(' ').append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}

