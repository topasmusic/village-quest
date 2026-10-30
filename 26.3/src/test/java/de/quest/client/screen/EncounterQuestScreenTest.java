package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.EncounterQuestPayloads.View;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class EncounterQuestScreenTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void allLanguagesScrollTheLastRuleAboveTheRewardAndButtonsAtSmallViewports()throws Exception{
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class,CALLS_REAL_METHODS)){
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for(String locale:List.of("de_de","en_us","es_es")){
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String prose=json.get("quest.village-quest.encounter.company.rules").getAsString();
                for(var size:new int[][]{{1000,700},{480,270},{320,200}}){
                    var v=new View(UUID.randomUUID(),true,Component.literal("Wanderer"),Component.literal("A little company"),Component.literal(prose.repeat(4)),Component.literal("45 seconds"),Component.literal("QA_FINAL_LINE"),Component.literal("4 emeralds + 2 levels"),4,4,2);
                    var s=new EncounterQuestScreen(v);s.width=size[0];s.height=size[1];
                    var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*7);
                    when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(a->((String)a.getArgument(0)).substring(0,Math.min(((String)a.getArgument(0)).length(),Math.max(0,(int)a.getArgument(1)/7))));
                    var ff=Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(s,font);var lh=Font.class.getDeclaredField("lineHeight");lh.setAccessible(true);lh.setInt(font,9);
                    var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(32));
                    var last=new java.util.concurrent.atomic.AtomicReference<Float>();
                    theme.when(()->VillageUiTheme.drawStringScaled(eq(g),eq(font),eq("QA_FINAL_LINE"),anyFloat(),anyFloat(),anyInt(),anyFloat())).thenAnswer(a->{last.set(a.getArgument(4));return null;});
                    s.render(g,0,0,0);s.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_END,0,0));s.render(g,0,0,0);
                    var controls=EncounterQuestScreen.class.getDeclaredField("controls");controls.setAccessible(true);
                    assertEquals(2,((List<?>)controls.get(s)).size(),"Exactly Accept and Farewell");
                    verify(g,atLeastOnce()).renderItem(argThat(stack->stack.is(net.minecraft.world.item.Items.EMERALD)),anyInt(),anyInt());
                    verify(g,atLeastOnce()).renderItem(argThat(stack->stack.is(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE)),anyInt(),anyInt());
                    int top=(s.height-EncounterQuestScreen.H)/2;
                    assertNotNull(last.get());assertTrue(last.get()>=top+EncounterQuestScreen.TOP);assertTrue(last.get()+9<=top+EncounterQuestScreen.BOTTOM,locale);
                }
            }
        }
    }
}
