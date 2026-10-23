package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.BuildingPayloads;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class KingdomNamingScreenTest {
    @Test void foundingNameAndFocusSurviveRepeatedUnknownAndMissingRequirementRefreshes() throws Exception {
        try(var client=mockStatic(Minecraft.class);var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            UUID session=UUID.randomUUID();var screen=naming(founding(session,1,true,BuildingScanner.Status.VALID,""),"Flussauen",BuildingPayloads.Kind.FOUND);
            var editor=(EditBox)field(screen,"nameField");clearInvocations(editor);
            for(int revision=2;revision<=12;revision++) {
                boolean ready=revision%3==0;
                screen.updateData(founding(session,revision,ready,ready?BuildingScanner.Status.VALID:BuildingScanner.Status.UNKNOWN,""));
                assertEquals(BuildingPayloads.Kind.FOUND,field(screen,"naming"),"Routine refresh discarded an edit");
                assertEquals("Flussauen",editor.getValue());assertTrue(editor.isFocused());
            }
            verify(editor,never()).setVisible(false);verify(editor,never()).setFocused(false);verify(editor,never()).setValue(anyString());network.verifyNoInteractions();
        }
    }
    @Test void foundingKeepsTheDraftUntilServerAcceptanceAndRetriesRejectionsWithTheCurrentRevision() throws Exception {
        try(var client=mockStatic(Minecraft.class);var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));network.when(() -> ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)).thenReturn(true);
            UUID session=UUID.randomUUID();var screen=naming(founding(session,4,true,BuildingScanner.Status.VALID,""),"Flussauen",BuildingPayloads.Kind.FOUND);
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));assertEquals(BuildingPayloads.Kind.FOUND,field(screen,"naming"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
            network.verify(() -> ClientPlayNetworking.send(new BuildingPayloads.Action(session,4,BuildingPayloads.Kind.FOUND,BuildingPayloads.NONE,"Flussauen",0)),times(1));
            screen.updateData(founding(session,5,false,BuildingScanner.Status.UNKNOWN,"screen.village-quest.building.checking"));
            assertEquals(BuildingPayloads.Kind.FOUND,field(screen,"naming"));assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
            screen.updateData(founding(session,6,false,BuildingScanner.Status.INVALID,"screen.village-quest.building.requirements"));
            assertEquals(BuildingPayloads.Kind.FOUND,field(screen,"naming"));assertEquals("Flussauen",((EditBox)field(screen,"nameField")).getValue());
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
            network.verify(() -> ClientPlayNetworking.send(new BuildingPayloads.Action(session,6,BuildingPayloads.Kind.FOUND,BuildingPayloads.NONE,"Flussauen",0)));
            screen.updateData(view(session,7,UUID.randomUUID()));assertNull(field(screen,"naming"));assertEquals(false,field(screen,"closed"));
        }
    }
    @Test void closeRecessCancelsOnlyTheNameDialogAtNormalAndReducedScale() throws Exception {
        for(int[] size:List.of(new int[]{600,450},new int[]{320,240})) try(var client=mockStatic(Minecraft.class);var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class);var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=naming(view(UUID.randomUUID(),2,UUID.randomUUID()),"Riverbend");screen.width=size[0];screen.height=size[1];
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,mock(net.minecraft.client.gui.Font.class));
            var g=mock(de.quest.client.compat.GuiGraphics.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));screen.render(g,0,0,0);
            float scale=.96096f*Math.max(.25f,Math.min(1f,Math.min(size[0]*.82f/448,size[1]*.86f/366)));
            var box=de.quest.client.ui.KingdomUiAssets.closeBounds(0,0,448,366);
            var click=mock(net.minecraft.client.input.MouseButtonEvent.class);when(click.button()).thenReturn(InputConstants.MOUSE_BUTTON_LEFT);
            when(click.x()).thenReturn(size[0]/2.0+(box.x()+box.width()/2-224)*scale);
            when(click.y()).thenReturn(size[1]/2.0+(box.y()+box.height()/2-183)*scale);
            assertTrue(screen.mouseClicked(click,false));assertNull(field(screen,"naming"));assertEquals(false,field(screen,"closed"));network.verifyNoInteractions();
        }
    }
    @Test void escapeReturnsThroughCouncilPanelsWithoutClosingOrSendingAMutation() throws Exception {
        for(String panel:List.of("marketEditing","projectEditing","diplomacyEditing","crisisEditing","releasing","travelEditing")) {
            try(var client=mockStatic(Minecraft.class); var network=mockStatic(ClientPlayNetworking.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var screen=naming(view(UUID.randomUUID(),3,UUID.randomUUID()),"Riverbend");
                set(screen,BuildingScreen.class,"naming",null);
                set(screen,BuildingScreen.class,panel,true);
                if(panel.equals("crisisEditing")) set(screen,BuildingScreen.class,"diplomacyEditing",true);
                if(panel.equals("travelEditing")) set(screen,BuildingScreen.class,"marketEditing",true);
                var options=mock(net.minecraft.client.Options.class);
                set(options,net.minecraft.client.Options.class,"keyInventory",mock(net.minecraft.client.KeyMapping.class));
                var mc=mock(Minecraft.class); set(mc,Minecraft.class,"options",options); set(screen,Screen.class,"minecraft",mc);
                assertTrue(screen.keyPressed(key(InputConstants.KEY_ESCAPE)),panel);
                assertEquals(false,field(screen,panel),panel); assertEquals(false,field(screen,"closed"),panel);
                if(panel.equals("diplomacyEditing")) assertEquals(true,field(screen,"projectEditing"));
                if(panel.equals("crisisEditing")) assertEquals(true,field(screen,"diplomacyEditing"));
                if(panel.equals("travelEditing")) assertEquals(true,field(screen,"marketEditing"));
                network.verifyNoInteractions();
            }
        }
    }
    @Test void enterSavesValidAccentedAndSupplementaryNamesWithCurrentSessionRevision() throws Exception {
        for(String value:List.of("  Peñaluz  ","E\u0301toile","\uD83C\uDF33".repeat(48))) {
            try(var client=mockStatic(Minecraft.class); var network=mockStatic(ClientPlayNetworking.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                network.when(() -> ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)).thenReturn(true);
                var data=view(UUID.randomUUID(),4,UUID.randomUUID()); var screen=naming(data,value);
                assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
                network.verify(() -> ClientPlayNetworking.send(new BuildingPayloads.Action(data.session(),4,BuildingPayloads.Kind.RENAME,data.selectedVillage(),value,0)));
                assertNull(field(screen,"naming")); assertEquals(false,field(screen,"closed"));
            }
        }
    }
    @Test void invalidNamesStayEditableAndEscapeCancelsOnlyTheDialog() throws Exception {
        for(String value:List.of("  ","a".repeat(49),"bad\u00a7cname","bad\u202Ename","bad\nname")) {
            try(var client=mockStatic(Minecraft.class); var network=mockStatic(ClientPlayNetworking.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var screen=naming(view(UUID.randomUUID(),2,UUID.randomUUID()),value);
                assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
                assertEquals(BuildingPayloads.Kind.RENAME,field(screen,"naming"));
                assertTrue(screen.keyPressed(key(InputConstants.KEY_ESCAPE)));
                assertNull(field(screen,"naming")); assertEquals(false,field(screen,"closed")); network.verifyNoInteractions();
            }
        }
    }
    @Test void refreshPreservesAnEditForTheSameVillageAndCancelsItOnTargetChange() throws Exception {
        try(var client=mockStatic(Minecraft.class); var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            UUID session=UUID.randomUUID(),village=UUID.randomUUID(); var screen=naming(view(session,2,village),"Riverbend");
            screen.updateData(view(session,3,village)); assertEquals(BuildingPayloads.Kind.RENAME,field(screen,"naming"));
            screen.updateData(view(session,4,UUID.randomUUID())); assertNull(field(screen,"naming"));
            assertEquals(false,field(screen,"closed")); network.verifyNoInteractions();
        }
    }
    @Test void firstCouncilNameSurvivesCheckingAndRejectionAndClosesOnlyAfterRegistration() throws Exception {
        try(var client=mockStatic(Minecraft.class);var network=mockStatic(ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));network.when(() -> ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)).thenReturn(true);
            UUID session=UUID.randomUUID(),village=UUID.randomUUID();
            var screen=naming(unregistered(session,1,village,""),"Rivermead",BuildingPayloads.Kind.CONFIRM);
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));assertEquals(true,field(screen,"namingPending"));
            screen.updateData(unregistered(session,2,village,"screen.village-quest.building.checking"));
            assertEquals(BuildingPayloads.Kind.CONFIRM,field(screen,"naming"));assertEquals(true,field(screen,"namingPending"));
            screen.updateData(unregistered(session,3,village,"screen.village-quest.building.requirements"));
            assertEquals(BuildingPayloads.Kind.CONFIRM,field(screen,"naming"));assertEquals(false,field(screen,"namingPending"));
            assertTrue(screen.keyPressed(key(InputConstants.KEY_RETURN)));
            network.verify(() -> ClientPlayNetworking.send(new BuildingPayloads.Action(session,3,BuildingPayloads.Kind.CONFIRM,village,"Rivermead",0)));
            screen.updateData(view(session,4,village));assertNull(field(screen,"naming"));
        }
    }
    private static BuildingPayloads.View unregistered(UUID session,long revision,UUID village,String feedback) {
        return new BuildingPayloads.View(session,revision,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                BuildingScanner.Status.VALID,village,0,BuildingPayloads.NONE,-1,true,false,0,0,false,"Old name",List.of(),List.of(),feedback);
    }
    private static BuildingScreen naming(BuildingPayloads.View data,String value) throws Exception { return naming(data,value,BuildingPayloads.Kind.RENAME); }
    private static BuildingScreen naming(BuildingPayloads.View data,String value,BuildingPayloads.Kind kind) throws Exception {
        var screen=new BuildingScreen(data); var editor=mock(EditBox.class); when(editor.getValue()).thenReturn(value); when(editor.isFocused()).thenReturn(true);
        var input=BuildingScreen.class.getDeclaredField("nameField"); input.setAccessible(true); input.set(screen,editor);
        var mc=Screen.class.getDeclaredField("minecraft"); mc.setAccessible(true); mc.set(screen,mock(Minecraft.class));
        var open=BuildingScreen.class.getDeclaredMethod("name",BuildingPayloads.Kind.class); open.setAccessible(true); open.invoke(screen,kind);
        return screen;
    }
    private static Object field(BuildingScreen screen,String name) throws Exception { var f=BuildingScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen); }
    private static void set(Object target,Class<?> type,String name,Object value) throws Exception { var f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value); }
    private static KeyEvent key(int code) { var key=mock(KeyEvent.class); when(key.key()).thenReturn(code); return key; }
    private static BuildingPayloads.View founding(UUID session,long revision,boolean ready,BuildingScanner.Status status,String feedback) {
        return new BuildingPayloads.View(session,revision,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,status,
                BuildingPayloads.NONE,0,BuildingPayloads.NONE,-1,false,ready,3,3,true,"",List.of(),List.of(),feedback);
    }
    private static BuildingPayloads.View view(UUID session,long revision,UUID village) {
        return new BuildingPayloads.View(session,revision,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                BuildingScanner.Status.VALID,village,0,UUID.randomUUID(),0,true,false,3,3,true,"Old name",List.of(),List.of(),"");
    }
}
