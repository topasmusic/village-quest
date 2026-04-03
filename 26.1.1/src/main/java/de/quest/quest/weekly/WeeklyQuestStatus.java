package de.quest.quest.weekly;

import java.util.List;
import net.minecraft.network.chat.Component;

public record WeeklyQuestStatus(Component title, List<Component> lines) {
}
