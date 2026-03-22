package de.quest.quest.daily;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public record DailyQuestCompletion(
        Text title,
        Text completionLine1,
        Text completionLine2,
        Text completionLine3,
        long currencyReward,
        ItemStack rewardB,
        ItemStack rewardC,
        int xp
) {
    public DailyQuestCompletion {
        currencyReward = Math.max(0L, currencyReward);
        rewardB = rewardB == null ? ItemStack.EMPTY : rewardB;
        rewardC = rewardC == null ? ItemStack.EMPTY : rewardC;
    }
}
