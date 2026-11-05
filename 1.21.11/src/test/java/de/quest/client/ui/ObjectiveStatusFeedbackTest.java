package de.quest.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.quest.QuestObjectiveProgress;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class ObjectiveStatusFeedbackTest {
    @Test void aFullTurnInGroupProducesOnlyMaterialRowsWithoutBlankSeparatorObjectives() {
        var group=de.quest.util.Texts.turnInMissing(true,
                Component.translatable("item.minecraft.wheat"),16,16,
                Component.translatable("item.minecraft.potato"),3,8);
        var rows=QuestObjectiveRows.separate(group);assertEquals(2,rows.size());
        assertTrue(QuestObjectiveProgress.complete(rows.get(0)));assertFalse(QuestObjectiveProgress.complete(rows.get(1)));
        var ordinary=(net.minecraft.network.chat.contents.TranslatableContents)de.quest.util.Texts.turnInMissing(
                Component.translatable("item.minecraft.wheat"),16,16,Component.translatable("item.minecraft.potato"),3,8).getContents();
        assertEquals("text.village-quest.turnin_missing.1",ordinary.getKey());assertEquals(3,ordinary.getArgs().length);
    }
    @Test void completedPartsKeepTheirStatusInAllLocalesAfterWrappingAndSegmentation() throws Exception {
        var previous=Language.getInstance();
        try {
            for(String locale:List.of("en_us","de_de","es_es")) {
                var catalog=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("assets/village-quest/lang/"+locale+".json")),StandardCharsets.UTF_8)).getAsJsonObject();
                // A translated row may itself wrap across paragraphs; every fragment keeps the same numeric status.
                catalog.addProperty("screen.village-quest.ui_review.objective_row","%1$s\n%2$s / %3$s");
                var language=mock(Language.class);
                when(language.getOrDefault(anyString())).thenAnswer(c -> catalog.has((String)c.getArgument(0))
                        ? catalog.get((String)c.getArgument(0)).getAsString() : c.getArgument(0));
                when(language.getOrDefault(anyString(),anyString())).thenAnswer(c -> catalog.has((String)c.getArgument(0))
                        ? catalog.get((String)c.getArgument(0)).getAsString() : c.getArgument(1));
                when(language.has(anyString())).thenAnswer(c -> catalog.has((String)c.getArgument(0)));
                Language.inject(language);
                var harvest=Component.translatable("quest.village-quest.weekly.harvest.stage.1a",66,97,20,20);
                var autumn=Component.translatable("quest.village-quest.daily.autumn.progress",13,13,4,13);
                var potato=Component.translatable("quest.village-quest.weekly.harvest.stage.1b",20,20);
                var combined=Component.empty().append(harvest).append(Component.literal("\n")).append(potato).append(autumn);
                var lines=QuestObjectiveText.wrap(combined,text -> Arrays.asList(text.split("\\R",-1)));
                assertTrue(lines.stream().filter(l -> l.text().contains("20 / 20")).allMatch(QuestObjectiveText.Line::complete),locale);
                assertTrue(lines.stream().anyMatch(l -> l.text().equals("13 / 13") && l.complete()),locale);
                assertTrue(lines.stream().anyMatch(l -> l.text().equals("66 / 97") && !l.complete()),locale);
                assertTrue(lines.stream().anyMatch(l -> l.text().equals("4 / 13") && !l.complete()),locale);
                for(var source:List.of(harvest,autumn)) {
                    var parts=QuestObjectiveProgress.separate(source);
                    assertEquals(2,parts.size());
                    for(var part:parts) {
                        var wrapped=QuestObjectiveText.wrap(part,text -> List.of("line one","line two"));
                        assertEquals(wrapped.getFirst().complete(),wrapped.getLast().complete());
                    }
                }
            }
        } finally { Language.inject(previous); }
    }

    @Test void arbitraryNumbersMalformedCountersAndZeroTargetsNeverBecomeCompletionProof() {
        assertFalse(QuestObjectiveProgress.complete(Component.literal("Coordinates 13/13")));
        assertFalse(QuestObjectiveProgress.complete(Component.translatable("custom.quest",20,20)));
        assertFalse(QuestObjectiveProgress.complete(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wave",3,3)));
        assertFalse(QuestObjectiveProgress.complete(Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.survivors",2,2)));
        for(var value:List.of(Component.translatable("quest.village-quest.daily.autumn.progress",13,13,4),
                Component.translatable("quest.village-quest.daily.autumn.progress",13,0,4,13),
                Component.translatable("quest.village-quest.daily.autumn.progress","13",13,4,13))) {
            assertTrue(QuestObjectiveProgress.parts(value).isEmpty());
            assertFalse(QuestObjectiveProgress.complete(value));
        }
    }

    @Test void expeditionPartsRetainFiveIndependentStatusesWithoutTreatingTheWholeLineAsOneGoal() {
        var source=Component.translatable("quest.village-quest.pilgrim.contract.expedition.progress",1,1,320,320,3,3,4,8,0,1);
        var parts=QuestObjectiveProgress.parts(source);
        assertEquals(5,parts.size());assertTrue(parts.subList(0,3).stream().allMatch(QuestObjectiveProgress.Part::complete));
        assertFalse(parts.get(3).complete());assertFalse(parts.get(4).complete());
    }
}
