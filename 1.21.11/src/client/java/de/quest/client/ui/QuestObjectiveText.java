package de.quest.client.ui;

import de.quest.quest.QuestObjectiveProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

/** Keep a part's server-provided numeric status on every wrapped line. */
public final class QuestObjectiveText {
    private QuestObjectiveText() {}
    public record Line(String text, boolean complete) {}
    public static List<Line> wrap(Component source, Function<String,List<String>> wrapper) {
        var result = new ArrayList<Line>();
        for (var part : QuestObjectiveProgress.separate(source)) {
            boolean complete = QuestObjectiveProgress.complete(part);
            for (var line : wrapper.apply(part.getString())) result.add(new Line(line, complete));
        }
        return List.copyOf(result);
    }
}
