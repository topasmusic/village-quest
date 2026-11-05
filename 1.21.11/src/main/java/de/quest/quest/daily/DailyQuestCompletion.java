package de.quest.quest.daily;

import de.quest.reputation.ReputationService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record DailyQuestCompletion(
        Component title,
        Component completionLine1,
        Component completionLine2,
        Component completionLine3,
        long currencyReward,
        ItemStack rewardB,
        ItemStack rewardC,
        int levels,
        ReputationService.ReputationTrack reputationTrack,
        int reputationAmount
) {
    public DailyQuestCompletion {
        currencyReward = Math.max(0L, currencyReward);
        rewardB = rewardB == null ? ItemStack.EMPTY : rewardB;
        rewardC = rewardC == null ? ItemStack.EMPTY : rewardC;
        levels = Math.max(0, levels);
        reputationAmount = Math.max(0, reputationAmount);
    }
}
