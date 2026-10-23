package de.quest.client.screen;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.ui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
final class KingdomHintLayoutTest {
    @Test void wrappedHintsStayInsideEveryManagementPanelAtAllPointerCorners() {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).codePointCount(0,((String)c.getArgument(0)).length())*6);
        for(int[] size:new int[][]{{416,306},{432,248}}) for(int x:new int[]{-12,5,size[0]-5,size[0]+12}) for(int y:new int[]{-12,50,size[1]-5,size[1]+12}) {
            var box=KingdomUiHints.layout(font,List.of(Component.literal("Dry straight bottom stairs facing a table. ".repeat(30)),Component.literal("ABCDEFGHIJK".repeat(60))),x,y,0,0,size[0],size[1]);
            assertTrue(box.x()>=18); assertTrue(box.x()+box.width()<=size[0]-18);
            assertTrue(box.y()>=43); assertTrue(box.y()+box.height()<=size[1]-18);
            assertTrue(box.lines().size()<=10);
            for(var line:box.lines()) assertTrue(font.width(line)*.75f<=box.width()-14);
        }
    }
    @Test void closeGlyphAndHitBoundsKeepFixedSizeForBothPanelShapes() {
        for(int[] size:new int[][]{{416,306},{432,248}}) {
            var b=KingdomUiAssets.closeBounds(12,19,size[0],size[1]);
            assertEquals(12+size[0]-25,b.x());
            assertEquals(22,b.y());
            assertEquals(20,b.width()); assertEquals(20,b.height());
            assertTrue(b.contains(12+size[0]-14,19+13),"Close target must include the painted corner socket center");
            assertTrue(b.contains(b.x()+b.width()/2,b.y()+b.height()/2));
            assertFalse(b.contains(b.x()-1,b.y())); assertFalse(b.contains(b.x()+b.width(),b.y()));
        }
    }
    @Test void actualLocalizedSeatInstructionsFitInThePopupAndDescribeNativeStairs() throws Exception {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        for(String locale:List.of("en_us","de_de","es_es")) try(var in=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
            var d=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:d.entrySet()) if(entry.getKey().startsWith("screen.village-quest.building.hint.")) {
                String hint=entry.getValue().getAsString();
                var box=KingdomUiHints.layout(font,List.of(Component.literal(hint)),200,150,0,0,416,306);
                assertFalse(box.lines().getLast().endsWith("..."),locale+" lost "+entry.getKey());
                assertTrue(VillageUiTheme.wrapScaledLines(font,hint,340,.8f).size()<=6,locale+" detail clipped "+entry.getKey());
            }
            String seats=d.get("screen.village-quest.building.hint.inn.seats").getAsString();
            assertTrue(seats.contains("5")); assertTrue(seats.contains("1–3"));
        }
    }
}
