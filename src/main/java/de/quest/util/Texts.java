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
}
