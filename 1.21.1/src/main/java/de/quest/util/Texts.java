package de.quest.util;

import java.util.ArrayList;
import java.util.List;
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
        return turnInMissing(new TurnInRequirement(label, current, target));
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB)
        );
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB,
                                     Text labelC, int currentC, int targetC) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC)
        );
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB,
                                     Text labelC, int currentC, int targetC,
                                     Text labelD, int currentD, int targetD) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC),
                new TurnInRequirement(labelD, currentD, targetD)
        );
    }

    public static Text turnInMissing(Text labelA, int currentA, int targetA,
                                     Text labelB, int currentB, int targetB,
                                     Text labelC, int currentC, int targetC,
                                     Text labelD, int currentD, int targetD,
                                     Text labelE, int currentE, int targetE) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC),
                new TurnInRequirement(labelD, currentD, targetD),
                new TurnInRequirement(labelE, currentE, targetE)
        );
    }

    private static Text turnInMissing(TurnInRequirement... requirements) {
        List<TurnInRequirement> missing = new ArrayList<>();
        for (TurnInRequirement requirement : requirements) {
            if (requirement != null && requirement.current() < requirement.target()) {
                missing.add(requirement);
            }
        }
        if (missing.isEmpty()) {
            return Text.empty();
        }

        Object[] arguments = new Object[missing.size() * 3];
        for (int index = 0; index < missing.size(); index++) {
            TurnInRequirement requirement = missing.get(index);
            arguments[index * 3] = requirement.label().copy().formatted(Formatting.RED);
            arguments[index * 3 + 1] = requirement.current();
            arguments[index * 3 + 2] = requirement.target();
        }
        return Text.translatable(
                "text.village-quest.turnin_missing." + missing.size(),
                arguments
        ).formatted(Formatting.RED);
    }

    private record TurnInRequirement(Text label, int current, int target) {}
}
