package de.quest.content.story;

final class PeacefulEscortProgress {
    private PeacefulEscortProgress() {}

    static boolean complete(int completedCheckpoints, int requiredCheckpoints, boolean suppliesDelivered) {
        return requiredCheckpoints > 0 && completedCheckpoints >= requiredCheckpoints && suppliesDelivered;
    }
}
