package de.quest.quest.weekly;

import de.quest.reputation.ReputationService;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public record WeeklyQuestCompletion(
        Text title,
        Text completionLine1,
        Text completionLine2,
        Text completionLine3,
        long currencyReward,
        ItemStack rewardB,
        ItemStack rewardC,
        int levels,
        ReputationService.ReputationTrack reputationTrack,
        int reputationAmount
) {
    public WeeklyQuestCompletion {
        currencyReward = Math.max(0L, currencyReward);
        rewardB = rewardB == null ? ItemStack.EMPTY : rewardB;
        rewardC = rewardC == null ? ItemStack.EMPTY : rewardC;
        levels = Math.max(0, levels);
        reputationAmount = Math.max(0, reputationAmount);
    }
}
