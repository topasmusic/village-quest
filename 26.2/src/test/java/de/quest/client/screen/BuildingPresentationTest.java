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
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

/** Actual screen drawing/control composition with a modeled font; no game or world is started. */
final class BuildingPresentationTest {
    @Test void escapeReturnsFromManagementPagesWithoutClosingTheServerSession() throws Exception {
        try(var client=mockStatic(Minecraft.class);var network=mockStatic(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            for(String page:List.of("marketEditing","travelEditing","projectEditing","villageEditing","relationEditing","crisisEditing")) {
                var screen=new BuildingScreen(view());
                var flag=BuildingScreen.class.getDeclaredField(page);flag.setAccessible(true);flag.setBoolean(screen,true);
                screen.onClose();assertFalse(flag.getBoolean(screen),page);
                var closed=BuildingScreen.class.getDeclaredField("closed");closed.setAccessible(true);assertFalse(closed.getBoolean(screen));
            }
            network.verifyNoInteractions();
        }
    }
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.world.item.Items.COMPOSTER.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }
    @Test void roleTitleUsesTheGoldPlaqueAndCloseIsAnUnambiguousGlyph() throws Exception {
        try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new BuildingScreen(view()); screen.width=600; screen.height=450;
            var font=mock(Font.class); when(font.width(anyString())).thenReturn(80);
            var field=Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
            var graphics=mock(GuiGraphics.class); when(graphics.pose()).thenReturn(new Matrix3x2fStack(16));
            screen.render(graphics,0,0,0);
            String title=Component.translatable("screen.village-quest.building.council_title").getString();
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),eq(title),floatThat(x -> x>=76+135 && x<76+300),
                    floatThat(y -> y>=42+7 && y<42+20),anyInt(),anyFloat()),times(2));
            // X pixels must sit inside the frame's painted recess; no second button is overlaid.
            var close=de.quest.client.ui.KingdomUiAssets.closeBounds(76,42,448,366);
            verify(graphics,atLeastOnce()).fill(intThat(x -> x>close.x()&&x<close.x()+close.width()),intThat(y -> y>close.y()&&y<close.y()+close.height()),anyInt(),anyInt(),eq(0xFFFFD366));
            // Existing-village snapshots need not contain any founding observation.
            // Do not turn their absent counters into false missing-population warnings.
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),
                    eq(Component.translatable("screen.village-quest.building.require_adults").getString()),anyFloat(),anyFloat(),anyInt(),anyFloat()),never());
        }
    }
    @Test void councilAllowsSettlementRegistrationBeforePopulationRecognition() throws Exception {
        for(boolean observed:List.of(false,true)) for(boolean job:List.of(false,true)) {
            try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var v=new BuildingPayloads.View(UUID.randomUUID(),0,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                        BuildingScanner.Status.VALID,BuildingPayloads.NONE,0,BuildingPayloads.NONE,0,false,true,3,3,job,"",List.of(),List.of(),"",
                        BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,BuildingPayloads.Diplomacy.EMPTY,observed);
                var screen=new BuildingScreen(v); screen.width=448;screen.height=366;
                var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,mock(Font.class));
                var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(16));screen.render(g,0,0,0);
                theme.verify(() -> VillageUiTheme.drawButton(eq(g),any(),eq(34),eq(276),eq(246),eq(18),
                        eq(Component.translatable("screen.village-quest.building.found").getString()),eq(true),anyBoolean(),eq(false)));
                theme.verify(() -> VillageUiTheme.drawButton(eq(g),any(),anyInt(),anyInt(),anyInt(),anyInt(),eq(Component.translatable("screen.village-quest.building.rename").getString()),anyBoolean(),anyBoolean(),anyBoolean()),never());
            }
        }
    }
    @Test void equalStandingPositionsStillShowTheirDistinctCounterAndContainer() throws Exception {
        try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new BuildingScreen(view()); var font=mock(Font.class);
            var field=Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
            theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
            var graphics=mock(GuiGraphics.class);
            var method=BuildingScreen.class.getDeclaredMethod("renderMarketPlaces",GuiGraphics.class,int.class,int.class,int.class,int.class);
            method.setAccessible(true); method.invoke(screen,graphics,0,0,-1,-1);
            String detail=Component.translatable("screen.village-quest.building.place_counter","1, 64, 0").getString();
            theme.verify(() -> VillageUiTheme.drawStringScaled(eq(graphics),eq(font),eq(detail),anyFloat(),anyFloat(),anyInt(),anyFloat()),atLeastOnce());
        }
    }
    @Test void registeredCouncilShowsItsVillageWithoutRepeatedConfirmationOrAssignment() throws Exception {
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=new BuildingScreen(view());screen.width=448;screen.height=366;
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,mock(Font.class));
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(16));screen.render(g,0,0,0);
            for(String key:List.of("confirm","choose_village")) theme.verify(() -> VillageUiTheme.drawButton(eq(g),any(),anyInt(),anyInt(),anyInt(),anyInt(),
                    eq(Component.translatable("screen.village-quest.building."+key).getString()),anyBoolean(),anyBoolean(),anyBoolean()),never());
        }
    }
    @Test void eachRoleDistinguishesSavedRegistrationFromOpeningAndPendingAreaChange() throws Exception {
        for(var role:BuildingScanner.Role.values()) for(var binding:List.of(BuildingPayloads.Binding.UNREGISTERED,BuildingPayloads.Binding.REGISTERED,BuildingPayloads.Binding.CHANGED)) {
            try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var base=view();var data=new BuildingPayloads.View(base.session(),0,true,BlockPos.ZERO,role,base.range(),base.status(),base.selectedVillage(),0,
                        binding==BuildingPayloads.Binding.UNREGISTERED?BuildingPayloads.NONE:base.official(),0,true,false,3,3,true,"Rivermead",List.of(),
                        List.of(new BuildingPayloads.Choice(base.selectedVillage(),"Rivermead")),"",BuildingPayloads.MarketLayout.EMPTY,BuildingPayloads.Recognition.EMPTY,BuildingPayloads.Diplomacy.EMPTY,true,binding);
                var screen=new BuildingScreen(data);screen.width=448;screen.height=366;
                var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,mock(Font.class));
                var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(16));screen.render(g,0,0,0);
                String key=binding==BuildingPayloads.Binding.CHANGED?"apply_area":role==BuildingScanner.Role.COUNCIL?"open_council":"open_building";
                String label=Component.translatable("screen.village-quest.building."+key,Component.translatable("screen.village-quest.building.role."+role.name().toLowerCase(Locale.ROOT)).getString()).getString();
                theme.verify(() -> VillageUiTheme.drawButton(eq(g),any(),anyInt(),anyInt(),anyInt(),anyInt(),eq(label),eq(true),anyBoolean(),anyBoolean()),
                        binding==BuildingPayloads.Binding.REGISTERED?never():times(1));
                theme.verify(() -> VillageUiTheme.drawButton(eq(g),any(),anyInt(),anyInt(),anyInt(),anyInt(),eq(Component.translatable("screen.village-quest.building.choose_village").getString()),anyBoolean(),anyBoolean(),anyBoolean()),never());
            }
        }
    }
    @Test void everySubpageUsesTheSharedV3BoardAndAnExplicitCloseControl() throws Exception {
        for(String methodName:List.of("renderRelations","renderProjects","renderDiplomacy","renderCrisis","renderMarketTravel","renderMarketPlaces","renderVillageChoices")) {
            try(var client=mockStatic(Minecraft.class); var theme=mockStatic(VillageUiTheme.class)) {
                client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
                var screen=new BuildingScreen(view()); var font=mock(Font.class); when(font.width(anyString())).thenReturn(80);
                var field=Screen.class.getDeclaredField("font"); field.setAccessible(true); field.set(screen,font);
                theme.when(() -> VillageUiTheme.ellipsize(eq(font),anyString(),anyInt())).thenAnswer(c -> c.getArgument(1));
                var graphics=mock(GuiGraphics.class);
                var method=BuildingScreen.class.getDeclaredMethod(methodName,GuiGraphics.class,int.class,int.class,int.class,int.class);
                method.setAccessible(true); method.invoke(screen,graphics,0,0,-1,-1);
                theme.verify(() -> VillageUiTheme.drawFrame(graphics,-16,-10,448,366));
                var close=de.quest.client.ui.KingdomUiAssets.closeBounds(-16,-10,448,366);
                verify(graphics,atLeastOnce()).fill(intThat(x -> x>close.x()&&x<close.x()+close.width()),intThat(y -> y>close.y()&&y<close.y()+close.height()),anyInt(),anyInt(),eq(0xFFFFD366));
            }
        }
    }
    @Test void translatedCouncilCardsAndFullWidthActionsFitWithoutHidingTheirNextStep() throws Exception {
        var font=mock(Font.class); when(font.width(anyString())).thenAnswer(c -> ((String)c.getArgument(0)).length()*6);
        for(String locale:List.of("en_us","de_de","es_es")) {
            try(var stream=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
                var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.function.Function<String,String> text=key -> catalog.get("screen.village-quest.building."+key).getAsString();
                assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply("council_intro"),246,.7f).size()<=2,locale);
                assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply("founding_help"),176,.7f).size()<=4,locale);
                assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply("found_next"),104,.75f).size()<=8,locale+" founding instructions");
                for(String state:List.of("-1","incoming","outgoing","1","2","3"))
                    assertTrue(font.width(text.apply("relation_agreement."+state))*.72f<=125,locale+" compact agreement "+state);
                assertTrue(font.width(catalog.get("screen.village-quest.trade_route.guide_title").getAsString())<=230,locale+" map title");
                assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply("places_intro"),363,.7f).size()<=1,locale);
                assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply("assignment_help"),176,.75f).size()<=6,locale);
                for(String key:List.of("auto_detected","auto_ambiguous","auto_no_village","membership_saved","area_pending"))
                    assertTrue(VillageUiTheme.wrapScaledLines(font,text.apply(key),128,.72f).size()<=5,locale+" "+key);
                for(String key:List.of("open_council","apply_area","move_building")) assertTrue(font.width(text.apply(key))*.75f<=116,locale+" "+key);
                for(String key:List.of("mandate","projects","relations","rename","release","story_target"))
                    assertTrue(font.width(text.apply(key))*.75f<=108,locale+" "+key);
                for(String key:List.of("require_missing","require_ok")) assertTrue(font.width(text.apply(key))*.65f<=39,locale+" "+key);
            }
        }
    }
    private static BuildingPayloads.View view() {
        return new BuildingPayloads.View(UUID.randomUUID(),0,true,BlockPos.ZERO,BuildingScanner.Role.COUNCIL,BuildingScanner.Range.BASIC,
                BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,false,false,3,3,true,"Home",List.of(),List.of(),"",
                new BuildingPayloads.MarketLayout(0,List.of(),List.of(new BuildingPayloads.Stand(0,new BlockPos(0,64,0),new BlockPos(1,64,0),new BlockPos(0,64,1),true)),List.of()),
                BuildingPayloads.Recognition.EMPTY,BuildingPayloads.Diplomacy.EMPTY);
    }
}
