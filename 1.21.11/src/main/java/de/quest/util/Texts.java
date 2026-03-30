package de.quest.util;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Texts {
    private static final Text OPEN = Text.literal("[").formatted(Formatting.WHITE);
    private static final Text CLOSE = Text.literal("]").formatted(Formatting.WHITE);

    private Texts() {}

    public static Text tr(String key, Object... args) {
        return Text.translatable(key, args);
    }

    public static Text dailyTitle(Text questName, Formatting questColor) {
        return Text.empty()
                .append(Text.translatable("text.village-quest.quest.daily_prefix").formatted(Formatting.GRAY))
                .append(questName.copy().formatted(questColor));
    }

    public static Text completedTitle(Text questName, Formatting questColor) {
        return Text.empty()
                .append(Text.translatable("text.village-quest.quest.completed_prefix").formatted(Formatting.GRAY))
                .append(questName.copy().formatted(questColor));
    }

    public static Text acceptedTitle(Text questName, Formatting questColor) {
        return Text.empty()
                .append(Text.translatable("text.village-quest.quest.accepted_prefix").formatted(Formatting.GRAY))
                .append(questName.copy().formatted(questColor));
    }

    public static Text bracket(Text inner) {
        return Text.empty()
                .append(OPEN.copy())
                .append(inner)
                .append(CLOSE.copy());
    }

    public static Text turnInMissing(Text label, int current, int target) {
        return Text.translatable("text.village-quest.turnin_missing.1", label, current, target)
                .formatted(Formatting.RED);
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB) {
        return Text.translatable(
                        "text.village-quest.turnin_missing.2",
                        labelA,
                        currentA,
                        targetA,
                        labelB,
                        currentB,
                        targetB)
                .formatted(Formatting.RED);
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB,
                                     Text labelC, int currentC, int targetC) {
        return Text.translatable(
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
                .formatted(Formatting.RED);
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB,
                                     Text labelC, int currentC, int targetC,
                                     Text labelD, int currentD, int targetD) {
        return Text.translatable(
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
                .formatted(Formatting.RED);
    }
}
