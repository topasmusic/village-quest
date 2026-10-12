package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class CompatScreenTourInputTest {
    @Test void tourPreservesNormalPanelSizeAndPointerCoordinatesAtEveryGuiScale() {
        CompatScreen screen = mock(CompatScreen.class, CALLS_REAL_METHODS);
        try (var tour = mockStatic(UiTestTour.class)) {
            for (int[] viewport : new int[][] {{640, 340}, {512, 272}, {320, 180}, {854, 480}}) {
                screen.width = viewport[0];
                screen.height = viewport[1];
                for (int[] panel : new int[][] {{416, 234}, {480, 270}, {360, 240}}) {
                    tour.when(UiTestTour::active).thenReturn(false);
                    double normalDrag = screen.responsiveDrag(10, panel[0], panel[1]);
                    int normalX = screen.responsiveMouseX(100, panel[0], panel[1]);
                    int normalY = screen.responsiveMouseY(80, panel[0], panel[1]);
                    tour.when(UiTestTour::active).thenReturn(true);
                    assertEquals(normalDrag, screen.responsiveDrag(10, panel[0], panel[1]),
                            "Tour must use the original panel scale");
                    assertEquals(normalX, screen.responsiveMouseX(100, panel[0], panel[1]));
                    assertEquals(normalY, screen.responsiveMouseY(80, panel[0], panel[1]));
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
