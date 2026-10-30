package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class ParallelMaterialObjectiveTest {
    private String label(Component row) {
        var text=(TranslatableContents)row.getContents();
        return text.getArgs().length==3 && text.getArgs()[0] instanceof Component c
                ? ((TranslatableContents)c.getContents()).getKey() : text.getKey();
    }
    private Component tracker(List<Component> progress) {
        var entries=new ArrayList<JournalActiveQuest>();
        QuestTrackerService.add(entries,"daily:test","daily",Component.literal("Test"),progress,-1);
        return entries.getFirst().entry().shortObjective();
    }
    @Test void independentMaterialsRemainVisibleIncludingTheCompletedFirstMaterial() {
        var line=Component.translatable("quest.village-quest.story.failing_harvest.chapter_1.progress",50,50,10,50);
        var result=tracker(List.of(line));
        var rows=QuestShortObjective.rows(result);assertEquals(2,rows.size());
        assertEquals("item.minecraft.wheat",label(rows.get(0)));assertTrue(QuestObjectiveProgress.complete(rows.get(0)));
        assertEquals("item.minecraft.potato",label(rows.get(1)));assertFalse(QuestObjectiveProgress.complete(rows.get(1)));
    }
    @Test void separateMaterialRowsAreShownBeforeEitherOneIsFinished() {
        var result=tracker(List.of(
                Component.translatable("quest.village-quest.story.silent_forge.chapter_1.progress.1",2,16),
                Component.translatable("quest.village-quest.story.silent_forge.chapter_1.progress.2",3,12)));
        assertTrue(result.getString().contains("chapter_1.progress.1"));
        assertTrue(result.getString().contains("chapter_1.progress.2"));
    }
    @Test void missingTurnInShowsEveryMaterialAndItsCurrentInventoryCount() {
        var result=tracker(List.of(Component.translatable("text.village-quest.turnin_missing.triple",
                Component.translatable("item.minecraft.honey_bottle"),10,10,
                Component.translatable("item.minecraft.honeycomb"),2,10,
                Component.translatable("block.minecraft.honey_block"),0,5)));
        assertEquals(List.of("item.minecraft.honey_bottle","item.minecraft.honeycomb","block.minecraft.honey_block"),
                QuestShortObjective.rows(result).stream().map(this::label).toList());
    }
    @Test void genuineSequentialActionStillSelectsTheNextAction() {
        var first=Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.1",0,1);
        var later=Component.translatable("quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.2",0,1);
        assertEquals(QuestShortObjective.first(List.of(first,later)),tracker(List.of(first,later)));
    }
}
