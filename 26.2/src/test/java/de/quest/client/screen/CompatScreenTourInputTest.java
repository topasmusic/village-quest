package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class CompatScreenTourInputTest {
    @Test void tourMatchesProductionSizeAndPointerRoundTripsAtEveryGuiScale() {
        CompatScreen screen = mock(CompatScreen.class, CALLS_REAL_METHODS);
        try (var tour = mockStatic(UiTestTour.class)) {
            for (int[] viewport : new int[][] {{640,340},{512,272},{320,180},{854,480},{1280,720}}) {
                screen.width=viewport[0];screen.height=viewport[1];
                for(int[] panel:new int[][]{{520,294},{560,315},{560,339},{448,366},
                        {416,364},{416,330},{416,290},{416,264},{416,200},{432,326},{432,292}}) {
                    tour.when(UiTestTour::active).thenReturn(false);
                    double normalDrag=screen.responsiveDrag(10,panel[0],panel[1]);
                    tour.when(UiTestTour::active).thenReturn(true);
                    double scale=10/screen.responsiveDrag(10,panel[0],panel[1]);
                    if(viewport[0]==1280) assertEquals(.96096,scale,1e-6,
                            "Every window is ten percent larger than revision .50 on a roomy viewport");
                    assertEquals(normalDrag,screen.responsiveDrag(10,panel[0],panel[1]),1e-6,
                            "The UI tour must show the actual production scale");
                    assertTrue(viewport[1]/2.0+panel[1]*scale/2<=viewport[1]-11,"panel must clear compact navigation");
                    double visualX=viewport[0]/2.0+(100-viewport[0]/2.0)*scale;
                    double visualY=viewport[1]/2.0+(80-viewport[1]/2.0)*scale;
                    assertEquals(100,screen.responsiveMouseX(visualX,panel[0],panel[1]));
                    assertEquals(80,screen.responsiveMouseY(visualY,panel[0],panel[1]));
                }
            }
        }
    }

    @Test void leftClickOnNextIsConsumedBeforeTheUnderlyingScreen() {
        CompatScreen screen = mock(CompatScreen.class, CALLS_REAL_METHODS);
        screen.width = 400;
        screen.height = 300;
        var click = new MouseButtonEvent(200, 285,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        doReturn(false).when(screen).handleMouseClicked(click, false);
        try (var tour = mockStatic(UiTestTour.class)) {
            tour.when(() -> UiTestTour.click(200, 285, 400, 300)).thenReturn(true);
            assertTrue(screen.mouseClicked(click, false));
            tour.verify(() -> UiTestTour.click(200, 285, 400, 300));
            verify(screen, never()).handleMouseClicked(any(), anyBoolean());
        }
    }

    @Test void unconsumedLeftClickStillReachesTheUnderlyingScreen() {
        CompatScreen screen = mock(CompatScreen.class, CALLS_REAL_METHODS);
        var click = new MouseButtonEvent(20, 30,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        doReturn(true).when(screen).handleMouseClicked(click, false);
        try (var tour = mockStatic(UiTestTour.class)) {
            assertTrue(screen.mouseClicked(click, false));
            verify(screen).handleMouseClicked(click, false);
        }
    }

    @Test void rightClickDoesNotTriggerTourNavigation() {
        CompatScreen screen = mock(CompatScreen.class, CALLS_REAL_METHODS);
        var click = new MouseButtonEvent(200, 285,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0));
        doReturn(false).when(screen).handleMouseClicked(click, false);
        try (var tour = mockStatic(UiTestTour.class)) {
            assertFalse(screen.mouseClicked(click, false));
            tour.verifyNoInteractions();
            verify(screen).handleMouseClicked(click, false);
        }
    }
}
