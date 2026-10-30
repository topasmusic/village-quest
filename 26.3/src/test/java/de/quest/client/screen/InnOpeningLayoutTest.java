package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.BuildingPayloads;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;

/** Actual render boundary, no world/client starts. */
final class InnOpeningLayoutTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void openingAndFeedbackStayBelowTheShiftedButtonsAndInsideTheFrame()throws Exception{
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class)){
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            String opening="Gasthaus-Anlaufzeit: 0/40 Min. Noch 4000 Betriebszeit.";
            var v=new BuildingPayloads.View(UUID.randomUUID(),0,true,BlockPos.ZERO,BuildingScanner.Role.INN,BuildingScanner.Range.BASIC,
                    BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,true,false,3,3,true,"Home",List.of(),List.of(),
                    "screen.village-quest.building.checking",BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,
                    BuildingPayloads.Diplomacy.EMPTY,true,BuildingPayloads.Binding.REGISTERED,Component.literal(opening));
            var screen=new BuildingScreen(v);var font=mock(Font.class);var field=Screen.class.getDeclaredField("font");field.setAccessible(true);field.set(screen,font);
            var method=BuildingScreen.class.getDeclaredMethod("renderAnchor",GuiGraphics.class,int.class,int.class,int.class,int.class);method.setAccessible(true);
            var g=mock(GuiGraphics.class);method.invoke(screen,g,0,0,-1,-1);
            // Real button helper adds 24: 226+24..268. Leave at least six pixels after it.
            theme.verify(()->VillageUiTheme.drawWrappedScaled(eq(g),eq(font),eq(opening),anyInt(),intThat(y->y>=274 && y+24<=308),eq(220),anyInt(),eq(.72f),eq(3)));
            theme.verify(()->VillageUiTheme.drawWrappedScaled(eq(g),eq(font),eq(Component.translatable("screen.village-quest.building.checking").getString()),anyInt(),intThat(y->y>=308 && y+32<=346),eq(220),anyInt(),eq(.72f),eq(4)));
        }
    }
}
