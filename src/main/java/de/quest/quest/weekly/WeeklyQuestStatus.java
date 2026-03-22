package de.quest.quest.weekly;

import net.minecraft.text.Text;

import java.util.List;

public record WeeklyQuestStatus(Text title, List<Text> lines) {
}
