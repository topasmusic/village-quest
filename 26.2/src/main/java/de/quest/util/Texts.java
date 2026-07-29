package de.quest.util;

import java.util.ArrayList;
import java.util.List;
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
        return turnInMissing(new TurnInRequirement(label, current, target));
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB)
        );
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB,
                                          Component labelC, int currentC, int targetC) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC)
        );
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB,
                                          Component labelC, int currentC, int targetC,
                                          Component labelD, int currentD, int targetD) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC),
                new TurnInRequirement(labelD, currentD, targetD)
        );
    }

    public static Component turnInMissing(Component labelA, int currentA, int targetA,
                                          Component labelB, int currentB, int targetB,
                                          Component labelC, int currentC, int targetC,
                                          Component labelD, int currentD, int targetD,
                                          Component labelE, int currentE, int targetE) {
        return turnInMissing(
                new TurnInRequirement(labelA, currentA, targetA),
                new TurnInRequirement(labelB, currentB, targetB),
                new TurnInRequirement(labelC, currentC, targetC),
                new TurnInRequirement(labelD, currentD, targetD),
                new TurnInRequirement(labelE, currentE, targetE)
        );
    }

    private static Component turnInMissing(TurnInRequirement... requirements) {
        List<TurnInRequirement> missing = new ArrayList<>();
        for (TurnInRequirement requirement : requirements) {
            if (requirement != null && requirement.current() < requirement.target()) {
                missing.add(requirement);
            }
        }
        if (missing.isEmpty()) {
            return Component.empty();
        }

        Object[] arguments = new Object[missing.size() * 3];
        for (int index = 0; index < missing.size(); index++) {
            TurnInRequirement requirement = missing.get(index);
            arguments[index * 3] = requirement.label().copy().withStyle(ChatFormatting.RED);
            arguments[index * 3 + 1] = requirement.current();
            arguments[index * 3 + 2] = requirement.target();
        }
        return Component.translatable(
                "text.village-quest.turnin_missing." + missing.size(),
                arguments
        ).withStyle(ChatFormatting.RED);
    }

    private record TurnInRequirement(Component label, int current, int target) {}
}
