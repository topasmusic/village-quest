package de.quest.quest.story;

import de.quest.reputation.ReputationService;
import net.minecraft.text.Text;

public record StoryChapterCompletion(
        Text title,
        Text completionLine1,
        Text completionLine2,
        Text completionLine3,
        long currencyReward,
        int levels,
        ReputationService.ReputationTrack reputationTrack,
        int reputationAmount,
        VillageProjectType unlockedProject
) {
}
