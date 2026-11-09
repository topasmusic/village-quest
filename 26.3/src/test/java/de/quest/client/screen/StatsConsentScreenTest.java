package de.quest.client.screen;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.client.config.GameplayStats;
import de.quest.client.ui.VillageUiTheme;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
final class StatsConsentScreenTest {
    @Test void completeConsentFitsInAllThreeLanguagesWithoutEllipsis() throws Exception {
        for (String locale : new String[]{"en_us","de_de","es_es"}) {
            var stream=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"); assertNotNull(stream);
            var catalog=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for (int glyph : new int[]{6,7,8}) {
                var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).codePointCount(0,((String)c.getArgument(0)).length())*glyph);
                for (String key : new String[]{"consent","consent.local"}) {
                    String body=catalog.get("screen.village-quest.stats."+key).getAsString();
                    assertTrue(VillageUiTheme.wrapScaledLines(font,body,422,.85f).size()<=29,locale+" "+key+" glyph="+glyph);
                }
                for(String key:new String[]{"intro","intro.local","collect.body","private.body","note","note.local"}) {
                    int maxLines=key.startsWith("intro")?2:4,width=key.endsWith("body")?402:422;
                    assertTrue(VillageUiTheme.wrapScaledLines(font,catalog.get("screen.village-quest.stats.summary."+key).getAsString(),width,.85f).size()<=maxLines,locale+" "+key+" glyph="+glyph);
                }
                for(String key:new String[]{"collect.title","private.title"})
                    assertTrue(font.width(catalog.get("screen.village-quest.stats.summary."+key).getAsString())*.9f<=402,locale);
                assertTrue(font.width(catalog.get("screen.village-quest.stats.adult_yes").getAsString())<=192,locale);
            }
        }
    }
    @Test void scaledClicksRequireAdultConsentAndDismissingDoesNotEnable() throws Exception {
        try(var client=mockStatic(Minecraft.class);var stats=mockStatic(GameplayStats.class)) {
            var mc=mock(Minecraft.class);client.when(Minecraft::getInstance).thenReturn(mc);
            var gui=mock(net.minecraft.client.gui.Gui.class); var guiField=Minecraft.class.getDeclaredField("gui"); guiField.setAccessible(true); guiField.set(mc,gui);
            var back=mock(Screen.class);stats.when(()->GameplayStats.choose(true)).thenReturn(true);
            for(int[] size:new int[][]{{800,600},{320,200}}) {
                var screen=new StatsConsentScreen(back);screen.width=size[0];screen.height=size[1];
                var field=Screen.class.getDeclaredField("minecraft");field.setAccessible(true);field.set(screen,mc);
                assertFalse(screen.isPauseScreen());
                screen.mouseClicked(pointer(screen,50,242,304),false);stats.verify(()->GameplayStats.choose(true),never());
                screen.mouseClicked(pointer(screen,50,310,390),false);stats.verify(()->GameplayStats.choose(true),never());
                screen.mouseClicked(pointer(screen,50,283,304),false);stats.verify(()->GameplayStats.choose(true));stats.clearInvocations();
                screen.mouseClicked(pointer(screen,250,283,304),false);stats.verify(()->GameplayStats.choose(true),never());
                screen.mouseClicked(pointer(screen,50,242,304),false);stats.verify(()->GameplayStats.choose(true),never());
                screen.mouseClicked(pointer(screen,50,369,390),false);stats.verify(()->GameplayStats.choose(true));stats.clearInvocations();
                screen.onClose();stats.verify(()->GameplayStats.choose(true),never());
            }
            verify(gui,atLeast(4)).setScreen(back);
        }
    }
    @Test void compactOverviewRendersDataGreenAndExclusionsRed() throws Exception {
        try(var client=mockStatic(Minecraft.class);var stats=mockStatic(GameplayStats.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new StatsConsentScreen(mock(Screen.class));screen.width=900;screen.height=600;
            var font=mock(Font.class);var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            var g=mock(de.quest.client.compat.GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
            screen.render(g,0,0,0);
            for(String key:new String[]{"collect","private"}) {
                int color=key.equals("collect")?0xFF2D6837:0xFF9C3C32;
                String text=net.minecraft.network.chat.Component.translatable("screen.village-quest.stats.summary."+key+".body").getString();
                theme.verify(()->VillageUiTheme.drawWrappedScaled(eq(g),eq(font),eq(text),anyInt(),anyInt(),eq(402),eq(color),eq(.85f),eq(4)));
            }
            stats.verify(()->GameplayStats.choose(true),never());
        }
    }
    private static MouseButtonEvent pointer(StatsConsentScreen s,int x,int y,int h) {
        double scale=1/s.responsiveDrag(1,450,h);int left=(s.width-450)/2,top=(s.height-h)/2;
        return new MouseButtonEvent(s.width/2.0+(left+x-s.width/2.0)*scale,s.height/2.0+(top+y-s.height/2.0)*scale,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
    }
}
