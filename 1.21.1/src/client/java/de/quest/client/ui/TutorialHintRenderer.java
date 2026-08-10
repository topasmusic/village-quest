package de.quest.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class TutorialHintRenderer {
    private static final int SCREEN_PADDING = 6;
    private static final int BOX_PADDING = 4;
    private static final int GAP = 10;
    private static final int OUTER = 0xFFF0D7A7;
    private static final int INNER = 0xEE2C1E12;
    private static final int ACCENT = 0xCC5A3D23;
    private static final int TEXT_COLOR = 0xFFF8EED8;
    private static final int ARROW_SHADOW = 0xAA1B130C;
    private static final int TARGET_OUTER = 0x99F0D7A7;
    private static final int TARGET_ACCENT = 0xCCB88A46;

    private TutorialHintRenderer() {}

    public static void drawHint(
            DrawContext context,
            TextRenderer textRenderer,
            Text label,
            int screenWidth,
            int screenHeight,
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            Placement preferredPlacement,
            boolean highlightTarget,
            int bobOffset
    ) {
        String text = label.getString();
        int boxWidth = textRenderer.getWidth(text) + (BOX_PADDING * 2);
        int boxHeight = textRenderer.fontHeight + (BOX_PADDING * 2);
        Placement placement = resolvePlacement(preferredPlacement, screenWidth, screenHeight, targetX, targetY, targetWidth, targetHeight, boxWidth, boxHeight);

        int boxX;
        int boxY;
        switch (placement) {
            case LEFT -> {
                boxX = targetX - GAP - boxWidth;
                int preferredY = targetY + (targetHeight / 2) - (boxHeight / 2) + bobOffset;
                boxY = MathHelper.clamp(preferredY, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenHeight - boxHeight - SCREEN_PADDING));
            }
            case RIGHT -> {
                boxX = targetX + targetWidth + GAP;
                int preferredY = targetY + (targetHeight / 2) - (boxHeight / 2) + bobOffset;
                boxY = MathHelper.clamp(preferredY, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenHeight - boxHeight - SCREEN_PADDING));
            }
            case ABOVE -> {
                int preferredX = targetX + (targetWidth / 2) - (boxWidth / 2);
                boxX = MathHelper.clamp(preferredX, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenWidth - boxWidth - SCREEN_PADDING));
                boxY = targetY - GAP - boxHeight + bobOffset;
            }
            case BELOW -> {
                int preferredX = targetX + (targetWidth / 2) - (boxWidth / 2);
                boxX = MathHelper.clamp(preferredX, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenWidth - boxWidth - SCREEN_PADDING));
                boxY = targetY + targetHeight + GAP + bobOffset;
            }
            default -> throw new IllegalStateException("Unhandled placement: " + placement);
        }

        boxX = MathHelper.clamp(boxX, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenWidth - boxWidth - SCREEN_PADDING));
        boxY = MathHelper.clamp(boxY, SCREEN_PADDING, Math.max(SCREEN_PADDING, screenHeight - boxHeight - SCREEN_PADDING));

        if (highlightTarget) {
            drawTargetHighlight(context, targetX, targetY, targetWidth, targetHeight);
        }

        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, OUTER);
        context.fill(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, INNER);
        context.fill(boxX + 2, boxY + 2, boxX + boxWidth - 2, boxY + 3, ACCENT);
        context.drawText(textRenderer, text, boxX + BOX_PADDING, boxY + BOX_PADDING, TEXT_COLOR, false);

        switch (placement) {
            case LEFT -> drawRightPointingArrow(context, targetX, boxY + (boxHeight / 2), boxX + boxWidth);
            case RIGHT -> drawLeftPointingArrow(context, targetX + targetWidth - 1, boxY + (boxHeight / 2), boxX);
            case ABOVE -> drawDownPointingArrow(context, targetX + (targetWidth / 2), targetY, boxY + boxHeight);
            case BELOW -> drawUpPointingArrow(context, targetX + (targetWidth / 2), targetY + targetHeight - 1, boxY);
        }
    }

    private static Placement resolvePlacement(
            Placement preferred,
            int screenWidth,
            int screenHeight,
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            int boxWidth,
            int boxHeight
    ) {
        if (fits(preferred, screenWidth, screenHeight, targetX, targetY, targetWidth, targetHeight, boxWidth, boxHeight)) {
            return preferred;
        }
        Placement fallback = preferred.opposite();
        if (fits(fallback, screenWidth, screenHeight, targetX, targetY, targetWidth, targetHeight, boxWidth, boxHeight)) {
            return fallback;
        }
        return preferred;
    }

    private static boolean fits(
            Placement placement,
            int screenWidth,
            int screenHeight,
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            int boxWidth,
            int boxHeight
    ) {
        return switch (placement) {
            case LEFT -> targetX - GAP - boxWidth >= SCREEN_PADDING;
            case RIGHT -> targetX + targetWidth + GAP + boxWidth <= screenWidth - SCREEN_PADDING;
            case ABOVE -> targetY - GAP - boxHeight >= SCREEN_PADDING;
            case BELOW -> targetY + targetHeight + GAP + boxHeight <= screenHeight - SCREEN_PADDING;
        };
    }

    private static void drawTargetHighlight(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 2, y - 2, x + width + 2, y - 1, TARGET_OUTER);
        context.fill(x - 2, y + height + 1, x + width + 2, y + height + 2, TARGET_OUTER);
        context.fill(x - 2, y - 1, x - 1, y + height + 1, TARGET_OUTER);
        context.fill(x + width + 1, y - 1, x + width + 2, y + height + 1, TARGET_OUTER);

        context.fill(x - 1, y - 1, x + width + 1, y, TARGET_ACCENT);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, TARGET_ACCENT);
        context.fill(x - 1, y, x, y + height, TARGET_ACCENT);
        context.fill(x + width, y, x + width + 1, y + height, TARGET_ACCENT);
    }

    private static void drawLeftPointingArrow(DrawContext context, int tipX, int centerY, int boxX) {
        int shaftStart = tipX + 3;
        int shaftEnd = boxX - 2;
        if (shaftEnd > shaftStart) {
            context.fill(shaftStart, centerY - 2, shaftEnd, centerY + 2, ARROW_SHADOW);
            context.fill(shaftStart, centerY - 1, shaftEnd, centerY + 1, OUTER);
        }
        context.fill(tipX + 2, centerY - 3, tipX + 4, centerY + 3, ARROW_SHADOW);
        context.fill(tipX + 1, centerY - 2, tipX + 3, centerY + 2, OUTER);
        context.fill(tipX, centerY - 1, tipX + 2, centerY + 1, OUTER);
    }

    private static void drawRightPointingArrow(DrawContext context, int tipX, int centerY, int boxRightX) {
        int shaftStart = boxRightX + 2;
        int shaftEnd = tipX - 3;
        if (shaftEnd > shaftStart) {
            context.fill(shaftStart, centerY - 2, shaftEnd, centerY + 2, ARROW_SHADOW);
            context.fill(shaftStart, centerY - 1, shaftEnd, centerY + 1, OUTER);
        }
        context.fill(tipX - 4, centerY - 3, tipX - 2, centerY + 3, ARROW_SHADOW);
        context.fill(tipX - 3, centerY - 2, tipX - 1, centerY + 2, OUTER);
        context.fill(tipX - 2, centerY - 1, tipX, centerY + 1, OUTER);
    }

    private static void drawDownPointingArrow(DrawContext context, int centerX, int tipY, int boxBottomY) {
        int shaftStart = boxBottomY + 2;
        int shaftEnd = tipY - 3;
        if (shaftEnd > shaftStart) {
            context.fill(centerX - 2, shaftStart, centerX + 2, shaftEnd, ARROW_SHADOW);
            context.fill(centerX - 1, shaftStart, centerX + 1, shaftEnd, OUTER);
        }
        context.fill(centerX - 3, tipY - 4, centerX + 3, tipY - 2, ARROW_SHADOW);
        context.fill(centerX - 2, tipY - 3, centerX + 2, tipY - 1, OUTER);
        context.fill(centerX - 1, tipY - 2, centerX + 1, tipY, OUTER);
    }

    private static void drawUpPointingArrow(DrawContext context, int centerX, int tipY, int boxTopY) {
        int shaftStart = tipY + 3;
        int shaftEnd = boxTopY - 2;
        if (shaftEnd > shaftStart) {
            context.fill(centerX - 2, shaftStart, centerX + 2, shaftEnd, ARROW_SHADOW);
            context.fill(centerX - 1, shaftStart, centerX + 1, shaftEnd, OUTER);
        }
        context.fill(centerX - 3, tipY + 2, centerX + 3, tipY + 4, ARROW_SHADOW);
        context.fill(centerX - 2, tipY + 1, centerX + 2, tipY + 3, OUTER);
        context.fill(centerX - 1, tipY, centerX + 1, tipY + 2, OUTER);
    }

    public enum Placement {
        LEFT,
        RIGHT,
        ABOVE,
        BELOW;

        public Placement opposite() {
            return switch (this) {
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
                case ABOVE -> BELOW;
                case BELOW -> ABOVE;
            };
        }
    }
}
