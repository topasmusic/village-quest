package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.BuildingPayloads;
import de.quest.network.VillageCheckPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class VillageDiagnosisInputTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:List.of(net.minecraft.world.item.Items.COMPOSTER,net.minecraft.world.item.Items.OAK_DOOR)) item.builtInRegistryHolder().bindComponents(components);
    }
    private static void font(Screen screen,Font font) throws Exception { var f=Screen.class.getDeclaredField("font"); f.setAccessible(true); f.set(screen,font); }
    private static Object get(Screen screen,String name) throws Exception { var f=screen.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(screen); }
    private static void click(CompatScreen screen,int x,int y) {
        boolean building=screen instanceof BuildingScreen;
        int w=building?448:416,h=building?366:330;
        if(building) { x+=16; y+=10; }
        double scale=1/screen.responsiveDrag(1,w,h);
        double px=screen.width/2f+(x-w/2)*scale,py=screen.height/2f+(y-h/2)*scale;
        assertTrue(screen.mouseClicked(new MouseButtonEvent(px,py,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false));
    }
    @Test void everyAnchorOpensHelpEvenWhenTheMissingFurnishingHasNoMarkerTarget() throws Exception {
        for(var role:BuildingScanner.Role.values()) for(int[] size:List.of(new int[]{600,450},new int[]{320,240})) {
            try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class); var network=mockStatic(UiTestTour.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var row=new BuildingPayloads.Row(BuildingScanner.Check.LIGHT,false,0,1,List.of());
                var view=new BuildingPayloads.View(UUID.randomUUID(),0,true,BlockPos.ZERO,role,BuildingScanner.Range.BASIC,
                        BuildingScanner.Status.INVALID,BuildingPayloads.NONE,0,BuildingPayloads.NONE,-1,false,false,0,0,false,"",List.of(row),List.of(),"");
                var screen=new BuildingScreen(view); screen.width=size[0]; screen.height=size[1];
                var f=mock(Font.class); when(f.width(anyString())).thenReturn(60); font(screen,f);
                theme.when(() -> VillageUiTheme.ellipsize(eq(f),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
                var g=mock(GuiGraphics.class); when(g.pose()).thenReturn(new Matrix3x2fStack(16));
                screen.render(g,0,0,0); click(screen,50,116); assertEquals(0,get(screen,"checkHelp"));
                screen.render(g,0,0,0); click(screen,80,264); assertEquals(0,get(screen,"checkHelp"));
                var escape=mock(KeyEvent.class); when(escape.key()).thenReturn(InputConstants.KEY_ESCAPE);
                assertTrue(screen.keyPressed(escape)); assertEquals(-1,get(screen,"checkHelp"));
                network.verify(() -> UiTestTour.send(any()),never());
            }
        }
    }
    @Test void liveDiagnosisKeepsSelectionAndIgnoresAnUnrelatedStream() throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));var session=UUID.randomUUID();
            var a=new VillageCheckPayload.Marker(BlockPos.ZERO,BuildingScanner.Status.UNKNOWN);
            var b=new VillageCheckPayload.Marker(new BlockPos(1,64,1),BuildingScanner.Status.UNKNOWN);
            var first=new VillageCheckPayload("minecraft:overworld",List.of(a,b),session,true);
            var screen=new VillageCheckScreen(first);var field=VillageCheckScreen.class.getDeclaredField("selected");field.setAccessible(true);field.set(screen,1);
            screen.updateData(new VillageCheckPayload("minecraft:overworld",List.of(b,a),session,false));
            assertEquals(0,get(screen,"selected"));
            var retained=get(screen,"data");screen.updateData(new VillageCheckPayload("minecraft:overworld",List.of(),UUID.randomUUID(),false));
            assertSame(retained,get(screen,"data"));
        }
    }
    @Test void eighthNearbyAnchorRemainsSelectableAtSmallGuiScale() throws Exception {
        try(var client=mockStatic(Minecraft.class); var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var markers=new ArrayList<VillageCheckPayload.Marker>();
            for(int i=0;i<8;i++) markers.add(new VillageCheckPayload.Marker(new BlockPos(i,64,0),BuildingScanner.Status.UNKNOWN));
            var screen=new VillageCheckScreen(new VillageCheckPayload("minecraft:overworld",markers)); screen.width=320; screen.height=240;
            click(screen,80,250); assertEquals(7,get(screen,"selected"));
            tour.verify(() -> UiTestTour.send(any()),never());
        }
    }
}
