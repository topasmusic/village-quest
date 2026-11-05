package de.quest.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

final class QuestTrackerLayoutTest {
    @Test void completedMaterialKeepsItsStatusAndColorWhenAnotherMaterialIsStillMissing() {
        var group=de.quest.quest.QuestShortObjective.current(List.of(net.minecraft.network.chat.Component.translatable(
                "quest.village-quest.story.failing_harvest.chapter_1.progress",16,16,0,8)));
        var rows=de.quest.quest.QuestShortObjective.rows(group);assertEquals(2,rows.size());
        assertTrue(QuestTrackerHud.isCompletedObjective(rows.get(0)));assertFalse(QuestTrackerHud.isCompletedObjective(rows.get(1)));
        assertEquals(List.of("Title","Wheat","Iron","Honey"),QuestTrackerHud.page(List.of("Title","Wheat","Iron","Honey"),List.of(),4,7000).lines());
    }
    @Test void aMaterialGroupNeverDiscardsItsThirdOrLaterRequirement() {
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        var group=net.minecraft.network.chat.Component.empty()
                .append("Wheat 20/50\n").append("Iron 10/50\n").append("Honey 0/5\n").append("Hay 0/12");
        for(int width:new int[]{72,220}) {
            var rows=QuestTrackerHud.objectiveLines(font,group,width);
            assertTrue(String.join(" ",rows).contains("Honey"));
            assertTrue(String.join(" ",rows).contains("Hay"));
        }
    }
    @Test void shortObjectiveUsesAtMostTwoBodyRowsAt720p1080pAndGuiScalesTwoThroughFour() {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        for(int viewport:new int[]{1280,1920}) for(int scale:new int[]{2,3,4}) {
            int width=Math.min(220,viewport/scale-36);
            var rows=QuestTrackerHud.objectiveLines(font,net.minecraft.network.chat.Component.literal("Bring the supplies to the village Questmaster ".repeat(10)),width);
            assertEquals(2,rows.size()); for(var row:rows) assertTrue(font.width(row)<=width);
        }
    }
    @Test void longObjectiveIdentifiersFitTheHudWithoutLosingAnyGlyphs() throws Exception {
        var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).codePointCount(0,((String)c.getArgument(0)).length())*6);
        var wrap=QuestTrackerHud.class.getDeclaredMethod("wrapText",Font.class,String.class,int.class);wrap.setAccessible(true);
        String original="village_quest_".repeat(60);
        for(int width:new int[]{72,220}) {
            @SuppressWarnings("unchecked") var lines=(List<String>)wrap.invoke(null,font,original,width);
            assertEquals(original,String.join("",lines));
            for(String line:lines) assertTrue(font.width(line)<=width);
        }
    }
}
