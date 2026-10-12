package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.VillageNetworkPayloads;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;

final class NoticeJourneyTrustFeedbackTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void deniedTrustExplanationIsDrawnInsideTheBoardAndRefreshRestoresTheWelcome() throws Exception {
        var data = new VillageNetworkPayloads.NoticeJourneyPayload(0,0,0,0,Component.literal("Village"),
                Component.literal("Village Welcome"),Component.literal("Meet three villagers"),
                0,3,0,0,false,ItemStack.EMPTY,0,null,false);
        try (var client = mockStatic(Minecraft.class); var theme = mockStatic(VillageUiTheme.class);
             var tour = mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen = new NoticeJourneyScreen(data); screen.width=512; screen.height=300;
            var font = mock(Font.class); when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length()*5);
            var field=Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
            var graphics = mock(GuiGraphics.class); when(graphics.unwrap()).thenReturn(mock(GuiGraphicsExtractor.class));
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(call -> call.getArgument(1));
            screen.showTrustFeedback("reputation.village-quest.open.unavailable");
            screen.render(graphics,0,0,0);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),
                    eq("reputation.village-quest.open.unavailable"),anyFloat(),anyFloat(),anyInt(),anyFloat()));
            var click = new MouseButtonEvent(100,226,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
            assertTrue(screen.mouseClicked(click,false));
            theme.clearInvocations();
            screen.render(graphics,0,0,0);
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),
                    eq("Meet three villagers"),anyFloat(),anyFloat(),anyInt(),anyFloat()));
        }
    }
}
