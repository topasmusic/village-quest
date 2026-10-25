package de.quest.client.hud;

import de.quest.VillageQuest;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.config.QuestTrackerFocus;
import de.quest.network.Payloads;
import de.quest.client.ui.VillageUiTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestTrackerHud {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_tracker");
    private static final int MAX_CONTENT_WIDTH = 220;
    private static final int LINE_HEIGHT = 10;
    private static final int COMPLETED_OBJECTIVE_COLOR = 0xFF306C42;
    private static final Pattern PROGRESS_FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static TrackerState state = TrackerState.disabled();
    private static KeyMapping toggleKey;

    private QuestTrackerHud() {}

    public static void register() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.village-quest.quest_tracker", InputConstants.KEY_PERIOD,
                TradeRouteMinimapHud.keyCategory()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(Payloads.QuestTrackerActionPayload.ID)) {
                    ClientPlayNetworking.send(new Payloads.QuestTrackerActionPayload());
                }
            }
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                HUD_LAYER_ID,
                (extractor, tickCounter) -> render(new GuiGraphics(extractor), tickCounter)
        );
    }

    public static void update(TrackerState trackerState) {
        state = trackerState == null ? TrackerState.disabled() : trackerState;
        if (state.enabled()) QuestTrackerFocus.current().reconcile(activeTitles(state));
    }

    private static java.util.Map<String, Component> activeTitles(TrackerState s) {
        var titles = new java.util.LinkedHashMap<String, Component>();
        if (s.storyActive()) titles.put("story", s.storyTitle());
        if (s.specialActive()) titles.put("special", s.specialTitle());
        if (s.weeklyActive()) titles.put("weekly", s.weeklyTitle());
        if (s.dailyActive()) titles.put("daily", s.dailyTitle());
        if (s.pilgrimActive()) titles.put("pilgrim", s.pilgrimTitle());
        return titles;
    }

    record Page<T>(List<T> lines, int number, int count, int rows) {}
    static <T> Page<T> page(List<T> pinned, List<T> others, int capacity, long milliseconds) {
        capacity = Math.max(2, capacity);
        // Keep the whole chosen quest fixed when it fits; retain its heading on very small HUDs.
        int fixed = pinned.size() >= capacity ? 1 : pinned.size();
        var rotating = new ArrayList<T>(pinned.subList(fixed, pinned.size()));
        rotating.addAll(others);
        int room = capacity - fixed, count = Math.max(1, (rotating.size() + room - 1) / room);
        int page = (int) Math.floorMod(milliseconds / 7000, count);
        var shown = new ArrayList<T>(pinned.subList(0, fixed));
        shown.addAll(rotating.subList(Math.min(rotating.size(), page * room), Math.min(rotating.size(), (page + 1) * room)));
        return new Page<>(List.copyOf(shown), page + 1, count, count>1 ? capacity : shown.size());
    }

    private static void render(GuiGraphics drawContext, net.minecraft.client.DeltaTracker tickCounter) {
        TrackerState tracker = state;
        if (!tracker.enabled() || (!tracker.dailyActive() && !tracker.weeklyActive() && !tracker.storyActive() && !tracker.pilgrimActive() && !tracker.specialActive())) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gui.hud.isHidden() || client.player == null) {
            return;
        }

        Font renderer = client.font;
        VillageQuestClientConfig config = VillageQuestClientConfig.get();
        float scale = config.questTrackerScale();
        int logicalWidth = Math.max(1, (int) Math.floor(drawContext.guiWidth() / scale));
        int logicalHeight = Math.max(1, (int) Math.floor(drawContext.guiHeight() / scale));
        int margin = Math.max(4, Math.round(8.0f / scale));
        int wrapWidth = Math.min(MAX_CONTENT_WIDTH,Math.max(24,logicalWidth-margin*2-20));
        List<RenderEntry> renderLines = new ArrayList<>();
        List<RenderEntry> pinnedLines = new ArrayList<>();
        var focus = QuestTrackerFocus.current();
        var titles = activeTitles(tracker);
        boolean pinned = titles.containsKey(focus.quest()) && focus.matches(focus.quest(), titles.get(focus.quest()));
        for (var entry : titles.entrySet()) {
            boolean chosen = pinned && entry.getKey().equals(focus.quest());
            if (pinned && focus.onlyPinned() && !chosen) continue;
            List<Component> lines = switch (entry.getKey()) {
                case "story" -> tracker.storyLines(); case "special" -> tracker.specialLines();
                case "weekly" -> tracker.weeklyLines(); case "daily" -> tracker.dailyLines();
                default -> tracker.pilgrimLines();
            };
            addSection(chosen ? pinnedLines : renderLines, renderer, entry.getValue(), lines, wrapWidth);
        }

        var pinnedFlat = new ArrayList<RenderEntry>();
        var otherFlat = new ArrayList<RenderEntry>();
        for (RenderEntry entry : pinnedLines) for (String line : entry.lines()) pinnedFlat.add(new RenderEntry(List.of(line), entry.color()));
        for (RenderEntry entry : renderLines) for (String line : entry.lines()) otherFlat.add(new RenderEntry(List.of(line), entry.color()));
        renderLines = new ArrayList<>(pinnedFlat); renderLines.addAll(otherFlat);

        int maxWidth = 1;
        for (RenderEntry line : renderLines) {
            for (String wrappedLine : line.lines()) {
                maxWidth = Math.max(maxWidth, renderer.width(wrappedLine));
            }
        }
        int capacity=Math.max(2,(logicalHeight-2*margin-42)/LINE_HEIGHT);
        var visible = page(pinnedFlat, otherFlat, capacity, System.currentTimeMillis());
        int pages=visible.count();
        int boxWidth=Math.min(logicalWidth-2*margin,maxWidth+20);
        int boxHeight=visible.rows()*LINE_HEIGHT+30+(pages>1?12:0);
        int x = switch (config.questTrackerPosition()) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> logicalWidth - boxWidth - margin;
        };
        int y = switch (config.questTrackerPosition()) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> logicalHeight - boxHeight - margin;
        };

        var matrices = drawContext.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);

        drawBackground(drawContext,x,y,boxWidth,boxHeight,config.questTrackerBackgroundOpacity());
        String header=VillageUiTheme.ellipsize(renderer,Component.translatable("text.village-quest.tracker.header").getString(),boxWidth-16);
        drawContext.drawString(renderer,header,x+8,y+6,VillageUiTheme.LIGHT_TEXT,false);
        boolean transparent=config.questTrackerBackgroundOpacity()<=.5f;
        int textX=x+10,textY=y+24;
        for(RenderEntry entry:visible.lines()) for(String line:entry.lines()) {
                int color=transparent?(entry.color()==COMPLETED_OBJECTIVE_COLOR?0xFF94C68F:VillageUiTheme.LIGHT_TEXT):entry.color();
                drawContext.drawString(renderer,line,textX,textY,color,transparent); textY+=LINE_HEIGHT;
        }
        if(pages>1) drawContext.drawString(renderer,
                Component.translatable("screen.village-quest.journal.page",visible.number(),pages),textX,y+boxHeight-12,transparent?VillageUiTheme.LIGHT_TEXT:VillageUiTheme.MUTED,transparent);
        matrices.popMatrix();
    }

    /** Every pixel of the panel receives one alpha layer, including the header. */
    static void drawBackground(GuiGraphics g,int x,int y,int width,int height,float opacity) {
        int alpha=Math.round(Math.max(0,Math.min(1,opacity))*255);
        // Shadow stays outside the panel rather than darkening the paper beneath it.
        g.fill(x+width,y+2,x+width+2,y+height+2,(alpha/3)<<24|0x18120D);
        g.fill(x+2,y+height,x+width,y+height+2,(alpha/3)<<24|0x18120D);
        border(g,x,y,width,height,alpha<<24|VillageUiTheme.WOOD&0xFFFFFF);
        border(g,x+1,y+1,width-2,height-2,alpha<<24|VillageUiTheme.GOLD&0xFFFFFF);
        g.fill(x+2,y+2,x+width-2,y+19,alpha<<24|VillageUiTheme.TEAL&0xFFFFFF);
        g.fill(x+2,y+19,x+width-2,y+height-2,alpha<<24|VillageUiTheme.PAPER&0xFFFFFF);
    }

    private static void border(GuiGraphics g,int x,int y,int width,int height,int color) {
        g.fill(x,y,x+width,y+1,color);
        g.fill(x,y+height-1,x+width,y+height,color);
        g.fill(x,y+1,x+1,y+height-1,color);
        g.fill(x+width-1,y+1,x+width,y+height-1,color);
    }

    private static void addSection(List<RenderEntry> output,Font font,Component title,List<Component> lines,int width) {
        output.add(new RenderEntry(wrapText(font,title.getString(),width),VillageUiTheme.TEAL));
        for(Component line:lines) output.add(new RenderEntry(wrapText(font,line.getString(),width),
                isCompletedObjective(line)?COMPLETED_OBJECTIVE_COLOR:VillageUiTheme.BODY));
    }

    private static boolean isCompletedObjective(Component component) {
        if (component == null) {
            return false;
        }
        Matcher matcher = PROGRESS_FRACTION_PATTERN.matcher(component.getString());
        boolean foundProgress = false;
        while (matcher.find()) {
            foundProgress = true;
            try {
                long current = Long.parseLong(matcher.group(1));
                long target = Long.parseLong(matcher.group(2));
                if (target <= 0L || current < target) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return foundProgress;
    }

    private static List<String> wrapText(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }

        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.width(candidate) > width) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }

        if (lines.isEmpty()) {
            lines.add("");
        }
        List<String> bounded=new ArrayList<>();
        for(String line:lines) {
            StringBuilder part=new StringBuilder();
            for(int cp:line.codePoints().toArray()) {
                String glyph=new String(Character.toChars(cp));
                if(!part.isEmpty() && font.width(part+glyph)>width) { bounded.add(part.toString());part.setLength(0); }
                part.append(glyph);
            }
            bounded.add(part.toString());
        }
        return bounded;
    }

    private record RenderEntry(List<String> lines, int color) {}

    public record TrackerState(
            boolean enabled,
            boolean dailyActive,
            Component dailyTitle,
            List<Component> dailyLines,
            boolean weeklyActive,
            Component weeklyTitle,
            List<Component> weeklyLines,
            boolean storyActive,
            Component storyTitle,
            List<Component> storyLines,
            boolean pilgrimActive,
            Component pilgrimTitle,
            List<Component> pilgrimLines,
            boolean specialActive,
            Component specialTitle,
            List<Component> specialLines
    ) {
        public static TrackerState disabled() {
            return new TrackerState(false, false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of(), false, Component.empty(), List.of());
        }
    }
}
