package de.quest.client.hud;

import de.quest.VillageQuest;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

public final class QuestTrackerHud {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_tracker");
    private static TrackerState state = TrackerState.disabled();

    private QuestTrackerHud() {}

    public static void register() {
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
        int maxWidth = renderer.width(Component.translatable("text.village-quest.tracker.header"));
        if (tracker.dailyActive()) {
            maxWidth = Math.max(maxWidth, renderer.width(tracker.dailyTitle()));
            for (Component line : tracker.dailyLines()) {
                maxWidth = Math.max(maxWidth, renderer.width(line));
            }
        }
        if (tracker.weeklyActive()) {
            maxWidth = Math.max(maxWidth, renderer.width(tracker.weeklyTitle()));
            for (Component line : tracker.weeklyLines()) {
                maxWidth = Math.max(maxWidth, renderer.width(line));
            }
        }
        if (tracker.storyActive()) {
            maxWidth = Math.max(maxWidth, renderer.width(tracker.storyTitle()));
            for (Component line : tracker.storyLines()) {
                maxWidth = Math.max(maxWidth, renderer.width(line));
            }
        }
        if (tracker.pilgrimActive()) {
            maxWidth = Math.max(maxWidth, renderer.width(tracker.pilgrimTitle()));
            for (Component line : tracker.pilgrimLines()) {
                maxWidth = Math.max(maxWidth, renderer.width(line));
            }
        }
        if (tracker.specialActive()) {
            maxWidth = Math.max(maxWidth, renderer.width(tracker.specialTitle()));
            for (Component line : tracker.specialLines()) {
                maxWidth = Math.max(maxWidth, renderer.width(line));
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
        if (tracker.storyActive()) {
            contentLines += 1 + tracker.storyLines().size();
        }
        if (tracker.pilgrimActive()) {
            contentLines += 1 + tracker.pilgrimLines().size();
        }
        if (tracker.specialActive()) {
            contentLines += 1 + tracker.specialLines().size();
        }
        int boxWidth = maxWidth + 12;
        int boxHeight = contentLines * lineHeight + 8;
        int x = drawContext.guiWidth() - boxWidth - 8;
        int y = 8;

        drawContext.fill(x, y, x + boxWidth, y + boxHeight, 0xA0101010);
        drawContext.fill(x, y, x + boxWidth, y + 1, 0x90D1B277);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0x90302010);

        int textX = x + 6;
        int textY = y + 4;
        drawContext.drawString(renderer, Component.translatable("text.village-quest.tracker.header"), textX, textY, 0xFFF0D7A7);
        textY += lineHeight + 2;

        if (tracker.dailyActive()) {
            drawContext.drawString(renderer, tracker.dailyTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Component line : tracker.dailyLines()) {
                drawContext.drawString(renderer, line, textX, textY, 0xFFD8D8D8);
                textY += lineHeight;
            }
        }

        if (tracker.weeklyActive()) {
            drawContext.drawString(renderer, tracker.weeklyTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Component line : tracker.weeklyLines()) {
                drawContext.drawString(renderer, line, textX, textY, 0xFFF0E2B0);
                textY += lineHeight;
            }
        }

        if (tracker.storyActive()) {
            drawContext.drawString(renderer, tracker.storyTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Component line : tracker.storyLines()) {
                drawContext.drawString(renderer, line, textX, textY, 0xFFD7E9A9);
                textY += lineHeight;
            }
        }

        if (tracker.pilgrimActive()) {
            drawContext.drawString(renderer, tracker.pilgrimTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Component line : tracker.pilgrimLines()) {
                drawContext.drawString(renderer, line, textX, textY, 0xFFE7D1A4);
                textY += lineHeight;
            }
        }

        if (tracker.specialActive()) {
            drawContext.drawString(renderer, tracker.specialTitle(), textX, textY, 0xFFFFFFFF);
            textY += lineHeight;
            for (Component line : tracker.specialLines()) {
                drawContext.drawString(renderer, line, textX, textY, 0xFFE2D0FF);
                textY += lineHeight;
            }
        }
    }

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
