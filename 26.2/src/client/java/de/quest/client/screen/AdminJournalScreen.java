package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.KingdomUiAssets;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class AdminJournalScreen extends CompatScreen {
    private static final float HEADER_TEXT_SCALE = .95f;
    private static final float BODY_TEXT_SCALE = .8f;
    private static final int PAGE_WIDTH = 416, PAGE_HEIGHT = 290;
    private static final int CONTENT_Y_OFFSET = 70, CONTENT_X_OFFSET = 26;
    private static final int CONTENT_WIDTH = 364, CONTENT_HEIGHT = 164;
    private static final int ARROW_WIDTH = 48, ARROW_HEIGHT = 20;
    private final List<String> rawLines;
    private List<List<RenderLine>> pages = List.of();
    private int pageIndex = 0;
    private final List<LinkHit> linkHits = new ArrayList<>();
    private Map<String, Integer> markerPages = new HashMap<>();

    public AdminJournalScreen(List<String> lines) {
        super(Component.translatable("screen.village-quest.admin_journal.title"));
        this.rawLines = lines == null ? List.of() : List.copyOf(lines);
    }

    @Override protected void init() { buildPages(); linkHits.clear(); }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(context, width, height);
        int mx=responsiveMouseX(mouseX,PAGE_WIDTH,PAGE_HEIGHT),my=responsiveMouseY(mouseY,PAGE_WIDTH,PAGE_HEIGHT);
        float scale=beginResponsivePanel(context,PAGE_WIDTH,PAGE_HEIGHT);
        try {
            int left=(width-PAGE_WIDTH)/2,top=(height-PAGE_HEIGHT)/2;
            VillageUiTheme.drawBoard(context,left,top,PAGE_WIDTH,PAGE_HEIGHT);
            VillageUiTheme.drawTitle(context,font,title.getString(),"story",left+80,top+7,256);
            var close=KingdomUiAssets.closeBounds(left,top,PAGE_WIDTH,PAGE_HEIGHT);
            KingdomUiAssets.closeButton(context,left,top,PAGE_WIDTH,PAGE_HEIGHT,close.contains(mx,my));
            VillageUiTheme.drawPaper(context,left+18,top+60,PAGE_WIDTH-36,CONTENT_HEIGHT+20);
            linkHits.clear();
            if(!pages.isEmpty()) drawPage(context,left,top,pages.get(pageIndex),mx,my);
            VillageUiTheme.drawPaper(context,left+18,top+244,PAGE_WIDTH-36,29);
            int buttonY=top+250;
            VillageUiTheme.drawButton(context,font,left+26,buttonY,ARROW_WIDTH,ARROW_HEIGHT,"<",pageIndex>0,isHover(mx,my,left+26,buttonY,ARROW_WIDTH,ARROW_HEIGHT),false);
            VillageUiTheme.drawButton(context,font,left+PAGE_WIDTH-26-ARROW_WIDTH,buttonY,ARROW_WIDTH,ARROW_HEIGHT,">",pageIndex+1<pages.size(),isHover(mx,my,left+PAGE_WIDTH-26-ARROW_WIDTH,buttonY,ARROW_WIDTH,ARROW_HEIGHT),false);
            String label=Component.translatable("screen.village-quest.journal.page",pageIndex+1,Math.max(1,pages.size())).getString();
            VillageUiTheme.drawStringScaled(context,font,label,left+(PAGE_WIDTH-font.width(label)*.8f)/2,buttonY+6,VillageUiTheme.BODY,.8f);
        } finally { endResponsivePanel(context,scale); }
    }

    private void drawPage(GuiGraphics context,int left,int top,List<RenderLine> page,int mx,int my) {
        int x=left+CONTENT_X_OFFSET,y=top+CONTENT_Y_OFFSET;
        for(RenderLine line:page) {
            int step=Math.round((font.lineHeight+3)*line.scale);
            if(y+step>top+CONTENT_Y_OFFSET+CONTENT_HEIGHT) break;
            int offset=0;
            for(RenderSegment segment:line.segments) {
                String display=replacePlaceholders(segment.text);
                boolean interactive=segment.actionType!=null || line.linkId!=null;
                int color=segment.color!=null?segment.color:line.color!=null?line.color:interactive?VillageUiTheme.TEAL:VillageUiTheme.BODY;
                int w=Math.round(font.width(display)*line.scale),h=Math.round(font.lineHeight*line.scale);
                VillageUiTheme.drawStringScaled(context,font,display,x+offset,y,color,line.scale);
                if(interactive && w>0) {
                    if(isHover(mx,my,x+offset,y,w,h)) context.fill(x+offset,y+h,x+offset+w,y+h+1,VillageUiTheme.GOLD);
                    linkHits.add(new LinkHit(x+offset,y,w,h,markerPages.get(line.linkId),segment.actionType,segment.actionValue));
                }
                offset+=w;
            }
            y+=step;
        }
    }

    private boolean isHover(int mx,int my,int x,int y,int w,int h) { return mx>=x && mx<x+w && my>=y && my<y+h; }
    private void goToPage(int target) {
        int next=Math.max(0,Math.min(target,pages.size()-1));
        if(next!=pageIndex) { pageIndex=next; linkHits.clear(); playPageTurnSound(); }
    }
    @Override protected boolean handleMouseClicked(net.minecraft.client.input.MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click,doubled);
        int mx=responsiveMouseX(click.x(),PAGE_WIDTH,PAGE_HEIGHT),my=responsiveMouseY(click.y(),PAGE_WIDTH,PAGE_HEIGHT);
        int left=(width-PAGE_WIDTH)/2,top=(height-PAGE_HEIGHT)/2,buttonY=top+250;
        if(KingdomUiAssets.closeBounds(left,top,PAGE_WIDTH,PAGE_HEIGHT).contains(mx,my)) { onClose(); return true; }
        if(isHover(mx,my,left+26,buttonY,ARROW_WIDTH,ARROW_HEIGHT)) { goToPage(pageIndex-1); return true; }
        if(isHover(mx,my,left+PAGE_WIDTH-26-ARROW_WIDTH,buttonY,ARROW_WIDTH,ARROW_HEIGHT)) { goToPage(pageIndex+1); return true; }
        for(LinkHit hit:List.copyOf(linkHits)) if(isHover(mx,my,hit.x,hit.y,hit.w,hit.h)) {
            if(hit.actionType==ActionType.COMMAND) sendCommand(hit.actionValue);
            else if(hit.actionType==ActionType.SUGGEST) suggestCommand(hit.actionValue);
            else if(hit.actionType==ActionType.CONFIRM) confirmCommand(hit.actionValue);
            else if(hit.targetPage!=null) goToPage(hit.targetPage);
            return true;
        }
        return super.handleMouseClicked(click,doubled);
    }

    private void playPageTurnSound() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    private void sendCommand(String command) {
        if (this.minecraft == null || this.minecraft.player == null || command == null || command.isBlank()) {
            return;
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        UiTestTour.command(trimmed);
    }

    private void confirmCommand(String command) {
        if (this.minecraft == null || command == null || command.isBlank()) {
            return;
        }
        Component title = Component.translatable("screen.village-quest.admin_journal.delete_title");
        Component message = Component.translatable("screen.village-quest.admin_journal.delete_warning");
        UiTestTour.navigate(new ConfirmDeleteScreen(this, title, message, confirmed -> {
            if (confirmed) {
                sendCommand(command);
                if (this.minecraft != null) {
                    UiTestTour.navigate(this);
                }
            } else if (this.minecraft != null) {
                UiTestTour.navigate(this);
            }
        }));
    }

    private void suggestCommand(String command) {
        if (this.minecraft == null || command == null || command.isBlank()) {
            return;
        }
        String text = command.trim();
        if (!text.startsWith("/")) {
            text = "/" + text;
        }
        UiTestTour.navigate(new net.minecraft.client.gui.screens.ChatScreen(text, false));
    }

    private void buildPages() {
        if (this.font == null) {
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
            int lineHeight = Math.round((this.font.lineHeight + 3) * token.scale);
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
            for(List<RenderSegment> row:wrapSegments(parsed.segments,Math.round(CONTENT_WIDTH/token.scale))) {
                if(usedHeight+lineHeight>CONTENT_HEIGHT && !current.isEmpty()) {
                    result.add(current); current=new ArrayList<>(); usedHeight=0;
                }
                current.add(new RenderLine(row,token.linkId,token.color,token.scale));
                usedHeight+=lineHeight;
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

    /** Wrap action labels with their action intact, including oversized single identifiers. */
    private List<List<RenderSegment>> wrapSegments(List<RenderSegment> segments,int limit) {
        List<List<RenderSegment>> rows=new ArrayList<>();
        List<RenderSegment> row=new ArrayList<>(); int used=0;
        for(RenderSegment segment:segments) {
            var words=java.util.regex.Pattern.compile("\\S+\\s*|\\s+").matcher(segment.text);
            while(words.find()) {
                String word=words.group();
                if(used>0 && used+font.width(word.stripTrailing())>limit) { rows.add(row);row=new ArrayList<>();used=0;word=word.stripLeading(); }
                StringBuilder part=new StringBuilder();
                for(int cp:word.codePoints().toArray()) {
                    String glyph=new String(Character.toChars(cp));
                    if(used+font.width(part+glyph)>limit && (used>0 || !part.isEmpty())) {
                        if(!part.isEmpty()) row.add(new RenderSegment(part.toString(),segment.actionType,segment.actionValue,segment.color));
                        rows.add(row);row=new ArrayList<>();used=0;part.setLength(0);
                    }
                    part.append(glyph);
                }
                if(!part.isEmpty()) { row.add(new RenderSegment(part.toString(),segment.actionType,segment.actionValue,segment.color));used+=font.width(part.toString()); }
            }
        }
        if(!row.isEmpty()) rows.add(row);
        return rows;
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
            case "gray", "grey" -> VillageUiTheme.MUTED;
            case "green" -> 0xFF306C42;
            case "red" -> 0xFF983E2C;
            case "blue" -> VillageUiTheme.TEAL;
            case "yellow" -> VillageUiTheme.GOLD;
            case "gold" -> VillageUiTheme.GOLD;
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
            return Math.max(.72f,Math.min(1.1f,scale));
        } catch (NumberFormatException ex) {
            return BODY_TEXT_SCALE;
        }
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

    private static class ConfirmDeleteScreen extends CompatScreen {
        private final Screen parent;
        private final Component message;
        private final java.util.function.Consumer<Boolean> onClose;

        private ConfirmDeleteScreen(Screen parent, Component title, Component message, java.util.function.Consumer<Boolean> onClose) {
            super(title);
            this.parent = parent;
            this.message = message;
            this.onClose = onClose;
        }

        private boolean resolved;
        private void resolve(boolean answer) { if(!resolved) { resolved=true; onClose.accept(answer); } }
        @Override public void onClose() { resolve(false); }
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            VillageUiTheme.drawScreenShade(g,width,height);
            float scale=beginResponsivePanel(g,416,200);
            int x=(width-416)/2,y=(height-200)/2;
            try {
                VillageUiTheme.drawBoard(g,x,y,416,200);
                VillageUiTheme.drawTitle(g,font,title.getString(),"story",x+64,y+8,288);
                VillageUiTheme.drawWrappedScaled(g,font,message.getString(),x+28,y+60,360,VillageUiTheme.INK,.85f,6);
                int px=responsiveMouseX(mx,416,200),py=responsiveMouseY(my,416,200);
                VillageUiTheme.drawButton(g,font,x+30,y+150,170,22,Component.translatable("gui.no").getString(),true,px>=x+30&&px<x+200&&py>=y+150&&py<y+172,false);
                VillageUiTheme.drawButton(g,font,x+216,y+150,170,22,Component.translatable("gui.yes").getString(),true,px>=x+216&&px<x+386&&py>=y+150&&py<y+172,false);
            } finally { endResponsivePanel(g,scale); }
        }
        @Override protected boolean handleMouseClicked(net.minecraft.client.input.MouseButtonEvent click,boolean doubled) {
            if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click,doubled);
            int px=responsiveMouseX(click.x(),416,200)-(width-416)/2,py=responsiveMouseY(click.y(),416,200)-(height-200)/2;
            if(py>=150&&py<172) {
                if(px>=30&&px<200) { resolve(false);return true; }
                if(px>=216&&px<386) { resolve(true);return true; }
            }
            return super.handleMouseClicked(click,doubled);
        }
    }

    private enum ActionType {
        COMMAND,
        SUGGEST,
        CONFIRM
    }
}
