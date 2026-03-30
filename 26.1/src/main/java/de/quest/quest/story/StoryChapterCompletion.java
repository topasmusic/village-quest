package de.quest.quest.story;

import de.quest.reputation.ReputationService;
import net.minecraft.network.chat.Component;

public record StoryChapterCompletion(
        Component title,
        Component completionLine1,
        Component completionLine2,
        Component completionLine3,
        long currencyReward,
        int levels,
        ReputationService.ReputationTrack reputationTrack,
        int reputationAmount,
        VillageProjectType unlockedProject
) {
}
