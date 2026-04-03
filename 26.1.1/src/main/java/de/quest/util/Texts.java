package de.quest.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class Texts {
    private static final Component OPEN = Component.literal("[").withStyle(ChatFormatting.WHITE);
    private static final Component CLOSE = Component.literal("]").withStyle(ChatFormatting.WHITE);

    private Texts() {}

    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static Component dailyTitle(Component questName, ChatFormatting questColor) {
        return Component.empty()
                .append(Component.translatable("text.village-quest.quest.daily_prefix").withStyle(ChatFormatting.GRAY))
                .append(questName.copy().withStyle(questColor));
    }

    public static Component completedTitle(Component questName, ChatFormatting questColor) {
        return Component.empty()
                .append(Component.translatable("text.village-quest.quest.completed_prefix").withStyle(ChatFormatting.GRAY))
                .append(questName.copy().withStyle(questColor));
    }

    public static Component acceptedTitle(Component questName, ChatFormatting questColor) {
        return Component.empty()
                .append(Component.translatable("text.village-quest.quest.accepted_prefix").withStyle(ChatFormatting.GRAY))
                .append(questName.copy().withStyle(questColor));
    }

    public static Component bracket(Component inner) {
        return Component.empty()
                .append(OPEN.copy())
                .append(inner)
                .append(CLOSE.copy());
    }

    public static Component turnInMissing(Component label, int current, int target) {
        return Component.translatable("text.village-quest.turnin_missing.1", label, current, target)
                .withStyle(ChatFormatting.RED);
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB) {
        return Component.translatable(
                        "text.village-quest.turnin_missing.2",
                        labelA,
                        currentA,
                        targetA,
                        labelB,
                        currentB,
                        targetB)
                .withStyle(ChatFormatting.RED);
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB,
                                          Component labelC, int currentC, int targetC) {
        return Component.translatable(
                        "text.village-quest.turnin_missing.3",
                        labelA,
                        currentA,
                        targetA,
                        labelB,
                        currentB,
                        targetB,
                        labelC,
                        currentC,
                        targetC)
                .withStyle(ChatFormatting.RED);
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB,
                                          Component labelC, int currentC, int targetC,
                                          Component labelD, int currentD, int targetD) {
        return Component.translatable(
                        "text.village-quest.turnin_missing.4",
                        labelA,
                        currentA,
                        targetA,
                        labelB,
                        currentB,
                        targetB,
                        labelC,
                        currentC,
                        targetC,
                        labelD,
                        currentD,
                        targetD)
                .withStyle(ChatFormatting.RED);
    }
}
