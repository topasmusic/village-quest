package de.quest.client.ui;

import de.quest.mixin.HandledScreenAccessor;

public final class InventoryJournalButtonLayout {
    // Switch this back to FLOATING_TOP_RIGHT if we want the previous inventory button style again.
    private static final LayoutMode MODE = LayoutMode.BOOKMARK_TAB_RIGHT;
    private static final int FLOATING_BUTTON_SIZE = 18;
    private static final int FLOATING_RIGHT_OFFSET = 9;
    private static final int FLOATING_TOP_OFFSET = 6;
    private static final int SIDE_TAB_WIDTH = 16;
    private static final int SIDE_TAB_HEIGHT = 24;
    private static final int SIDE_TAB_OVERLAP = 4;
    private static final int SIDE_TAB_COLLAPSED_VISIBLE = 5;
    private static final int SIDE_TAB_VERTICAL_OFFSET = -12;
    private static final int TOP_TAB_WIDTH = 22;
    private static final int TOP_TAB_HEIGHT = 16;
    private static final int TOP_TAB_OVERLAP = 4;
    private static final int TOP_TAB_COLLAPSED_VISIBLE = 5;
    private static final int TOP_TAB_RIGHT_OFFSET = 8;
    private static final int TOP_TAB_DOWN_OFFSET = 3;

    private InventoryJournalButtonLayout() {}

    public static Layout resolve(HandledScreenAccessor accessor, boolean preferTopRightFallback) {
        if (preferTopRightFallback && MODE == LayoutMode.BOOKMARK_TAB_RIGHT) {
            return bookmarkTabTopRight(accessor);
        }
        return switch (MODE) {
            case FLOATING_TOP_RIGHT -> floatingTopRight(accessor);
            case BOOKMARK_TAB_TOP_RIGHT -> bookmarkTabTopRight(accessor);
            case BOOKMARK_TAB_RIGHT -> bookmarkTabRight(accessor);
        };
    }

    private static Layout floatingTopRight(HandledScreenAccessor accessor) {
        return new Layout(
                LayoutMode.FLOATING_TOP_RIGHT,
                accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth() - FLOATING_BUTTON_SIZE - FLOATING_RIGHT_OFFSET,
                accessor.villageQuest$getY() + FLOATING_TOP_OFFSET,
                FLOATING_BUTTON_SIZE,
                FLOATING_BUTTON_SIZE,
                0,
                0,
                0
        );
    }

    private static Layout bookmarkTabRight(HandledScreenAccessor accessor) {
        int inventoryRight = accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth();
        int x = inventoryRight - SIDE_TAB_WIDTH + SIDE_TAB_COLLAPSED_VISIBLE;
        int y = accessor.villageQuest$getY()
                + (accessor.villageQuest$getBackgroundHeight() / 2)
                - (SIDE_TAB_HEIGHT / 2)
                + SIDE_TAB_VERTICAL_OFFSET;
        int slideX = SIDE_TAB_WIDTH - SIDE_TAB_OVERLAP - SIDE_TAB_COLLAPSED_VISIBLE;
        return new Layout(LayoutMode.BOOKMARK_TAB_RIGHT, x, y, SIDE_TAB_WIDTH, SIDE_TAB_HEIGHT, SIDE_TAB_OVERLAP, slideX, 0);
    }

    private static Layout bookmarkTabTopRight(HandledScreenAccessor accessor) {
        int inventoryRight = accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth();
        int x = inventoryRight - TOP_TAB_WIDTH - TOP_TAB_RIGHT_OFFSET;
        int y = accessor.villageQuest$getY() - TOP_TAB_HEIGHT + TOP_TAB_COLLAPSED_VISIBLE + TOP_TAB_DOWN_OFFSET;
        int slideY = -(TOP_TAB_HEIGHT - TOP_TAB_OVERLAP - TOP_TAB_COLLAPSED_VISIBLE);
        return new Layout(LayoutMode.BOOKMARK_TAB_TOP_RIGHT, x, y, TOP_TAB_WIDTH, TOP_TAB_HEIGHT, TOP_TAB_OVERLAP, 0, slideY);
    }

    public record Layout(LayoutMode mode, int x, int y, int width, int height, int overlap, int slideX, int slideY) {
        public int contentWidth() {
            return Math.max(1, width - overlap);
        }

        public int contentHeight() {
            return Math.max(1, height - overlap);
        }

        public int expandedX() {
            return x + slideX;
        }

        public int expandedY() {
            return y + slideY;
        }

        public int renderX(float progress) {
            float clamped = Math.max(0.0f, Math.min(1.0f, progress));
            return x + Math.round(slideX * clamped);
        }

        public int renderY(float progress) {
            float clamped = Math.max(0.0f, Math.min(1.0f, progress));
            return y + Math.round(slideY * clamped);
        }
    }

    public enum LayoutMode {
        FLOATING_TOP_RIGHT,
        BOOKMARK_TAB_RIGHT,
        BOOKMARK_TAB_TOP_RIGHT
    }
}
