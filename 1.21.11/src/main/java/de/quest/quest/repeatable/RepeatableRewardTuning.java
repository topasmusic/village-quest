package de.quest.quest.repeatable;

import net.minecraft.item.ItemStack;

public final class RepeatableRewardTuning {
    private RepeatableRewardTuning() {}

    public static long adjustCurrency(long baseReward, RepeatableTargetProfile profile) {
        if (baseReward <= 0L) {
            return 0L;
        }
        return adjustDiscrete(baseReward, profile, 0.88d, 1.18d);
    }

    public static int adjustLevels(int baseReward, RepeatableTargetProfile profile) {
        if (baseReward <= 0) {
            return 0;
        }
        return (int) adjustDiscrete(baseReward, profile, 0.85d, 1.2d);
    }

    public static int adjustReputation(int baseReward, RepeatableTargetProfile profile) {
        if (baseReward <= 0) {
            return 0;
        }
        return (int) adjustDiscrete(baseReward, profile, 0.9d, 1.2d);
    }

    public static ItemStack adjustRewardStack(ItemStack baseReward, RepeatableTargetProfile profile) {
        if (baseReward == null || baseReward.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack adjusted = baseReward.copy();
        if (!adjusted.isStackable()) {
            return adjusted;
        }
        int currentCount = adjusted.getCount();
        int nextCount = switch (profile == null ? RepeatableTargetProfile.NORMAL : profile) {
            case LIGHT -> currentCount > 1 ? currentCount - 1 : currentCount;
            case NORMAL -> currentCount;
            case HEAVY -> Math.min(adjusted.getMaxCount(), currentCount + 1);
        };
        adjusted.setCount(Math.max(1, nextCount));
        return adjusted;
    }

    private static long adjustDiscrete(long baseReward,
                                       RepeatableTargetProfile profile,
                                       double lightFactor,
                                       double heavyFactor) {
        RepeatableTargetProfile effectiveProfile = profile == null ? RepeatableTargetProfile.NORMAL : profile;
        return switch (effectiveProfile) {
            case LIGHT -> Math.max(1L, (long) Math.floor(baseReward * lightFactor));
            case NORMAL -> baseReward;
            case HEAVY -> Math.max(baseReward + 1L, (long) Math.ceil(baseReward * heavyFactor));
        };
    }
}
