package de.quest.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminJournalScreen extends Screen {
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
    private static final int CONTENT_WIDTH = 125;
    private static final int CONTENT_HEIGHT = 118;
    private static final int CONTENT_X_OFFSET = 40;
    private static final int ARROW_WIDTH = 23;
    private static final int ARROW_HEIGHT = 13;
    private static final int DONE_BUTTON_WIDTH = 80;
    private static final int BUTTON_ROW_HEIGHT = 20;

    private final List<String> rawLines;
    private List<List<RenderLine>> pages = List.of();
    private int pageIndex = 0;
    private ButtonWidget doneButton;
    private final List<LinkHit> linkHits = new ArrayList<>();
    private Map<String, Integer> markerPages = new HashMap<>();

    public AdminJournalScreen(List<String> lines) {
        super(Text.literal("Admin Journal"));
        this.rawLines = lines == null ? List.of() : List.copyOf(lines);
    }

    @Override
    protected void init() {
        buildPages();

        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;
        int buttonY = getButtonRowY(top);
        int centerX = left + PAGE_WIDTH / 2;

        this.doneButton = ButtonWidget.builder(Text.literal("Fertig"), b -> this.close())
                .dimensions(centerX - DONE_BUTTON_WIDTH / 2, buttonY, DONE_BUTTON_WIDTH, BUTTON_ROW_HEIGHT)
                .build();

        addDrawableChild(this.doneButton);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, BOOK_TEXTURE, left, top, 0.0f, 0.0f, PAGE_WIDTH, PAGE_HEIGHT, 256, 256);

        if (!pages.isEmpty()) {
            linkHits.clear();
            drawPage(context, left, top, pages.get(pageIndex));
            drawPageIndicator(context, left, top);
            drawArrows(context, left, top, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPage(DrawContext context, int left, int top, List<RenderLine> page) {
        int x = left + CONTENT_X_OFFSET;
        int y = top + CONTENT_Y_OFFSET;
        int maxY = top + CONTENT_Y_OFFSET + CONTENT_HEIGHT;

        for (RenderLine line : page) {
            if (y > maxY) {
                break;
            }
            int xOffset = 0;
            for (RenderSegment segment : line.segments) {
                String display = replacePlaceholders(segment.text);
                if (display.isEmpty()) {
                    continue;
                }
                boolean interactive = segment.actionType != null || line.linkId != null;
                int color = segment.color != null
                        ? segment.color
                        : (line.color != null ? line.color : (interactive ? 0xFF6FA9FF : 0xFF9E9E9E));
                float scale = line.scale;
                int drawX = x + xOffset;
                var matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.scale(scale, scale);
                context.drawText(this.textRenderer, display, Math.round(drawX / scale), Math.round(y / scale), color, false);
                matrices.popMatrix();
                int width = Math.round(this.textRenderer.getWidth(display) * scale);
                if (interactive) {
                    Integer target = markerPages.get(line.linkId);
                    linkHits.add(new LinkHit(
                            drawX,
                            y,
                            width,
                            Math.round(this.textRenderer.fontHeight * scale),
                            target,
                            segment.actionType,
                            segment.actionValue
                    ));
                }
                xOffset += width;
            }
            y += Math.round((this.textRenderer.fontHeight + 2) * line.scale);
        }
    }

    private void drawPageIndicator(DrawContext context, int left, int top) {
        if (pages.size() <= 1) {
            return;
        }
        String label = "Seite " + (pageIndex + 1) + " von " + pages.size();
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
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;
        int buttonY = top + PAGE_HEIGHT - 14 - 15 + 8;
        int prevX = left + 20 + 15;
        int nextX = left + PAGE_WIDTH - 20 - ARROW_WIDTH - 15;

        if (click.button() == 0) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();
            for (LinkHit hit : linkHits) {
                if (isHover(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    if (hit.actionType == ActionType.COMMAND) {
                        sendCommand(hit.actionValue);
                        return true;
                    }
                    if (hit.actionType == ActionType.SUGGEST) {
                        suggestCommand(hit.actionValue);
                        return true;
                    }
                    if (hit.actionType == ActionType.CONFIRM) {
                        confirmCommand(hit.actionValue);
                        return true;
                    }
                    if (hit.targetPage != null) {
                        pageIndex = hit.targetPage;
                        playPageTurnSound();
                        return true;
                    }
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

    private void playPageTurnSound() {
        if (this.client == null) {
            return;
        }
        this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
    }

    private void sendCommand(String command) {
        if (this.client == null || this.client.player == null || command == null || command.isBlank()) {
            return;
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        this.client.player.networkHandler.sendChatCommand(trimmed);
    }

    private void confirmCommand(String command) {
        if (this.client == null || command == null || command.isBlank()) {
            return;
        }
        Text title = Text.literal("NPC loeschen?");
        Text message = Text.literal("NPC und alle seine Einstellungen gehen verloren. Dieser Vorgang kann nicht rueckgaengig gemacht werden.");
        this.client.setScreen(new ConfirmDeleteScreen(this, title, message, confirmed -> {
            if (confirmed) {
                sendCommand(command);
                if (this.client != null) {
                    this.client.setScreen(this);
                }
            } else if (this.client != null) {
                this.client.setScreen(this);
            }
        }));
    }

    private void suggestCommand(String command) {
        if (this.client == null || command == null || command.isBlank()) {
            return;
        }
        String text = command.trim();
        if (!text.startsWith("/")) {
            text = "/" + text;
        }
        this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(text, false));
    }

    private void buildPages() {
        if (this.textRenderer == null) {
            return;
        }
        List<LineToken> tokens = new ArrayList<>();
        for (String line : rawLines) {
            if ("<<PAGE>>".equals(line)) {
                tokens.add(LineToken.pageBreak());
                continue;
            }
            if (line != null && line.startsWith("<<MARK:") && line.endsWith(">>")) {
                String id = line.substring(7, line.length() - 2).trim();
                tokens.add(LineToken.marker(id));
                continue;
            }
            String linkId = null;
            Integer color = null;
            float scale = BODY_TEXT_SCALE;
            String text = line == null ? "" : line;
            boolean parsing = true;
            while (parsing) {
                if (text.startsWith("[[SCALE:")) {
                    int end = text.indexOf("]]");
                    if (end > 8) {
                        String scaleValue = text.substring(8, end).trim();
                        scale = parseScale(scaleValue);
                        text = text.substring(end + 2);
                        continue;
                    }
                } else if (text.startsWith("[[COLOR:")) {
                    int end = text.indexOf("]]");
                    if (end > 8) {
                        String colorValue = text.substring(8, end).trim();
                        color = parseColor(colorValue);
                        text = text.substring(end + 2);
                        continue;
                    }
                } else if (text.startsWith("[[LINK:")) {
                    int end = text.indexOf("]]");
                    if (end > 7) {
                        linkId = text.substring(7, end);
                        text = text.substring(end + 2);
                        continue;
                    }
                }
                parsing = false;
            }
            tokens.add(LineToken.text(text == null ? "" : text, linkId, color, scale));
        }

        List<List<RenderLine>> result = new ArrayList<>();
        List<RenderLine> current = new ArrayList<>();
        Map<String, Integer> markers = new HashMap<>();
        int usedHeight = 0;

        for (LineToken token : tokens) {
            if (token.isPageBreak()) {
                if (!current.isEmpty()) {
                    result.add(current);
                    current = new ArrayList<>();
                    usedHeight = 0;
                }
                continue;
            }
            if (token.isMarker()) {
                if (!current.isEmpty()) {
                    result.add(current);
                    current = new ArrayList<>();
                    usedHeight = 0;
                }
                markers.putIfAbsent(token.markerId, result.size());
                continue;
            }

            String text = token.text == null ? "" : token.text;
            int lineHeight = Math.round((this.textRenderer.fontHeight + 2) * token.scale);
            if (text.isEmpty()) {
                if (usedHeight + lineHeight > CONTENT_HEIGHT && !current.isEmpty()) {
                    result.add(current);
                    current = new ArrayList<>();
                    usedHeight = 0;
                }
                current.add(new RenderLine(List.of(new RenderSegment("", null, null, null)), token.linkId, token.color, token.scale));
                usedHeight += lineHeight;
                continue;
            }

            ParsedSegments parsed = parseSegments(text);
            if (parsed.hasActions) {
                if (usedHeight + lineHeight > CONTENT_HEIGHT && !current.isEmpty()) {
                    result.add(current);
                    current = new ArrayList<>();
                    usedHeight = 0;
                }
                current.add(new RenderLine(parsed.segments, token.linkId, token.color, token.scale));
                usedHeight += lineHeight;
                continue;
            }

            int maxWidth = Math.round(CONTENT_WIDTH / token.scale);
            for (String wrapped : wrapText(text, maxWidth)) {
                if (usedHeight + lineHeight > CONTENT_HEIGHT && !current.isEmpty()) {
                    result.add(current);
                    current = new ArrayList<>();
                    usedHeight = 0;
                }
                current.add(new RenderLine(List.of(new RenderSegment(wrapped, null, null, null)), token.linkId, token.color, token.scale));
                usedHeight += lineHeight;
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }

        this.markerPages = markers;
        this.pages = result.isEmpty() ? List.of(List.of()) : result;
        if (pageIndex >= pages.size()) {
            pageIndex = Math.max(0, pages.size() - 1);
        }
    }

    private ParsedSegments parseSegments(String text) {
        List<RenderSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            segments.add(new RenderSegment("", null, null, null));
            return new ParsedSegments(segments, false);
        }
        int index = 0;
        boolean hasActions = false;
        while (index < text.length()) {
            int cmdIndex = text.indexOf("[[CMD:", index);
            int suggestIndex = text.indexOf("[[SUGGEST:", index);
            int confirmIndex = text.indexOf("[[CONFIRM:", index);
            int nextIndex = nextTagIndex(cmdIndex, suggestIndex, confirmIndex);
            if (nextIndex < 0) {
                segments.add(createSegment(text.substring(index), null, null));
                break;
            }
            if (nextIndex > index) {
                segments.add(createSegment(text.substring(index, nextIndex), null, null));
            }
            boolean isSuggest = nextIndex == suggestIndex
                    && (cmdIndex < 0 || suggestIndex < cmdIndex)
                    && (confirmIndex < 0 || suggestIndex < confirmIndex);
            boolean isConfirm = nextIndex == confirmIndex
                    && (cmdIndex < 0 || confirmIndex < cmdIndex)
                    && (suggestIndex < 0 || confirmIndex < suggestIndex);
            int tagStart = nextIndex;
            int payloadStart = tagStart + (isSuggest ? 10 : (isConfirm ? 10 : 6));
            int tagEnd = text.indexOf("]]", payloadStart);
            if (tagEnd < 0) {
                segments.add(createSegment(text.substring(tagStart), null, null));
                break;
            }
            String payload = text.substring(payloadStart, tagEnd);
            int nextTag = nextTagIndex(
                    text.indexOf("[[CMD:", tagEnd + 2),
                    text.indexOf("[[SUGGEST:", tagEnd + 2),
                    text.indexOf("[[CONFIRM:", tagEnd + 2)
            );
            String segmentText = nextTag < 0 ? text.substring(tagEnd + 2) : text.substring(tagEnd + 2, nextTag);
            ActionType type = isConfirm ? ActionType.CONFIRM : (isSuggest ? ActionType.SUGGEST : ActionType.COMMAND);
            segments.add(createSegment(segmentText, type, payload));
            hasActions = true;
            if (nextTag < 0) {
                break;
            }
            index = nextTag;
        }
        return new ParsedSegments(segments, hasActions);
    }

    private int nextTagIndex(int cmdIndex, int suggestIndex, int confirmIndex) {
        int next = -1;
        if (cmdIndex >= 0) {
            next = cmdIndex;
        }
        if (suggestIndex >= 0) {
            next = next < 0 ? suggestIndex : Math.min(next, suggestIndex);
        }
        if (confirmIndex >= 0) {
            next = next < 0 ? confirmIndex : Math.min(next, confirmIndex);
        }
        return next;
    }

    private RenderSegment createSegment(String text, ActionType actionType, String actionValue) {
        String segmentText = text == null ? "" : text;
        Integer segmentColor = null;
        int colorIndex = segmentText.indexOf("[[COLOR:");
        if (colorIndex >= 0) {
            int end = segmentText.indexOf("]]", colorIndex + 8);
            if (end > colorIndex + 8) {
                String colorValue = segmentText.substring(colorIndex + 8, end).trim();
                segmentColor = parseColor(colorValue);
                segmentText = segmentText.substring(0, colorIndex) + segmentText.substring(end + 2);
            }
        }
        return new RenderSegment(segmentText, actionType, actionValue, segmentColor);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
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

    private String replacePlaceholders(String text) {
        if (text == null || text.isEmpty() || markerPages.isEmpty()) {
            return text;
        }
        String result = text;
        for (var entry : markerPages.entrySet()) {
            String key = "{page:" + entry.getKey() + "}";
            int pageNumber = entry.getValue() + 1;
            result = result.replace(key, String.valueOf(pageNumber));
        }
        return result;
    }

    private Integer parseColor(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String key = value.trim().toLowerCase();
        return switch (key) {
            case "gray", "grey" -> 0xFF9E9E9E;
            case "green" -> 0xFF6BCB6B;
            case "red" -> 0xFFFF4B4B;
            case "blue" -> 0xFF6FA9FF;
            case "yellow" -> 0xFFE3C76A;
            case "gold" -> 0xFFD7B46A;
            default -> {
                String hex = key.startsWith("#") ? key.substring(1) : key;
                if (hex.length() != 6) {
                    yield null;
                }
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    yield 0xFF000000 | rgb;
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    private float parseScale(String value) {
        if (value == null || value.isEmpty()) {
            return BODY_TEXT_SCALE;
        }
        String key = value.trim().toLowerCase();
        if ("header".equals(key)) {
            return HEADER_TEXT_SCALE;
        }
        try {
            float scale = Float.parseFloat(key);
            if (scale <= 0.0f || Float.isNaN(scale) || Float.isInfinite(scale)) {
                return BODY_TEXT_SCALE;
            }
            return scale;
        } catch (NumberFormatException ex) {
            return BODY_TEXT_SCALE;
        }
    }

    private int getButtonRowY(int top) {
        return top + PAGE_HEIGHT + 4;
    }

    private static class RenderLine {
        private final List<RenderSegment> segments;
        private final String linkId;
        private final Integer color;
        private final float scale;

        private RenderLine(List<RenderSegment> segments, String linkId, Integer color, float scale) {
            this.segments = segments == null ? List.of() : segments;
            this.linkId = linkId;
            this.color = color;
            this.scale = scale;
        }
    }

    private static class RenderSegment {
        private final String text;
        private final ActionType actionType;
        private final String actionValue;
        private final Integer color;

        private RenderSegment(String text, ActionType actionType, String actionValue, Integer color) {
            this.text = text == null ? "" : text;
            this.actionType = actionType;
            this.actionValue = actionValue;
            this.color = color;
        }
    }

    private static class LinkHit {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final Integer targetPage;
        private final ActionType actionType;
        private final String actionValue;

        private LinkHit(int x, int y, int w, int h, Integer targetPage, ActionType actionType, String actionValue) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.targetPage = targetPage;
            this.actionType = actionType;
            this.actionValue = actionValue;
        }
    }

    private static class LineToken {
        private final String text;
        private final String linkId;
        private final Integer color;
        private final float scale;
        private final String markerId;
        private final boolean pageBreak;

        private LineToken(String text, String linkId, Integer color, float scale, String markerId, boolean pageBreak) {
            this.text = text;
            this.linkId = linkId;
            this.color = color;
            this.scale = scale;
            this.markerId = markerId;
            this.pageBreak = pageBreak;
        }

        private static LineToken text(String text, String linkId, Integer color, float scale) {
            return new LineToken(text, linkId, color, scale, null, false);
        }

        private static LineToken marker(String markerId) {
            return new LineToken(null, null, null, BODY_TEXT_SCALE, markerId, false);
        }

        private static LineToken pageBreak() {
            return new LineToken(null, null, null, BODY_TEXT_SCALE, null, true);
        }

        private boolean isMarker() {
            return markerId != null;
        }

        private boolean isPageBreak() {
            return pageBreak;
        }
    }

    private static class ParsedSegments {
        private final List<RenderSegment> segments;
        private final boolean hasActions;

        private ParsedSegments(List<RenderSegment> segments, boolean hasActions) {
            this.segments = segments;
            this.hasActions = hasActions;
        }
    }

    private static class ConfirmDeleteScreen extends Screen {
        private final Screen parent;
        private final Text message;
        private final java.util.function.Consumer<Boolean> onClose;

        private ConfirmDeleteScreen(Screen parent, Text title, Text message, java.util.function.Consumer<Boolean> onClose) {
            super(title);
            this.parent = parent;
            this.message = message;
            this.onClose = onClose;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int buttonWidth = 80;
            int buttonY = centerY + 10;
            addDrawableChild(ButtonWidget.builder(Text.literal("Ja"), b -> onClose.accept(true))
                    .dimensions(centerX - buttonWidth - 6, buttonY, buttonWidth, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Nein"), b -> onClose.accept(false))
                    .dimensions(centerX + 6, buttonY, buttonWidth, 20)
                    .build());
        }

        @Override
        public boolean shouldCloseOnEsc() {
            onClose.accept(false);
            return true;
        }

        @Override
        public void close() {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0x80000000);
            int centerX = this.width / 2;
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, this.height / 2 - 30, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, this.message, centerX, this.height / 2 - 10, 0xFF9E9E9E);
            super.render(context, mouseX, mouseY, delta);
        }
    }

    private enum ActionType {
        COMMAND,
        SUGGEST,
        CONFIRM
    }
}
