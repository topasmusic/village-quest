package de.quest.caravan;

final class RouteEmergencyProgress {
    private static final int START_VALUE = 1;
    private static final int TICKS_PER_CHECKPOINT = 10;
    private static final int REQUIRED_CHECKPOINTS = 3;

    private RouteEmergencyProgress() {}

    static int advance(int progress, boolean escortNear) {
        int current = Math.max(START_VALUE, progress);
        return escortNear && !complete(current) ? current + 1 : current;
    }

    static int checkpoints(int progress) {
        return Math.min(REQUIRED_CHECKPOINTS,
                Math.max(0, progress - START_VALUE) / TICKS_PER_CHECKPOINT);
    }

    static boolean complete(int progress) {
        return checkpoints(progress) >= REQUIRED_CHECKPOINTS;
    }
}
