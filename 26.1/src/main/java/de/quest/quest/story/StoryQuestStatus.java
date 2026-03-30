package de.quest.quest.story;

import java.util.List;
import net.minecraft.network.chat.Component;

public record StoryQuestStatus(Component title, List<Component> lines) {
}
