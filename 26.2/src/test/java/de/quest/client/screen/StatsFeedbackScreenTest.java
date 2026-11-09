package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.client.config.GameplayStats;
import de.quest.client.ui.VillageUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

final class StatsFeedbackScreenTest {
    @Test void deliberateScaledClicksSubmitOnlyFixedCategoryAnswers() throws Exception {
        try(var client=mockStatic(Minecraft.class);var stats=mockStatic(GameplayStats.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            stats.when(GameplayStats::feedbackTarget).thenReturn("general");
            stats.when(GameplayStats::enabled).thenReturn(true);
            for(int[] size:new int[][]{{900,600},{320,200}}) {
                var screen=new StatsFeedbackScreen(mock(Screen.class));screen.width=size[0];screen.height=size[1];
                click(screen,40,115);stats.verify(()->GameplayStats.feedback("general","helpful"));stats.clearInvocations();
                click(screen,40,80);click(screen,255,144);stats.verify(()->GameplayStats.feedback("daily","balanced"));stats.clearInvocations();
                var f=StatsFeedbackScreen.class.getDeclaredField("target");f.setAccessible(true);
                rightClick(screen,40,80);assertEquals(0,f.getInt(screen));rightClick(screen,40,80);assertEquals(9,f.getInt(screen));
                click(screen,0,0);stats.verifyNoInteractions();
            }
        }
    }
    @Test void allVisibleTextsFitTheirENDEESAreas() throws Exception {
        for(String locale:new String[]{"en_us","de_de","es_es"}) {
            var json=JsonParser.parseString(new String(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*8);
            for(var entry:json.entrySet()) {
                String key=entry.getKey();if(!key.startsWith("screen.village-quest.stats.feedback."))continue;
                String text=entry.getValue().getAsString();
                if(key.contains(".answer."))assertTrue(font.width(text)<=192,key+" "+locale);
                if(key.contains(".target."))assertTrue(font.width(text)<=406,key+" "+locale);
                if(key.endsWith(".text.notice")||key.endsWith(".text.notice.local"))assertTrue(VillageUiTheme.wrapScaledLines(font,text,422,.8f).size()<=4,locale);
                else if(key.endsWith(".notice"))assertTrue(VillageUiTheme.wrapScaledLines(font,text,422,.85f).size()<=3,locale);
                if(key.endsWith(".limit")||key.endsWith(".disabled")||key.endsWith(".sent"))assertTrue(VillageUiTheme.wrapScaledLines(font,text,422,.85f).size()<=2,locale);
            }
        }
    }
    private static void rightClick(StatsFeedbackScreen s,int x,int y) {
        double scale=1/s.responsiveDrag(1,450,420);
        s.mouseClicked(new MouseButtonEvent(s.width/2.0+(x-225)*scale,s.height/2.0+((s.height-420)/2+y-s.height/2.0)*scale,
                new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0)),false);
    }
    private static void click(StatsFeedbackScreen s,int x,int y) {
        double scale=1/s.responsiveDrag(1,450,420);
        s.mouseClicked(new MouseButtonEvent(s.width/2.0+(x-225)*scale,s.height/2.0+((s.height-420)/2+y-s.height/2.0)*scale,
                new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
    }
}
