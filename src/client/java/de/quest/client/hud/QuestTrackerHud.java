package de.quest.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public final class QuestTrackerHud {
    private static TrackerState state = TrackerState.disabled();

    private QuestTrackerHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(QuestTrackerHud::render);
    }

    public static void update(TrackerState trackerState) {
        state = trackerState == null ? TrackerState.disabled() : trackerState;
    }

    private static void render(DrawContext drawContext, net.minecraft.client.render.RenderTickCounter tickCounter) {
        TrackerState tracker = state;
        if (!tracker.enabled() || (!tracker.dailyActive() && !tracker.weeklyActive() && !tracker.specialActive())) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int maxWidth = renderer.getWidth(Text.translatable("text.village-quest.tracker.header"));
        if (tracker.dailyActive()) {
            maxWidth = Math.max(maxWidth, renderer.getWidth(tracker.dailyTitle()));
            for (Text line : tracker.dailyLines()) {
                maxWidth = Math.max(maxWidth, renderer.getWidth(line));
            }
        }
        if (tracker.weeklyActive()) {
            maxWidth = Math.max(maxWidth, renderer.getWidth(tracker.weeklyTitle()));
            for (Text line : tracker.weeklyLines()) {
                maxWidth = Math.max(maxWidth, renderer.getWidth(line));
            }
        }
        if (tracker.specialActive()) {
            maxWidth = Math.max(maxWidth, renderer.getWidth(tracker.specialTitle()));
            for (Text line : tracker.specialLines()) {
                maxWidth = Math.max(maxWidth, renderer.getWidth(line));
            }
        }

        int lineHeight = 10;
        int contentLines = 1;
        if (tracker.dailyActive()) {
            contentLines += 1 + tracker.dailyLines().size();
        }
        if (tracker.weeklyActive()) {
            contentLines += 1 + tracker.weeklyLines().size();
        }
        if (tracker.specialActive()) {
            contentLines += 1 + tracker.specialLines().size();
        }
        int boxWidth = maxWidth + 12;
        int boxHeight = contentLines * lineHeight + 8;
        int x = drawContext.getScaledWindowWidth() - boxWidth - 8;
        int y = 8;

        drawContext.fill(x, y, x + boxWidth, y + boxHeight, 0xA0101010);
        drawContext.fill(x, y, x + boxWidth, y + 1, 0x90D1B277);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0x90302010);

        int textX = x + 6;
        int textY = y + 4;
        drawContext.drawTextWithShadow(renderer, Text.translatable("text.village-quest.tracker.header"), textX, textY, 0xFFF0D7A7);
        textY += lineHeight + 2;

        if (tracker.dailyActive()) {
            drawContext.drawTextWithShadow(renderer, tracker.dailyTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Text line : tracker.dailyLines()) {
                drawContext.drawTextWithShadow(renderer, line, textX, textY, 0xFFD8D8D8);
                textY += lineHeight;
            }
        }

        if (tracker.weeklyActive()) {
            drawContext.drawTextWithShadow(renderer, tracker.weeklyTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Text line : tracker.weeklyLines()) {
                drawContext.drawTextWithShadow(renderer, line, textX, textY, 0xFFF0E2B0);
                textY += lineHeight;
            }
        }

        if (tracker.specialActive()) {
            drawContext.drawTextWithShadow(renderer, tracker.specialTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Text line : tracker.specialLines()) {
                drawContext.drawTextWithShadow(renderer, line, textX, textY, 0xFFE2D0FF);
                textY += lineHeight;
            }
        }
    }

    public record TrackerState(
            boolean enabled,
            boolean dailyActive,
            Text dailyTitle,
            List<Text> dailyLines,
            boolean weeklyActive,
            Text weeklyTitle,
            List<Text> weeklyLines,
            boolean specialActive,
            Text specialTitle,
            List<Text> specialLines
    ) {
        public static TrackerState disabled() {
            return new TrackerState(false, false, Text.empty(), List.of(), false, Text.empty(), List.of(), false, Text.empty(), List.of());
        }
    }
}
