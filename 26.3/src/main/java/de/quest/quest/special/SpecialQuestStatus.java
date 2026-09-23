package de.quest.quest.special;

import java.util.List;
import net.minecraft.network.chat.Component;

public record SpecialQuestStatus(Component title, List<Component> lines) {
}
