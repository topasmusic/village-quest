package de.quest.client.screen;

import de.quest.economy.CurrencyService;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestCurrencyDetailTest {
    org.mockito.MockedStatic<net.minecraft.client.Minecraft> minecraft;
    @BeforeEach void client() { minecraft=mockStatic(net.minecraft.client.Minecraft.class);minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class)); }
    @AfterEach void close() { minecraft.close(); }
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void allQuestCategoriesKeepEachFormattedCurrencyRewardTogether() throws Exception {
        var screen=new QuestMasterScreen(null);var font=mock(Font.class);
        when(font.width(anyString())).thenAnswer(c->((String)c.getArgument(0)).length()*6);
        var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
        var build=QuestMasterScreen.class.getDeclaredMethod("buildDetailLines",QuestMasterScreen.EntryView.class,int.class);build.setAccessible(true);
        for(String category:List.of("story","daily","weekly","special","pilgrim","repeatable"))
            for(long amount:new long[]{0,1,9,10,11,21,99,100}) for(boolean locked:new boolean[]{false,true}) {
                var balance=CurrencyService.formatBalance(amount);
                var reward=Component.translatable("screen.village-quest.questmaster.reward.currency",balance);
                var entry=new QuestMasterScreen.EntryView("test",category,Component.literal("Title"),Component.empty(),Component.empty(),false,
                        Component.empty(),List.of(),List.of(),List.of(reward),0,Component.empty(),false,0,Component.empty(),false,locked);
                var lines=(List<?>)build.invoke(screen,entry,6000);var values=new ArrayList<String>();
                for(var line:lines){var text=line.getClass().getDeclaredMethod("text");text.setAccessible(true);values.add((String)text.invoke(line));}
                assertEquals(List.of("screen.village-quest.questmaster.rewards",balance.getString()),values,category+" "+amount+" locked="+locked);
            }
    }
}
