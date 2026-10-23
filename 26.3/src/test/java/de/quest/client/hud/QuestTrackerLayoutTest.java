package de.quest.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

final class QuestTrackerLayoutTest {
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
