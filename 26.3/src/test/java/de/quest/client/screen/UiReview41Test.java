package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import de.quest.commands.UiTestTourSteps;
import de.quest.network.RewardPayloads;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.*;

final class UiReview41Test {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.translatable(item.getDescriptionId())).build());
    }
    private static Object field(Object object,String name)throws Exception {
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    @Test void weeklyResetOpensARealWeeklyRatherThanAnEmptyDailyCategory()throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=(QuestMasterScreen)UiTestTourScreens.create(new UiTestTourSteps.Step("questmaster","weekly_reset"));
            assertEquals("weekly",field(screen,"selectedCategoryId"));
            var data=(QuestMasterScreen.QuestMasterData)field(screen,"data");
            assertEquals(1,data.entries().size());
            var entry=data.entries().getFirst();assertEquals("weekly",entry.categoryId());
            assertEquals(de.quest.quest.weekly.WeeklyQuestGenerator.definition(
                    de.quest.quest.weekly.WeeklyQuestService.WeeklyQuestType.BAKEHOUSE_STOCK).title().getString(),entry.title().getString());
            assertFalse(entry.primaryEnabled());assertFalse(entry.secondaryEnabled());
        }
    }
    @Test void everyPreviewRewardChoiceHasAnActualLabelInAllLocales()throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("reward","choice"));
            var view=(RewardPayloads.View)field(screen,"data");
            for(String locale:List.of("en_us","de_de","es_es")) {
                try(var reader=new InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),StandardCharsets.UTF_8)) {
                    var catalog=JsonParser.parseReader(reader).getAsJsonObject();
                    for(var choice:view.choices()) assertTrue(catalog.has("reward.village-quest.option."+choice.id()),locale+" "+choice.id());
                    assertTrue(catalog.has("screen.village-quest.trade_route.availability.active"));
                }
            }
        }
    }
    @Test void compactResetDurationRoundsUpWithoutAmbiguousColonOrOverflow() {
        assertEquals("6h 24m",de.quest.util.TimeUtil.compactResetDuration((6*60+24)*60000L));
        assertEquals("0h 01m",de.quest.util.TimeUtil.compactResetDuration(1));
        assertEquals("0h 00m",de.quest.util.TimeUtil.compactResetDuration(-1));
        assertFalse(de.quest.util.TimeUtil.compactResetDuration(Long.MAX_VALUE).contains("-"));
    }
    @Test void resetCardsUseShortHeadingsKeepTheExplanationAndHaveNoCancelAction()throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("journal","quests_reset"));
            var data=(JournalScreenData)field(screen,"data");
            var cards=new JournalContentBuilder(data).activeQuestCards();assertEquals(2,cards.size());
            for(var card:cards) {
                assertEquals(-1,card.cancelAction());assertEquals(2,card.details().size());
                assertNotEquals(card.title().getString(),card.details().get(1).getString());
            }
        }
    }
    @Test void longQuestHeadingsWrapAndReserveSpaceBeforeTheSubtitle()throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("journal","quests_reset"));
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var f=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            String title="Deliver the winter reserves to the distant village and return to its Questmaster";
            var card=new JournalCard("daily",net.minecraft.network.chat.Component.literal(title),
                    net.minecraft.network.chat.Component.literal("Daily quest"),List.of(),0,-1);
            var wrap=JournalScreen.class.getDeclaredMethod("cardHeadingLines",JournalCard.class,int.class);wrap.setAccessible(true);
            @SuppressWarnings("unchecked") var lines=(List<String>)wrap.invoke(screen,card,190);
            assertTrue(lines.size()>1);assertEquals(title,String.join(" ",lines));
            var height=JournalScreen.class.getDeclaredMethod("collapsedCardHeight",JournalCard.class,int.class);height.setAccessible(true);
            assertTrue((int)height.invoke(screen,card,190)>=31+(lines.size()-1)*10);
        }
    }
    @Test void councilActionsStayInsideTheParchmentAfterMovingContentUp()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("building","council"));
            screen.width=448;screen.height=366;
            var font=mock(Font.class);var f=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
            var graphics=mock(de.quest.client.compat.GuiGraphics.class);when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
            screen.render(graphics,-1,-1,0);
            for(Object control:(List<?>)field(screen,"controls")) {
                var c=control.getClass();var h=c.getDeclaredMethod("height");h.setAccessible(true);
                if((int)h.invoke(control)!=18) continue;
                var y=c.getDeclaredMethod("y");y.setAccessible(true);
                assertTrue((int)y.invoke(control)+18<=327,"Council action crosses the lower parchment edge");
            }
        }
    }
    @Test void rewardPreviewConfirmationStaysInTheTourAndCloseClearsTheGuard()throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));tour.when(UiTestTour::active).thenReturn(true);
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("reward","choice"));screen.width=416;screen.height=264;
            double scale=1/screen.responsiveDrag(1,416,264);
            var click=new net.minecraft.client.input.MouseButtonEvent(208+(270-208)*scale,132+(238-132)*scale,
                    new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
            assertEquals(270,screen.responsiveMouseX(click.x(),416,264));
            assertEquals(238,screen.responsiveMouseY(click.y(),416,264));
            assertEquals(false,field(screen,"submitted"));
            assertTrue(screen.mouseClicked(click,false),"reward click at "+click.x()+","+click.y()+" button "+click.button());assertEquals(false,field(screen,"submitted"));
            tour.verify(()->UiTestTour.send(any()),never());screen.onClose();tour.verify(UiTestTour::closed);
        }
    }
    @Test void diagnosisCardsAndFooterFitTheSharedParchmentOpening()throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class);
            var assets=mockStatic(de.quest.client.ui.KingdomUiAssets.class,CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=UiTestTourScreens.create(new UiTestTourSteps.Step("villagecheck","overview"));screen.width=416;screen.height=330;
            var f=net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,mock(Font.class));
            var g=mock(de.quest.client.compat.GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
            var rectangles=new java.util.ArrayList<int[]>();
            assets.when(()->de.quest.client.ui.KingdomUiAssets.card(eq(g),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean()))
                    .thenAnswer(a->{rectangles.add(new int[]{a.getArgument(1),a.getArgument(2),a.getArgument(3),a.getArgument(4)});return null;});
            theme.when(()->de.quest.client.ui.VillageUiTheme.drawPaper(eq(g),anyInt(),anyInt(),anyInt(),anyInt()))
                    .thenAnswer(a->{rectangles.add(new int[]{a.getArgument(1),a.getArgument(2),a.getArgument(3),a.getArgument(4)});return null;});
            screen.render(g,-1,-1,0);assertTrue(rectangles.size()>=3);
            for(int[] rect:rectangles) assertTrue(rect[0]>=28&&rect[0]+rect[2]<=388&&rect[1]>=39&&rect[1]+rect[3]<=291,
                    "Diagnosis content crosses the shared parchment opening: "+java.util.Arrays.toString(rect));
        }
    }
}
