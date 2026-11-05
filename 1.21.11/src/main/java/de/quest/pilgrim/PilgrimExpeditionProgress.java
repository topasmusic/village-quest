package de.quest.pilgrim;

final class PilgrimExpeditionProgress {
    static final int REQUIRED_DISTANCE = 320;
    private static final int MAX_OBSERVED_STEP = 384;

    private PilgrimExpeditionProgress() {}

    record State(
            boolean entered,
            boolean originSet,
            int originX,
            int originZ,
            boolean lastSet,
            int lastX,
            int lastZ,
            int maxDistance,
            int checkpoints,
            boolean returned
    ) {
        static State empty() {
            return new State(false, false, 0, 0, false, 0, 0, 0, 0, false);
        }
    }

    static State tick(State current,
                      boolean inTargetDimension,
                      boolean inOverworld,
                      int x,
                      int z,
                      boolean resourceReady,
                      boolean overworldTarget) {
        State state = current == null ? State.empty() : current;
        if (!inTargetDimension) {
            boolean returned = state.returned()
                    || (!overworldTarget && inOverworld && readyToReturn(state, resourceReady));
            return copy(state, state.maxDistance(), state.checkpoints(), returned,
                    state.lastSet(), state.lastX(), state.lastZ());
        }

        if (!state.originSet()) {
            return new State(true, true, x, z, true, x, z, 0, 0, false);
        }

        int maxDistance = state.maxDistance();
        int lastX = state.lastX();
        int lastZ = state.lastZ();
        boolean lastSet = state.lastSet();
        if (!lastSet || horizontalDistance(lastX, lastZ, x, z) <= MAX_OBSERVED_STEP) {
            maxDistance = Math.max(maxDistance, horizontalDistance(state.originX(), state.originZ(), x, z));
            lastX = x;
            lastZ = z;
            lastSet = true;
        }
        int checkpoints = maxDistance >= REQUIRED_DISTANCE ? 3
                : maxDistance >= 192 ? 2
                : maxDistance >= 96 ? 1 : 0;
        boolean returned = state.returned()
                || (overworldTarget
                && maxDistance >= REQUIRED_DISTANCE
                && checkpoints >= 3
                && resourceReady
                && horizontalDistance(state.originX(), state.originZ(), x, z) <= 32);
        return new State(true, true, state.originX(), state.originZ(), lastSet, lastX, lastZ,
                maxDistance, checkpoints, returned);
    }

    static boolean complete(State state, boolean resourceReady) {
        return state != null && state.entered() && state.maxDistance() >= REQUIRED_DISTANCE
                && state.checkpoints() >= 3 && resourceReady && state.returned();
    }

    private static boolean readyToReturn(State state, boolean resourceReady) {
        return state.entered() && state.maxDistance() >= REQUIRED_DISTANCE
                && state.checkpoints() >= 3 && resourceReady;
    }

    private static State copy(State state,
                              int maxDistance,
                              int checkpoints,
                              boolean returned,
                              boolean lastSet,
                              int lastX,
                              int lastZ) {
        return new State(state.entered(), state.originSet(), state.originX(), state.originZ(),
                lastSet, lastX, lastZ, maxDistance, checkpoints, returned);
    }

    private static int horizontalDistance(int fromX, int fromZ, int toX, int toZ) {
        long dx = (long) toX - fromX;
        long dz = (long) toZ - fromZ;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(dx * dx + dz * dz)));
    }
}
