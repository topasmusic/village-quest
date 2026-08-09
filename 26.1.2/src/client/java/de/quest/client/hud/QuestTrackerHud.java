package de.quest.client.hud;

import de.quest.VillageQuest;
import de.quest.client.config.VillageQuestClientConfig;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestTrackerHud {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_tracker");
    private static final int MAX_CONTENT_WIDTH = 220;
    private static final int LINE_HEIGHT = 10;
    private static final int COMPLETED_OBJECTIVE_COLOR = 0xFF00AA00;
    private static final Pattern PROGRESS_FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static TrackerState state = TrackerState.disabled();
    private static KeyMapping toggleKey;

    private QuestTrackerHud() {}

    public static void register() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.village-quest.quest_tracker", GLFW.GLFW_KEY_PERIOD,
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
    }

    private static void render(GuiGraphics drawContext, net.minecraft.client.DeltaTracker tickCounter) {
        TrackerState tracker = state;
        if (!tracker.enabled() || (!tracker.dailyActive() && !tracker.weeklyActive() && !tracker.storyActive() && !tracker.pilgrimActive() && !tracker.specialActive())) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.player == null) {
            return;
        }

        Font renderer = client.font;
        List<RenderEntry> renderLines = new ArrayList<>();
        addEntry(renderLines, renderer, Component.translatable("text.village-quest.tracker.header"), 0xFFF0D7A7);
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
                maxWidth = Math.max(maxWidth, renderer.width(wrappedLine));
                contentLines++;
            }
        }
        VillageQuestClientConfig config = VillageQuestClientConfig.get();
        float scale = config.questTrackerScale();
        int logicalWidth = Math.max(1, (int) Math.floor(drawContext.guiWidth() / scale));
        int logicalHeight = Math.max(1, (int) Math.floor(drawContext.guiHeight() / scale));
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

        var matrices = drawContext.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);

        int backgroundAlpha = Math.round(config.questTrackerBackgroundOpacity() * 255.0f);
        drawContext.fill(x, y, x + boxWidth, y + boxHeight, backgroundAlpha << 24 | 0x00101010);
        drawContext.fill(x, y, x + boxWidth, y + 1, 0x90D1B277);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0x90302010);

        int textX = x + 6;
        int textY = y + 4;
        for (int index = 0; index < renderLines.size(); index++) {
            RenderEntry line = renderLines.get(index);
            for (String wrappedLine : line.lines()) {
                drawContext.drawString(renderer, wrappedLine, textX, textY, line.color(), false);
                textY += LINE_HEIGHT;
            }
            if (index + 1 == headerLineCount) {
                textY += 2;
            }
        }
        matrices.popMatrix();
    }

    private static void addSection(List<RenderEntry> output,
                                   Font font,
                                   Component title,
                                   List<Component> lines,
                                   int lineColor) {
        addEntry(output, font, title, 0xFFFFFFFF);
        for (Component line : lines) {
            boolean completedObjective = isCompletedObjective(line);
            addEntry(output, font, line,
                    completedObjective ? COMPLETED_OBJECTIVE_COLOR : lineColor,
                    completedObjective);
        }
    }

    private static void addEntry(List<RenderEntry> output, Font font, Component component, int color) {
        addEntry(output, font, component, color, false);
    }

    private static void addEntry(List<RenderEntry> output,
                                 Font font,
                                 Component component,
                                 int color,
                                 boolean forceColor) {
        int resolvedColor = forceColor || component.getStyle().getColor() == null
                ? color
                : 0xFF000000 | component.getStyle().getColor().getValue();
        output.add(new RenderEntry(wrapText(font, component.getString()), resolvedColor));
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

    private static List<String> wrapText(Font font, String text) {
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
                if (!current.isEmpty() && font.width(candidate) > MAX_CONTENT_WIDTH) {
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
