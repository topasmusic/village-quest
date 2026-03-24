package de.quest.client.ui;

import de.quest.mixin.HandledScreenAccessor;

public final class InventoryJournalButtonLayout {
    // Switch this back to FLOATING_TOP_RIGHT if we want the previous inventory button style again.
    private static final LayoutMode MODE = LayoutMode.BOOKMARK_TAB_RIGHT;
    private static final int FLOATING_BUTTON_SIZE = 18;
    private static final int FLOATING_RIGHT_OFFSET = 9;
    private static final int FLOATING_TOP_OFFSET = 6;
    private static final int TAB_WIDTH = 16;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_OVERLAP = 4;
    private static final int TAB_COLLAPSED_VISIBLE = 5;
    private static final int TAB_VERTICAL_OFFSET = -12;

    private InventoryJournalButtonLayout() {}

    public static Layout resolve(HandledScreenAccessor accessor) {
        return MODE == LayoutMode.FLOATING_TOP_RIGHT
                ? floatingTopRight(accessor)
                : bookmarkTabRight(accessor);
    }

    private static Layout floatingTopRight(HandledScreenAccessor accessor) {
        return new Layout(
                LayoutMode.FLOATING_TOP_RIGHT,
                accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth() - FLOATING_BUTTON_SIZE - FLOATING_RIGHT_OFFSET,
                accessor.villageQuest$getY() + FLOATING_TOP_OFFSET,
                FLOATING_BUTTON_SIZE,
                FLOATING_BUTTON_SIZE,
                0,
                0
        );
    }

    private static Layout bookmarkTabRight(HandledScreenAccessor accessor) {
        int inventoryRight = accessor.villageQuest$getX() + accessor.villageQuest$getBackgroundWidth();
        int x = inventoryRight - TAB_WIDTH + TAB_COLLAPSED_VISIBLE;
        int y = accessor.villageQuest$getY()
                + (accessor.villageQuest$getBackgroundHeight() / 2)
                - (TAB_HEIGHT / 2)
                + TAB_VERTICAL_OFFSET;
        int slideDistance = TAB_WIDTH - TAB_OVERLAP - TAB_COLLAPSED_VISIBLE;
        return new Layout(LayoutMode.BOOKMARK_TAB_RIGHT, x, y, TAB_WIDTH, TAB_HEIGHT, TAB_OVERLAP, slideDistance);
    }

    public record Layout(LayoutMode mode, int x, int y, int width, int height, int overlap, int slideDistance) {
        public int contentWidth() {
            return Math.max(1, width - overlap);
        }

        public int expandedX() {
            return x + slideDistance;
        }

        public int renderX(float progress) {
            float clamped = Math.max(0.0f, Math.min(1.0f, progress));
            return x + Math.round(slideDistance * clamped);
        }
    }

    public enum LayoutMode {
        FLOATING_TOP_RIGHT,
        BOOKMARK_TAB_RIGHT
    }
}
