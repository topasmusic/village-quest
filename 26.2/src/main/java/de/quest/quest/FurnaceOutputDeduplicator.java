package de.quest.quest;

/** Keeps the result-slot hook and quick-move fallback from dispatching the same take twice. */
public final class FurnaceOutputDeduplicator {
    private static final ThreadLocal<QuickMoveState> QUICK_MOVE = new ThreadLocal<>();

    private FurnaceOutputDeduplicator() {}

    public static void beginQuickMove() { beginQuickMove(-1); }

    public static void beginQuickMove(int initialOutputCount) {
        QUICK_MOVE.set(new QuickMoveState(Math.max(-1, initialOutputCount)));
    }

    /** Records the amount actually removed by the result slot. */
    public static boolean recordResultSlotRemoval(int amount) {
        QuickMoveState state = QUICK_MOVE.get();
        if (state == null) return true;
        state.resultSlotCalled = true;
        state.removedCount = (int) Math.min(Integer.MAX_VALUE,
                (long) state.removedCount + Math.max(0, amount));
        return false;
    }

    /**
     * Returns the exact nested result-slot removal, falling back to the menu return only when
     * vanilla never called the result slot hook.
     */
    public static int finishQuickMoveCount(int fallbackCount) {
        return finishQuickMoveCount(fallbackCount, -1);
    }

    public static int finishQuickMoveCount(int fallbackCount, int remainingOutputCount) {
        QuickMoveState active = QUICK_MOVE.get();
        QUICK_MOVE.remove();
        if (active == null) return 0;
        if (active.resultSlotCalled) return active.removedCount;
        if (active.initialOutputCount >= 0 && remainingOutputCount >= 0) {
            return Math.max(0, active.initialOutputCount - remainingOutputCount);
        }
        return Math.max(0, fallbackCount);
    }

    /** Source-compatible helpers retained for callers compiled against unreleased.6. */
    public static boolean shouldDispatchFromResultSlot() { return recordResultSlotRemoval(1); }
    public static boolean finishQuickMove() { return finishQuickMoveCount(1) > 0; }

    static boolean hasOpenQuickMove() { return QUICK_MOVE.get() != null; }

    private static final class QuickMoveState {
        private final int initialOutputCount;
        private int removedCount;
        private boolean resultSlotCalled;

        private QuickMoveState(int initialOutputCount) { this.initialOutputCount = initialOutputCount; }
    }
}
