package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.Payloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class JournalHomeInputTest {
    @org.junit.jupiter.api.BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static JournalScreenData data(boolean daily, boolean weekly, boolean story, boolean pilgrim, boolean special, String next) {
        var c = Component.literal("Saved progress, exact server value");
        return new JournalScreenData(10, 5, 2, 5, 13, 0, 0, 0, 0, 0,
                false, false, false, false, false, false,
                daily, c, c, weekly, c, c, story, c, c, pilgrim, c, c, special, c, c,
                false, false, false, false, false, false, false, false, List.of(), Component.literal(next),
                new Payloads.NetworkSummaryData(0, 0, 0, c, c, c), List.of(), List.of());
    }
    private static Object get(JournalScreen screen, String name) throws Exception {
        var field = JournalScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    @Test void independentKingdomLocalAndCommissionCardsRemainAccessibleWithoutAnActiveCoreStory() {
        var c=Component.empty();
        var quests=List.of("story:kingdom:living_kingdoms","story:local:2:shared_table","story:commission:legacy:winter_feed")
                .stream().map(id -> new de.quest.quest.JournalActiveQuest(new de.quest.quest.TrackerEntry(id,"story",Component.literal(id),
                        Component.literal("Short current objective")),List.of(Component.literal("Full server detail")),-1)).toList();
        var view=new JournalScreenData(10,0,0,3,0,0,0,0,0,0,
                false,false,false,false,false,false,
                false,c,c,false,c,c,false,c,c,false,c,c,false,c,c,
                false,false,false,false,false,false,false,false,List.of(),c,
                new Payloads.NetworkSummaryData(0,0,0,c,c,c),List.of(),List.of(),quests);
        assertEquals(JournalScreen.HomeDestination.QUESTS,JournalScreen.homeDestination(view));
        assertEquals(quests.stream().map(q -> q.entry().id()).toList(),new JournalContentBuilder(view).activeQuestCards().stream().map(JournalCard::id).toList());
        assertEquals(3,view.activeTitles().size());
        assertTrue(new JournalContentBuilder(view).overviewCards().getLast().details().stream().anyMatch(line -> line.getString().equals("Short current objective")));
    }
    @Test void serverLockedStoryPreviewRemainsVisibleWithoutBecomingAnActiveQuest() {
        var c=Component.empty();
        var view=new JournalScreenData(10,0,0,0,0,0,0,0,0,0,
                false,false,false,false,false,false,
                false,c,c,false,c,c,false,Component.literal("Failing Harvest"),
                Component.translatable("quest.village-quest.guidance.first_daily"),false,c,c,false,c,c,
                false,false,false,false,false,false,false,false,List.of(),c,
                new Payloads.NetworkSummaryData(0,0,0,c,c,c),List.of(),List.of());
        var cards=new JournalContentBuilder(view).activeQuestCards();
        assertEquals(List.of("story_locked"),cards.stream().map(JournalCard::id).toList());
        assertFalse(view.storyActive);
    }
    private static void font(JournalScreen screen) throws Exception {
        var font = mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length() * 6);
        var field = Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen, font);
    }
    private static Minecraft clientWithSound() {
        var mc=mock(Minecraft.class);
        var sound=mock(net.minecraft.client.sounds.SoundManager.class);
        when(mc.getSoundManager()).thenReturn(sound);
        return mc;
    }
    private static void click(JournalScreen screen, int x, int y) {
        double scale = 1/screen.responsiveDrag(1,520,294);
        double mx = screen.width / 2.0 + (x - 260) * scale;
        double my = screen.height / 2.0 + (y - 147) * scale;
        assertTrue(screen.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false));
    }
    private static boolean scroll(JournalScreen screen,double x,double y) {
        double scale=1/screen.responsiveDrag(1,520,294);
        return screen.mouseScrolled(screen.width/2.0+(x-screen.width/2.0)*scale,
                screen.height/2.0+(y-screen.height/2.0)*scale,0,-1);
    }
    @Test void nextActionUsesExistingQuestVillageAndQuestmasterDestinations() {
        assertEquals(JournalScreen.HomeDestination.QUESTS, JournalScreen.homeDestination(data(false, false, true, false, false, "Village")));
        assertEquals(JournalScreen.HomeDestination.QUESTS, JournalScreen.homeDestination(data(false, false, false, true, false, "")));
        assertEquals(JournalScreen.HomeDestination.VILLAGES, JournalScreen.homeDestination(data(false, false, false, false, false, "Village")));
        assertEquals(JournalScreen.HomeDestination.QUEST_MASTER, JournalScreen.homeDestination(data(false, false, false, false, false, "")));
    }
    @Test void settingsGearUsesTheLeftFrameAtEveryResponsiveScaleWithoutAnOldRightHandTarget() throws Exception {
        try (var client = mockStatic(Minecraft.class); var tour = mockStatic(UiTestTour.class);
             var network = mockStatic(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            network.when(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                    de.quest.network.QuestSetupPayloads.Open.ID)).thenReturn(true);
            for (int[] size : new int[][]{{800,500},{480,270},{320,200}}) {
                var screen = new JournalScreen(data(false,false,false,false,false,""));
                screen.width=size[0]; screen.height=size[1]; font(screen);
                click(screen, 14, 14);
                network.verify(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new de.quest.network.QuestSetupPayloads.Open()));
                network.clearInvocations();
                double scale=1/screen.responsiveDrag(1,520,294);
                screen.mouseClicked(new MouseButtonEvent(screen.width/2.0+(420-260)*scale,
                        screen.height/2.0+(13-147)*scale,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false);
                network.verify(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(any()),never());
            }
        }
    }
    @Test void homeActionFocusesPriorityQuestAndKeepsAllFiveActionsAtSeveralSizes() throws Exception {
        try (var client = mockStatic(Minecraft.class); var tour = mockStatic(UiTestTour.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            for (int[] size : new int[][]{{600,450},{320,200},{480,270}}) {
                var screen = new JournalScreen(data(true,true,true,true,true,"Village"));
                screen.width = size[0]; screen.height = size[1]; font(screen);
                click(screen, 430, 166);
                assertEquals("QUESTS", get(screen,"section").toString());
                assertEquals("story", get(screen,"expandedCardId"));
                var method = JournalScreen.class.getDeclaredMethod("cardsForSection"); method.setAccessible(true);
                @SuppressWarnings("unchecked") var cards = (List<JournalCard>) method.invoke(screen);
                assertEquals(List.of("story","special","weekly","daily","pilgrim"), cards.stream().map(JournalCard::id).toList());
                assertTrue(cards.stream().filter(c -> c.id().equals("daily") || c.id().equals("weekly")).allMatch(c -> c.cancelAction() >= 0));
            }
            tour.verify(() -> UiTestTour.send(any()), never());
        }
    }
    @Test void trustShortcutAndVillageNextStepUseRealNetworkSubpages() throws Exception {
        try (var client = mockStatic(Minecraft.class); var tour = mockStatic(UiTestTour.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            var screen = new JournalScreen(data(false,false,false,false,false,"Help the village"));
            screen.width=600; screen.height=450; font(screen);
            click(screen, 328, 160);
            assertEquals("NETWORK", get(screen,"section").toString()); assertEquals(1,get(screen,"networkPage"));
            screen.selectTourSection("overview"); click(screen,430,166);
            assertEquals("NETWORK", get(screen,"section").toString()); assertEquals(0,get(screen,"networkPage"));
            assertEquals("network_next",get(screen,"expandedCardId"));
        }
    }
    @Test void realQuestRowsScrollWithoutInterceptingFixedNextAction() throws Exception {
        try (var client = mockStatic(Minecraft.class); var tour = mockStatic(UiTestTour.class); var theme = mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            theme.when(() -> VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenCallRealMethod();
            var screen = new JournalScreen(data(true,true,true,true,true,"Village"));
            screen.width=600; screen.height=450; font(screen);
            var draw=JournalScreen.class.getDeclaredMethod("drawCards",GuiGraphics.class,int.class,int.class,int.class,int.class); draw.setAccessible(true);
            draw.invoke(screen,mock(GuiGraphics.class),40,78,0,0);
            assertTrue((int)get(screen,"scrollMax")>0);
            assertFalse(scroll(screen,40+430,78+160));
            assertTrue(scroll(screen,40+160,78+230));
            assertTrue((int)get(screen,"scrollOffset")>0);
            for (int i=0;i<10;i++) scroll(screen,40+160,78+230);
            assertEquals(get(screen,"scrollMax"),get(screen,"scrollOffset"));
        }
    }
    @Test void pinAndFilterControlsNeverCancelOrRemoveJournalTasksAtAnyResponsiveSize() throws Exception {
        try (var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class);
             var focusStatic=mockStatic(de.quest.client.config.QuestTrackerFocus.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            var focus=mock(de.quest.client.config.QuestTrackerFocus.class);
            focusStatic.when(de.quest.client.config.QuestTrackerFocus::current).thenReturn(focus);
            for(int[] size:new int[][]{{800,500},{480,270},{320,200}}) {
                var screen=new JournalScreen(data(true,false,false,false,false,""));
                screen.width=size[0];screen.height=size[1];font(screen);screen.selectTourSection("quests");
                click(screen,430,74);
                verify(focus).toggle(eq("daily"),any(Component.class));
                assertEquals(-1,get(screen,"pendingCancelAction"));
                click(screen,420,52);
                verify(focus).toggleOnlyPinned();
                var method=JournalScreen.class.getDeclaredMethod("cardsForSection");method.setAccessible(true);
                @SuppressWarnings("unchecked") var cards=(List<JournalCard>)method.invoke(screen);
                assertEquals(List.of("daily","weekly_reset"),cards.stream().map(JournalCard::id).toList(),
                        "The fixture's completed Weekly reset card must remain available too");
                clearInvocations(focus);
            }
            tour.verify(()->UiTestTour.send(any()),never());
        }
    }
    @Test void pinnedDailyBecomesTheHomeNextActionAndItsOpenButtonTargetsThatQuest() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);
            var focusStatic=mockStatic(de.quest.client.config.QuestTrackerFocus.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            var focus=mock(de.quest.client.config.QuestTrackerFocus.class);
            focusStatic.when(de.quest.client.config.QuestTrackerFocus::current).thenReturn(focus);
            when(focus.matches(eq("daily"),any(Component.class))).thenReturn(true);
            var view=data(true,true,true,true,true,"Village");
            var next=new JournalContentBuilder(view).overviewCards().get(2);
            assertTrue(next.details().stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("screen.village-quest.journal.active.daily_hint")));
            var screen=new JournalScreen(view);screen.width=600;screen.height=450;font(screen);
            click(screen,430,166);
            assertEquals("daily",get(screen,"expandedCardId"));
            tour.verify(()->UiTestTour.send(any()),never());
        }
    }
    @Test void homeInstructionKeepsTheConcreteLocalizedActionSeparateFromTheFullObjectiveList() {
        var guidance=Component.translatable("quest.village-quest.story.living_kingdoms.next.place","Home")
                .append("\nTarget and objective counters");
        var card=new JournalCard("overview_next",Component.literal("Next"),Component.literal("Village story"),List.of(guidance),0,-1);
        var instruction=JournalScreen.homeInstruction(card).orElseThrow();
        assertEquals(Component.translatable("quest.village-quest.story.living_kingdoms.next.place","Home"),instruction);
        assertTrue(instruction.getSiblings().isEmpty());
        assertTrue(card.details().getFirst().getString().contains("Target and objective counters"));
        assertTrue(JournalScreen.homeInstruction(new JournalCard("overview_next",Component.empty(),Component.empty(),
                List.of(Component.literal("Ordinary quest progress")),0,-1)).isEmpty());
    }
    @Test void cancellationNeedsSecondClickAndNavigationDisarmsIt() throws Exception {
        try (var client = mockStatic(Minecraft.class); var tour = mockStatic(UiTestTour.class)) {
            var mc=clientWithSound(); client.when(Minecraft::getInstance).thenReturn(mc);
            var screen = new JournalScreen(data(true,false,false,false,false,""));
            screen.width=600; screen.height=450; font(screen);
            screen.selectTourSection("quests");
            click(screen,402,74);
            tour.verify(() -> UiTestTour.send(any()), never());
            assertNotEquals(-1,get(screen,"pendingCancelAction"));
            click(screen,50,50); // Overview navigation must forget a destructive intention.
            assertEquals(-1,get(screen,"pendingCancelAction"));
            click(screen,50,82); // Quests
            click(screen,402,74);
            tour.verify(() -> UiTestTour.send(any()), never());
            click(screen,402,74);
            tour.verify(() -> UiTestTour.send(any(Payloads.JournalActionPayload.class)), times(1));
        }
    }
    @Test void firstJournalHintTargetsTheActualQuestmasterHomeButton() throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);
            var theme=mockStatic(VillageUiTheme.class);
            var visuals=mockStatic(de.quest.client.ui.JournalV3Visuals.class);
            var tutorial=mockStatic(de.quest.client.ui.InventoryJournalTutorialState.class);
            var hint=mockStatic(de.quest.client.ui.TutorialHintRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            tutorial.when(de.quest.client.ui.InventoryJournalTutorialState::shouldShowQuestMasterButtonHint).thenReturn(true);
            theme.when(()->VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenAnswer(a->a.getArgument(1));
            theme.when(()->VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
            var screen=new JournalScreen(data(false,false,false,false,false,""));
            // Reuse the screen-font setup used by the real input regressions.
            var font=mock(Font.class);
            var fontField=Screen.class.getDeclaredField("font");fontField.setAccessible(true);fontField.set(screen,font);
            screen.width=800;screen.height=500;
            var graphics=mock(GuiGraphics.class);when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
            screen.render(graphics,0,0,0);
            hint.verify(()->de.quest.client.ui.TutorialHintRenderer.drawHint(eq(graphics),eq(font),any(Component.class),
                    eq(800),eq(500),eq(379),eq(287),eq(70),eq(15),
                    eq(de.quest.client.ui.TutorialHintRenderer.Placement.ABOVE),eq(true),anyInt()));
            var activeClient=mock(Minecraft.class);
            activeClient.player=mock(net.minecraft.client.player.LocalPlayer.class);
            var clientField=Screen.class.getDeclaredField("minecraft");clientField.setAccessible(true);clientField.set(screen,activeClient);
            tour.when(UiTestTour::active).thenReturn(true); // Close through the existing no-game test tour hook.
            var handle=JournalScreen.class.getDeclaredMethod("handleHomeClick",int.class,int.class,int.class,int.class);
            handle.setAccessible(true);int left=30,top=40;var target=JournalScreen.homeQuestMasterTarget(left,top);
            assertTrue((boolean)handle.invoke(screen,target.x()+target.width()/2,target.y()+target.height()/2,left,top));
            tour.verify(()->UiTestTour.command("vq questmaster"));
        }
    }
}
