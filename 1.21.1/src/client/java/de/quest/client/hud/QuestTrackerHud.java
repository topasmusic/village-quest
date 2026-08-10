package de.quest.client.hud;

import de.quest.client.config.VillageQuestClientConfig;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestTrackerHud {
    private static final int MAX_CONTENT_WIDTH = 220;
    private static final int LINE_HEIGHT = 10;
    private static final int COMPLETED_OBJECTIVE_COLOR = 0xFF00AA00;
    private static final Pattern PROGRESS_FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static TrackerState state = TrackerState.disabled();
    private static KeyBinding toggleKey;

    private QuestTrackerHud() {}

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.village-quest.quest_tracker", GLFW.GLFW_KEY_PERIOD,
                TradeRouteMinimapHud.keyCategory()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                if (client.player != null && ClientPlayNetworking.canSend(Payloads.QuestTrackerActionPayload.ID)) {
                    ClientPlayNetworking.send(new Payloads.QuestTrackerActionPayload());
                }
            }
        });
        HudRenderCallback.EVENT.register(QuestTrackerHud::render);
    }

    public static void update(TrackerState trackerState) {
        state = trackerState == null ? TrackerState.disabled() : trackerState;
    }

    private static void render(DrawContext drawContext, net.minecraft.client.render.RenderTickCounter tickCounter) {
        TrackerState tracker = state;
        if (!tracker.enabled() || (!tracker.dailyActive() && !tracker.weeklyActive() && !tracker.storyActive() && !tracker.pilgrimActive() && !tracker.specialActive())) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        List<RenderEntry> renderLines = new ArrayList<>();
        addEntry(renderLines, renderer, Text.translatable("text.village-quest.tracker.header"), 0xFFF0D7A7);
        int headerLineCount = renderLines.size();
        if (tracker.dailyActive()) {
            addSection(renderLines, renderer, tracker.dailyTitle(), tracker.dailyLines(), 0xFFD8D8D8);
        }
        if (tracker.weeklyActive()) {
            addSection(renderLines, renderer, tracker.weeklyTitle(), tracker.weeklyLines(), 0xFFF0E2B0);
        }
        if (tracker.storyActive()) {
            addSection(renderLines, renderer, tracker.storyTitle(), tracker.storyLines(), 0xFFD7E9A9);
        }
        if (tracker.pilgrimActive()) {
            addSection(renderLines, renderer, tracker.pilgrimTitle(), tracker.pilgrimLines(), 0xFFE7D1A4);
        }
        if (tracker.specialActive()) {
            addSection(renderLines, renderer, tracker.specialTitle(), tracker.specialLines(), 0xFFE2D0FF);
        }

        int maxWidth = 1;
        int contentLines = 0;
        for (RenderEntry line : renderLines) {
            for (String wrappedLine : line.lines()) {
                maxWidth = Math.max(maxWidth, renderer.getWidth(wrappedLine));
                contentLines++;
            }
        }
        VillageQuestClientConfig config = VillageQuestClientConfig.get();
        float scale = config.questTrackerScale();
        int logicalWidth = Math.max(1, (int) Math.floor(drawContext.getScaledWindowWidth() / scale));
        int logicalHeight = Math.max(1, (int) Math.floor(drawContext.getScaledWindowHeight() / scale));
        int boxWidth = maxWidth + 12;
        int boxHeight = contentLines * LINE_HEIGHT + 10;
        int margin = Math.max(4, Math.round(8.0f / scale));
        int x = switch (config.questTrackerPosition()) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> logicalWidth - boxWidth - margin;
        };
        int y = switch (config.questTrackerPosition()) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> logicalHeight - boxHeight - margin;
        };

        var matrices = drawContext.getMatrices();
        matrices.push();
        matrices.scale(scale, scale, 1);

        int backgroundAlpha = Math.round(config.questTrackerBackgroundOpacity() * 255.0f);
        drawContext.fill(x, y, x + boxWidth, y + boxHeight, backgroundAlpha << 24 | 0x00101010);
        drawContext.fill(x, y, x + boxWidth, y + 1, 0x90D1B277);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0x90302010);

        int textX = x + 6;
        int textY = y + 4;
        for (int index = 0; index < renderLines.size(); index++) {
            RenderEntry line = renderLines.get(index);
            for (String wrappedLine : line.lines()) {
                drawContext.drawText(renderer, wrappedLine, textX, textY, line.color(), false);
                textY += LINE_HEIGHT;
            }
            if (index + 1 == headerLineCount) {
                textY += 2;
            }
        }
        matrices.pop();
    }

    private static void addSection(List<RenderEntry> output,
                                   TextRenderer renderer,
                                   Text title,
                                   List<Text> lines,
                                   int lineColor) {
        addEntry(output, renderer, title, 0xFFFFFFFF);
        for (Text line : lines) {
            boolean completedObjective = isCompletedObjective(line);
            addEntry(output, renderer, line,
                    completedObjective ? COMPLETED_OBJECTIVE_COLOR : lineColor,
                    completedObjective);
        }
    }

    private static void addEntry(List<RenderEntry> output, TextRenderer renderer, Text text, int color) {
        addEntry(output, renderer, text, color, false);
    }

    private static void addEntry(List<RenderEntry> output,
                                 TextRenderer renderer,
                                 Text text,
                                 int color,
                                 boolean forceColor) {
        int resolvedColor = forceColor || text.getStyle().getColor() == null
                ? color
                : 0xFF000000 | text.getStyle().getColor().getRgb();
        output.add(new RenderEntry(wrapText(renderer, text.getString()), resolvedColor));
    }

    private static boolean isCompletedObjective(Text text) {
        if (text == null) {
            return false;
        }
        Matcher matcher = PROGRESS_FRACTION_PATTERN.matcher(text.getString());
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

    private static List<String> wrapText(TextRenderer renderer, String text) {
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
                if (!current.isEmpty() && renderer.getWidth(candidate) > MAX_CONTENT_WIDTH) {
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
        return lines;
    }

    private record RenderEntry(List<String> lines, int color) {}

    public record TrackerState(
            boolean enabled,
            boolean dailyActive,
            Text dailyTitle,
            List<Text> dailyLines,
            boolean weeklyActive,
            Text weeklyTitle,
            List<Text> weeklyLines,
            boolean storyActive,
            Text storyTitle,
            List<Text> storyLines,
            boolean pilgrimActive,
            Text pilgrimTitle,
            List<Text> pilgrimLines,
            boolean specialActive,
            Text specialTitle,
            List<Text> specialLines
    ) {
        public static TrackerState disabled() {
            return new TrackerState(false, false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of());
        }
    }
}
