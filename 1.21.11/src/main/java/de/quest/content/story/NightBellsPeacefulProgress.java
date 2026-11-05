package de.quest.content.story;

import de.quest.content.story.ShadowsTradeRoadEncounterService.VillageMarker;
import net.minecraft.core.BlockPos;

final class NightBellsPeacefulProgress {
    private NightBellsPeacefulProgress() {}

    static boolean inStoryVillage(VillageMarker village, BlockPos actionPos,
                                  boolean bound, int storyX, int storyZ) {
        if (!bound || village == null || actionPos == null
                || village.centerX() != storyX || village.centerZ() != storyZ) {
            return false;
        }
        // Use the full structure footprint, including distant CTOV districts, with
        // the same small outskirts allowance as currentVillage's structure lookup.
        return actionPos.getX() >= (long) village.minX() - 16
                && actionPos.getX() <= (long) village.maxX() + 16
                && actionPos.getZ() >= (long) village.minZ() - 16
                && actionPos.getZ() <= (long) village.maxZ() + 16;
    }

    static boolean complete(boolean inStoryVillage, int chapterIndex,
                            int bellUses,
                            int distinctMarkers,
                            boolean suppliesReady,
                            boolean guardianReady,
                            boolean finalConfirmation) {
        if (!inStoryVillage) return false;
        return switch (chapterIndex) {
            case 0 -> bellUses >= 1 && distinctMarkers >= 3 && suppliesReady;
            case 1, 2 -> distinctMarkers >= 3 && suppliesReady;
            case 3 -> bellUses >= 1 && distinctMarkers >= 4 && suppliesReady
                    && guardianReady && finalConfirmation;
            default -> false;
        };
    }
}
