package de.quest.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;

final class InstructionScrollTest {
    @Test void allLocalizedRulesCanReachTheirLastLineWithinIntroAndCrisisViewports() throws Exception {
        for(String locale:java.util.List.of("de_de","en_us","es_es")) {
            var url=getClass().getResource("/assets/village-quest/lang/"+locale+".json");
            var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(url.openStream(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(String key:java.util.List.of("screen.village-quest.intro.detail_0","screen.village-quest.intro.detail_1","screen.village-quest.building.crisis_time.rules"))
            for(int glyph:java.util.List.of(5,8)) for(int visible:java.util.List.of(4,5)) {
                var font=mock(Font.class);when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*glyph);
                var lineHeight=Font.class.getDeclaredField("lineHeight");lineHeight.setAccessible(true);lineHeight.setInt(font,9);
                var graphics=mock(GuiGraphics.class);when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
                String text=catalog.get(key).getAsString()+" FINAL_RULE_LINE";
                var scroll=new InstructionScroll();
                try(var theme=mockStatic(VillageUiTheme.class,CALLS_REAL_METHODS)) {
                    scroll.draw(graphics,font,text,10,20,266,visible,.65f,0);
                    boolean overflows=scroll.maximum()>0;
                    assertEquals(overflows,scroll.key(new KeyEvent(InputConstants.KEY_END,0,0)));
                    assertEquals(scroll.maximum(),scroll.offset());
                    theme.clearInvocations();scroll.draw(graphics,font,text,10,20,266,visible,.65f,0);
                    theme.verify(()->VillageUiTheme.drawStringScaled(eq(graphics),eq(font),contains("FINAL_RULE_LINE"),eq(10f),floatThat(y->y>=20 && y<20+visible*7),eq(0),eq(.65f)));
                    assertEquals(overflows,scroll.key(new KeyEvent(InputConstants.KEY_HOME,0,0)));assertEquals(0,scroll.offset());
                    assertEquals(overflows,scroll.scroll(-1));assertEquals(overflows?1:0,scroll.offset());
                    scroll.draw(graphics,font,"Another selection",10,20,266,visible,.65f,0);assertEquals(0,scroll.offset());
                }
            }
        }
    }
}
